import { pointEstimatedRequest as request } from '@/services/point-estimate';

export type SimilarityCharacter = { id: number; name: string; prompt: string };
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
