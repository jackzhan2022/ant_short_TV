import { useCallback, useEffect, useRef, useState } from 'react';
import {
  type CreationPage,
  type CreationQuery,
  type CreationRecord,
  getCreation,
  listCreations,
} from './api';

const pending = (record: CreationRecord) =>
  record.status === 'QUEUED' || record.status === 'RUNNING' ||
  record.outputs.some((output) => output.thumbnailStatus === 'PENDING');
const matches = (query: CreationQuery, record: CreationRecord) =>
  (!query.mediaType || query.mediaType === record.mediaType) &&
  (!query.status || query.status === record.status) &&
  (!query.keyword ||
    record.prompt.toLowerCase().includes(query.keyword.toLowerCase()) ||
    record.creatorName.toLowerCase().includes(query.keyword.toLowerCase()));

export default function useCreationRecords(
  query: CreationQuery,
  refreshKey: string,
) {
  const [page, setPage] = useState<CreationPage>({
    records: [],
    total: 0,
    current: 1,
    pageSize: query.pageSize,
  });
  const [loading, setLoading] = useState(true);
  const [fetching, setFetching] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState('');
  const [moreError, setMoreError] = useState('');
  const loadMoreRef = useRef<() => void>(() => {});
  const loadMore = useCallback(() => loadMoreRef.current(), []);
  const insertRef = useRef<(record: CreationRecord) => boolean>(() => false);
  const insert = useCallback(
    (record: CreationRecord) => insertRef.current(record),
    [],
  );

  useEffect(() => {
    const abort = new AbortController();
    let pages: CreationPage[] = [];
    const inserted = new Map<number, CreationRecord>();
    let total = 0;
    let inFlight = false;
    let more = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setPage({ records: [], total: 0, current: 1, pageSize: query.pageSize });
    setLoading(true);
    setHasMore(false);
    setError('');
    setMoreError('');

    const records = () => {
      const merged = new Map(inserted);
      // Offset pages can overlap after a new creation is prepended on the server.
      for (const item of pages.flatMap((part) => part.records)) {
        if (!merged.has(item.id)) merged.set(item.id, item);
      }
      return [...merged.values()];
    };
    const publish = () => {
      const items = records();
      if (items.length >= total) more = false;
      setHasMore(more);
      setPage({
        records: items,
        total,
        current: Math.max(1, pages.length),
        pageSize: query.pageSize,
      });
    };
    const schedule = () => {
      clearTimeout(timer);
      if (!inFlight && records().some(pending)) {
        timer = setTimeout(() => void load('refresh'), 5000);
      }
    };

    const load = async (kind: 'initial' | 'more' | 'refresh') => {
      if (abort.signal.aborted || inFlight || (kind === 'more' && !more))
        return;
      inFlight = true;
      clearTimeout(timer);
      setFetching(true);
      if (kind === 'more') setMoreError('');
      try {
        if (kind === 'refresh') {
          const refreshed = await Promise.allSettled(
            records()
              .filter(pending)
              .map((item) => getCreation(item.id, abort.signal)),
          );
          if (abort.signal.aborted) return;
          for (const result of refreshed) {
            if (result.status !== 'fulfilled') continue;
            const item = result.value;
            const visible = matches(query, item);
            if (!visible) total = Math.max(0, total - 1);
            if (inserted.has(item.id)) {
              if (visible) inserted.set(item.id, item);
              else inserted.delete(item.id);
            }
            pages = pages.map((part) => ({
              ...part,
              records: part.records.flatMap((old) =>
                old.id === item.id ? (visible ? [item] : []) : [old],
              ),
            }));
          }
        } else {
          const next = await listCreations(
            { ...query, current: pages.length + 1 },
            abort.signal,
          );
          if (abort.signal.aborted) return;
          pages = [...pages, next];
          // A first-page response started before submission may not contain the inserted item yet.
          total =
            kind === 'initial'
              ? next.total +
                [...inserted.keys()].filter(
                  (id) => !next.records.some((item) => item.id === id),
                ).length
              : Math.max(total, next.total);
          more =
            next.records.length > 0 && pages.length * query.pageSize < total;
        }
        publish();
        setError('');
      } catch (failure) {
        if (!abort.signal.aborted) {
          const message =
            failure instanceof Error ? failure.message : '创作记录加载失败';
          if (kind === 'more') setMoreError(message);
          else setError(message);
        }
      } finally {
        inFlight = false;
        if (!abort.signal.aborted) {
          setLoading(false);
          setFetching(false);
          schedule();
        }
      }
    };
    loadMoreRef.current = () => void load('more');
    insertRef.current = (item) => {
      if (abort.signal.aborted || !matches(query, item)) return false;
      if (!records().some((old) => old.id === item.id)) total += 1;
      // Keep the newest submission first, including after an idempotent response.
      const existing = [...inserted.entries()].filter(([id]) => id !== item.id);
      inserted.clear();
      inserted.set(item.id, item);
      for (const [id, value] of existing) inserted.set(id, value);
      publish();
      setLoading(false);
      schedule();
      return true;
    };
    void load('initial');
    return () => {
      abort.abort();
      clearTimeout(timer);
      loadMoreRef.current = () => {};
      insertRef.current = () => false;
    };
  }, [query, refreshKey]);

  return {
    page,
    loading,
    fetching,
    hasMore,
    error,
    moreError,
    loadMore,
    insert,
  };
}
