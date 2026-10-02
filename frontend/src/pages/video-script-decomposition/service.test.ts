import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  queryVideoDecompositionBatchScreenplays,
  queryVideoDecompositionBatches,
  retryVideoDecompositionEpisode,
  uploadEpisodeVideo,
} from './service';
import {
  startMediaUpload,
  type MediaUploadHandle,
  type VerifiedMediaUpload,
} from '@/services/mediaUpload';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: vi.fn() }));

describe('video decomposition service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
  });

  it('passes server paging and cancellation for batch browsing', async () => {
    const controller = new AbortController();
    await queryVideoDecompositionBatches(undefined, { current: 2, pageSize: 20 }, controller.signal);
    expect(request).toHaveBeenCalledWith('/api/video-script-decomposition/batches', {
      params: { projectId: undefined, current: 2, pageSize: 20 }, signal: controller.signal,
    });
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
    const uploaded: VerifiedMediaUpload = {
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.mp4',
      contentType: 'video/mp4',
      size: 3,
      eTag: 'etag-1',
    };
    const handle: MediaUploadHandle = {
      session: {
        sessionToken: 'session-1',
        bucket: 'antv-1418200553',
        region: 'ap-guangzhou',
        storageClass: 'INTELLIGENT_TIERING',
        objectKey: 'uploads/11/session-1/source.mp4',
        status: 'PENDING',
        expiresAt: '2026-10-07T00:00:00Z',
      },
      sessionToken: 'session-1',
      objectKey: 'uploads/11/session-1/source.mp4',
      taskId: 'task-1',
      attempt: Promise.resolve(uploaded),
      pause: vi.fn(),
      resume: vi.fn().mockResolvedValue(uploaded),
      cancel: vi.fn(),
      retryCompletion: vi.fn().mockResolvedValue(uploaded),
      retry: vi.fn().mockResolvedValue(uploaded),
    };
    vi.mocked(startMediaUpload).mockResolvedValue(handle);
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

  it('resumes the retained episode upload instead of creating another session', async () => {
    const file = new File(['video'], 'episode.mp4', { type: 'video/mp4' });
    const attempt = Promise.reject(new Error('COS 上传失败'));
    void attempt.catch(() => undefined);
    const uploaded = {
      sessionToken: 'session-1',
      objectKey: 'materials/11/uploads/session-1/v1/original.mp4',
      contentType: 'video/mp4',
      size: file.size,
      eTag: 'etag-1',
    };
    const handle = {
      attempt,
      retry: vi.fn().mockResolvedValue(uploaded),
    } as unknown as MediaUploadHandle;
    vi.mocked(startMediaUpload).mockResolvedValue(handle);
    const retained = vi.fn();

    await expect(
      uploadEpisodeVideo(file, undefined, { onHandle: retained }),
    ).rejects.toThrow('COS 上传失败');
    expect(retained).toHaveBeenCalledWith(handle);
    const response = await uploadEpisodeVideo(file, undefined, { handle });

    expect(startMediaUpload).toHaveBeenCalledTimes(1);
    expect(handle.retry).toHaveBeenCalledTimes(1);
    expect(response.data.uploadSessionToken).toBe('session-1');
  });
});
