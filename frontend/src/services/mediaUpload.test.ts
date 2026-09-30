import { request } from '@umijs/max';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  startMediaUpload,
  type CosClientFactory,
  type MediaUploadSession,
} from './mediaUpload';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

const session = (): MediaUploadSession => ({
  sessionToken: 'session-1',
  bucket: 'antv-1418200553',
  region: 'ap-guangzhou',
  storageClass: 'INTELLIGENT_TIERING',
  objectKey: 'uploads/11/session-1/source.mp4',
  status: 'PENDING',
  expiresAt: '2026-10-07T00:00:00Z',
}) as MediaUploadSession;

describe('media upload client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.setSystemTime(new Date('2026-09-30T00:00:00Z'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uploads directly to COS and confirms through the backend', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: true,
        data: {
          authorization: 'q-sign-algorithm=sha1&signature=one',
          securityToken: 'security-token',
          expiresAt: 1_796_000_300,
        },
      })
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
          options.getAuthorization?.(
            {
              Method: 'PUT',
              Pathname: '/uploads/11/session-1/source.mp4',
              Query: {},
              Headers: { host: 'cos.example' },
            } as never,
            (value) => {
            authorization = value;
            resolve();
            },
          );
        });
        expect(authorization).toEqual(
          expect.objectContaining({
            Authorization: 'q-sign-algorithm=sha1&signature=one',
            SecurityToken: 'security-token',
          }),
        );
        expect(params).toEqual(
          expect.objectContaining({
            Bucket: 'antv-1418200553',
            Region: 'ap-guangzhou',
            StorageClass: 'INTELLIGENT_TIERING',
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
      '/api/media-uploads/session-1/authorization',
      expect.objectContaining({
        method: 'POST',
        data: expect.objectContaining({
          method: 'PUT',
          pathname: '/uploads/11/session-1/source.mp4',
        }),
      }),
    );
    expect(request).toHaveBeenNthCalledWith(
      3,
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

  it('requests a fresh authorization for every COS operation', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: true,
        data: { authorization: 'signature-1', securityToken: 'token-1', expiresAt: 1_796_000_300 },
      })
      .mockResolvedValueOnce({
        success: true,
        data: { authorization: 'signature-2', securityToken: 'token-2', expiresAt: 1_796_000_300 },
      })
      .mockResolvedValueOnce({ success: true, data: { eTag: 'etag-2' } });
    const factory: CosClientFactory = (options) => ({
      uploadFile: async () => {
        for (const method of ['POST', 'PUT']) {
          await new Promise<void>((resolve) => {
            options.getAuthorization?.(
              {
                Method: method,
                Pathname: '/uploads/11/session-1/source.png',
                Query: method === 'POST' ? { uploads: '' } : {},
                Headers: { host: 'cos.example' },
              } as never,
              () => resolve(),
            );
          });
        }
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

    const authorizationCalls = (
      vi.mocked(request).mock.calls as unknown as Array<
        [string, Record<string, unknown>]
      >
    ).filter(
      ([url]) => url === '/api/media-uploads/session-1/authorization',
    );
    expect(authorizationCalls).toHaveLength(2);
    expect(authorizationCalls[0][1]).toEqual(
      expect.objectContaining({ data: expect.objectContaining({ method: 'POST' }) }),
    );
    expect(authorizationCalls[1][1]).toEqual(
      expect.objectContaining({ data: expect.objectContaining({ method: 'PUT' }) }),
    );
  });

  it('reports request authorization failures separately from COS failures', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: false,
        errorMessage: '上传授权服务不可用',
      });
    const factory: CosClientFactory = (options) => ({
      uploadFile: async () => {
        await new Promise<void>((resolve) => {
          options.getAuthorization?.(
            {
              Method: 'POST',
              Pathname: '/uploads/11/session-1/source.mp4',
              Query: { uploads: '' },
              Headers: { host: 'cos.example' },
            } as never,
            () => resolve(),
          );
        });
        throw new Error('SDK authorization failed');
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await expect(
      startMediaUpload(
        new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
        { clientFactory: factory },
      ),
    ).rejects.toThrow('上传授权服务不可用');
  });

  it('reports an offline upload as recoverable', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false);
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    const factory: CosClientFactory = () => ({
      uploadFile: async () => {
        throw new Error('Network Error');
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await expect(
      startMediaUpload(
        new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
        { clientFactory: factory },
      ),
    ).rejects.toThrow('网络已断开，上传任务可在恢复网络后重试');
  });

  it('normalizes COS upload errors without exposing SDK details', async () => {
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    const factory: CosClientFactory = () => ({
      uploadFile: async () => {
        throw new Error('Request has expired: q-signature=secret');
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await expect(
      startMediaUpload(
        new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
        { clientFactory: factory },
      ),
    ).rejects.toThrow('COS 上传失败，请重试');
  });

  it('preserves backend completion verification errors', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: false,
        errorMessage: '上传文件大小与会话声明不一致',
      });
    const factory: CosClientFactory = () => ({
      uploadFile: async () => ({ ETag: 'etag-1', Location: 'location' }) as never,
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await expect(
      startMediaUpload(
        new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
        { clientFactory: factory },
      ),
    ).rejects.toThrow('上传文件大小与会话声明不一致');
  });
});
