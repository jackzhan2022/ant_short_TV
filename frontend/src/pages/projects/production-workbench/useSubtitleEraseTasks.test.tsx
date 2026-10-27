import { act, renderHook } from '@testing-library/react';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import useSubtitleEraseTasks from './useSubtitleEraseTasks';
const mocks = vi.hoisted(() => ({ query: vi.fn(), retry: vi.fn() }));
vi.mock('./subtitle-erase-service', () => ({ querySubtitleEraseTasks: mocks.query, retrySubtitleEraseTask: mocks.retry,
  isEraseActive: (task: { status: string }) => ['PENDING', 'RUNNING'].includes(task.status) }));
const pending = { id: 8, storyboardId: 4, sourceResultId: 2, status: 'RUNNING' };
const success = { ...pending, status: 'SUCCEEDED', result: { id: 33, processingType: 'SUBTITLE_ERASE' } };
beforeEach(() => { vi.resetAllMocks(); vi.useFakeTimers(); Object.defineProperty(document, 'hidden', { configurable: true, value: false }); });
afterEach(() => vi.useRealTimers());
it('restores an active task and refreshes the result exactly once after completion', async () => {
  mocks.query.mockResolvedValueOnce([pending]).mockResolvedValue([success]);
  const done = vi.fn(); const view = renderHook(() => useSubtitleEraseTasks(1, [4], true, done));
  await act(async () => { await Promise.resolve(); });
  expect(view.result.current.tasks).toEqual([pending]);
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(done).toHaveBeenCalledExactlyOnceWith(success);
  await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
  expect(done).toHaveBeenCalledOnce(); view.unmount();
});
it('does not switch a completed historical result into preview when opening the page', async () => {
  mocks.query.mockResolvedValue([success]); const done = vi.fn();
  const view = renderHook(() => useSubtitleEraseTasks(1, [4], true, done));
  await act(async () => { await Promise.resolve(); });
  expect(done).not.toHaveBeenCalled(); view.unmount();
});
it('aborts an old project read and ignores its late response', async () => {
  let old!: (value: unknown) => void;
  mocks.query.mockImplementationOnce(() => new Promise(resolve => { old = resolve; })).mockResolvedValue([]);
  const done = vi.fn(); const view = renderHook(({ project }) => useSubtitleEraseTasks(project, [4], true, done), { initialProps: { project: 1 } });
  const signal = mocks.query.mock.calls[0][2]; view.rerender({ project: 2 });
  await act(async () => { old([success]); await Promise.resolve(); });
  expect(signal.aborted).toBe(true); expect(view.result.current.tasks).toEqual([]); expect(done).not.toHaveBeenCalled(); view.unmount();
});

it('retries a completed result refresh after a temporary media-list failure', async () => {
  mocks.query.mockResolvedValueOnce([pending]).mockResolvedValue([success]);
  const done = vi.fn().mockRejectedValueOnce(new Error('结果刷新失败')).mockResolvedValue(undefined);
  const view = renderHook(() => useSubtitleEraseTasks(1, [4], true, done));
  await act(async () => { await Promise.resolve(); });
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(view.result.current.error).toBe('结果刷新失败');
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(done).toHaveBeenCalledTimes(2); expect(view.result.current.error).toBe(''); view.unmount();
});
