import { request } from '@umijs/max';
import COS from 'cos-js-sdk-v5';

type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type MediaUploadSession = {
  sessionToken: string;
  bucket: string;
  region: string;
  storageClass: COS.StorageClass;
  objectKey: string;
  status: string;
  expiresAt: string;
};

type CosUploadAuthorization = {
  authorization: string;
  securityToken: string;
  expiresAt: number;
};

export type VerifiedMediaUpload = {
  sessionToken: string;
  objectKey: string;
  contentType: string;
  size: number;
  eTag: string;
};

export type MediaUploadHandle = {
  readonly session: MediaUploadSession;
  readonly sessionToken: string;
  readonly objectKey: string;
  readonly taskId?: COS.TaskId;
  readonly attempt: Promise<VerifiedMediaUpload>;
  pause: () => void;
  resume: () => Promise<VerifiedMediaUpload>;
  cancel: () => void;
  retryCompletion: () => Promise<VerifiedMediaUpload>;
};

export type UploadControls = Pick<
  MediaUploadHandle,
  'pause' | 'resume' | 'cancel'
>;

type CosClient = {
  uploadFile: (
    params: COS.UploadFileParams,
    callback: CosUploadCallback,
  ) => void;
  pauseTask: COS['pauseTask'];
  restartTask: COS['restartTask'];
  cancelTask: COS['cancelTask'];
};

type CosUploadCallback = (
  error: COS.CosError | null,
  data: COS.UploadFileResult,
) => void;

export type CosClientFactory = (options: COS.COSOptions) => CosClient;

export type StartMediaUploadOptions = {
  projectId?: number;
  onProgress?: (progress: COS.ProgressInfo) => void;
  onControls?: (controls: UploadControls) => void;
  clientFactory?: CosClientFactory;
};

const defaultClientFactory: CosClientFactory = (options) => new COS(options);

const unwrap = <T>(response: ApiResponse<T>): T => {
  if (!response.success) {
    throw new Error(response.errorMessage || '媒体上传请求失败');
  }
  return response.data;
};

const createSession = async (file: File, projectId?: number) =>
  unwrap(
    await request<ApiResponse<MediaUploadSession>>('/api/media-uploads', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: {
        projectId,
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        fileSize: file.size,
      },
    }),
  );

const stringValues = (values: Record<string, unknown> | undefined) =>
  Object.fromEntries(
    Object.entries(values || {})
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => [key, String(value)]),
  );

const authorizeRequest = async (
  sessionToken: string,
  options: COS.GetAuthorizationOptions,
) =>
  unwrap(
    await request<ApiResponse<CosUploadAuthorization>>(
      `/api/media-uploads/${sessionToken}/authorization`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: {
          method: options.Method,
          pathname: options.Pathname,
          query: stringValues(options.Query),
          headers: stringValues(options.Headers),
        },
      },
    ),
  );

const completeSession = async (sessionToken: string) =>
  unwrap(
    await request<ApiResponse<VerifiedMediaUpload>>(
      `/api/media-uploads/${sessionToken}/complete`,
      { method: 'POST' },
    ),
  );

type UploadAttempt = {
  promise: Promise<VerifiedMediaUpload>;
  resolve: (value: VerifiedMediaUpload) => void;
  reject: (reason: Error) => void;
  settled: boolean;
};

const createUploadAttempt = (): UploadAttempt => {
  let resolvePromise!: (value: VerifiedMediaUpload) => void;
  let rejectPromise!: (reason: Error) => void;
  const promise = new Promise<VerifiedMediaUpload>((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });
  const attempt: UploadAttempt = {
    promise,
    resolve: (value) => {
      if (!attempt.settled) {
        attempt.settled = true;
        resolvePromise(value);
      }
    },
    reject: (reason) => {
      if (!attempt.settled) {
        attempt.settled = true;
        rejectPromise(reason);
      }
    },
    settled: false,
  };
  return attempt;
};

