import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';
import type { MediaPage, MediaPageParams } from '@/services/media/paging';

export type AssetLibraryItem = {
  id: number;
  resourceType: 'AI_IMAGE_RESULT' | 'AI_VIDEO_RESULT' | 'SHOT_COMPOSE_RESULT' | 'EPISODE_VIDEO_VERSION' | 'PROJECT_AUDIO_ASSET' | 'AI_VOICE_RESULT';
  name: string;
  category: string;
  sourceType: string;
  thumbnailUrl: string;
  previewUrl: string;
  downloadUrl?: string | null;
  episodeNo?: number | null;
  shotNo?: number | null;
  durationSeconds?: number | null;
  current: boolean;
  updatedAt: string;
};
export type AssetLibraryResponse = {
  items: MediaPage<AssetLibraryItem>;
  episodeNumbers: number[];
  categoryCounts: Record<string, number>;
};
export const queryProjectAssetLibrary = (
  projectId: number,
  params: MediaPageParams & { mediaType: 'IMAGE' | 'VIDEO' | 'AUDIO'; category?: string; episodeNo?: number; keyword?: string },
  signal?: AbortSignal,
) => request<ApiResponse<AssetLibraryResponse>>(`/api/projects/${projectId}/asset-library`, { params, signal });

const downloadImageFile = async (
  endpoint: string, fallbackName: string, kind: 'image' | 'zip', signal?: AbortSignal, resultIds?: number[],
) => {
  const tenantId = localStorage.getItem('currentTenantId');
  const csrfCookie = resultIds ? document.cookie.split(';').map((entry) => entry.trim())
    .find((entry) => entry.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length) : undefined;
  let csrfToken = csrfCookie;
  if (csrfCookie) {
    try { csrfToken = decodeURIComponent(csrfCookie); } catch { csrfToken = csrfCookie; }
  }
  const response = await fetch(endpoint, {
    method: resultIds ? 'POST' : 'GET',
    credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal,
    headers: {
      ...(tenantId ? { 'X-Tenant-Id': tenantId } : {}),
      ...(resultIds ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    ...(resultIds ? { body: JSON.stringify({ resultIds }) } : {}),
  });
  const type = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
  const validType = type === 'application/octet-stream' || (kind === 'zip' ? type === 'application/zip' : type.startsWith('image/'));
  if (!response.ok || !validType) {
    let detail = `下载失败，请重试（${response.status}）。`;
    if (type === 'application/json') {
      const error = await response.json().catch(() => undefined);
      if (typeof error?.errorMessage === 'string') detail = error.errorMessage;
    }
    throw new Error(detail);
  }
  const disposition = response.headers.get('content-disposition') || '';
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  let name = disposition.match(/filename="?([^";]+)"?/i)?.[1] || fallbackName;
  if (encoded) {
    try { name = decodeURIComponent(encoded); } catch { name = fallbackName; }
  }
  const blob = await response.blob();
  signal?.throwIfAborted();
  if (!blob.size) throw new Error('下载文件为空，请重试。');
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  try {
    link.href = url; link.download = name;
    document.body.appendChild(link);
    link.click();
  } finally {
    link.remove();
    URL.revokeObjectURL(url);
  }
};

export const downloadAssetImage = (projectId: number, resultId: number, signal?: AbortSignal) =>
  downloadImageFile(`/api/projects/${projectId}/ai-image-results/${resultId}/download-file`, `图片_${resultId}.png`, 'image', signal);

export const downloadAssetImages = (projectId: number, resultIds: number[], signal?: AbortSignal) =>
  downloadImageFile(`/api/projects/${projectId}/ai-image-results/download-archive`, `项目${projectId}_资产图片.zip`, 'zip', signal, resultIds);
