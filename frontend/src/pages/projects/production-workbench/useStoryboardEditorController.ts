import { useEffect, useMemo, useRef } from 'react';
import { createStoryboardDraftStore, type StoryboardDraft } from './storyboard-drafts';
import { getStoryboardPrompt, getStoryboardScriptText, getStoryboardSavePayload,
  parseStoryboardScriptText, getStoryboardProps, getReferenceDisplayFields,
  preserveReferenceClientKeys, type StoryboardReferenceDisplayFields } from './storyboard-model';
import { updateStoryboard, replaceStoryboardAssetReferences,
  type StoryboardShot, type SaveStoryboardValues, type StoryboardAssetReference } from './service';

type Callbacks = {
  readShot: (id: number) => StoryboardShot | undefined;
  patchShot: (id: number, values: Partial<StoryboardShot>) => void;
  refresh: () => Promise<boolean>;
  message: { success: (text: string) => unknown; error: (text: string) => unknown };
};
export type StoryboardSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';
type Queue = { version: number; tail: Promise<unknown>; values?: SaveStoryboardValues };
type ReferenceQueue = { tail: Promise<void>; latestVersion: number; pendingCount: number;
  confirmedReferences: StoryboardAssetReference[]; confirmedFields: StoryboardReferenceDisplayFields };

