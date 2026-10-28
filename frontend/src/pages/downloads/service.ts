import { request } from '@umijs/max';
import { cachedDownload, type DownloadDelivery } from './file-cache';

export type DownloadStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'READY'
  | 'FAILED'
  | 'EXPIRED';
export type DownloadSource = {
  kind: string;
  projectId?: number;
  id: number;
  parentId?: number;
};
export type DownloadTask = {
  id: string;
  name: string;
  sourceType: string;
  status: DownloadStatus;
  fileCount: number;
  fileSize?: number;
  outputFormat?: 'FILE' | 'ZIP';
  fileName?: string;
  contentType?: string;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
  expiresAt?: string;
};
export type DownloadPage = {
  items: DownloadTask[];
  total: number;
  page: number;
  pageSize: number;
};
export const statusLabels: Record<DownloadStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  READY: '可下载',
  FAILED: '生成失败',
  EXPIRED: '已过期',
};
export const sourceLabels: Record<string, string> = {
  IMAGE: '资产图片',
  VIDEO: '分镜视频',
  COMPOSE: '合成视频',
  EPISODE_VERSION: '成片',
  VOICE: '语音',
  SUBTITLE: '字幕',
  REVIEW: '审核报告',
  PROJECT_REVIEW: '项目审核报告',
  FREE_CREATION: '自由创作',
  FREE_REFERENCE: '创作参考素材',
  VIDEO_REFERENCE: '视频参考素材',
};
async function read<T>(
  url: string,
  options: Record<string, unknown> = {},
): Promise<T> {
  const result = await request<{
    success: boolean;
    data: T;
    errorMessage?: string;
  }>(url, { skipErrorHandler: true, ...options });
  if (!result.success)
    throw new Error(result.errorMessage || '下载任务操作失败，请重试');
  return result.data;
}
export async function createDownloadTask(
  name: string,
  sources: DownloadSource[],
  signal?: AbortSignal,
) {
  const task = await read<DownloadTask>('/api/downloads', {
    method: 'POST',
    data: { name, sources },
    signal,
  });
  if (!signal?.aborted)
    window.dispatchEvent(
      new CustomEvent('download-center-created', { detail: task }),
    );
  return task;
}
export const listDownloadTasks = (
  tenant: number,
  query: { keyword?: string; status?: string; page: number; pageSize: number },
  signal?: AbortSignal,
) =>
  read<DownloadPage>('/api/downloads', {
    params: query,
    headers: { 'X-Tenant-Id': String(tenant) },
    signal,
  });
export const retryDownloadTask = (tenant: number, id: string) =>
  read<DownloadTask>(`/api/downloads/${id}/retry`, {
    method: 'POST',
    headers: { 'X-Tenant-Id': String(tenant) },
  });
export async function receiveDownload(tenant: number, task: DownloadTask) {
  const delivery = await read<DownloadDelivery>(
    `/api/downloads/${task.id}/delivery`,
    {
      headers: { 'X-Tenant-Id': String(tenant) },
      cache: 'no-store',
    },
  );
  if (!(Date.parse(delivery.expiresAt) > Date.now()))
    throw new Error('文件已过期，请重新生成');
  const blob = await cachedDownload(delivery);
  if (!(Date.parse(delivery.expiresAt) > Date.now()))
    throw new Error('文件已过期，请重新生成');
  const objectUrl = blob ? URL.createObjectURL(blob) : undefined;
  const link = document.createElement('a');
  // The server checks access again and redirects; it never proxies file bytes.
  link.href =
    objectUrl ||
    `/api/downloads/${encodeURIComponent(task.id)}/file?tenantId=${tenant}`;
  link.download = delivery.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  if (objectUrl) window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
}
export function effectiveStatus(
  task: Pick<DownloadTask, 'status' | 'expiresAt'>,
  now = Date.now(),
): DownloadStatus {
  return task.status === 'READY' &&
    task.expiresAt &&
    Date.parse(task.expiresAt) <= now
    ? 'EXPIRED'
    : task.status;
}
export function sourceFromDownloadUrl(url: string): DownloadSource | undefined {
  const match = url.match(
    /^\/api\/projects\/(\d+)\/(ai-image-results|ai-video-results|shot-compose-results|episode-video-versions|ai-voice-results)\/(\d+)\/(?:download|download-file)$/,
  );
  if (!match) return undefined;
  const kinds: Record<string, string> = {
    'ai-image-results': 'IMAGE',
    'ai-video-results': 'VIDEO',
    'shot-compose-results': 'COMPOSE',
    'episode-video-versions': 'EPISODE_VERSION',
    'ai-voice-results': 'VOICE',
  };
  return {
    kind: kinds[match[2]],
    projectId: Number(match[1]),
    id: Number(match[3]),
  };
}
