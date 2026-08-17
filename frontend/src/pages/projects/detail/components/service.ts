import { request } from '@umijs/max';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type ScriptInfo = {
  id: number;
  projectId: number;
  title: string;
  sourceType: string;
  content: string;
  status: string;
  currentVersionId?: number | null;
  updatedAt?: string;
};

export type ScriptVersion = {
  id: number;
  scriptId?: number;
  versionNo: number;
  sourceType: string;
  inputSummary?: string | null;
  content?: string;
  status?: string;
  createdAt?: string;
};

export type CharacterAsset = {
  id: number;
  name: string;
  roleType: string;
  gender: string;
  ageRange: string;
  identity: string;
  personality: string[];
  appearance: string;
  prompt: string;
};

export type SceneAsset = {
  id: number;
  name: string;
  sceneType: string;
  atmosphere: string;
  description: string;
  visualStyle: string;
  prompt: string;
};

export type PropAsset = {
  id: number;
  name: string;
  propType: string;
  appearance: string;
  plotFunction: string;
  prompt: string;
};

export type StoryboardShot = {
  id: number;
  shotNo: number;
  episodeNo: number;
  shotType: string;
  visualDescription: string;
  characters: string;
  scene: string;
  dialogue: string;
  durationSeconds: number;
  imagePrompt: string;
  videoPrompt: string;
};

export type ScriptWorkspace = {
  projectId: number;
  script: ScriptInfo | null;
  versions: ScriptVersion[];
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  storyboards: StoryboardShot[];
};

export type GenerateScriptValues = {
  title?: string;
  storyIdea: string;
  genre: string;
  episodeCount?: number;
  duration?: number;
  mainCharacter?: string;
  styleRequirement?: string;
  referenceContent?: string;
};

export const queryScriptWorkspace = async (projectId: number) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/script-workspace`,
  );

export const generateScript = async (
  projectId: number,
  values: GenerateScriptValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/scripts/ai-generate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );
