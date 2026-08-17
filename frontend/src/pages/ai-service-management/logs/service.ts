import { request } from '@umijs/max';
import type { AiCallLogPage, AiCallLogQueryParams } from './data';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
}

export const queryAiCallLogs = async (
  tenantId: number,
  params: AiCallLogQueryParams,
) =>
  request<ApiResponse<AiCallLogPage>>(`/api/tenants/${tenantId}/ai-call-logs`, {
    params,
  });
