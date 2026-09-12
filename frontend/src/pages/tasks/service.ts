import { request } from '@umijs/max';

export type Task = {
  taskKey: string;
  type: string;
  title: string;
  projectId?: number;
  projectName?: string;
  creatorId: number;
  creatorName: string;
  statusGroup: string;
  domainStatus: string;
  progress?: number | null;
  phase?: string;
  createdAt: string;
  completedAt?: string;
  restricted: boolean;
  destination?: string;
  resultSummary?: string;
  warningSummary?: string;
  childCounts: Record<string, number>;
  allowedActions: string[];
};
export type TaskPage = {
  items: Task[];
  total: number;
  page: number;
  pageSize: number;
  canViewTeamTasks: boolean;
};
export type Summary = { total: number; counts: Record<string, number> };
export type ContentItem = { id?: number; url?: string; thumbnailUrl?: string; width?: number; height?: number; selected?: boolean; [key: string]: unknown };
export type ContentSection = {
  key: string;
  title: string;
  kind: 'TEXT' | 'FIELDS' | 'IMAGE' | 'VIDEO' | 'STRUCTURED' | 'BUSINESS_STAGE';
  availability: 'AVAILABLE' | 'PENDING' | 'NOT_RECORDED' | 'DELETED' | 'RESTRICTED' | 'UNSUPPORTED';
  preview?: string | null;
  fields: { label: string; value: string }[];
  items: ContentItem[];
  hasMore: boolean;
};
export type TaskContent = { schemaVersion: number; taskKey: string; contentRevision: string; sections: ContentSection[] };
export type TaskContentSection = { taskKey: string; sectionKey: string; availability: ContentSection['availability']; text: string; hasMore: boolean; nextOffset?: number };
export type Query = {
  scope: string;
  page: number;
  pageSize: number;
  type?: string;
  statusGroup?: string;
  projectId?: string;
  creatorId?: string;
  createdFrom?: string;
  createdTo?: string;
};
const base = (tenant: number) => `/api/tenants/${tenant}/production-tasks`;
async function read<T>(
  url: string,
  options: Record<string, unknown> = {},
): Promise<T> {
  const response = await request<{
    success: boolean;
    data: T;
    errorMessage?: string;
  }>(url, { skipErrorHandler: true, ...options });
  if (!response.success)
    throw new Error(response.errorMessage || '任务加载失败');
  return response.data;
}
export const listTasks = (tenant: number, query: Query, signal?: AbortSignal) =>
  read<TaskPage>(base(tenant), { params: query, signal });
export const taskSummary = (
  tenant: number,
  query: Query,
  signal?: AbortSignal,
) => read<Summary>(`${base(tenant)}/summary`, { params: query, signal });
export const taskDetail = (tenant: number, key: string, signal?: AbortSignal) =>
  read<Task>(`${base(tenant)}/${encodeURIComponent(key)}`, { signal });
export const taskContent = (tenant: number, key: string, signal?: AbortSignal) =>
  read<TaskContent>(`${base(tenant)}/${encodeURIComponent(key)}/content`, { signal });
export const taskContentSection = (tenant: number, key: string, sectionKey: string, offset: number, signal?: AbortSignal) =>
  read<TaskContentSection>(`${base(tenant)}/${encodeURIComponent(key)}/content/${encodeURIComponent(sectionKey)}`, { params: { offset }, signal });
export const taskChildren = (
  tenant: number,
  key: string,
  query: Query,
  signal?: AbortSignal,
) =>
  read<TaskPage>(`${base(tenant)}/${encodeURIComponent(key)}/children`, {
    params: query,
    signal,
  });
export const controlTask = (
  tenant: number,
  key: string,
  action: string,
  idempotencyKey: string,
) =>
  read<Task>(
    `${base(tenant)}/${encodeURIComponent(key)}/${action.toLowerCase()}`,
    { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey } },
  );
