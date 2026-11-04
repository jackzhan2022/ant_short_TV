import type { AssetVisualStatus, AssetVisualWorkspace, ScriptElementType } from './service';

export function mergeAssetVisualWorkspace(previous: AssetVisualWorkspace | undefined, next: AssetVisualWorkspace) {
  const variants = new Map([
    ...(previous?.variants || []), previous?.primaryVariant, previous?.selectedVariant,
    ...next.variants, next.primaryVariant, next.selectedVariant,
  ].filter((variant): variant is NonNullable<typeof variant> => Boolean(variant))
    .map(variant => [variant.id, variant]));
  return { ...next, variants: [...variants.values()] };
}

export type VisualPollTarget = {
  assetType: Exclude<ScriptElementType, 'ALL'>;
  assetId: number;
  variantId: number;
  generationTaskId?: number | null;
  generationKey?: string;
};

type Options = {
  readStatus: (ids: number[], signal: AbortSignal) => Promise<AssetVisualStatus[]>;
  readWorkspace: (target: VisualPollTarget, signal: AbortSignal) => Promise<AssetVisualWorkspace>;
  onWorkspace: (target: VisualPollTarget, workspace: AssetVisualWorkspace) => void;
  onSettled: (target: VisualPollTarget, status: AssetVisualStatus) => void;
  random?: () => number;
};

const targetKey = (target: VisualPollTarget) => `${target.assetType}:${target.assetId}:${target.variantId}`;
const assetKey = (target: VisualPollTarget) => `${target.assetType}:${target.assetId}`;

export function createAssetVisualPoller(options: Options) {
  const pending = new Map<string, VisualPollTarget>();
  const settled = new Map<string, number | null | undefined>();
  const versions = new Map<number, string>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let controller: AbortController | undefined;
  let running = false;
  let stopped = false;
  let wakeRequested = false;
  let failures = 0;
  let changedAt = Date.now();

  const schedule = (delay: number) => {
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
    if (!stopped && pending.size && !document.hidden) {
      timer = setTimeout(() => { timer = undefined; void poll(); }, delay);
    }
  };

  const poll = async () => {
    if (stopped || running || document.hidden || !pending.size) return;
    running = true;
    wakeRequested = false;
    const currentController = new AbortController();
    controller = currentController;
    const { signal } = currentController;
    const alive = () => !stopped && !signal.aborted && !document.hidden;
    try {
      const snapshot = [...pending.values()];
      const ids = [...new Set(snapshot.map(target => target.variantId))];
      const states = new Map<number, AssetVisualStatus>();
      // Bound both HTTP request size and concurrent database work for large batches.
      for (let offset = 0; offset < ids.length && alive(); offset += 100) {
        const rows = await options.readStatus(ids.slice(offset, offset + 100), signal);
        for (const row of rows) states.set(row.variantId, row);
      }
      if (!alive()) return;
      const completed = new Map<string, { target: VisualPollTarget; state: AssetVisualStatus }[]>();
      for (const target of snapshot) {
        if (pending.get(targetKey(target)) !== target) continue;
        const state = states.get(target.variantId);
        // Missing responses are retried; they must never look like successful completion.
        if (!state) throw new Error('Missing asset generation status');
        const version = JSON.stringify(state);
        if (versions.get(target.variantId) !== version) {
          changedAt = Date.now(); versions.set(target.variantId, version);
        }
        if (state.generationStatus === 'GENERATING'
          || (state.generationStatus === 'COMPLETED' && !state.displayReady)) continue;
        const key = assetKey(target);
        const group = completed.get(key) || [];
        group.push({ target, state }); completed.set(key, group);
      }
      const groups = [...completed.values()];
      let next = 0;
      let detailFailed = false;
      const refresh = async () => {
        while (next < groups.length && alive()) {
          const group = groups[next++];
          try {
            const first = (group.find(item => item.state.available) || group[0]).target;
            const needsWorkspace = group.some(item => item.state.available);
            let visual = needsWorkspace
              ? await options.readWorkspace(first, signal) : undefined;
            if (!alive()) return;
            if (needsWorkspace && !visual) throw new Error('Missing asset visual workspace');
            if (visual) {
              visual = mergeAssetVisualWorkspace(undefined, visual);
              for (const { target, state } of group) {
                if (!alive()) return;
                if (state.available && !visual.variants.some(variant => variant.id === target.variantId)) {
                  const detail = await options.readWorkspace(target, signal);
                  if (!detail) throw new Error('Missing asset variant detail');
                  visual = mergeAssetVisualWorkspace(visual, detail);
                }
              }
            }
            if (!alive()) return;
            // A generation submitted while this detail request was running must not
            // be overwritten by its older asset snapshot, even for another variant.
            if ([...pending.values()].some(target => assetKey(target) === assetKey(first)
              && !snapshot.includes(target))) continue;
            const current = group.filter(item => pending.get(targetKey(item.target)) === item.target);
            if (!current.length) continue;
            if (visual) options.onWorkspace(first, visual);
            for (const { target, state } of current) {
              const latest = visual?.variants.find(variant => variant.id === target.variantId);
              if (state.available && !latest) { detailFailed = true; continue; }
              if (latest?.generationStatus === 'GENERATING') continue;
              const key = targetKey(target);
              pending.delete(key); settled.set(key, target.generationTaskId);
              options.onSettled(target, state);
            }
          } catch {
            if (alive()) detailFailed = true;
          }
        }
      };
      await Promise.all([refresh(), refresh()]);
      failures = detailFailed ? failures + 1 : 0;
    } catch {
      if (alive()) failures++;
    } finally {
      running = false;
      if (controller === currentController) controller = undefined;
      const quietFor = Date.now() - changedAt;
      const interval = quietFor >= 120000 ? 10000 : quietFor >= 30000 ? 5000 : 3000;
      const delay = failures ? Math.min(30000, 3000 * 2 ** Math.min(failures, 4)) : interval;
      schedule(wakeRequested ? 0 : delay + Math.floor((options.random || Math.random)() * 400));
    }
  };

  const visibilityChanged = () => {
    if (document.hidden) {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined; controller?.abort();
    } else if (running) wakeRequested = true;
    else schedule(0);
  };
  document.addEventListener('visibilitychange', visibilityChanged);
  return {
    watch(targets: VisualPollTarget[]) {
      if (stopped) return;
      let added = false;
      for (const target of new Map(targets.map(target => [targetKey(target), target])).values()) {
        const key = targetKey(target);
        if (settled.has(key) && settled.get(key) === target.generationTaskId) continue;
        const existing = pending.get(key);
        if (existing && existing.generationTaskId === target.generationTaskId) {
          if (target.generationKey) existing.generationKey = target.generationKey;
          continue;
        }
        pending.set(key, { ...target }); added = true;
      }
      if (added) {
        changedAt = Date.now(); failures = 0;
        if (running) wakeRequested = true;
        else schedule(0);
      }
    },
    stop() {
      stopped = true;
      if (timer !== undefined) clearTimeout(timer);
      controller?.abort(); pending.clear();
      document.removeEventListener('visibilitychange', visibilityChanged);
    },
  };
}
