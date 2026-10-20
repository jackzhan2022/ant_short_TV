import { request } from '@umijs/max';

export type AssetNormalizationStatus = {
  status: 'IDLE' | 'EXTRACTING' | 'NORMALIZING' | 'RUNNING' | 'FAILED' | 'SUCCEEDED';
  blocked: boolean;
  executionId?: number;
  errorMessage?: string;
  result?: { mergedAssets: number; splitGroups: number };
};

export const queryAssetNormalization = (projectId: number) =>
  request<{ data: AssetNormalizationStatus }>(`/api/projects/${projectId}/asset-normalization`);

export const submitAssetNormalization = (projectId: number) =>
  request<{ data: API.AiExecutionResponse }>(`/api/projects/${projectId}/asset-normalization`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
