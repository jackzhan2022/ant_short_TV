import type { AvatarRecovery } from './avatar-recovery';
import { request } from '@umijs/max';
import { pointEstimatedRequest } from '@/services/point-estimate';
import type { CreationMode } from './service';
export type CreationParameters = {
  count?: number;
  aspectRatio?: string;
  quality?: string;
  outputFormat?: string;
  durationSeconds?: number;
  intelligentDuration?: boolean;
  resolution?: string;
  size?: string;
  negativePrompt?: string;
  generateAudio?: boolean;
  watermark?: boolean;
};
export type CreationReference = {
  id: number;
  referenceId: number;
  kind: 'IMAGE' | 'VIDEO' | 'AUDIO';
  fileName: string;
  thumbnailUrl?: string;
  previewUrl?: string;
};
export type CreationOutput = {
  id: number;
  thumbnailUrl?: string;
  thumbnailStatus?: 'PENDING' | 'READY' | 'FAILED' | 'RETIRED';
  previewUrl?: string;
  downloadUrl?: string;
  durationSeconds?: number;
};
export type CreationStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED';
export type CreationRecord = {
  id: number;
  executionId?: number;
  taskKey?: string;
  allowedActions?: ('CANCEL' | 'RETRY' | 'REGENERATE')[];
  mediaType: CreationMode;
  status: CreationStatus;
  phase?: string;
  originalStatus?: CreationStatus;
  originalExecutionId?: number;
  prompt: string;
  creatorId: number;
  creatorName: string;
  createdAt: string;
  modelId: number;
  modelName: string;
  parameters: CreationParameters;
  references: CreationReference[];
  outputs: CreationOutput[];
  errorMessage?: string;
  avatarRecovery?: AvatarRecovery;
  avatarRecoverySourceCreationId?: number;
};
export type CreationRequest = {
  requestId: string;
  mediaType: CreationMode;
  modelId: number;
  prompt: string;
  parameters: CreationParameters;
  references: {
    kind: CreationReference['kind'];
    uploadSessionToken?: string;
    referenceId?: number;
    projectImage?: { projectId: number; resultId: number };
  }[];
};
export type CreationPage = {
  records: CreationRecord[];
  total: number;
  current: number;
  pageSize: number;
};
export type CreationQuery = {
  scope: 'mine' | 'team';
  mediaType?: CreationMode;
  status?: CreationStatus;
  keyword?: string;
  current: number;
  pageSize: number;
};
type Response<T> = { success: boolean; data: T; errorMessage?: string };
function unwrap<T>(response: Response<T>): T {
  if (!response?.success || response.data == null)
    throw new Error(response?.errorMessage || '自由创作请求失败，请重试');
  return response.data;
}
export async function listCreations(
  params: CreationQuery,
  signal?: AbortSignal,
) {
  const page = unwrap(
    await request<Response<CreationPage>>('/api/free-creations', {
      method: 'GET',
      params,
      signal,
      skipErrorHandler: true,
    }),
  );
  if (!Array.isArray(page.records) || !Number.isFinite(page.total))
    throw new Error('创作记录响应不完整，请重试');
  return page;
}
export async function getCreation(id: number, signal?: AbortSignal) {
  return unwrap(
    await request<Response<CreationRecord>>(`/api/free-creations/${id}`, {
      method: 'GET',
      signal,
      skipErrorHandler: true,
    }),
  );
}
export async function createCreation(
  data: CreationRequest,
  signal?: AbortSignal,
) {
  return unwrap(
    await pointEstimatedRequest<Response<CreationRecord>>(
      '/api/free-creations',
      { method: 'POST', data, signal, skipErrorHandler: true },
    ),
  );
}
export async function getCreationDownload(
  id: number,
  outputId: number,
  signal?: AbortSignal,
) {
  const tenantId = localStorage.getItem('currentTenantId');
  const response = await fetch(
    `/api/free-creations/${id}/outputs/${outputId}/download-file`,
    {
      method: 'GET', credentials: 'same-origin', cache: 'no-store',
      redirect: 'error', signal,
      headers: tenantId ? { 'X-Tenant-Id': tenantId } : {},
    },
  );
  const type = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
  if (!response.ok || !(type.startsWith('image/') || type.startsWith('video/') || type === 'application/octet-stream')) {
    let detail = `下载失败，请重试（${response.status}）。`;
    if (type === 'application/json') {
      const error = await response.json().catch(() => undefined);
      if (typeof error?.errorMessage === 'string') detail = error.errorMessage;
    }
    throw new Error(detail);
  }
  const disposition = response.headers.get('content-disposition') || '';
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  let fileName = disposition.match(/filename="?([^";]+)"?/i)?.[1] || `作品_${outputId}`;
  if (encoded) {
    try { fileName = decodeURIComponent(encoded); } catch { fileName = `作品_${outputId}`; }
  }
  const blob = await response.blob();
  signal?.throwIfAborted();
  if (!blob.size) throw new Error('下载文件为空，请重试。');
  return { url: URL.createObjectURL(blob), fileName };
}
