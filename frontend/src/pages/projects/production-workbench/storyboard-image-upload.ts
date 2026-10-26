import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export type UploadedStoryboardImage = { materialId: number; name: string };

export const bindStoryboardImageUpload = (
  projectId: number, storyboardId: number, sessionToken: string, name: string,
) => request<ApiResponse<UploadedStoryboardImage>>(
  `/api/projects/${projectId}/storyboards/${storyboardId}/reference-image-upload`,
  { method: 'POST', data: { sessionToken, name } },
);
