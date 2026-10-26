import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';
import type { MediaPage, MediaPageParams } from '@/services/media/paging';

export type SystemAudio = {
  id: number;
  name: string;
  category: string;
  gender: string;
  ageGroup: string;
  mimeType: string;
  fileSize: number;
  durationSeconds: number;
  previewUrl: string;
};

export const querySystemAudio = (
  params: MediaPageParams & {
    keyword?: string;
    gender?: string;
    ageGroup?: string;
  },
  signal?: AbortSignal,
) =>
  request<ApiResponse<MediaPage<SystemAudio>>>('/api/system-audio', {
    params,
    signal,
  });
