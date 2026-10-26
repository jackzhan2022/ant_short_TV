import { request } from '@umijs/max';

export type AvatarCandidate = {
  id: number;
  characterName: string;
  variantName: string;
  imageUrl?: string;
  assetId?: string;
  taskId?: number;
  status?: string;
  phase?: string;
  errorMessage?: string;
  retryable?: boolean;
};
export type AvatarTask = {
  id?: number;
  taskKey?: string;
  variantId: number;
  status?: string;
  phase?: string;
  assetId?: string;
  errorMessage?: string;
};
const base = (project: number) => `/api/projects/${project}/virtual-avatar-tasks`;
async function read<T>(url: string, options: Record<string, unknown> = {}): Promise<T> {
  const response = await request<{ success: boolean; data: T; errorMessage?: string }>(url, options);
  if (!response.success) throw new Error(response.errorMessage || '虚拟人像任务操作失败');
  return response.data;
}
export const queryAvatarCandidates = (project: number, page: number, variantId?: number) =>
  read<AvatarCandidate[]>(`${base(project)}/candidates`, { params: { page, variantId } });
export const createAvatarTasks = (project: number, variantIds: number[]) =>
  read<AvatarTask[]>(base(project), { method: 'POST', data: { variantIds } });
export const retryAvatarTask = (project: number, taskId: number) =>
  read<AvatarTask>(`${base(project)}/${taskId}/retry`, { method: 'POST' });
