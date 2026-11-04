import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import useAssetVisualPolling from './useAssetVisualPolling';

const reads = vi.hoisted(() => ({ status: vi.fn(), workspace: vi.fn() }));
vi.mock('./service', () => ({ queryAssetVisualStatuses: reads.status, queryAssetVisualWorkspace: reads.workspace }));
const target = { assetType: 'CHARACTER' as const, assetId: 1, variantId: 21, generationTaskId: 100 };
const completed = { variantId: 21, generationStatus: 'COMPLETED', available: true, displayReady: true };

beforeEach(() => {
  vi.useFakeTimers(); vi.clearAllMocks();
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
});
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

it('loads the explicitly tracked off-page variant when it completes', async () => {
  reads.status.mockResolvedValue({ data: [completed] });
  reads.workspace.mockResolvedValue({ data: { variants: [], generationSummary: {}, episodeBindings: [], variantCount: 21,
    selectedVariant: { id: 21, generationStatus: 'COMPLETED' } } });
  const onWorkspace = vi.fn();
  const { unmount } = renderHook(() => useAssetVisualPolling(1, [target], onWorkspace, vi.fn()));
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  expect(reads.workspace).toHaveBeenCalledWith(1, 'CHARACTER', 1, { selectedVariantId: 21 }, expect.any(AbortSignal));
  expect(onWorkspace.mock.calls[0][1].variants).toEqual([expect.objectContaining({ id: 21 })]);
  unmount();
});

it('aborts old project reads and discards their late completion', async () => {
  let resolve!: (value: unknown) => void;
  reads.status.mockReturnValue(new Promise(done => { resolve = done; }));
  const onWorkspace = vi.fn(); const onSettled = vi.fn();
  const { rerender, unmount } = renderHook(({ projectId, targets }) =>
    useAssetVisualPolling(projectId, targets, onWorkspace, onSettled),
  { initialProps: { projectId: 1, targets: [target] } });
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  const signal = reads.status.mock.calls[0][2] as AbortSignal;
  rerender({ projectId: 2, targets: [] });
  expect(signal.aborted).toBe(true);
  await act(async () => { resolve({ data: [completed] }); await vi.advanceTimersByTimeAsync(10000); });
  expect(reads.status).toHaveBeenCalledTimes(1);
  expect(reads.workspace).not.toHaveBeenCalled();
  expect(onWorkspace).not.toHaveBeenCalled();
  expect(onSettled).not.toHaveBeenCalled();
  unmount();
});
