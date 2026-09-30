import { request } from '@umijs/max';
import COS from 'cos-js-sdk-v5';

type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type TemporaryCosCredentials = {
  tmpSecretId: string;
  tmpSecretKey: string;
  sessionToken: string;
  expiredTime: number;
  requestId?: string;
};

export type MediaUploadSession = {
  sessionToken: string;
  bucket: string;
  region: string;
  objectKey: string;
  status: string;
  expiresAt: string;
  credentials?: TemporaryCosCredentials | null;
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

const renewSession = async (sessionToken: string) =>
  unwrap(
    await request<ApiResponse<MediaUploadSession>>(
      `/api/media-uploads/${sessionToken}/credentials`,
      { method: 'POST' },
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
  let session = await createSession(file, options.projectId);
  const factory = options.clientFactory || defaultClientFactory;
  const cos = factory({
    ChunkSize: 16 * 1024 * 1024,
    SliceSize: 16 * 1024 * 1024,
    ChunkParallelLimit: 3,
    FileParallelLimit: 1,
    getAuthorization: (_requestOptions, callback) => {
      void (async () => {
        const now = Math.floor(Date.now() / 1000);
        if (!session.credentials || session.credentials.expiredTime <= now + 60) {
          session = await renewSession(session.sessionToken);
        }
        const credentials = session.credentials;
        if (!credentials) {
          throw new Error('服务端未返回 COS 临时凭证');
        }
        callback({
          TmpSecretId: credentials.tmpSecretId,
          TmpSecretKey: credentials.tmpSecretKey,
          SecurityToken: credentials.sessionToken,
          StartTime: now - 60,
          ExpiredTime: credentials.expiredTime,
          ScopeLimit: true,
        });
      })();
    },
  });

  try {
    await cos.uploadFile({
      Bucket: session.bucket,
      Region: session.region,
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
    return await completeSession(session.sessionToken);
  } catch (error) {
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      throw new Error('网络已断开，上传任务可在恢复网络后重试');
    }
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('COS 上传失败，请重试');
  }
};
