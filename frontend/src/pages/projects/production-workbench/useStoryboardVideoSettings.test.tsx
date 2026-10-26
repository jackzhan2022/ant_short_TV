import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useStoryboardVideoSettings, type VideoGenerationSettings } from './useStoryboardVideoSettings';

const api = vi.hoisted(() => ({ save: vi.fn() }));
vi.mock('./service', () => ({ saveStoryboardVideoSettings: api.save }));
const first: VideoGenerationSettings = { durationSeconds: 15, resolution: '720p', generateAudio: true, watermark: false };
const last = { ...first, resolution: '1080p', generateAudio: false };

describe('storyboard video settings autosave', () => {
  it('serializes writes, preserves the latest draft and waits for its write', async () => {
    let finishFirst!: (response: unknown) => void;
    api.save.mockReset().mockImplementationOnce(() => new Promise((resolve) => { finishFirst = resolve; }))
      .mockResolvedValueOnce({ data: last });
    const onSaved = vi.fn();
    const { result } = renderHook(() => useStoryboardVideoSettings(1, onSaved, vi.fn()));
    act(() => { result.current.save(10, first); result.current.save(10, last); });
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(1));
    expect(result.current.latest(10)).toEqual(last);
    expect(result.current.status[10]).toBe('saving');
    let done = false;
    const pending = result.current.wait([10]).then(() => { done = true; });
    expect(done).toBe(false);
    await act(async () => { finishFirst({ data: first }); await pending; });
    expect(api.save.mock.calls.map((call) => call[2])).toEqual([first, last]);
    expect(result.current.latest(10)).toEqual(last);
    expect(result.current.status[10]).toBe('saved');
    expect(onSaved).toHaveBeenLastCalledWith(10, last);
  });

  it('also waits for a newer edit made while generation is already waiting', async () => {
    let finishFirst!: (response: unknown) => void;
    let finishLast!: (response: unknown) => void;
    api.save.mockReset().mockImplementationOnce(() => new Promise((resolve) => { finishFirst = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { finishLast = resolve; }));
    const { result } = renderHook(() => useStoryboardVideoSettings(1, vi.fn(), vi.fn()));
    act(() => result.current.save(10, first));
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(1));
    let done = false;
    const pending = result.current.wait([10]).then(() => { done = true; });
    act(() => result.current.save(10, last));
    await act(async () => finishFirst({ data: first }));
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(2));
    expect(done).toBe(false);
    await act(async () => { finishLast({ data: last }); await pending; });
    expect(done).toBe(true);
    expect(result.current.latest(10)).toEqual(last);
  });

  it('shows failed writes, blocks generation and retries the current draft', async () => {
    api.save.mockReset().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: first });
    const onError = vi.fn();
    const { result } = renderHook(() => useStoryboardVideoSettings(1, vi.fn(), onError));
    act(() => result.current.save(10, first));
    await waitFor(() => expect(result.current.status[10]).toBe('error'));
    expect(onError).toHaveBeenCalledWith(10);
    await expect(result.current.wait([10])).rejects.toThrow('视频参数尚未保存');
    act(() => result.current.retry(10));
    await act(async () => { await result.current.wait([10]); });
    expect(result.current.status[10]).toBe('saved');
  });

  it('restores an in-flight draft and waits for it after returning without making another edit', async () => {
    let finishOld!: (response: unknown) => void;
    api.save.mockReset().mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve; }))
      .mockImplementation(async (_projectId, _shotId, values) => ({ data: values }));
    const old = renderHook(() => useStoryboardVideoSettings(1, vi.fn(), vi.fn()));
    act(() => { old.result.current.save(10, first); old.result.current.save(10, last); });
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(1));
    old.unmount();
    const onSaved = vi.fn();
    const current = renderHook(() => useStoryboardVideoSettings(1, onSaved, vi.fn()));
    const restored = current.result.current.latest(10);
    let done = false;
    const pending = current.result.current.wait([10]).then(() => { done = true; });
    await act(async () => { await Promise.resolve(); });
    const completedBeforeSave = done;
    await act(async () => { finishOld({ data: first }); await pending; });
    expect(restored).toEqual(last);
    expect(completedBeforeSave).toBe(false);
    expect(current.result.current.latest(10)).toEqual(last);
    expect(onSaved).toHaveBeenLastCalledWith(10, last);
  });

  it('keeps writes ordered when leaving and returning to the same project', async () => {
    let finishOld!: (response: unknown) => void;
    api.save.mockReset().mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve; }))
      .mockImplementation(async (_projectId, _shotId, values) => ({ data: values }));
    const previous = renderHook(() => useStoryboardVideoSettings(1, vi.fn(), vi.fn()));
    act(() => { previous.result.current.save(10, first); previous.result.current.save(10, last); });
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(1));
    previous.unmount();
    const newest = { ...last, durationSeconds: 8, watermark: true };
    const current = renderHook(() => useStoryboardVideoSettings(1, vi.fn(), vi.fn()));
    act(() => current.result.current.save(10, newest));
    const pending = current.result.current.wait([10]);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { finishOld({ data: first }); await pending; });
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(3));
    expect(api.save.mock.calls.map((call) => call[2])).toEqual([first, last, newest]);
    expect(current.result.current.status[10]).toBe('saved');
  });

  it('isolates new projects and ignores late writes from the previous project', async () => {
    let finish!: (response: unknown) => void;
    api.save.mockReset().mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }))
      .mockResolvedValueOnce({ data: last });
    const onSaved = vi.fn();
    const { result, rerender } = renderHook(({ projectId }) => useStoryboardVideoSettings(projectId, onSaved, vi.fn()),
      { initialProps: { projectId: 1 } });
    act(() => result.current.save(10, first));
    await waitFor(() => expect(api.save).toHaveBeenCalledTimes(1));
    rerender({ projectId: 2 });
    expect(result.current.latest(10)).toBeUndefined();
    act(() => result.current.save(10, last));
    await act(async () => { finish({ data: first }); await result.current.wait([10]); });
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(result.current.latest(10)).toEqual(last);
    expect(api.save.mock.calls.map((call) => call[0])).toEqual([1,2]);
  });
});
