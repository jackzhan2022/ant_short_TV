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
  thumbnailUrl?: string;
  mimeType?: string;
  sortOrder?: number;
  sourceCreatedAt?: string;
  tags?: string[];
  promptSummary?: string;
};

export type InspirationCreationDetail = InspirationCreation & {
  detailJson?: Record<string, unknown>;
  promptText?: string;
};

export type ManagedInspiration = InspirationCreation & {
  promptText: string;
  publishStatus: 'PUBLISHED' | 'UNPUBLISHED';
  sourceType: 'IMPORTED' | 'MANUAL';
  createdAt?: string;
  updatedAt?: string;
};

export type ManagedInspirationPage = {
  records: ManagedInspiration[];
  total: number;
  current: number;
  pageSize: number;
};

export const queryManagedInspirations = (params: Record<string, unknown>) =>
  request<ApiResponse<ManagedInspirationPage>>(
    '/api/platform/inspiration-creations',
    { params },
  );

export const createManagedInspiration = (values: {
  file: File;
  title: string;
  promptText: string;
  tags: string[];
  publishStatus: string;
}) => {
  const data = new FormData();
  data.append('file', values.file);
  data.append('title', values.title);
  data.append('promptText', values.promptText);
  values.tags.forEach((tag) => {
    data.append('tags', tag);
  });
  data.append('publishStatus', values.publishStatus);
  return request<ApiResponse<ManagedInspiration>>(
    '/api/platform/inspiration-creations',
    { method: 'POST', data },
  );
};

export const updateManagedInspiration = (
  id: number,
  data: Pick<ManagedInspiration, 'title' | 'tags' | 'promptText'>,
) =>
  request<ApiResponse<ManagedInspiration>>(
    `/api/platform/inspiration-creations/${id}`,
    { method: 'PUT', data },
  );

export const updateManagedInspirationStatus = (
  id: number,
  publishStatus: string,
) =>
  request<ApiResponse<ManagedInspiration>>(
    `/api/platform/inspiration-creations/${id}/publish-status`,
    { method: 'PUT', data: { publishStatus } },
  );

export const reorderManagedInspirations = (orderedIds: number[]) =>
  request<ApiResponse<void>>('/api/platform/inspiration-creations/reorder', {
    method: 'PUT',
    data: { orderedIds },
  });

export const deleteManagedInspiration = (id: number) =>
  request<ApiResponse<void>>(`/api/platform/inspiration-creations/${id}`, {
    method: 'DELETE',
  });

export type InspirationCreationPage = {
  records: InspirationCreation[];
  total: number;
  current: number;
  pageSize: number;
};

export const queryInspirationCreations = async (params: {
  page: number;
  pageSize: number;
}) =>
  request<ApiResponse<InspirationCreationPage>>('/api/inspiration-creations', {
    params,
  });

export const queryInspirationCreationDetail = async (id: number) =>
  request<ApiResponse<InspirationCreationDetail>>(
    `/api/inspiration-creations/${id}`,
  );

export type ParsedScriptContent = {
  fileName: string;
  content: string;
};

export const parseScriptFile = (file: File) => {
  const data = new FormData();
  data.append('file', file);
  return request<ApiResponse<ParsedScriptContent>>(
    '/api/script-content/parse',
    {
      method: 'POST',
      data,
    },
  );
};

export {
  queryReviewProject,
  queryReviewProjectMetrics,
  queryReviewProjectSummaries,
  queryReviewProjects,
  type ReviewProject,
  type ReviewProjectDetail,
  type ReviewProjectMetrics,
  type ReviewProjectSummary,
  type ReviewVersion,
} from '../script-review/service';
