import { useCallback, useEffect, useRef } from 'react';

const PROMPT_SAVE_DELAY_MS = 2500;

export default function useDelayedPromptSave(onSave: () => void) {
  const save = useRef(onSave);
  save.current = onSave;
  const dirty = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const pause = useCallback(() => {
    clearTimeout(timer.current);
    timer.current = undefined;
  }, []);

  const cancel = useCallback(() => {
    pause();
    const pending = dirty.current;
    dirty.current = false;
    return pending;
  }, [pause]);

  const flush = useCallback(() => {
    if (cancel()) save.current();
  }, [cancel]);

  const markDirty = useCallback(() => {
    pause();
    dirty.current = true;
  }, [pause]);

  const schedule = useCallback(() => {
    pause();
    if (dirty.current) timer.current = setTimeout(flush, PROMPT_SAVE_DELAY_MS);
  }, [flush, pause]);

  // A route/page change must not discard edits waiting for the blur delay.
  useEffect(() => () => flush(), [flush]);

  return { markDirty, pause, schedule, flush, cancel };
}
