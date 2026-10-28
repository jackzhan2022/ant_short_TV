import { createDownloadTask } from '@/pages/downloads/service';
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

export const downloadAssetImage = (projectId: number, resultId: number, signal?: AbortSignal) =>
  createDownloadTask(`图片_${resultId}`, [{ kind: 'IMAGE', projectId, id: resultId }], signal);

export const downloadAssetImages = (projectId: number, resultIds: number[], signal?: AbortSignal) =>
  createDownloadTask(`项目${projectId}_资产图片`, resultIds.map(id => ({ kind: 'IMAGE', projectId, id })), signal);
