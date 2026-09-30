import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  startMediaUpload,
  type CosClientFactory,
  type MediaUploadSession,
} from './mediaUpload';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

const session = (expiredTime: number): MediaUploadSession => ({
  sessionToken: 'session-1',
  bucket: 'antv-1418200553',
  region: 'ap-guangzhou',
  objectKey: 'uploads/11/session-1/source.mp4',
  status: 'PENDING',
  expiresAt: '2026-10-07T00:00:00Z',
  credentials: {
    tmpSecretId: 'tmp-id',
    tmpSecretKey: 'tmp-key',
    sessionToken: 'security-token',
    expiredTime,
    requestId: 'request-1',
  },
});

describe('media upload client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.setSystemTime(new Date('2026-09-30T00:00:00Z'));
  });

  it('uploads directly to COS and confirms through the backend', async () => {
    const now = Math.floor(Date.now() / 1000);
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session(now + 3600) })
      .mockResolvedValueOnce({
        success: true,
        data: {
          sessionToken: 'session-1',
          objectKey: 'uploads/11/session-1/source.mp4',
          contentType: 'video/mp4',
          size: 3,
          eTag: 'etag-1',
        },
      });
    const progress = vi.fn();
    const pause = vi.fn();
    const restart = vi.fn();
    const cancel = vi.fn();
    const factory: CosClientFactory = (options) => ({
      uploadFile: async (params) => {
        let authorization: unknown;
        await new Promise<void>((resolve) => {
          options.getAuthorization?.({} as never, (value) => {
            authorization = value;
            resolve();
          });
        });
        expect(authorization).toEqual(
          expect.objectContaining({
            TmpSecretId: 'tmp-id',
            TmpSecretKey: 'tmp-key',
            SecurityToken: 'security-token',
            ScopeLimit: true,
          }),
        );
        expect(params).toEqual(
          expect.objectContaining({
            Bucket: 'antv-1418200553',
            Region: 'ap-guangzhou',
            Key: 'uploads/11/session-1/source.mp4',
            SliceSize: 16 * 1024 * 1024,
          }),
        );
        params.onTaskReady?.('task-1');
        params.onProgress?.({ loaded: 3, total: 3, speed: 1, percent: 1 });
        return { ETag: 'etag-1', Location: 'location' } as never;
      },
      pauseTask: pause,
      restartTask: restart,
      cancelTask: cancel,
    });
    const controls = vi.fn();
    const file = new File([new Uint8Array([1, 2, 3])], 'episode.mp4', {
      type: 'video/mp4',
    });

    const result = await startMediaUpload(file, {
      onProgress: progress,
      onControls: controls,
      clientFactory: factory,
    });

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/api/media-uploads',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/media-uploads/session-1/complete',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(progress).toHaveBeenCalled();
    const uploadControls = controls.mock.calls[0][0];
    uploadControls.pause();
    uploadControls.resume();
    uploadControls.cancel();
    expect(pause).toHaveBeenCalledWith('task-1');
    expect(restart).toHaveBeenCalledWith('task-1');
    expect(cancel).toHaveBeenCalledWith('task-1');
    expect(result.eTag).toBe('etag-1');
  });

  it('renews credentials when the current token is near expiry', async () => {
    const now = Math.floor(Date.now() / 1000);
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session(now + 30) })
      .mockResolvedValueOnce({ success: true, data: session(now + 3600) })
      .mockResolvedValueOnce({ success: true, data: { eTag: 'etag-2' } });
    const factory: CosClientFactory = (options) => ({
      uploadFile: async () => {
        await new Promise<void>((resolve) => {
          options.getAuthorization?.({} as never, () => resolve());
        });
        return { ETag: 'etag-2', Location: 'location' } as never;
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await startMediaUpload(
      new File([new Uint8Array([1])], 'image.png', { type: 'image/png' }),
      { clientFactory: factory },
    );

    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/media-uploads/session-1/credentials',
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