export const startMediaUpload = async (
  file: File,
  options: StartMediaUploadOptions = {},
): Promise<MediaUploadHandle> => {
  const session = await createSession(file, options.projectId);
  let authorizationFailure: Error | undefined;
  let taskId: COS.TaskId | undefined;
  let currentAttempt = createUploadAttempt();
  let pendingAttempts = [currentAttempt];
  let completionPromise: Promise<VerifiedMediaUpload> | undefined;
  let completedUpload: VerifiedMediaUpload | undefined;
  let cosUploadSucceeded = false;
  let canceled = false;
  const factory = options.clientFactory || defaultClientFactory;
  const cos = factory({
    ChunkSize: 16 * 1024 * 1024,
    SliceSize: 16 * 1024 * 1024,
    ChunkParallelLimit: 3,
    FileParallelLimit: 1,
    getAuthorization: (requestOptions, callback) => {
      void authorizeRequest(session.sessionToken, requestOptions)
        .then((value) => {
          callback({
            Authorization: value.authorization,
            SecurityToken: value.securityToken,
          } as COS.GetAuthorizationCallbackParams);
        })
        .catch((error) => {
          authorizationFailure =
            error instanceof Error ? error : new Error('COS 上传授权失败');
          callback('');
        });
    },
  });

  const completion = () => {
    if (completedUpload) {
      return Promise.resolve(completedUpload);
    }
    if (!completionPromise) {
      completionPromise = completeSession(session.sessionToken)
        .then((uploaded) => {
          completedUpload = uploaded;
          return uploaded;
        })
        .catch((error) => {
          completionPromise = undefined;
          throw error;
        });
    }
    return completionPromise;
  };

  const normalizedCosFailure = () => {
    if (authorizationFailure) {
      return authorizationFailure;
    }
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      return new Error('网络已断开，上传任务可在恢复网络后重试');
    }
    return new Error('COS 上传失败，请重试');
  };

  const rejectPendingAttempts = (error: Error) => {
    const attempts = pendingAttempts;
    pendingAttempts = [];
    for (const attempt of attempts) {
      attempt.reject(error);
    }
  };

  const handle: MediaUploadHandle = {
    session,
    sessionToken: session.sessionToken,
    objectKey: session.objectKey,
    get taskId() {
      return taskId;
    },
    get attempt() {
      return currentAttempt.promise;
    },
    pause: () => {
      if (taskId && !canceled && !completedUpload) {
        cos.pauseTask(taskId);
      }
    },
    resume: () => {
      if (canceled) {
        return Promise.reject(new Error('上传已取消'));
      }
      if (cosUploadSucceeded) {
        return Promise.reject(new Error('COS 上传已完成，请重试确认'));
      }
      if (!taskId) {
        return Promise.reject(new Error('COS 上传任务尚未就绪，无法恢复'));
      }
      authorizationFailure = undefined;
      currentAttempt = createUploadAttempt();
      pendingAttempts.push(currentAttempt);
      try {
        cos.restartTask(taskId);
      } catch {
        rejectPendingAttempts(normalizedCosFailure());
      }
      return currentAttempt.promise;
    },
    cancel: () => {
      if (canceled || completedUpload) {
        return;
      }
      canceled = true;
      if (taskId) {
        cos.cancelTask(taskId);
      }
      rejectPendingAttempts(new Error('上传已取消'));
    },
    retryCompletion: () => {
      if (canceled) {
        return Promise.reject(new Error('上传已取消'));
      }
      if (!cosUploadSucceeded) {
        return Promise.reject(new Error('COS 上传尚未完成'));
      }
      return completion();
    },
  };

  try {
    cos.uploadFile(
      {
        Bucket: session.bucket,
        Region: session.region,
        StorageClass: session.storageClass,
        Key: session.objectKey,
        Body: file,
        SliceSize: 16 * 1024 * 1024,
        onProgress: options.onProgress,
        onTaskReady: (readyTaskId) => {
          taskId = readyTaskId;
          options.onControls?.(handle);
        },
      },
      (error) => {
        if (canceled) {
          return;
        }
        if (cosUploadSucceeded) {
          return;
        }
        if (error) {
          rejectPendingAttempts(normalizedCosFailure());
          return;
        }
        cosUploadSucceeded = true;
        const completingAttempts = pendingAttempts;
        void completion().then(
          (uploaded) => {
            pendingAttempts = [];
            for (const attempt of completingAttempts) {
              attempt.resolve(uploaded);
            }
          },
          (completionError) => {
            pendingAttempts = [];
            for (const attempt of completingAttempts) {
              attempt.reject(completionError);
            }
          },
        );
      },
    );
  } catch {
    rejectPendingAttempts(normalizedCosFailure());
  }

  return handle;
};
