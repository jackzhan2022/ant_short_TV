import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());

vi.mock('@umijs/max', () => ({ request }));

import {
  queryStoryboardEditorPage,
  queryStoryboardEpisodeDetails,
  queryStoryboardWorkspace,
  createAssetImageBatch,
  downloadEpisodeVideoVersion,
  queryAssetImageBatch,
  queryAssetImageBatchPreflight,
} from './service';

// Transport contract tests; confirmation behaviour is covered in point-estimate.test.tsx.
vi.mock('@/services/point-estimate', async () => {
  const { request } = await import('@umijs/max');
  return { pointEstimatedRequest: request };
});

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

describe('focused storyboard editor transport', () => {
  beforeEach(() => request.mockReset());

  it('requests the focused editor page with episode pagination', async () => {
    await queryStoryboardEditorPage(5, { episodeNo: 2, current: 3, pageSize: 20 });
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-editor-page', {
      params: { episodeNo: 2, current: 3, pageSize: 20 },
    });
  });

  it('forwards the optional deep-link shot for current-script page resolution', async () => {
    await queryStoryboardEditorPage(5, { episodeNo: 2, current: 4, pageSize: 20, storyboardId: 201 });
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-editor-page', {
      params: { episodeNo: 2, current: 4, pageSize: 20, storyboardId: 201 },
    });
  });

  it('requests independent episode details with cancellation support', async () => {
    const controller = new AbortController();
    await queryStoryboardEpisodeDetails(5, 12, controller.signal);
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-episodes/12/details', {
      signal: controller.signal,
    });
  });

  it('preserves the original workspace endpoint for other consumers', async () => {
    await queryStoryboardWorkspace(5);
    expect(request).toHaveBeenCalledWith('/api/projects/5/storyboard-workspace', { params: {} });
  });
});
