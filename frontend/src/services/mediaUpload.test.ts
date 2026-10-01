import { request } from '@umijs/max';
import type COS from 'cos-js-sdk-v5';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  startMediaUpload,
  type CosClientFactory,
  type MediaUploadSession,
  type VerifiedMediaUpload,
} from './mediaUpload';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

const session = (): MediaUploadSession =>
  ({
    sessionToken: 'session-1',
    bucket: 'antv-1418200553',
    region: 'ap-guangzhou',
    storageClass: 'INTELLIGENT_TIERING',
    objectKey: 'uploads/11/session-1/source.mp4',
    status: 'PENDING',
    expiresAt: '2026-10-07T00:00:00Z',
  }) as MediaUploadSession;

const verifiedUpload = () => ({
  sessionToken: 'session-1',
  objectKey: 'uploads/11/session-1/source.mp4',
  contentType: 'video/mp4',
  size: 3,
  eTag: 'etag-1',
});

const uploadResult = {
  ETag: 'etag-1',
  Location: 'location',
} as never;

const sdkError = (message: string) => new Error(message) as never;

type CosUploadCallback = (
  error: COS.CosError | null,
  data: COS.UploadFileResult,
) => void;

describe('media upload client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.setSystemTime(new Date('2026-09-30T00:00:00Z'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('resumes an interrupted SDK task without creating another upload', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    let finishUpload: CosUploadCallback | undefined;
    const uploadFile = vi.fn((params, callback: CosUploadCallback) => {
      params.onTaskReady?.('task-1');
      finishUpload = callback;
    });
    const restartTask = vi.fn();
    const factory: CosClientFactory = () => ({
      uploadFile,
      pauseTask: vi.fn(),
      restartTask,
      cancelTask: vi.fn(),
    });

    const handle = await startMediaUpload(
      new File([new Uint8Array([1, 2, 3])], 'episode.mp4', {
        type: 'video/mp4',
      }),
      { clientFactory: factory },
    );

    expect(handle.session.sessionToken).toBe('session-1');
    expect(handle.sessionToken).toBe('session-1');
    expect(handle.objectKey).toBe('uploads/11/session-1/source.mp4');
    expect(handle.taskId).toBe('task-1');
    finishUpload?.(sdkError('RequestTimeout'), undefined as never);
    await expect(handle.attempt).rejects.toThrow('COS 上传失败，请重试');

    const failedResumedAttempt = handle.resume();
    expect(restartTask).toHaveBeenCalledTimes(1);
    expect(restartTask).toHaveBeenCalledWith('task-1');
    finishUpload?.(sdkError('RequestTimeout'), undefined as never);
    await expect(failedResumedAttempt).rejects.toThrow(
      'COS 上传失败，请重试',
    );

    const resumedAttempt = handle.resume();
    expect(restartTask).toHaveBeenCalledTimes(2);
    finishUpload?.(null, uploadResult);

    await expect(resumedAttempt).resolves.toEqual(
      expect.objectContaining({ eTag: 'etag-1' }),
    );
    expect(uploadFile).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledTimes(2);
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/media-uploads/session-1/complete',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('uploads directly to COS with progress and confirms through the backend', async () => {
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
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    const progress = vi.fn();
    const factory: CosClientFactory = (options) => ({
      uploadFile: (params, callback) => {
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
        options.getAuthorization?.(
          {
            Method: 'PUT',
            Pathname: '/uploads/11/session-1/source.mp4',
            Query: {},
            Headers: { host: 'cos.example' },
          } as never,
          (authorization) => {
            expect(authorization).toEqual(
              expect.objectContaining({
                Authorization: 'q-sign-algorithm=sha1&signature=one',
                SecurityToken: 'security-token',
              }),
            );
            params.onProgress?.({
              loaded: 3,
              total: 3,
              speed: 1,
              percent: 1,
            });
            callback(null, uploadResult);
          },
        );
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });
    const file = new File([new Uint8Array([1, 2, 3])], 'episode.mp4', {
      type: 'video/mp4',
    });

    const handle = await startMediaUpload(file, {
      onProgress: progress,
      clientFactory: factory,
    });
    const result = await handle.attempt;

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
    expect(result.eTag).toBe('etag-1');
  });

  it('replays an immediate pause after the SDK task is registered', async () => {
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    let registered = false;
    const appliedControls: string[] = [];
    const factory: CosClientFactory = () => ({
      uploadFile: (params) => {
        params.onTaskReady?.('task-1');
        registered = true;
      },
      pauseTask: (taskId) => {
        if (registered) {
          appliedControls.push(`pause:${taskId}`);
        }
      },
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      {
        clientFactory: factory,
        onControls: (controls) => controls.pause(),
      },
    );

    expect(appliedControls).toEqual(['pause:task-1']);
  });

  it('replays an immediate resume on the same registered SDK task', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    let registered = false;
    let finishUpload: CosUploadCallback | undefined;
    let resumedAttempt: Promise<VerifiedMediaUpload> | undefined;
    const appliedControls: string[] = [];
    const uploadFile = vi.fn((params, callback: CosUploadCallback) => {
      finishUpload = callback;
      params.onTaskReady?.('task-1');
      registered = true;
    });
    const factory: CosClientFactory = () => ({
      uploadFile,
      pauseTask: vi.fn(),
      restartTask: (taskId) => {
        if (registered) {
          appliedControls.push(`resume:${taskId}`);
          finishUpload?.(null, uploadResult);
        }
      },
      cancelTask: vi.fn(),
    });

    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      {
        clientFactory: factory,
        onControls: (controls) => {
          resumedAttempt = controls.resume();
        },
      },
    );

    expect(appliedControls).toEqual(['resume:task-1']);
    await expect(handle.attempt).resolves.toEqual(verifiedUpload());
    await expect(resumedAttempt).resolves.toEqual(verifiedUpload());
    expect(uploadFile).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('lets immediate cancellation dominate queued pause and resume controls', async () => {
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    let registered = false;
    let resumedAttempt: Promise<VerifiedMediaUpload> | undefined;
    const appliedControls: string[] = [];
    const applyWhenRegistered = (operation: string, taskId: string) => {
      if (registered) {
        appliedControls.push(`${operation}:${taskId}`);
      }
    };
    const factory: CosClientFactory = () => ({
      uploadFile: (params) => {
        params.onTaskReady?.('task-1');
        registered = true;
      },
      pauseTask: (taskId) => applyWhenRegistered('pause', taskId),
      restartTask: (taskId) => applyWhenRegistered('resume', taskId),
      cancelTask: (taskId) => applyWhenRegistered('cancel', taskId),
    });

    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      {
        clientFactory: factory,
        onControls: (controls) => {
          controls.pause();
          resumedAttempt = controls.resume();
          void resumedAttempt.catch(() => undefined);
          controls.cancel();
        },
      },
    );

    expect(appliedControls).toEqual(['cancel:task-1']);
    await expect(handle.attempt).rejects.toThrow('上传已取消');
    await expect(resumedAttempt).rejects.toThrow('上传已取消');
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('requests a fresh authorization for every COS operation', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: true,
        data: {
          authorization: 'signature-1',
          securityToken: 'token-1',
          expiresAt: 1_796_000_300,
        },
      })
      .mockResolvedValueOnce({
        success: true,
        data: {
          authorization: 'signature-2',
          securityToken: 'token-2',
          expiresAt: 1_796_000_300,
        },
      })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    const factory: CosClientFactory = (options) => ({
      uploadFile: (params, callback) => {
        params.onTaskReady?.('task-1');
        options.getAuthorization?.(
          {
            Method: 'POST',
            Pathname: '/uploads/11/session-1/source.mp4',
            Query: { uploads: '' },
            Headers: { host: 'cos.example' },
          } as never,
          () => {
            options.getAuthorization?.(
              {
                Method: 'PUT',
                Pathname: '/uploads/11/session-1/source.mp4',
                Query: { partNumber: '1', uploadId: 'upload-1' },
                Headers: { host: 'cos.example' },
              } as never,
              () => callback(null, uploadResult),
            );
          },
        );
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );
    await handle.attempt;

    const authorizationCalls = (
      vi.mocked(request).mock.calls as unknown as Array<
        [string, Record<string, unknown>]
      >
    ).filter(
        ([url]) => url === '/api/media-uploads/session-1/authorization',
      );
    expect(authorizationCalls).toHaveLength(2);
    expect(authorizationCalls[0][1]).toEqual(
      expect.objectContaining({
        data: expect.objectContaining({ method: 'POST' }),
      }),
    );
    expect(authorizationCalls[1][1]).toEqual(
      expect.objectContaining({
        data: expect.objectContaining({ method: 'PUT' }),
      }),
    );
  });

  it('requests fresh authorization again when an authorization failure resumes', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: false,
        errorMessage: '上传授权服务不可用',
      })
      .mockResolvedValueOnce({
        success: true,
        data: {
          authorization: 'signature-2',
          securityToken: 'token-2',
          expiresAt: 1_796_000_300,
        },
      })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    let runTask: (() => void) | undefined;
    const factory: CosClientFactory = (options) => {
      let finishUpload: CosUploadCallback | undefined;
      runTask = () => {
        options.getAuthorization?.(
          {
            Method: 'PUT',
            Pathname: '/uploads/11/session-1/source.mp4',
            Query: {},
            Headers: { host: 'cos.example' },
          } as never,
          (authorization) => {
            if (!authorization) {
              finishUpload?.(
                sdkError('SDK authorization failed'),
                undefined as never,
              );
              return;
            }
            finishUpload?.(null, uploadResult);
          },
        );
      };
      return {
        uploadFile: (params, callback) => {
          finishUpload = callback;
          params.onTaskReady?.('task-1');
          runTask?.();
        },
        pauseTask: vi.fn(),
        restartTask: vi.fn(() => runTask?.()),
        cancelTask: vi.fn(),
      };
    };

    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );

    await expect(handle.attempt).rejects.toThrow('上传授权服务不可用');
    await expect(handle.resume()).resolves.toEqual(verifiedUpload());
    const authorizationCalls = vi
      .mocked(request)
      .mock.calls.filter(
        ([url]) => url === '/api/media-uploads/session-1/authorization',
      );
    expect(authorizationCalls).toHaveLength(2);
  });

  it('reports an offline upload as recoverable on the same task', async () => {
    let online = false;
    vi.spyOn(window.navigator, 'onLine', 'get').mockImplementation(
      () => online,
    );
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    let finishUpload: CosUploadCallback | undefined;
    const restartTask = vi.fn(() => finishUpload?.(null, uploadResult));
    const factory: CosClientFactory = () => ({
      uploadFile: (params, callback) => {
        finishUpload = callback;
        params.onTaskReady?.('task-1');
      },
      pauseTask: vi.fn(),
      restartTask,
      cancelTask: vi.fn(),
    });
    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );

    finishUpload?.(sdkError('Network Error'), undefined as never);
    await expect(handle.attempt).rejects.toThrow(
      '网络已断开，上传任务可在恢复网络后重试',
    );
    online = true;

    await expect(handle.resume()).resolves.toEqual(verifiedUpload());
    expect(restartTask).toHaveBeenCalledWith('task-1');
  });

  it('normalizes COS upload errors without exposing SDK details', async () => {
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    const factory: CosClientFactory = () => ({
      uploadFile: (params, callback) => {
        params.onTaskReady?.('task-1');
        callback(
          sdkError('Request has expired: q-signature=secret'),
          undefined as never,
        );
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask: vi.fn(),
    });

    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );

    await expect(handle.attempt).rejects.toThrow('COS 上传失败，请重试');
  });

  it('retries backend completion without re-uploading the object', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockResolvedValueOnce({
        success: false,
        errorMessage: '上传文件大小与会话声明不一致',
      })
      .mockResolvedValueOnce({ success: true, data: verifiedUpload() });
    const uploadFile = vi.fn((params, callback: CosUploadCallback) => {
      params.onTaskReady?.('task-1');
      callback(null, uploadResult);
    });
    const restartTask = vi.fn();
    const factory: CosClientFactory = () => ({
      uploadFile,
      pauseTask: vi.fn(),
      restartTask,
      cancelTask: vi.fn(),
    });
    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );

    await expect(handle.attempt).rejects.toThrow(
      '上传文件大小与会话声明不一致',
    );
    await expect(handle.retryCompletion()).resolves.toEqual(verifiedUpload());
    expect(uploadFile).toHaveBeenCalledTimes(1);
    expect(restartTask).not.toHaveBeenCalled();
    expect(request).toHaveBeenCalledTimes(3);
  });

  it('cancels an attempt while backend completion is in flight', async () => {
    let resolveCompletion!: (response: {
      success: boolean;
      data: ReturnType<typeof verifiedUpload>;
    }) => void;
    const completionResponse = new Promise<{
      success: boolean;
      data: ReturnType<typeof verifiedUpload>;
    }>((resolve) => {
      resolveCompletion = resolve;
    });
    vi.mocked(request)
      .mockResolvedValueOnce({ success: true, data: session() })
      .mockReturnValueOnce(completionResponse as never);
    let finishUpload: CosUploadCallback | undefined;
    const cancelTask = vi.fn();
    const factory: CosClientFactory = () => ({
      uploadFile: (params, callback) => {
        finishUpload = callback;
        params.onTaskReady?.('task-1');
      },
      pauseTask: vi.fn(),
      restartTask: vi.fn(),
      cancelTask,
    });
    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory },
    );
    const attempt = handle.attempt;
    const success = vi.fn();
    void attempt.then(success, () => undefined);
    const canceledAttempt = expect(attempt).rejects.toThrow('上传已取消');

    finishUpload?.(null, uploadResult);
    await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(2));
    handle.cancel();
    resolveCompletion({ success: true, data: verifiedUpload() });

    await canceledAttempt;
    await Promise.resolve();
    expect(cancelTask).toHaveBeenCalledWith('task-1');
    expect(success).not.toHaveBeenCalled();
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('pauses and cancels the retained task without completing it', async () => {
    vi.mocked(request).mockResolvedValueOnce({ success: true, data: session() });
    let finishUpload: CosUploadCallback | undefined;
    const pauseTask = vi.fn();
    const restartTask = vi.fn();
    const cancelTask = vi.fn();
    const controls = vi.fn();
    const factory: CosClientFactory = () => ({
      uploadFile: (params, callback) => {
        finishUpload = callback;
        params.onTaskReady?.('task-1');
      },
      pauseTask,
      restartTask,
      cancelTask,
    });
    const handle = await startMediaUpload(
      new File([new Uint8Array([1])], 'episode.mp4', { type: 'video/mp4' }),
      { clientFactory: factory, onControls: controls },
    );

    const initialAttempt = handle.attempt;
    handle.pause();
    const resumedAttempt = handle.resume();
    const initialRejection = expect(initialAttempt).rejects.toThrow(
      '上传已取消',
    );
    const resumedRejection = expect(resumedAttempt).rejects.toThrow(
      '上传已取消',
    );
    handle.cancel();
    finishUpload?.(null, uploadResult);

    expect(controls).toHaveBeenCalledWith(handle);
    expect(pauseTask).toHaveBeenCalledWith('task-1');
    expect(restartTask).toHaveBeenCalledWith('task-1');
    expect(cancelTask).toHaveBeenCalledWith('task-1');
    expect(resumedAttempt).not.toBe(initialAttempt);
    await Promise.all([initialRejection, resumedRejection]);
    await expect(handle.resume()).rejects.toThrow('上传已取消');
    expect(request).toHaveBeenCalledTimes(1);
  });
});
