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

export type UploadControls = {
  pause: () => void;
  resume: () => void;
  cancel: () => void;
};

type CosClient = Pick<
  COS,
  'uploadFile' | 'pauseTask' | 'restartTask' | 'cancelTask'
>;

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

export const startMediaUpload = async (
  file: File,
  options: StartMediaUploadOptions = {},
): Promise<VerifiedMediaUpload> => {
  const session = await createSession(file, options.projectId);
  let authorizationFailure: Error | undefined;
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

  try {
    await cos.uploadFile({
      Bucket: session.bucket,
      Region: session.region,
      StorageClass: session.storageClass,
      Key: session.objectKey,
      Body: file,
      SliceSize: 16 * 1024 * 1024,
      onProgress: options.onProgress,
      onTaskReady: (taskId) => {
        options.onControls?.({
          pause: () => cos.pauseTask(taskId),
          resume: () => cos.restartTask(taskId),
          cancel: () => cos.cancelTask(taskId),
        });
      },
    });
  } catch {
    if (authorizationFailure) {
      throw authorizationFailure;
    }
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      throw new Error('网络已断开，上传任务可在恢复网络后重试');
    }
    throw new Error('COS 上传失败，请重试');
  }

  return await completeSession(session.sessionToken);
};
