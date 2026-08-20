import { request } from '@umijs/max';
import type { ApiResponse } from '@/pages/ai-service-management/platform-service';

export type ProjectModelOption = {
  id: number;
  name: string;
  description?: string;
};

export type ProjectAiModels = {
  textModels: ProjectModelOption[];
  imageModels: ProjectModelOption[];
  videoModels: ProjectModelOption[];
  audioModels: ProjectModelOption[];
};

export type ProjectAiConfig = {
  projectId: number;
  textModelId?: number | null;
  imageModelId?: number | null;
  videoModelId?: number | null;
  audioModelId?: number | null;
};

export type ProjectAiConfigFormValues = Omit<ProjectAiConfig, 'projectId'>;

export const queryProjectAiModels = async (projectId: number) =>
  request<ApiResponse<ProjectAiModels>>(`/api/projects/${projectId}/ai/models`);

export const queryProjectAiConfig = async (projectId: number) =>
  request<ApiResponse<ProjectAiConfig>>(`/api/projects/${projectId}/ai/config`);

export const saveProjectAiConfig = async (
  projectId: number,
  values: ProjectAiConfigFormValues,
) =>
  request<ApiResponse<ProjectAiConfig>>(`/api/projects/${projectId}/ai/config`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
