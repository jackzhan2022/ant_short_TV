import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cachedDownload, type DownloadDelivery } from './file-cache';

const CACHE_KEY = '/__download-cache/1/2/version';
const delivery: DownloadDelivery = {
  fileName: '星河_图片.png',
  contentType: 'image/png',
  outputFormat: 'FILE',
  fileSize: 6,
  expiresAt: '2099-01-01T00:00:00Z',
  url: 'https://bucket.example/snapshot?sign=fresh',
  cacheKey: CACHE_KEY,
  eTag: 'version',
};
let entries: Map<string, Response>;
let fetchMock: ReturnType<typeof vi.fn>;
let cache: {
  match: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
  keys: ReturnType<typeof vi.fn>;
};
const key = (input: string | Request) =>
  typeof input === 'string' ? input : new URL(input.url).pathname;
const response = (
  body = 'cached',
  etag = 'version',
  expires = '2099-01-01T00:00:00Z',
) =>
  new Response(body, {
    headers: {
      'Content-Type': 'image/png',
      'Content-Length': String(body.length),
      ETag: etag,
      'X-Download-Expires': expires,
    },
  });
beforeEach(() => {
  entries = new Map();
  fetchMock = vi.fn().mockResolvedValue(response('remote'));
  cache = {
    match: vi.fn(async (input) => entries.get(key(input))?.clone()),
    put: vi.fn(async (input, value) => {
      entries.set(key(input), value.clone());
    }),
    delete: vi.fn(async (input) => entries.delete(key(input))),
    keys: vi.fn(async () =>
      [...entries.keys()].map(
        (value) => new Request(`https://app.example${value}`),
      ),
    ),
  };
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('caches', { open: vi.fn(async () => cache) });
  vi.spyOn(performance, 'getEntriesByType').mockReturnValue([]);
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it('uses the complete authorized local original without any network file request', async () => {
  entries.set(CACHE_KEY, response());
  const blob = await cachedDownload(delivery);
  expect(await blob?.text()).toBe('cached');
  expect(fetchMock).not.toHaveBeenCalled();
});
it('does not reuse a different user cache entry', async () => {
  entries.set('/__download-cache/1/99/version', response());
  expect(await (await cachedDownload(delivery))?.text()).toBe('remote');
  expect(fetchMock).toHaveBeenCalledOnce();
});
it('rejects stale versions and replaces them with the current original', async () => {
  entries.set(CACHE_KEY, response('cached', 'old-version'));
  expect(await (await cachedDownload(delivery))?.text()).toBe('remote');
  expect(cache.delete).toHaveBeenCalledWith(delivery.cacheKey);
});
it('rejects expired entries even when the current task is valid', async () => {
  entries.set(CACHE_KEY, response('cached', 'version', '2020-01-01T00:00:00Z'));
  await cachedDownload(delivery);
  expect(fetchMock).toHaveBeenCalledOnce();
});
it('never reads cache or fetches bytes for expired tasks, ZIPs, or large files', async () => {
  for (const task of [
    { ...delivery, expiresAt: '2020-01-01T00:00:00Z' },
    { ...delivery, outputFormat: 'ZIP' as const },
    { ...delivery, fileSize: 100000000 },
  ])
    expect(await cachedDownload(task)).toBeNull();
  expect(cache.match).not.toHaveBeenCalled();
  expect(fetchMock).not.toHaveBeenCalled();
});
it('reuses a browser HTTP cached original using its exact preview URL and matching ETag', async () => {
  vi.mocked(performance.getEntriesByType).mockReturnValue([
    {
      name: 'https://cdn.example/original.png?sign=old&t=123',
    } as PerformanceEntry,
  ]);
  fetchMock.mockResolvedValue(response('cached', '"version"'));
  expect(
    await (
      await cachedDownload({
        ...delivery,
        sourceUrl: 'https://cdn.example/original.png?sign=new&t=456',
      })
    )?.text(),
  ).toBe('cached');
  expect(fetchMock).toHaveBeenCalledWith(
    'https://cdn.example/original.png?sign=old&t=123',
    expect.objectContaining({ cache: 'force-cache', credentials: 'omit' }),
  );
  expect(fetchMock).toHaveBeenCalledOnce();
});
it('never reuses thumbnails or transformed preview files', async () => {
  vi.mocked(performance.getEntriesByType).mockReturnValue([
    {
      name: 'https://cdn.example/original.png?imageMogr2/thumbnail/200x',
    } as PerformanceEntry,
  ]);
  await cachedDownload({
    ...delivery,
    sourceUrl: 'https://cdn.example/original.png?sign=new',
  });
  expect(fetchMock).toHaveBeenCalledWith(delivery.url, expect.anything());
  expect(fetchMock).toHaveBeenCalledOnce();
});
it('rejects video fragments and falls back to the full signed original', async () => {
  vi.mocked(performance.getEntriesByType).mockReturnValue([
    { name: 'https://cdn.example/original.mp4?sign=old' } as PerformanceEntry,
  ]);
  fetchMock.mockResolvedValueOnce(
    new Response('part', {
      status: 206,
      headers: { 'Content-Range': 'bytes 0-3/6', ETag: 'version' },
    }),
  );
  expect(
    await (
      await cachedDownload({
        ...delivery,
        sourceUrl: 'https://cdn.example/original.mp4?sign=new',
      })
    )?.text(),
  ).toBe('remote');
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
it('does not treat incomplete bytes as a valid cached download', async () => {
  entries.set(CACHE_KEY, response('part'));
  fetchMock.mockResolvedValue(response('part'));
  expect(await cachedDownload(delivery)).toBeNull();
});
it('keeps downloading when private browsing or storage quota disables Cache API', async () => {
  vi.stubGlobal('caches', {
    open: vi.fn().mockRejectedValue(new Error('disabled')),
  });
  expect(await (await cachedDownload(delivery))?.text()).toBe('remote');
});
it('falls back to native download when cross-origin file reads are unavailable', async () => {
  fetchMock.mockRejectedValue(new TypeError('CORS'));
  expect(await cachedDownload(delivery)).toBeNull();
});
