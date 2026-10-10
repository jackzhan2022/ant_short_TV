import { useCallback, useEffect, useRef, useState } from 'react';
import type { PublicStyle } from '../style-library/service';
import { queryStyleLibrary } from './service';

const pageSize = 12;

export default function usePlatformStyleGallery(enabled: boolean) {
  const [category, setCategory] = useState('全部');
  const [gallery, setGallery] = useState<PublicStyle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const bottomRef = useRef<HTMLElement>(null);
  const requestRef = useRef(0);
  const inFlightRef = useRef(false);
  const categoryRef = useRef(category);
  const cursor = useRef({ nextPage: 1, hasMore: false, failed: false });

  const loadPage = useCallback(
    async (page: number) => {
      if (inFlightRef.current || categoryRef.current !== category) return;
      const request = ++requestRef.current;
      inFlightRef.current = true;
      setLoading(true);
      setError(false);
      try {
        const response = await queryStyleLibrary({
          current: page,
          pageSize,
          ...(category === '全部' ? {} : { category }),
        });
        if (request !== requestRef.current) return;
        const records = response.data.data || [];
        const more =
          records.length > 0 && page * pageSize < response.data.total;
        setGallery((previous) => {
          const items = new Map(
            (page === 1 ? [] : previous).map((item) => [item.id, item]),
          );
          for (const item of records) items.set(item.id, item);
          return [...items.values()];
        });
        cursor.current = { nextPage: page + 1, hasMore: more, failed: false };
        setHasMore(more);
      } catch {
        if (request === requestRef.current) {
          cursor.current.failed = true;
          setError(true);
        }
      } finally {
        if (request === requestRef.current) {
          inFlightRef.current = false;
          setLoading(false);
        }
      }
    },
    [category],
  );

  useEffect(() => {
    void loadPage(1);
    return () => {
      requestRef.current += 1;
      inFlightRef.current = false;
    };
  }, [loadPage]);

  const changeCategory = (next: string) => {
    if (next === categoryRef.current) return;
    categoryRef.current = next;
    requestRef.current += 1;
    inFlightRef.current = false;
    cursor.current = { nextPage: 1, hasMore: false, failed: false };
    setGallery([]);
    setHasMore(false);
    setError(false);
    setLoading(true);
    setCategory(next);
  };

  const loadMore = useCallback(() => {
    if (!cursor.current.hasMore || cursor.current.failed) return;
    void loadPage(cursor.current.nextPage);
  }, [loadPage]);

  useEffect(() => {
    if (
      !enabled ||
      loading ||
      error ||
      !hasMore ||
      !bottomRef.current ||
      typeof IntersectionObserver === 'undefined'
    )
      return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) loadMore();
      },
      { rootMargin: '200px' },
    );
    observer.observe(bottomRef.current);
    return () => observer.disconnect();
  }, [enabled, loading, error, hasMore, loadMore]);

  return {
    category,
    changeCategory,
    gallery,
    loading,
    error,
    hasMore,
    bottomRef,
    loadMore,
    retry: () => {
      void loadPage(cursor.current.nextPage);
    },
  };
}