export function createStoryboardEditor(projectId: number, callbacks: Callbacks) {
  const drafts = createStoryboardDraftStore();
  const sources = { current: {} as StoryboardDraft };
  const queues = new Map<number, Queue>();
  const referenceSaveStates = { current: new Map<number, ReferenceQueue>() };
  const statuses = new Map<number, StoryboardSaveStatus>();
  const revisions = new Map<number, number>();
  const listeners = new Map<number, Set<() => void>>();
  let active = true;
  const publish = (id: number, status: StoryboardSaveStatus) => {
    if (statuses.get(id) === status) return;
    statuses.set(id, status);
    if (active) listeners.get(id)?.forEach((notify) => { notify(); });
  };
  const loadDrafts = (shots: StoryboardShot[]) => {
    const previousSources = sources.current;
    const incoming = Object.fromEntries(shots.map((shot) => [shot.id, {
      scriptText: getStoryboardScriptText(shot), videoPrompt: getStoryboardPrompt(shot), promptDocument: shot.promptDocument,
    }]));
    sources.current = { ...previousSources, ...incoming };
    drafts.set((previous) => ({ ...previous, ...Object.fromEntries(shots.map((shot) => {
      const draft = previous[shot.id];
      const source = previousSources[shot.id];
      const edited = draft && source && (draft.scriptText !== source.scriptText || draft.videoPrompt !== source.videoPrompt
        || JSON.stringify(draft.promptDocument ?? null) !== JSON.stringify(source.promptDocument ?? null));
      return [shot.id, edited ? draft : incoming[shot.id]];
    })) }));
  };
  const updateDraft = (id: number, values: Partial<StoryboardDraft[number]>) => {
    revisions.set(id, (revisions.get(id) || 0) + 1);
    drafts.set((previous) => ({ ...previous, [id]: {
      scriptText: previous[id]?.scriptText || '', videoPrompt: previous[id]?.videoPrompt || '',
      promptDocument: previous[id]?.promptDocument, ...values,
    } }));
    publish(id, 'dirty');
  };
  const persist = (id: number, values: SaveStoryboardValues) => {
    const queue = queues.get(id) || { version: 0, tail: Promise.resolve() };
    queues.set(id, queue);
    const version = ++queue.version;
    const revision = revisions.get(id);
    queue.values = values;
    publish(id, 'saving');
    const next = queue.tail.catch(() => undefined).then(() => updateStoryboard(projectId, id, values));
    queue.tail = next;
    void next.then(() => {
      if (queue.version === version) publish(id, revisions.get(id) === revision ? 'saved' : 'dirty');
    }, () => {
      if (queue.version === version) publish(id, 'error');
    });
    return next;
  };
  const saveFields = async (storyboard: StoryboardShot, values: Partial<SaveStoryboardValues>) => {
    const shot = (active && callbacks.readShot(storyboard.id)) || storyboard;
    const previous = drafts.get(shot.id);
    const next = { ...shot, ...values };
    const source = sources.current[shot.id];
    const savedDraft = {
      scriptText: previous && previous.scriptText !== source?.scriptText ? previous.scriptText : getStoryboardScriptText(next),
      videoPrompt: values.videoPrompt ?? previous?.videoPrompt ?? getStoryboardPrompt(next),
      promptDocument: values.promptDocument ?? previous?.promptDocument ?? next.promptDocument ?? undefined,
    };
    const parsedDraft = parseStoryboardScriptText(next, savedDraft.scriptText);
    const scriptWasSubmitted = parsedDraft.scene === next.scene
      && parsedDraft.visualDescription === next.visualDescription && parsedDraft.dialogue === next.dialogue;
    // A prompt-only save can coexist with a dirty script. Confirm the submitted fields,
    // not all text currently displayed in the editor.
    const confirmedDraft = { ...savedDraft,
      scriptText: scriptWasSubmitted ? savedDraft.scriptText : getStoryboardScriptText(next),
    };
    drafts.set((all) => ({ ...all, [shot.id]: savedDraft }));
    if (active) callbacks.patchShot(shot.id, { ...next, videoPrompt: savedDraft.videoPrompt, promptDocument: savedDraft.promptDocument });
    const saving = persist(shot.id, getStoryboardSavePayload(next, {
      videoPrompt: savedDraft.videoPrompt, promptDocument: savedDraft.promptDocument,
    }));
    try {
      await saving;
      sources.current[shot.id] = confirmedDraft;
      if (queues.get(shot.id)?.tail === saving) {
        const currentDraft = drafts.get(shot.id);
        const dirty = currentDraft.scriptText !== confirmedDraft.scriptText
          || currentDraft.videoPrompt !== confirmedDraft.videoPrompt
          || JSON.stringify(currentDraft.promptDocument ?? null) !== JSON.stringify(confirmedDraft.promptDocument ?? null);
        publish(shot.id, dirty ? 'dirty' : 'saved');
        if (active && !dirty && currentDraft === savedDraft) callbacks.message.success('分镜已保存');
      }
      return true;
    } catch {
      // The user's draft remains editable. An older failed request never rolls it back.
      if (active && queues.get(shot.id)?.tail === saving) callbacks.message.error('分镜保存失败');
      return false;
    }
  };
  const saveScript = async (shot: StoryboardShot) => {
    const scriptText = drafts.get(shot.id)?.scriptText;
    if (!scriptText || scriptText === getStoryboardScriptText(shot)) return;
    if (await saveFields(shot, parseStoryboardScriptText(shot, scriptText)) && active) await callbacks.refresh();
  };
  const replaceReferences = async (storyboard: StoryboardShot, references: StoryboardAssetReference[]) => {
    const previousReferences = storyboard.assetReferences || [];
    const displayFields = getReferenceDisplayFields(references);
    const applyReferences = (next: StoryboardAssetReference[], fields = displayFields) => {
      if (active) callbacks.patchShot(storyboard.id, { ...fields, assetReferences: next });
    };
    applyReferences(references);
    let saveState = referenceSaveStates.current.get(storyboard.id);
    if (!saveState) {
      saveState = { tail: Promise.resolve(), latestVersion: 0, pendingCount: 0,
        confirmedReferences: previousReferences, confirmedFields: {
          characters: storyboard.characters, scene: storyboard.scene, props: getStoryboardProps(storyboard),
        } };
      referenceSaveStates.current.set(storyboard.id, saveState);
    }
    const currentSaveState = saveState;
    const version = ++currentSaveState.latestVersion;
    currentSaveState.pendingCount += 1;
    currentSaveState.tail = currentSaveState.tail.then(async () => {
      try {
        const response = await replaceStoryboardAssetReferences(projectId, storyboard.id, references.map((reference) => ({
          assetType: reference.assetType, assetId: reference.assetId, variantId: reference.variantId,
          referenceRole: reference.referenceRole, sortOrder: reference.sortOrder, sourceName: reference.sourceName,
        })));
        const savedReferences = preserveReferenceClientKeys(references, response.data || references);
        currentSaveState.confirmedReferences = savedReferences;
        currentSaveState.confirmedFields = getReferenceDisplayFields(savedReferences);
        if (active && version === currentSaveState.latestVersion) {
          applyReferences(savedReferences, currentSaveState.confirmedFields);
          callbacks.message.success('素材引用已保存');
        }
      } catch {
        if (active && version === currentSaveState.latestVersion) {
          applyReferences(currentSaveState.confirmedReferences, currentSaveState.confirmedFields);
          callbacks.message.error('素材引用保存失败');
        }
      } finally {
        currentSaveState.pendingCount -= 1;
        if (!currentSaveState.pendingCount && referenceSaveStates.current.get(storyboard.id) === currentSaveState) {
          referenceSaveStates.current.delete(storyboard.id);
        }
      }
    });
    await currentSaveState.tail;
  };
  return {
    drafts, sources, loadDrafts, updateDraft, persist, saveFields, saveScript, replaceReferences,
    getStatus: (id: number): StoryboardSaveStatus => statuses.get(id) || 'idle',
    subscribeStatus: (id: number, notify: () => void) => {
      const subscriptions = listeners.get(id) || new Set<() => void>();
      subscriptions.add(notify); listeners.set(id, subscriptions);
      return () => { subscriptions.delete(notify); if (!subscriptions.size) listeners.delete(id); };
    },
    retry: async (id: number) => {
      if (!active) return;
      const shot = callbacks.readShot(id);
      if (!shot) return;
      const draft = drafts.get(id);
      await saveFields(shot, {
        ...queues.get(id)?.values,
        ...(draft ? {
          ...parseStoryboardScriptText(shot, draft.scriptText),
          videoPrompt: draft.videoPrompt, promptDocument: draft.promptDocument ?? undefined,
        } : {}),
      });
    },
    activate: () => { active = true; },
    dispose: () => { active = false; },
  };
}

export type StoryboardEditorController = ReturnType<typeof createStoryboardEditor>;

export default function useStoryboardEditorController(projectId: number, callbacks: Callbacks) {
  const scope = useMemo(() => ({ callbacks, active: true }), [projectId]);
  scope.callbacks = callbacks;
  const current = useRef(scope);
  current.current = scope;
  const isCurrent = () => current.current === scope && scope.active;
  const editor = useMemo(() => createStoryboardEditor(projectId, {
    readShot: (id) => isCurrent() ? scope.callbacks.readShot(id) : undefined,
    patchShot: (id, values) => { if (isCurrent()) scope.callbacks.patchShot(id, values); },
    refresh: () => isCurrent() ? scope.callbacks.refresh() : Promise.resolve(false),
    message: {
      success: (text) => { if (isCurrent()) scope.callbacks.message.success(text); },
      error: (text) => { if (isCurrent()) scope.callbacks.message.error(text); },
    },
  }), [projectId, scope]);
  useEffect(() => {
    scope.active = true;
    editor.activate();
    return () => { scope.active = false; editor.dispose(); };
  }, [editor, scope]);
  return editor;
}
