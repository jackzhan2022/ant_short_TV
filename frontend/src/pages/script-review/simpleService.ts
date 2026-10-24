import { pointEstimatedRequest as request } from '@/services/point-estimate';
import type { ApiResponse, ReviewVersion } from './service';

export type ReviewSummary = {
  id: number;
  projectId: number;
  name: string;
  scriptVersionId: number;
  status: string;
  selectedDimensions: string[];
  createdAt: string;
  completedAt?: string | null;
};
export type ReviewHistoryPage = {
  items: ReviewSummary[];
  page: number;
  pageSize: number;
  total: number;
};
export type ReviewProgress = {
  id: number;
  status: string;
  overallProgress: number;
  currentAction?: string | null;
  errorMessage?: string | null;
  retryKind?: string | null;
};
export type ReviewReport = {
  task: ReviewSummary;
  reportMarkdown?: string | null;
};
export type ReviewSubmission = {
  content: string;
  selectedDimensions: string[];
  requestId: string;
};

const options = (tenantId: number, signal?: AbortSignal) => ({
  headers: { 'X-Tenant-Id': String(tenantId) },
  signal,
});
export const submitReview = (
  tenantId: number,
  data: ReviewSubmission,
  signal?: AbortSignal,
) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    '/api/script-review/submissions',
    {
      ...options(tenantId, signal),
      method: 'POST',
      data,
    },
  );
export const getReviewHistory = (
  tenantId: number,
  page: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<ReviewHistoryPage>>('/api/script-review/history', {
    ...options(tenantId, signal),
    params: { page, pageSize: 20 },
  }).then((response) => response.data);
export const getReviewProgress = (
  tenantId: number,
  taskId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<ReviewProgress>>(
    `/api/script-review/tasks/${taskId}/progress`,
    options(tenantId, signal),
  ).then((response) => response.data);
export const getReviewReport = (
  tenantId: number,
  taskId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<ReviewReport>>(
    `/api/script-review/tasks/${taskId}/report`,
    options(tenantId, signal),
  ).then((response) => response.data);
export const getReviewContent = (
  tenantId: number,
  taskId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<ReviewVersion>>(
    `/api/script-review/tasks/${taskId}/content`,
    options(tenantId, signal),
  ).then((response) => response.data);
export const retrySimpleReview = (
  tenantId: number,
  taskId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    `/api/script-review/tasks/${taskId}/retry`,
    {
      ...options(tenantId, signal),
      method: 'POST',
      params: { fullRegeneration: false },
    },
  );
export const cancelSimpleReview = (
  tenantId: number,
  taskId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<API.AiExecutionResponse>>(
    `/api/script-review/tasks/${taskId}/cancel`,
    {
      ...options(tenantId, signal),
      method: 'POST',
    },
  );
