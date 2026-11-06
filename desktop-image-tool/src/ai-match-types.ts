import type { AssetType, SourceAsset } from './workspace-shared';

export type AiMatchMode = 'vector' | 'llm';

export interface AiModelSettings {
  baseUrl: string;
  model: string;
  hasKey: boolean;
  usingImageKey: boolean;
}
export interface AiModelInput {
  baseUrl: string;
  model: string;
  key?: string;
  clearKey?: boolean;
}
export interface AiMatchOption {
  similarity?: number;
  assetId: number;
  variantId: number | null;
  name: string;
  parentName: string | null;
  hasPrompt: boolean;
}
export interface AiSuggestion {
  sourceAssetId: number;
  sourceName: string;
  type: AssetType;
  sourceParentName?: string;
  options: AiMatchOption[];
  reason: string;
  status: 'suggested' | 'ambiguous' | 'unmatched' | 'parentRequired';
}
export interface AiMatchReview {
  reviewId: string;
  projectId: number;
  targetProjectId: number;
  model: string;
  suggestions: AiSuggestion[];
}
export interface AiMatchProgress {
  detail?: string;
  projectId: number;
  phase: 'preparing' | 'matching' | 'vector';
  completed: number;
  total: number;
}
export interface AiApplyInput {
  reviewId: string;
  choices: {
    sourceAssetId: number;
    assetId: number;
    variantId?: number | null;
  }[];
}
export interface AiApplyResult {
  applied: number;
  assets: SourceAsset[];
  failures: { sourceAssetId: number; message: string }[];
}
