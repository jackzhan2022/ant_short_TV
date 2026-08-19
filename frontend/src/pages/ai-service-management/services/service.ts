import { request } from '@umijs/max';
import type {
  AiProvider,
  AiServiceConfig,
  AiServiceConfigFormValues,
} from './data';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
}

interface TestResult {
  status: 'SUCCESS' | 'FAILED';
  message: string;
}

export const queryAiProviders = async () =>
  request<ApiResponse<AiProvider[]>>('/api/ai-providers');

export const queryAiServiceConfigs = async () =>
  request<ApiResponse<AiServiceConfig[]>>('/api/ai-service-configs');

export const createAiServiceConfig = async (values: AiServiceConfigFormValues) =>
  request<ApiResponse<AiServiceConfig>>('/api/ai-service-configs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updateAiServiceConfig = async (
  id: number,
  values: AiServiceConfigFormValues,
) =>
  request<ApiResponse<AiServiceConfig>>(`/api/ai-service-configs/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const deleteAiServiceConfig = async (id: number) =>
  request<ApiResponse<null>>(`/api/ai-service-configs/${id}`, {
    method: 'DELETE',
  });

export const updateAiServiceConfigStatus = async (
  id: number,
  enabled: boolean,
) =>
  request<ApiResponse<AiServiceConfig>>(
    `/api/ai-service-configs/${id}/status`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { enabled },
    },
  );

export const setDefaultAiServiceConfig = async (id: number) =>
  request<ApiResponse<AiServiceConfig>>(`/api/ai-service-configs/${id}/default`, {
    method: 'PUT',
  });

export const testAiServiceConfig = async (id: number) =>
  request<ApiResponse<TestResult>>(`/api/ai-service-configs/${id}/test`, {
    method: 'POST',
  });
