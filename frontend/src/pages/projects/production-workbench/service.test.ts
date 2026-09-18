import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());

vi.mock('@umijs/max', () => ({ request }));

import {
  createAssetImageBatch,
  downloadEpisodeVideoVersion,
  queryAssetImageBatch,
  queryAssetImageBatchPreflight,
} from './service';

describe('asset image batch service', () => {
  const values = {
    assetType: 'CHARACTER' as const,
    assetIds: [11, 12],
    mode: 'PRIMARY' as const,
    modelId: 8,
    aspectRatio: '16:9',
    imageCount: 1,
  };

  beforeEach(() => request.mockReset());

  it('preflights selected assets', async () => {
    await queryAssetImageBatchPreflight(5, values);

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches/preflight',
      expect.objectContaining({ method: 'POST', data: values }),
    );
  });

  it('submits an idempotent batch', async () => {
    await createAssetImageBatch(5, values, 'batch-key');

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches',
      expect.objectContaining({
        method: 'POST',
        data: values,
        headers: expect.objectContaining({ 'Idempotency-Key': 'batch-key' }),
      }),
    );
  });

  it('queries batch progress', async () => {
    await queryAssetImageBatch(5, 71);

    expect(request).toHaveBeenCalledWith(
      '/api/projects/5/asset-image-batches/71',
    );
  });
});

describe('downloadEpisodeVideoVersion', () => {
  const originalCreateElement = document.createElement.bind(document);
  const originalObjectUrl = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('accessToken', 'token-1');
    localStorage.setItem('currentTenantId', '7');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        blob: async () => new Blob(['video-bytes']),
        headers: {
          get: (name: string) =>
            name.toLowerCase() === 'content-disposition'
              ? 'attachment; filename="episode_1_v1.mp4"'
              : null,
        },
      }),
    );
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:episode');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.createElement = originalCreateElement;
    URL.createObjectURL = originalObjectUrl;
    URL.revokeObjectURL = originalRevoke;
  });

  it('downloads the episode video with auth headers', async () => {
    const click = vi.fn();
    const anchor = {
      href: '',
      download: '',
      rel: '',
      click,
    } as unknown as HTMLAnchorElement;
    vi.spyOn(document, 'createElement').mockReturnValue(anchor);

    await downloadEpisodeVideoVersion(5, 11);

    expect(fetch).toHaveBeenCalledWith(
      '/api/projects/5/episode-video-versions/11/download',
      expect.objectContaining({
        headers: {
          Authorization: 'Bearer token-1',
          'X-Tenant-Id': '7',
        },
      }),
    );
    expect(click).toHaveBeenCalled();
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:episode');
  });
});
