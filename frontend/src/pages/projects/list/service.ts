export { queryTenantMembers } from '@/services/account-team/member';
export {
  createProject,
  queryProjects,
  updateProject,
  updateProjectStatus,
} from '@/services/account-team/project';

import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';
import { startMediaUpload } from '@/services/mediaUpload';

export const startProjectCoverUpload = (projectId: number, file: File) =>
  startMediaUpload(file, { projectId });

export const bindProjectCoverUpload = (
  projectId: number,
  sessionToken: string,
) =>
  request<ApiResponse<{ status: 'MISSING' | 'PENDING' | 'READY' | 'FAILED' }>>(
    `/api/projects/${projectId}/cover/upload`,
    { method: 'POST', data: { sessionToken } },
  );
