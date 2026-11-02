import { useCallback, useSyncExternalStore } from 'react';
import type { StoryboardPromptDocument } from './service';

export type StoryboardDraft = Record<number, {
  scriptText: string;
  videoPrompt: string;
  promptDocument?: StoryboardPromptDocument | null;
}>;

// Each card subscribes to its own draft. Page actions read the latest value
// directly, including edits that have not reached the delayed save yet.
export function createStoryboardDraftStore() {
  let drafts: StoryboardDraft = {};
  const listeners = new Map<number, Set<() => void>>();
  return {
    get: (id: number) => drafts[id],
    set: (update: StoryboardDraft | ((previous: StoryboardDraft) => StoryboardDraft)) => {
      const previous = drafts;
      drafts = typeof update === 'function' ? update(previous) : update;
      for (const [id, callbacks] of listeners) {
        if (previous[id] !== drafts[id]) callbacks.forEach((notify) => { notify(); });
      }
    },
    subscribe: (id: number, notify: () => void) => {
      const callbacks = listeners.get(id) || new Set<() => void>();
      callbacks.add(notify);
      listeners.set(id, callbacks);
      return () => {
        callbacks.delete(notify);
        if (!callbacks.size) listeners.delete(id);
      };
    },
  };
}

export type StoryboardDraftStore = ReturnType<typeof createStoryboardDraftStore>;

export function useStoryboardDraft(store: StoryboardDraftStore, id: number) {
  const subscribe = useCallback((notify: () => void) => store.subscribe(id, notify), [store, id]);
  const snapshot = useCallback(() => store.get(id), [store, id]);
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
