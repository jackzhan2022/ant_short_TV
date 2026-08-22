export { createProject, queryOrganizations } from '@/services/account-team/project';
import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export { queryTenantMembers } from '@/services/account-team/member';
export { queryStyleLibrary } from '../style-library/service';

export type InspirationCreation = {
  id: number;
  externalId?: string;
  creationType?: string;
  taskType?: string;
  title?: string;
  authorName?: string;
  localUrl: string;
  mimeType?: string;
  sortOrder?: number;
  sourceCreatedAt?: string;
};

export type InspirationCreationDetail = InspirationCreation & {
  detailJson?: string;
};

export const queryInspirationCreations = async () =>
  request<ApiResponse<InspirationCreation[]>>('/api/inspiration-creations');

export const queryInspirationCreationDetail = async (externalId: string) =>
  request<ApiResponse<InspirationCreationDetail>>(
    `/api/inspiration-creations/${externalId}`,
  );
