import { request } from '@umijs/max';
import type { AiVideoTask } from './service';

export type AvatarRecovery = {
  id: number;
  sourceTaskId: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  phase: string;
  retryTaskId?: number | null;
  errorMessage?: string | null;
};

export async function queryAvatarRecovery(projectId: number, taskId: number, signal?: AbortSignal) {
  const response = await request<{ success: boolean; data: AvatarRecovery | null; errorMessage?: string }>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}/avatar-recovery`, { signal },
  );
  if (!response.success) throw new Error(response.errorMessage || '素材准备状态查询失败');
  return response.data;
}

export async function queryRecoveredVideoTask(projectId: number, taskId: number, signal?: AbortSignal) {
  const response = await request<{ success: boolean; data: AiVideoTask; errorMessage?: string }>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}`, { signal },
  );
  if (!response.success || !response.data) throw new Error(response.errorMessage || '重试视频状态查询失败');
  return response.data;
}
