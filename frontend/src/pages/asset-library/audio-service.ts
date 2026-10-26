import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export type AudioReference = {
  sourceProjectId: number;
  sourceProjectName: string;
  resourceType: 'PROJECT_AUDIO_ASSET' | 'AI_VOICE_RESULT' | 'SYSTEM_AUDIO_ASSET';
  resourceId: number;
  name: string;
  previewUrl: string;
  durationSeconds?: number;
};

export type StoryboardVoiceScope = {
  id: number;
  variantId?: number | null;
  variantName?: string | null;
};
export type EffectiveStoryboardVoice = {
  audio: AudioReference | null;
  overridden: boolean;
};

export const queryStoryboardCharacterVoice = (
  projectId: number,
  characterId: number,
  scope: StoryboardVoiceScope,
  signal?: AbortSignal,
) =>
  request<ApiResponse<EffectiveStoryboardVoice>>(
    `/api/projects/${projectId}/storyboards/${scope.id}/characters/${characterId}/voice-reference`,
    { params: { variantId: scope.variantId ?? undefined }, signal },
  );

export const saveStoryboardCharacterVoice = (
  projectId: number,
  characterId: number,
  scope: StoryboardVoiceScope,
  audio: Pick<
    AudioReference,
    'sourceProjectId' | 'resourceType' | 'resourceId'
  >,
) =>
  request<ApiResponse<EffectiveStoryboardVoice>>(
    `/api/projects/${projectId}/storyboards/${scope.id}/characters/${characterId}/voice-reference`,
    {
      method: 'PUT',
      params: { variantId: scope.variantId ?? undefined },
      data: audio,
    },
  );

export const resetStoryboardCharacterVoice = (
  projectId: number,
  characterId: number,
  scope: StoryboardVoiceScope,
) =>
  request<ApiResponse<EffectiveStoryboardVoice>>(
    `/api/projects/${projectId}/storyboards/${scope.id}/characters/${characterId}/voice-reference`,
    { method: 'DELETE', params: { variantId: scope.variantId ?? undefined } },
  );

export const queryCharacterVoice = (
  projectId: number,
  characterId: number,
  signal?: AbortSignal,
) =>
  request<ApiResponse<AudioReference | null>>(
    `/api/projects/${projectId}/characters/${characterId}/voice-reference`,
    { signal },
  );

export const saveCharacterVoice = (
  projectId: number,
  characterId: number,
  audio: Pick<
    AudioReference,
    'sourceProjectId' | 'resourceType' | 'resourceId'
  >,
) =>
  request<ApiResponse<AudioReference>>(
    `/api/projects/${projectId}/characters/${characterId}/voice-reference`,
    { method: 'PUT', data: audio },
  );

export const registerProjectAudio = (
  projectId: number,
  sessionToken: string,
  name: string,
) =>
  request<ApiResponse<AudioReference>>(
    `/api/projects/${projectId}/audio-assets`,
    { method: 'POST', data: { sessionToken, name } },
  );
