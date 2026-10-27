import { request } from '@umijs/max';
import type { ApiResponse, AiVideoResult } from './service';

export type EraseRegion = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};
export type SubtitleEraseSettings = {
  method: 'AUTO' | 'REGION';
  quality: 'STANDARD' | 'PRO';
  region?: EraseRegion;
};
export type SubtitleEraseTask = {
  id: number;
  executionId: number;
  storyboardId: number;
  sourceResultId: number;
  method: SubtitleEraseSettings['method'];
  quality: SubtitleEraseSettings['quality'];
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT';
  progress?: number;
  phase?: string;
  errorMessage?: string;
  retryable: boolean;
  createdAt?: string;
  result?: AiVideoResult | null;
};
export const isEraseActive = (task: SubtitleEraseTask) =>
  ['PENDING', 'RUNNING'].includes(task.status);
export const subtitleEraseSource = (projectId: number, resultId: number) =>
  `/api/projects/${projectId}/ai-video-results/${resultId}/playback`;
async function read<T>(url: string, options: Record<string, unknown> = {}) {
  const response = await request<ApiResponse<T>>(url, {
    skipErrorHandler: true,
    ...options,
  });
  if (!response.success || !response.data)
    throw new Error(response.errorMessage || '字幕擦除请求失败，请重试');
  return response.data;
}
export const createSubtitleEraseTask = (
  projectId: number,
  resultId: number,
  settings: SubtitleEraseSettings,
  key: string,
) =>
  read<SubtitleEraseTask>(
    `/api/projects/${projectId}/ai-video-results/${resultId}/subtitle-erasure`,
    {
      method: 'POST',
      data: settings,
      headers: { 'Idempotency-Key': key },
    },
  );
export const querySubtitleEraseTasks = (
  projectId: number,
  storyboardIds: number[],
  signal?: AbortSignal,
) =>
  read<SubtitleEraseTask[]>(`/api/projects/${projectId}/subtitle-erase-tasks`, {
    params: { storyboardIds: storyboardIds.join(',') },
    signal,
  });
export const retrySubtitleEraseTask = (
  projectId: number,
  id: number,
  key: string,
) =>
  read<SubtitleEraseTask>(
    `/api/projects/${projectId}/subtitle-erase-tasks/${id}/retry`,
    { method: 'POST', headers: { 'Idempotency-Key': key } },
  );
