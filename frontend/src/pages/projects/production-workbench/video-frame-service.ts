import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export const videoFrameSource = (projectId: number, resultId: number) =>
  `/api/projects/${projectId}/ai-video-results/${resultId}/frame-source`;

export type SavedVideoFrame = {
  id: number;
  imageUrl: string;
  thumbnailUrl: string;
  timeSeconds: number;
  sourceVideoResultId: number;
};

export async function saveVideoFrame(projectId: number, resultId: number, sessionToken: string, timeSeconds: number, signal: AbortSignal) {
  const response = await request<ApiResponse<SavedVideoFrame>>(
    `/api/projects/${projectId}/ai-video-results/${resultId}/frames`,
    { method: 'POST', data: { sessionToken, timeSeconds }, signal, skipErrorHandler: true },
  );
  if (!response.success || !response.data) throw new Error(response.errorMessage || '截图保存失败，请重试');
  return response.data;
}
