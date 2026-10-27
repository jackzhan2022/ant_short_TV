import { useCallback, useEffect, useRef, useState } from 'react';
import {
  isEraseActive,
  querySubtitleEraseTasks,
  retrySubtitleEraseTask,
  type SubtitleEraseTask,
} from './subtitle-erase-service';

export default function useSubtitleEraseTasks(
  projectId: number,
  storyboardIds: number[],
  enabled: boolean,
  onComplete: (task: SubtitleEraseTask) => void | Promise<void>,
) {
  const key = storyboardIds.join(','),
    scope = `${projectId}:${key}`;
  const [tasks, setTasks] = useState<SubtitleEraseTask[]>([]),
    [error, setError] = useState('');
  const known = useRef<SubtitleEraseTask[]>([]),
    activeScope = useRef(scope),
    request = useRef<AbortController | undefined>(undefined);
  const retryKeys = useRef(new Map<number, string>());
  const pendingCompletions = useRef(new Set<number>());
  const completed = useRef(onComplete),
    mounted = useRef(true);
  activeScope.current = scope;
  completed.current = onComplete;
  const upsert = useCallback((task: SubtitleEraseTask) => {
    known.current = [
      task,
      ...known.current.filter((item) => item.id !== task.id),
    ].sort((a, b) => b.id - a.id);
    setTasks(known.current);
  }, []);
  const refresh = useCallback(async () => {
    if (!enabled || !key) return;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const requestedScope = `${projectId}:${key}`;
    try {
      const next = await querySubtitleEraseTasks(
        projectId,
        key.split(',').map(Number),
        controller.signal,
      );
      if (
        controller.signal.aborted ||
        !mounted.current ||
        activeScope.current !== requestedScope
      )
        return;
      const previous = new Map(known.current.map((task) => [task.id, task]));
      known.current = next;
      setTasks(next);
      setError('');
      for (const task of next) {
        if (
          task.status === 'SUCCEEDED' &&
          task.result &&
          ((previous.has(task.id) &&
            previous.get(task.id)?.status !== 'SUCCEEDED') ||
            pendingCompletions.current.has(task.id))
        ) {
          pendingCompletions.current.add(task.id);
          await completed.current(task);
          pendingCompletions.current.delete(task.id);
          if (
            controller.signal.aborted ||
            !mounted.current ||
            activeScope.current !== requestedScope
          )
            return;
        }
      }
    } catch (failure) {
      if (
        !controller.signal.aborted &&
        mounted.current &&
        activeScope.current === requestedScope
      )
        setError(
          failure instanceof Error ? failure.message : '字幕擦除任务加载失败',
        );
    }
  }, [projectId, key, enabled]);
  useEffect(() => {
    mounted.current = true;
    known.current = [];
    pendingCompletions.current.clear();
    setTasks([]);
    setError('');
    void refresh();
    const timer = window.setInterval(() => {
      if (
        !document.hidden &&
        (known.current.some(isEraseActive) ||
          pendingCompletions.current.size > 0)
      )
        void refresh();
    }, 3000);
    const visible = () => {
      if (!document.hidden) void refresh();
    };
    window.addEventListener('focus', visible);
    document.addEventListener('visibilitychange', visible);
    return () => {
      mounted.current = false;
      request.current?.abort();
      window.clearInterval(timer);
      window.removeEventListener('focus', visible);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [refresh]);
  const retry = async (id: number) => {
    const requestedScope = activeScope.current;
    const key = retryKeys.current.get(id) || crypto.randomUUID();
    retryKeys.current.set(id, key);
    const next = await retrySubtitleEraseTask(projectId, id, key);
    retryKeys.current.delete(id);
    if (mounted.current && activeScope.current === requestedScope) {
      upsert(next);
      void refresh();
    }
  };
  return { tasks, error, refresh, upsert, retry };
}
