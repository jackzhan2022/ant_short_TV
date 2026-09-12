import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { conflictingExecutionId, useAssetExtractionTracking } from './useAssetExtractionTracking';

const { poll } = vi.hoisted(() => ({ poll: vi.fn() }));
vi.mock('@/services/ai-execution/task', () => ({ aiExecutionTaskService: { poll } }));
beforeEach(() => {
  poll.mockReset();
  sessionStorage.clear();
  localStorage.setItem('currentTenantId', '10');
});

it('restores only the current project task without submitting new work', async () => {
  sessionStorage.setItem('asset-extraction:10:33', '601');
  sessionStorage.setItem('asset-extraction:11:33', '999');
  poll.mockResolvedValue({ id: 601, status: 'SUCCEEDED' });
  const complete = vi.fn().mockResolvedValue(undefined);
  const { result } = renderHook(() => useAssetExtractionTracking(33, complete, vi.fn()));
  await waitFor(() => expect(complete).toHaveBeenCalledWith({ id: 601, status: 'SUCCEEDED' }));
  expect(poll).toHaveBeenCalledWith(10, 601, expect.any(Function), 1500, expect.any(AbortSignal));
  expect(sessionStorage.getItem('asset-extraction:10:33')).toBeNull();
  expect(sessionStorage.getItem('asset-extraction:11:33')).toBe('999');
  expect(result.current.busy).toBe(false);
});

it.each(['FAILED', 'CANCELED', 'TIMED_OUT'])('clears loading and recovery state after %s', async (status) => {
  poll.mockResolvedValue({ id: 77, status });
  const { result } = renderHook(() => useAssetExtractionTracking(33, vi.fn(), vi.fn()));
  act(() => result.current.follow(77));
  await waitFor(() => expect(result.current.task?.status).toBe(status));
  expect(result.current.busy).toBe(false);
  expect(sessionStorage.getItem('asset-extraction:10:33')).toBeNull();
});

it('keeps a task reference on network failure and stops polling after unmount', async () => {
  poll.mockRejectedValue(new Error('offline'));
  const error = vi.fn();
  const { result, unmount } = renderHook(() => useAssetExtractionTracking(33, vi.fn(), error));
  act(() => result.current.follow(77));
  await waitFor(() => expect(error).toHaveBeenCalled());
  expect(result.current.busy).toBe(false);
  expect(sessionStorage.getItem('asset-extraction:10:33')).toBe('77');
  const signal = poll.mock.calls[0][4] as AbortSignal;
  unmount();
  expect(signal.aborted).toBe(true);
});

it('only follows typed conflicts with a valid execution reference', () => {
  expect(conflictingExecutionId({ response: { data: {
    errorCode: 'ASSET_EXTRACTION_CONFLICT', data: { executionId: 12 },
  } } })).toBe(12);
  expect(conflictingExecutionId({ info: {
    errorCode: 'FORBIDDEN', data: { executionId: 12 },
  } })).toBeUndefined();
  expect(conflictingExecutionId(null)).toBeUndefined();
});

it('clears a prior project task and busy state when navigating to another project', async () => {
  poll.mockReturnValue(new Promise(() => {}));
  sessionStorage.setItem('asset-extraction:10:33', '601');
  const { result, rerender } = renderHook(({ project }) => useAssetExtractionTracking(project, vi.fn(), vi.fn()), {
    initialProps: { project: 33 },
  });
  await waitFor(() => expect(result.current.busy).toBe(true));
  rerender({ project: 34 });
  expect(result.current.busy).toBe(false);
  expect(result.current.task).toBeUndefined();
  expect((poll.mock.calls[0][4] as AbortSignal).aborted).toBe(true);
  expect(sessionStorage.getItem('asset-extraction:10:33')).toBe('601');
});
