import { useEffect, useMemo, useRef, useState } from 'react';
import { saveStoryboardVideoSettings } from './service';

export type VideoGenerationSettings = {
  durationSeconds: number;
  resolution: string;
  generateAudio: boolean;
  watermark: boolean;
};
export type VideoSettingsSaveStatus = 'saving' | 'saved' | 'error';
type PendingWrite = { projectId: number; id: number; values: VideoGenerationSettings; tail: Promise<void> };
// Survives route unmounts so a returning editor can resume and await pending edits.
const pendingWrites = new Map<string, PendingWrite>();
type Queue = { version: number; tail: Promise<void> };
type Scope = {
  projectId: number;
  disposed: boolean;
  drafts: Record<number, VideoGenerationSettings>;
  status: Record<number, VideoSettingsSaveStatus>;
  queues: Map<number, Queue>;
  adopted: Map<number, PendingWrite>;
};

export function useStoryboardVideoSettings(projectId: number,
  onSaved: (id: number, values: VideoGenerationSettings) => void,
  onError: (id: number) => void) {
  const callbacks = useRef({ onSaved, onError });
  callbacks.current = { onSaved, onError };
  const scope = useMemo<Scope>(() => {
    const next: Scope = { projectId, disposed: false, drafts: {}, status: {}, queues: new Map(), adopted: new Map() };
    for (const write of pendingWrites.values()) {
      if (write.projectId !== projectId) continue;
      next.drafts[write.id] = { ...write.values };
      next.status[write.id] = 'saving';
      next.queues.set(write.id, { version: 0, tail: write.tail });
      next.adopted.set(write.id, write);
    }
    return next;
  }, [projectId]);
  const current = useRef(scope);
  current.current = scope;
  const [view, setView] = useState({ scope, status: { ...scope.status } });
  const active = () => !scope.disposed && current.current === scope;
  const publish = () => { if (active()) setView({ scope, status: { ...scope.status } }); };
  useEffect(() => {
    let live = true;
    scope.disposed = false;
    const settle = (id: number, write: PendingWrite, failed: boolean) => {
      if (!live || scope.disposed || current.current !== scope || scope.queues.get(id)?.version !== 0) return;
      if (failed) {
        scope.status[id] = 'error';
        callbacks.current.onError(id);
      } else {
        scope.drafts[id] = write.values;
        scope.status[id] = 'saved';
        callbacks.current.onSaved(id, write.values);
      }
      setView({ scope, status: { ...scope.status } });
    };
    for (const [id, write] of scope.adopted) {
      void write.tail.then(() => settle(id, write, false), () => settle(id, write, true));
    }
    return () => { live = false; scope.disposed = true; };
  }, [scope]);

  const save = (id: number, values: VideoGenerationSettings) => {
    scope.drafts[id] = { ...values };
    scope.status[id] = 'saving';
    const queue = scope.queues.get(id) || { version: 0, tail: Promise.resolve() };
    scope.queues.set(id, queue);
    const version = ++queue.version;
    const snapshot = { ...values };
    const key = `${scope.projectId}:${id}`;
    const previous = pendingWrites.get(key)?.tail || Promise.resolve();
    const write: PendingWrite = { projectId: scope.projectId, id, values: snapshot, tail: Promise.resolve() };
    queue.tail = previous.catch(() => undefined).then(async () => {
      const response = await saveStoryboardVideoSettings(scope.projectId, id, snapshot);
      write.values = response.data;
      if (!active()) return;
      callbacks.current.onSaved(id, response.data);
      if (version === queue.version) {
        scope.drafts[id] = response.data;
        scope.status[id] = 'saved';
        publish();
      }
    }).catch(() => {
      if (active() && version === queue.version) {
        scope.status[id] = 'error';
        publish();
        callbacks.current.onError(id);
      }
      throw new Error('视频参数尚未保存，请重试');
    });
    const tail = queue.tail;
    write.tail = tail;
    pendingWrites.set(key, write);
    const release = () => { if (pendingWrites.get(key) === write) pendingWrites.delete(key); };
    // Keep the rejecting tail for generation's wait(), while settling the shared queue safely.
    void tail.then(release, release);
    publish();
  };
  const wait = async (ids: number[]) => {
    for (;;) {
      if (!active()) throw new Error('分镜页面已切换，请重新生成');
      const versions = ids.map((id) => scope.queues.get(id)?.version);
      try {
        await Promise.all(ids.map((id) => scope.queues.get(id)?.tail));
      } catch (error) {
        if (ids.some((id, index) => scope.queues.get(id)?.version !== versions[index])) continue;
        throw error;
      }
      if (ids.every((id, index) => scope.queues.get(id)?.version === versions[index])) {
        if (!active()) throw new Error('分镜页面已切换，请重新生成');
        return;
      }
    }
  };
  return {
    status: view.scope === scope ? view.status : scope.status,
    latest: (id: number) => scope.drafts[id],
    save,
    retry: (id: number) => { const values = scope.drafts[id]; if (values) save(id, values); },
    wait,
  };
}
