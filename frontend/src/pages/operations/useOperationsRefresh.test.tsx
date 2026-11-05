import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useOperationsRefresh } from './useOperationsRefresh';

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

describe('coordinated operations refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('waits for all sources then waits the full delay without overlapping slow requests', async () => {
    const first = deferred<number>();
    const source = vi
      .fn()
      .mockReturnValueOnce(first.promise)
      .mockResolvedValue(2);
    const sources = { snapshot: source };
    const { result } = renderHook(() =>
      useOperationsRefresh({ key: 'live', live: true, sources }),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(45000);
    });
    expect(source).toHaveBeenCalledTimes(1);
    await act(async () => {
      first.resolve(1);
    });
    expect(result.current.data.snapshot).toBe(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(14999);
    });
    expect(source).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(source).toHaveBeenCalledTimes(2);
  });

  it('aborts obsolete filters and ignores late responses while preserving partial successful data', async () => {
    const old = deferred<number>();
    let oldSignal: AbortSignal | undefined;
    const sources: Record<string, (signal: AbortSignal) => Promise<number>> = {
      snapshot: vi.fn((signal: AbortSignal) => {
        oldSignal = signal;
        return old.promise;
      }),
    };
    const current = {
      snapshot: vi.fn().mockResolvedValue(7),
      jobs: vi.fn().mockResolvedValue(3),
    };
    const { result, rerender } = renderHook(
      ({ scope, active }) =>
        useOperationsRefresh({ key: scope, live: false, sources: active }),
      { initialProps: { scope: 'old', active: sources } },
    );
    rerender({ scope: 'new', active: current });
    await act(async () => {});
    expect(oldSignal?.aborted).toBe(true);
    await act(async () => {
      old.resolve(99);
    });
    expect(result.current.data.snapshot).toBe(7);
    current.snapshot.mockRejectedValue(new Error('source timeout'));
    current.jobs.mockResolvedValue(4);
    await act(async () => {
      result.current.refresh();
    });
    expect(result.current.data.snapshot).toBe(7);
    expect(result.current.data.jobs).toBe(4);
    expect(result.current.errors.snapshot).toContain('source timeout');
  });

  it('pauses while hidden, refreshes on return and keeps historical windows static', async () => {
    const sources = { snapshot: vi.fn().mockResolvedValue(1) };
    const { rerender } = renderHook(
      ({ live }) => useOperationsRefresh({ key: 'scope', live, sources }),
      { initialProps: { live: true } },
    );
    await act(async () => {});
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        value: 'hidden',
      });
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(45000);
    });
    expect(sources.snapshot).toHaveBeenCalledTimes(1);
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        value: 'visible',
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(sources.snapshot).toHaveBeenCalledTimes(2);
    rerender({ live: false });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(45000);
    });
    expect(sources.snapshot).toHaveBeenCalledTimes(3);
  });
  it('retains only failed snapshot partitions and their last good timestamp within the same scope', async () => {
    const source = vi.fn().mockResolvedValue({
      resources: [{ instanceId: 'one' }],
      queue: { running: 2 },
      sourceStatus: {
        resources: { status: 'OK', observedAt: '2026-11-05T01:00:00Z' },
        queue: { status: 'OK', observedAt: '2026-11-05T01:00:00Z' },
      },
    });
    const sources = { snapshot: source };
    const { result, rerender } = renderHook(
      ({ scope }) => useOperationsRefresh({ key: scope, live: false, sources }),
      { initialProps: { scope: 'one' } },
    );
    await act(async () => {});
    source.mockResolvedValue({
      resources: [],
      queue: { running: 3 },
      sourceStatus: {
        resources: { status: 'ERROR', observedAt: null },
        queue: { status: 'OK', observedAt: '2026-11-05T01:01:00Z' },
      },
    });
    await act(async () => result.current.refresh());
    expect(result.current.data.snapshot?.resources).toEqual([
      { instanceId: 'one' },
    ]);
    expect(result.current.data.snapshot?.queue).toEqual({ running: 3 });
    expect(result.current.data.snapshot?.sourceStatus.resources).toEqual({
      status: 'ERROR',
      observedAt: '2026-11-05T01:00:00Z',
    });
    rerender({ scope: 'two' });
    await act(async () => {});
    expect(result.current.data.snapshot?.resources).toEqual([]);
  });
});
