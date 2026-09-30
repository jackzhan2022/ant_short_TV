import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  queryVideoDecompositionBatchScreenplays,
  retryVideoDecompositionEpisode,
  uploadEpisodeVideo,
} from './service';
import { startMediaUpload } from '@/services/mediaUpload';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: vi.fn() }));

describe('video decomposition service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
  });

  it('loads the ordered per-episode screenplay view without a merge mutation', async () => {
    await queryVideoDecompositionBatchScreenplays(19);
    expect(request).toHaveBeenCalledWith(
      '/api/video-script-decomposition/batches/19/screenplays',
    );
  });

  it('retries only the video-understanding phase through the public contract', async () => {
    await retryVideoDecompositionEpisode(88);
    expect(request).toHaveBeenCalledWith(
      '/api/video-script-decomposition/episodes/88/retry',
      expect.objectContaining({ data: { phase: 'VIDEO_ANALYSIS' } }),
    );
  });

  it('uploads episode bytes directly to COS', async () => {
    vi.mocked(startMediaUpload).mockResolvedValue({
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.mp4',
      contentType: 'video/mp4',
      size: 3,
      eTag: 'etag-1',
    });
    const file = new File([new Uint8Array([1, 2, 3])], 'episode.mp4', {
      type: 'video/mp4',
    });

    const result = await uploadEpisodeVideo(file);

    expect(startMediaUpload).toHaveBeenCalledWith(file, expect.any(Object));
    expect(result.data).toEqual({
      fileName: 'episode.mp4',
      storagePath: 'uploads/11/session-1/source.mp4',
      uploadSessionToken: 'session-1',
      mimeType: 'video/mp4',
      fileSize: 3,
      durationSeconds: null,
    });
    expect(request).not.toHaveBeenCalledWith(
      '/api/video-script-decomposition/uploads',
      expect.anything(),
    );
  });
});
