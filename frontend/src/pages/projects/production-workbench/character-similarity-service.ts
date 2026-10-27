import { pointEstimatedRequest as request } from '@/services/point-estimate';

export type SimilarityVariant = {
  id: number;
  name: string;
  appearance: string;
  prompt: string;
  isPrimary?: boolean;
};
export type SimilarityCharacter = {
  id: number;
  name: string;
  prompt: string;
  variants?: SimilarityVariant[];
};
export type VariantResolutionDecision = 'MERGED' | 'DISTINCT';
export type ResolutionVariant = SimilarityVariant & {
  currentImageUrl?: string;
  generationStatus: string;
  episodeNumbers: number[];
};
export type CharacterVariantResolution = {
  fingerprint: string;
  characterId: number;
  characterName: string;
  left: ResolutionVariant;
  right: ResolutionVariant;
  mergedEpisodeNumbers: number[];
  affectedStoryboardCount: number;
  voiceConflictCount: number;
  recommendedKeptVariantId: number;
  canMerge: boolean;
  disabledReason?: string;
};
export type VariantResolutionResult = {
  decision: VariantResolutionDecision;
  keptVariantId?: number;
  characterId: number;
  leftVariantId: number;
  rightVariantId: number;
};
export type CharacterVariantSimilarityPair = {
  resolution?: VariantResolutionDecision;
  keptVariantId?: number;
  canResolve?: boolean;
  canInspect?: boolean;
  characterId: number;
  leftVariantId: number;
  rightVariantId: number;
  score: number;
  sharedFeatures: string[];
  differences: string[];
  suggestions: string[];
};
export type CharacterSimilarityPair = {
  leftId: number;
  rightId: number;
  score: number;
  sharedFeatures: string[];
  differences: string[];
  suggestions: string[];
};
export type CharacterPromptRevision = {
  characterId: number;
  originalFace: string;
  face: string;
  prompt: string;
  changes: string[];
};
export type CharacterSimilarityStatus = {
  id?: number;
  executionId?: number;
  status: string;
  createdAt?: string;
  stale?: boolean;
  errorMessage?: string;
  characters?: SimilarityCharacter[];
  analysis?: {
    summary: string;
    pairs: CharacterSimilarityPair[];
    insufficient: { characterId: number; reason: string }[];
    hasMore: boolean;
    revisions?: CharacterPromptRevision[];
    variantPairs?: CharacterVariantSimilarityPair[];
    variantsHasMore?: boolean;
  };
};

export const queryCharacterSimilarity = (projectId: number) =>
  request<{ data: CharacterSimilarityStatus }>(
    `/api/projects/${projectId}/character-prompt-similarity`,
  );

export const submitCharacterSimilarity = (projectId: number) =>
  request<{ data: API.AiExecutionResponse }>(
    `/api/projects/${projectId}/character-prompt-similarity`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    },
  );

export const applyCharacterSimilarityPrompt = (
  projectId: number,
  checkId: number,
  characterId: number,
  prompt: string,
) =>
  request<{ data: { characterId: number; prompt: string } }>(
    `/api/projects/${projectId}/character-prompt-similarity/${checkId}/characters/${characterId}/prompt`,
    { method: 'PUT', data: { prompt } },
  );

export const queryCharacterVariantResolution = (
  projectId: number,
  checkId: number,
  characterId: number,
  params: { leftVariantId: number; rightVariantId: number },
) =>
  request<{ data: CharacterVariantResolution }>(
    `/api/projects/${projectId}/character-prompt-similarity/${checkId}/characters/${characterId}/variant-resolution`,
    { params },
  );

export const resolveCharacterVariants = (
  projectId: number,
  checkId: number,
  characterId: number,
  data: {
    leftVariantId: number;
    rightVariantId: number;
    decision: VariantResolutionDecision;
    keptVariantId?: number;
    fingerprint: string;
  },
) =>
  request<{ data: VariantResolutionResult }>(
    `/api/projects/${projectId}/character-prompt-similarity/${checkId}/characters/${characterId}/variant-resolution`,
    { method: 'POST', data },
  );
