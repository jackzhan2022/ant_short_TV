export { createProject } from '@/services/account-team/project';
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
  url: string;
  mimeType?: string;
  sortOrder?: number;
  sourceCreatedAt?: string;
};

export type InspirationCreationDetail = InspirationCreation & {
  detailJson?: Record<string, unknown>;
};

export type InspirationCreationPage = {
  records: InspirationCreation[];
  total: number;
  current: number;
  pageSize: number;
};

export const queryInspirationCreations = async (params: {
  page: number;
  pageSize: number;
}) => request<ApiResponse<InspirationCreationPage>>('/api/inspiration-creations', { params });

export const queryInspirationCreationDetail = async (id: number) =>
  request<ApiResponse<InspirationCreationDetail>>(
    `/api/inspiration-creations/${id}`,
  );
