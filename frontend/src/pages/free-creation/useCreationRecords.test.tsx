import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  type CreationPage,
  type CreationQuery,
  type CreationRecord,
  getCreation,
  listCreations,
} from './api';
import useCreationRecords from './useCreationRecords';

vi.mock('./api', () => ({ listCreations: vi.fn(), getCreation: vi.fn() }));
const query: CreationQuery = { scope: 'mine', current: 1, pageSize: 2 };
const record = (
  id: number,
  status: CreationRecord['status'] = 'SUCCEEDED',
): CreationRecord => ({
  id,
  status,
  mediaType: 'IMAGE',
  prompt: `创作 ${id}`,
  creatorId: 1,
  creatorName: '作者',
  createdAt: '2026-10-23T10:00:00',
  modelId: 1,
  modelName: '模型',
  parameters: {},
  references: [],
  outputs: [],
});
const page = (ids: number[], current = 1, total = 4): CreationPage => ({
  records: ids.map((id) => record(id)),
  total,
  current,
  pageSize: 2,
});
beforeEach(() => vi.resetAllMocks());
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('creation record scrolling', () => {
  it('appends pages once, preserves visible records while loading, and deduplicates overlapping pages', async () => {
    let resolveNext!: (value: CreationPage) => void;
    vi.mocked(listCreations)
      .mockResolvedValueOnce(page([1, 2]))
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveNext = resolve;
          }),
      );
    const { result } = renderHook(() => useCreationRecords(query, '1:0'));
    await waitFor(() => expect(result.current.page.records).toHaveLength(2));
    act(() => {
      result.current.loadMore();
      result.current.loadMore();
    });
    expect(listCreations).toHaveBeenCalledTimes(2);
    expect(result.current.page.records.map((item) => item.id)).toEqual([1, 2]);
    await act(async () => resolveNext(page([2, 3], 2)));
    expect(result.current.page.records.map((item) => item.id)).toEqual([
      1, 2, 3,
    ]);
    expect(result.current.hasMore).toBe(false);
    act(() => result.current.loadMore());
    expect(listCreations).toHaveBeenCalledTimes(2);
  });

  it('keeps earlier pages after a failure and retries the same next page', async () => {
    vi.mocked(listCreations)
      .mockResolvedValueOnce(page([1, 2]))
      .mockRejectedValueOnce(new Error('连接中断'))
      .mockResolvedValueOnce(page([3, 4], 2));
    const { result } = renderHook(() => useCreationRecords(query, '1:0'));
    await waitFor(() => expect(result.current.hasMore).toBe(true));
    act(() => result.current.loadMore());
    await waitFor(() => expect(result.current.moreError).toBe('连接中断'));
    expect(result.current.page.records).toHaveLength(2);
    act(() => result.current.loadMore());
    await waitFor(() => expect(result.current.page.records).toHaveLength(4));
    expect(listCreations).toHaveBeenLastCalledWith(
      { ...query, current: 2 },
      expect.any(AbortSignal),
    );
    expect(result.current.moreError).toBe('');
  });

  it('cancels an old next page when filters change and ignores its late response', async () => {
    let resolveOld!: (value: CreationPage) => void;
    vi.mocked(listCreations)
      .mockResolvedValueOnce(page([1, 2]))
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce(page([9], 1, 1));
    const { result, rerender } = renderHook(
      (filters) => useCreationRecords(filters, '1:0'),
      { initialProps: query },
    );
    await waitFor(() => expect(result.current.hasMore).toBe(true));
    act(() => result.current.loadMore());
    const oldSignal = vi.mocked(listCreations).mock.calls[1][1];
    rerender({ ...query, scope: 'team' });
    await waitFor(() => expect(result.current.page.records[0]?.id).toBe(9));
    expect(oldSignal?.aborted).toBe(true);
    await act(async () => resolveOld(page([3, 4], 2)));
    expect(result.current.page.records.map((item) => item.id)).toEqual([9]);
    expect(listCreations).toHaveBeenLastCalledWith(
      { ...query, scope: 'team' },
      expect.any(AbortSignal),
    );
  });

  it('polls only active records and stops polling after completion', async () => {
    vi.useFakeTimers();
    const filters = { ...query, pageSize: 1 };
    vi.mocked(listCreations)
      .mockResolvedValueOnce({
        ...page([1], 1, 2),
        records: [record(1, 'RUNNING')],
        pageSize: 1,
      })
      .mockResolvedValueOnce({ ...page([2], 2, 2), pageSize: 1 });
    vi.mocked(getCreation).mockResolvedValueOnce(record(1));
    const { result } = renderHook(() => useCreationRecords(filters, '1:0'));
    await act(async () => {});
    await act(async () => result.current.loadMore());
    expect(result.current.page.records).toHaveLength(2);
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(result.current.page.records.map((item) => item.id)).toEqual([1, 2]);
    expect(
      result.current.page.records.every((item) => item.status === 'SUCCEEDED'),
    ).toBe(true);
    expect(listCreations).toHaveBeenCalledTimes(2);
    expect(getCreation).toHaveBeenCalledExactlyOnceWith(1, expect.any(AbortSignal));
    await act(async () => vi.advanceTimersByTimeAsync(10000));
    expect(listCreations).toHaveBeenCalledTimes(2);
    expect(getCreation).toHaveBeenCalledTimes(1);
  });

  it('prepends submissions without reloading or dropping earlier pages and deduplicates repeated responses', async () => {
    vi.useFakeTimers();
    vi.mocked(listCreations).mockResolvedValueOnce(page([1, 2])).mockResolvedValueOnce(page([3, 4], 2));
    vi.mocked(getCreation).mockResolvedValueOnce({ ...record(5), outputs: [{ id: 50, thumbnailUrl: '/result.png' }] });
    const { result } = renderHook(() => useCreationRecords(query, '1:0'));
    await act(async () => {});
    await act(async () => result.current.loadMore());
    const existing = result.current.page.records;
    act(() => { result.current.insert(record(5, 'RUNNING')); result.current.insert(record(5, 'RUNNING')); });
    expect(result.current.page.records.map((item) => item.id)).toEqual([5, 1, 2, 3, 4]);
    expect(result.current.page.records.slice(1)).toEqual(existing);
    expect(result.current.page.total).toBe(5);
    expect(result.current.loading).toBe(false);
    expect(listCreations).toHaveBeenCalledTimes(2);
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(result.current.page.records[0].outputs[0].thumbnailUrl).toBe('/result.png');
    expect(result.current.page.records[1]).toBe(existing[0]);
    expect(listCreations).toHaveBeenCalledTimes(2);
    expect(getCreation).toHaveBeenCalledExactlyOnceWith(5, expect.any(AbortSignal));
  });

  it('preserves an inserted record when an earlier first-page response arrives late', async () => {
    let resolveFirst!: (value: CreationPage) => void;
    vi.mocked(listCreations).mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));
    const { result } = renderHook(() => useCreationRecords(query, '1:0'));
    act(() => result.current.insert(record(5, 'QUEUED')));
    expect(result.current.loading).toBe(false);
    expect(result.current.page.records[0].id).toBe(5);
    await act(async () => resolveFirst(page([1, 2])));
    expect(result.current.page.records.map((item) => item.id)).toEqual([5, 1, 2]);
    expect(result.current.page.total).toBe(5);
    expect(result.current.hasMore).toBe(true);
    expect(listCreations).toHaveBeenCalledTimes(1);
  });

  it('keeps filters when inserting and removes a task that leaves the selected status', async () => {
    vi.useFakeTimers();
    const filters: CreationQuery = { ...query, status: 'RUNNING' };
    vi.mocked(listCreations).mockResolvedValueOnce(page([], 1, 0));
    vi.mocked(getCreation).mockResolvedValueOnce(record(5));
    const { result } = renderHook(() => useCreationRecords(filters, '1:0'));
    await act(async () => {});
    act(() => {
      expect(result.current.insert(record(4, 'QUEUED'))).toBe(false);
      expect(result.current.insert(record(5, 'RUNNING'))).toBe(true);
    });
    expect(result.current.page.total).toBe(1);
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(result.current.page.records).toEqual([]);
    expect(result.current.page.total).toBe(0);
    expect(listCreations).toHaveBeenCalledTimes(1);
  });
});
