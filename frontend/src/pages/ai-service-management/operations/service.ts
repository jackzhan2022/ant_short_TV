import { request } from '@umijs/max';
import type { PlatformAiOperationsOverview } from './data';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
}

export const queryAiOperationsOverview = () =>
  request<ApiResponse<PlatformAiOperationsOverview>>(
    '/api/platform/ai/operations/overview',
  );
