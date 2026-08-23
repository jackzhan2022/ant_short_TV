import { request } from '@umijs/max';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type PlatformProviderStatus = 'ENABLED' | 'DISABLED';
export type PlatformTestStatus = 'UNTESTED' | 'SUCCESS' | 'FAILED';
export type PlatformModelServiceType =
  | 'TEXT'
  | 'IMAGE'
  | 'VIDEO'
  | 'VIDEO_UNDERSTANDING'
  | 'AUDIO';

export type PlatformProvider = {
  id: number;
  name: string;
  code: string;
  supportedTypes?: string;
  defaultBaseUrl?: string;
  baseUrl?: string;
  apiKey?: string;
  description?: string;
  status: PlatformProviderStatus;
  lastTestStatus?: PlatformTestStatus;
  lastTestMessage?: string;
  lastTestAt?: string;
  updatedAt?: string;
};

export type PlatformProviderFormValues = {
  name: string;
  code: string;
  supportedTypes?: string[];
  defaultBaseUrl?: string;
  baseUrl?: string;
  apiKey?: string;
  description?: string;
  enabled: boolean;
};

export type PlatformProviderPayload = Omit<
  PlatformProviderFormValues,
  'supportedTypes'
> & {
  supportedTypes?: string;
};

export type PlatformModel = {
  id: number;
  providerId: number;
  providerName?: string;
  code: string;
  name: string;
  modelCode: string;
  serviceType: PlatformModelServiceType;
  description?: string;
  status: PlatformProviderStatus;
  isDefault?: boolean;
  sort?: number;
  capabilities?: string[];
  updatedAt?: string;
};

export type PlatformModelFormValues = {
  providerId: number;
  code: string;
  name: string;
  modelCode: string;
  serviceType: PlatformModelServiceType;
  description?: string;
  enabled: boolean;
  isDefault?: boolean;
  sort?: number;
  configJson?: string;
};

export type AiServiceTestResult = {
  status: 'SUCCESS' | 'FAILED';
  message: string;
};

export type BuiltInAgentVariable = {
  name: string;
  label: string;
  type: string;
  required: boolean;
  description?: string;
};

export type BuiltInSkillSummary = {
  code: string;
  name: string;
  category: string;
};

export type BuiltInAgent = {
  code: string;
  name: string;
  description: string;
  businessScene: string;
  businessSceneName: string;
  capability: string;
  modelRouting: string;
  variables: BuiltInAgentVariable[];
  outputSchema: string;
  skills: BuiltInSkillSummary[];
};

export type BuiltInAgentSummary = {
  code: string;
  name: string;
  businessScene: string;
};

export type BuiltInSkill = {
  code: string;
  name: string;
  description: string;
  category: string;
  content: string;
  agents: BuiltInAgentSummary[];
};

export type BuiltInAgentPreview = {
  agentCode: string;
  prompt: string;
  outputSchema: string;
};

export const serviceTypeText: Record<PlatformModelServiceType, string> = {
  TEXT: '文本',
  IMAGE: '图片',
  VIDEO: '视频',
  VIDEO_UNDERSTANDING: '视频理解',
  AUDIO: '音频',
};

export const queryPlatformProviders = async () =>
  request<ApiResponse<PlatformProvider[]>>('/api/platform/ai/providers');

export const createPlatformProvider = async (
  values: PlatformProviderPayload,
) =>
  request<ApiResponse<PlatformProvider>>('/api/platform/ai/providers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updatePlatformProvider = async (
  id: number,
  values: PlatformProviderPayload,
) =>
  request<ApiResponse<PlatformProvider>>(`/api/platform/ai/providers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updatePlatformProviderStatus = async (
  id: number,
  enabled: boolean,
) =>
  request<ApiResponse<PlatformProvider>>(
    `/api/platform/ai/providers/${id}/${enabled ? 'enable' : 'disable'}`,
    { method: 'POST' },
  );

export const testPlatformProvider = async (id: number) =>
  request<ApiResponse<AiServiceTestResult>>(
    `/api/platform/ai/providers/${id}/test`,
    { method: 'POST' },
  );

export const queryPlatformModels = async () =>
  request<ApiResponse<PlatformModel[]>>('/api/platform/ai/models');

export const createPlatformModel = async (values: PlatformModelFormValues) =>
  request<ApiResponse<PlatformModel>>('/api/platform/ai/models', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updatePlatformModel = async (
  id: number,
  values: PlatformModelFormValues,
) =>
  request<ApiResponse<PlatformModel>>(`/api/platform/ai/models/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updatePlatformModelStatus = async (id: number, enabled: boolean) =>
  request<ApiResponse<PlatformModel>>(
    `/api/platform/ai/models/${id}/${enabled ? 'enable' : 'disable'}`,
    { method: 'POST' },
  );

export const setDefaultPlatformModel = async (id: number) =>
  request<ApiResponse<PlatformModel>>(`/api/platform/ai/models/${id}/default`, {
    method: 'POST',
  });

export const queryBuiltInAgents = async () =>
  request<ApiResponse<BuiltInAgent[]>>('/api/platform/ai/agents');

export const queryBuiltInSkills = async () =>
  request<ApiResponse<BuiltInSkill[]>>('/api/platform/ai/skills');

export const previewBuiltInAgent = async (
  code: string,
  variables: Record<string, unknown>,
) =>
  request<ApiResponse<BuiltInAgentPreview>>(
    `/api/platform/ai/agents/${code}/preview`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { variables },
    },
  );
