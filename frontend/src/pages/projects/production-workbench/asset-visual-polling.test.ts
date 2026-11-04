import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAssetVisualPoller, mergeAssetVisualWorkspace } from './asset-visual-polling';
import type { AssetVisualStatus } from './service';

describe('batched asset generation polling', () => {
  let hidden = false;
  let poller: ReturnType<typeof createAssetVisualPoller>;
  const target = (id: number) => ({ assetType: 'CHARACTER' as const, assetId: id, variantId: id, generationTaskId: id + 100 });
  const state = (id: number, generationStatus = 'GENERATING', displayReady = false): AssetVisualStatus => ({
    variantId: id, available: true, generationTaskId: id + 100, generationStatus, displayReady,
  });
  const workspace = { variants: Array.from({ length: 10 }, (_, i) => ({
    id: i + 1, assetType: 'CHARACTER' as const, assetId: i + 1, name: `形态${i+1}`,
    generationStatus: 'COMPLETED', sourceType: 'USER', primary: true, usable: true,
  })), variantCount: 10, episodeBindings: [], generationSummary: {} };
  let readStatus = vi.fn();
  let readWorkspace = vi.fn();
  let onWorkspace = vi.fn();
  let onSettled = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers(); hidden = false;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    readStatus = vi.fn(async (ids: number[]) => ids.map(id => state(id)));
    readWorkspace = vi.fn(async () => workspace);
    onWorkspace = vi.fn(); onSettled = vi.fn();
    poller = createAssetVisualPoller({ readStatus, readWorkspace, onWorkspace, onSettled, random: () => 0 });
  });
  afterEach(() => { poller.stop(); vi.useRealTimers(); vi.restoreAllMocks(); });

  it('combines 100 variants and never reloads unchanged workspaces', async () => {
    poller.watch(Array.from({ length: 100 }, (_, i) => target(i + 1)));
    await vi.advanceTimersByTimeAsync(0);
    expect(readStatus).toHaveBeenCalledTimes(1);
    expect(readStatus.mock.calls[0][0]).toHaveLength(100);
    await vi.advanceTimersByTimeAsync(3000);
    expect(readStatus).toHaveBeenCalledTimes(2);
    expect(readWorkspace).not.toHaveBeenCalled();
  });

  it('waits for publication then refreshes each changed asset once and stops', async () => {
    poller.watch([target(1), { ...target(1), variantId: 2 }]);
    readStatus.mockResolvedValue([state(1, 'COMPLETED'), state(2)]);
    await vi.advanceTimersByTimeAsync(0);
    expect(readWorkspace).not.toHaveBeenCalled();
    readStatus.mockResolvedValue([state(1, 'COMPLETED', true), state(2, 'FAILED')]);
    await vi.advanceTimersByTimeAsync(3000);
    expect(readWorkspace).toHaveBeenCalledTimes(1);
    expect(onWorkspace).toHaveBeenCalledWith(target(1), workspace);
    expect(onSettled).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(30000);
    expect(readStatus).toHaveBeenCalledTimes(2);
  });

  it('pauses hidden pages and refreshes immediately on visibility restoration', async () => {
    hidden = true; poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(15000);
    expect(readStatus).not.toHaveBeenCalled();
    hidden = false; document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
    expect(readStatus).toHaveBeenCalledTimes(1);
    hidden = true; document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(15000);
    expect(readStatus).toHaveBeenCalledTimes(1);
  });

  it('never overlaps requests and cancels in-flight reads when stopped', async () => {
    let resolve!: (states: AssetVisualStatus[]) => void;
    readStatus.mockImplementation(() => new Promise<AssetVisualStatus[]>(done => { resolve = done; }));
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(10000);
    expect(readStatus).toHaveBeenCalledTimes(1);
    const signal = readStatus.mock.calls[0][1] as AbortSignal;
    poller.stop(); expect(signal.aborted).toBe(true);
    resolve([state(1, 'COMPLETED', true)]);
    await vi.advanceTimersByTimeAsync(10000);
    expect(readWorkspace).not.toHaveBeenCalled();
    expect(onWorkspace).not.toHaveBeenCalled();
  });

  it('retries failed reads with backoff and keeps completed assets pending until refreshed', async () => {
    readStatus.mockRejectedValueOnce(new Error('temporary failure'));
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(3000);
    expect(readStatus).toHaveBeenCalledTimes(1);
    readStatus.mockResolvedValue([state(1, 'FAILED')]);
    readWorkspace.mockRejectedValueOnce(new Error('detail unavailable'));
    await vi.advanceTimersByTimeAsync(3000);
    expect(onSettled).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(12000);
    expect(onWorkspace).toHaveBeenCalledTimes(1);
    expect(onSettled).toHaveBeenCalledTimes(1);
  });

  it('limits simultaneous completion detail reads to two', async () => {
    let running = 0; let maximum = 0;
    readStatus.mockImplementation(async (ids: number[]) => ids.map(id => state(id, 'COMPLETED', true)));
    readWorkspace.mockImplementation(async () => {
      running++; maximum = Math.max(maximum, running);
      await new Promise(resolve => setTimeout(resolve, 100)); running--;
      return workspace;
    });
    poller.watch(Array.from({ length: 10 }, (_, i) => target(i + 1)));
    await vi.advanceTimersByTimeAsync(1000);
    expect(maximum).toBe(2);
    expect(onWorkspace).toHaveBeenCalledTimes(10);
  });

  it('backs off unchanged jobs and never revives an already settled generation', async () => {
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(30000);
    const count = readStatus.mock.calls.length;
    await vi.advanceTimersByTimeAsync(3000);
    expect(readStatus).toHaveBeenCalledTimes(count);
    readStatus.mockResolvedValue([state(1, 'COMPLETED', true)]);
    await vi.advanceTimersByTimeAsync(2000);
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(10000);
    expect(readWorkspace).toHaveBeenCalledTimes(1);
  });

  it('deduplicates stale workspace and newly submitted targets before updating an active read', async () => {
    const newer = { ...target(1), generationTaskId: 999 };
    let resolve!: (states: AssetVisualStatus[]) => void;
    readStatus.mockImplementation(() => new Promise<AssetVisualStatus[]>(done => { resolve = done; }));
    poller.watch([target(1), newer]);
    await vi.advanceTimersByTimeAsync(0);
    poller.watch([target(1), newer]);
    resolve([{ ...state(1, 'COMPLETED', true), generationTaskId: 999 }]);
    await vi.advanceTimersByTimeAsync(0);
    expect(onWorkspace).toHaveBeenCalledTimes(1);
    expect(onSettled).toHaveBeenCalledWith(newer, expect.objectContaining({ generationTaskId: 999 }));
  });

  it('does not finish successful tasks when the workspace response is empty', async () => {
    readStatus.mockResolvedValue([state(1, 'COMPLETED', true)]);
    readWorkspace.mockResolvedValueOnce(undefined);
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(0);
    expect(onSettled).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(6000);
    expect(onWorkspace).toHaveBeenCalledTimes(1);
  });

  it('does not apply an asset detail snapshot after another generation starts on the same asset', async () => {
    readStatus.mockResolvedValue([state(1, 'COMPLETED', true)]);
    let resolve!: (visual: typeof workspace) => void;
    readWorkspace.mockImplementationOnce(() => new Promise<typeof workspace>(done => { resolve = done; }));
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(0);
    poller.watch([{ ...target(2), assetId: 1 }]);
    resolve(workspace);
    await vi.advanceTimersByTimeAsync(0);
    expect(onWorkspace).not.toHaveBeenCalled();
    expect(onSettled).not.toHaveBeenCalled();
  });

  it('splits large reads into batches of 100 and retires unavailable variants without details', async () => {
    readStatus.mockImplementation(async (ids: number[]) => ids.map(variantId => ({
      ...state(variantId, 'UNAVAILABLE'), available: false,
    })));
    poller.watch(Array.from({ length: 201 }, (_, i) => target(i + 1)));
    await vi.advanceTimersByTimeAsync(0);
    expect(readStatus.mock.calls.map(call => call[0].length)).toEqual([100, 100, 1]);
    expect(readWorkspace).not.toHaveBeenCalled();
    expect(onSettled).toHaveBeenCalledTimes(201);
  });

  it('aborts hidden in-flight reads and ignores their late completion after resuming', async () => {
    let resolve!: (states: AssetVisualStatus[]) => void;
    readStatus.mockImplementationOnce(() => new Promise<AssetVisualStatus[]>(done => { resolve = done; }));
    poller.watch([target(1)]);
    await vi.advanceTimersByTimeAsync(0);
    const signal = readStatus.mock.calls[0][1] as AbortSignal;
    hidden = true; document.dispatchEvent(new Event('visibilitychange'));
    expect(signal.aborted).toBe(true);
    hidden = false; document.dispatchEvent(new Event('visibilitychange'));
    resolve([state(1, 'COMPLETED', true)]);
    await vi.advanceTimersByTimeAsync(0);
    expect(onWorkspace).not.toHaveBeenCalled();
    expect(readStatus).toHaveBeenCalledTimes(2);
  });

  it('reads and merges completed variants outside the first detail page before retiring them', async () => {
    const variant = (id: number) => ({ id, assetType: 'CHARACTER' as const, assetId: 1,
      name: `形态${id}`, sourceType: 'USER', generationStatus: 'COMPLETED', primary: false, usable: true });
    readStatus.mockResolvedValue([state(21, 'COMPLETED', true), state(22, 'COMPLETED', true)]);
    readWorkspace.mockImplementation(async (item: ReturnType<typeof target>) => ({
      ...workspace, selectedVariant: variant(item.variantId), variants: [variant(1)],
    }));
    poller.watch([{ ...target(21), assetId: 1 }, { ...target(22), assetId: 1 }]);
    await vi.advanceTimersByTimeAsync(0);
    expect(readWorkspace).toHaveBeenCalledTimes(2);
    expect(onWorkspace.mock.calls[0][1].variants.map((item: { id: number }) => item.id))
      .toEqual(expect.arrayContaining([1, 21, 22]));
    expect(onSettled).toHaveBeenCalledTimes(2);
  });

  it('uses an available variant to load a group containing a deleted variant', async () => {
    readStatus.mockResolvedValue([{ ...state(1, 'UNAVAILABLE'), available: false }, state(2, 'COMPLETED', true)]);
    readWorkspace.mockImplementation(async (item: ReturnType<typeof target>) => {
      if (item.variantId === 1) throw new Error('deleted variant');
      return workspace;
    });
    poller.watch([target(1), { ...target(2), assetId: 1 }]);
    await vi.advanceTimersByTimeAsync(0);
    expect(onWorkspace).toHaveBeenCalledTimes(1);
    expect(onSettled).toHaveBeenCalledTimes(2);
  });

  it('preserves loaded pages while replacing completed variant snapshots', () => {
    const old = { ...workspace, variants: [{ ...workspace.variants[0], id: 21, currentImageUrl: '/old.png' }] };
    const next = { ...workspace, selectedVariant: { ...workspace.variants[0], id: 22 } };
    const merged = mergeAssetVisualWorkspace(old, next);
    expect(merged.variants.map(variant => variant.id)).toEqual(expect.arrayContaining([1, 21, 22]));
    expect(mergeAssetVisualWorkspace(merged, { ...next, selectedVariant: {
      ...workspace.variants[0], id: 21, currentImageUrl: '/new.png',
    } }).variants.find(variant => variant.id === 21)?.currentImageUrl).toBe('/new.png');
  });
});
