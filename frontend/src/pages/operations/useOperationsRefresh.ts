import { useCallback, useEffect, useRef, useState } from 'react';
import type { Snapshot } from './service';

type Sources = Record<
  string,
  (signal: AbortSignal, at: string) => Promise<unknown>
>;
type SourceData<T extends Sources> = Partial<{
  [K in keyof T]: Awaited<ReturnType<T[K]>>;
}>;
const hidden = () => document.visibilityState === 'hidden';
function retainSnapshotPartitions(
  previous: unknown,
  incoming: unknown,
): unknown {
  if (
    !previous ||
    !incoming ||
    typeof previous !== 'object' ||
    typeof incoming !== 'object'
  )
    return incoming;
  const before = previous as Partial<Snapshot> & Record<string, unknown>;
  const after = incoming as Partial<Snapshot> & Record<string, unknown>;
  if (!after.sourceStatus) return incoming;
  const merged: Partial<Snapshot> &
    Record<string, unknown> & {
      sourceStatus: NonNullable<Snapshot['sourceStatus']>;
    } = { ...after, sourceStatus: { ...after.sourceStatus } };
  for (const [part, status] of Object.entries(after.sourceStatus)) {
    if (status.status === 'ERROR' && part in before) {
      merged[part] = before[part];
      merged.sourceStatus[part] = {
        ...status,
        observedAt: before.sourceStatus?.[part]?.observedAt ?? null,
      };
    }
  }
  return merged;
}
export function useOperationsRefresh<T extends Sources>({
  key,
  live,
  sources,
  delay = 15000,
}: {
  key: string;
  live: boolean;
  sources: T;
  delay?: number;
}) {
  const [data, setData] = useState<SourceData<T>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [updatedAt, setUpdatedAt] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const refreshRef = useRef<() => void>(() => {});
  const refresh = useCallback(() => refreshRef.current(), []);

  useEffect(() => {
    let disposed = false;
    let running = false;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setData({});
    setErrors({});
    setUpdatedAt({});
    const run = async () => {
      if (disposed || running || hidden()) return;
      clearTimeout(timer);
      running = true;
      setLoading(true);
      controller = new AbortController();
      const signal = controller.signal;
      const entries = Object.entries(sources);
      const at = new Date().toISOString();
      const results = await Promise.allSettled(
        entries.map(([, load]) => load(signal, at)),
      );
      if (disposed || signal.aborted) return;
      const successes: Record<string, unknown> = {};
      const failures: Record<string, string> = {};
      const timestamps: Record<string, string> = {};
      results.forEach((result, index) => {
        const name = entries[index][0];
        if (result.status === 'fulfilled') {
          successes[name] = result.value;
          timestamps[name] = new Date().toISOString();
        } else
          failures[name] =
            result.reason instanceof Error
              ? result.reason.message
              : '数据源不可用';
      });
      setData((previous) => {
        if ('snapshot' in successes)
          successes.snapshot = retainSnapshotPartitions(
            previous.snapshot,
            successes.snapshot,
          );
        return { ...previous, ...successes };
      });
      setErrors(failures);
      setUpdatedAt((previous) => ({ ...previous, ...timestamps }));
      setLoading(false);
      running = false;
      if (live && !hidden()) timer = setTimeout(run, delay);
    };
    const visibility = () => {
      clearTimeout(timer);
      if (live && !hidden()) void run();
    };
    refreshRef.current = () => {
      void run();
    };
    document.addEventListener('visibilitychange', visibility);
    void run();
    return () => {
      disposed = true;
      controller?.abort();
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', visibility);
    };
  }, [key, live, sources, delay]);
  return { data, errors, updatedAt, loading, refresh };
}
