import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  queryVideoDecompositionBatchScreenplays,
  retryVideoDecompositionEpisode,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

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
});
