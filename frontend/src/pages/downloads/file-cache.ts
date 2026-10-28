export type DownloadDelivery = {
  fileName: string;
  contentType: string;
  outputFormat: 'FILE' | 'ZIP';
  fileSize: number;
  expiresAt: string;
  url: string;
  cacheKey?: string;
  eTag?: string;
  sourceUrl?: string;
};
const CACHE = 'antv-download-files-v1';
const MAX_FILE = 64 * 1024 * 1024;
const MAX_CACHE = 128 * 1024 * 1024;
const signatureParameters = new Set([
  'sign',
  't',
  'q-sign-algorithm',
  'q-ak',
  'q-sign-time',
  'q-key-time',
  'q-header-list',
  'q-url-param-list',
  'q-signature',
  'x-cos-security-token',
]);
const tag = (value: string | null | undefined) => value?.replace(/^"|"$/g, '');
const valid = (delivery: DownloadDelivery) =>
  Date.parse(delivery.expiresAt) > Date.now();

async function readableBlob(
  response: Response,
  delivery: DownloadDelivery,
  checkTag: boolean,
) {
  const length = response.headers.get('content-length');
  if (
    response.status !== 200 ||
    response.type === 'opaque' ||
    response.headers.has('content-range') ||
    (length !== null && Number(length) !== delivery.fileSize) ||
    (checkTag &&
      (!delivery.eTag ||
        tag(response.headers.get('etag')) !== tag(delivery.eTag)))
  ) {
    await response.body?.cancel().catch(() => {});
    return null;
  }
  const blob = await response.blob();
  return blob.size === delivery.fileSize && blob.size <= MAX_FILE ? blob : null;
}

async function save(cache: Cache, delivery: DownloadDelivery, blob: Blob) {
  if (!delivery.cacheKey || !valid(delivery)) return;
  await cache.put(
    delivery.cacheKey,
    new Response(blob, {
      headers: {
        'Content-Type': delivery.contentType,
        'Content-Length': String(blob.size),
        ETag: delivery.eTag || '',
        'X-Download-Expires': delivery.expiresAt,
      },
    }),
  );
  let total = 0;
  const entries = [...(await cache.keys())].reverse();
  for (const entry of entries) {
    const response = await cache.match(entry);
    const size = Number(response?.headers.get('content-length') || 0);
    if (
      !response ||
      !(
        Date.parse(response.headers.get('x-download-expires') || '') >
        Date.now()
      ) ||
      total + size > MAX_CACHE
    )
      await cache.delete(entry);
    else total += size;
  }
}

/** Called only after a successful, uncached server permission/expiry check. */
export async function cachedDownload(
  delivery: DownloadDelivery,
): Promise<Blob | null> {
  if (
    delivery.outputFormat !== 'FILE' ||
    !valid(delivery) ||
    delivery.fileSize > MAX_FILE ||
    delivery.fileSize <= 0
  )
    return null;
  let cache: Cache | undefined;
  try {
    if (delivery.cacheKey && delivery.eTag && typeof caches !== 'undefined') {
      cache = await caches.open(CACHE);
      const response = await cache.match(delivery.cacheKey);
      if (response) {
        if (
          Date.parse(response.headers.get('x-download-expires') || '') >
          Date.now()
        ) {
          const blob = await readableBlob(response, delivery, true);
          if (blob && valid(delivery)) return blob;
        }
        await cache.delete(delivery.cacheKey);
      }
    }
  } catch {
    /* Private browsing or quota restrictions must not prevent downloading. */
  }
  const candidates: string[] = [];
  if (delivery.sourceUrl) {
    try {
      const source = new URL(delivery.sourceUrl);
      // Never reuse thumbnails, transformed images, or unrelated resources.
      for (const entry of performance
        .getEntriesByType('resource')
        .slice(-100)
        .reverse()) {
        const seen = new URL(entry.name);
        if (
          seen.origin === source.origin &&
          seen.pathname === source.pathname &&
          [...seen.searchParams.keys()].every((key) =>
            signatureParameters.has(key),
          ) &&
          !candidates.includes(seen.href)
        )
          candidates.push(seen.href);
        if (candidates.length === 1) break;
      }
    } catch {
      /* Resource timing is optional. */
    }
  }
  candidates.push(delivery.url);
  for (const url of candidates) {
    if (!valid(delivery)) return null;
    try {
      const response = await fetch(url, {
        cache: 'force-cache',
        mode: 'cors',
        credentials: 'omit',
        signal: AbortSignal.timeout(15000),
      });
      const blob = await readableBlob(response, delivery, url !== delivery.url);
      if (!blob || !valid(delivery)) continue;
      if (cache && delivery.cacheKey && delivery.eTag)
        await save(cache, delivery, blob).catch(() => {});
      return blob;
    } catch {
      /* An expired preview URL or missing CORS falls back to the signed original. */
    }
  }
  return null;
}
