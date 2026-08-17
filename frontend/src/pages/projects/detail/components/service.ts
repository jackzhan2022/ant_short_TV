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
  firstFrameUrl?: string | null;
  currentVideoResultId?: number | null;
  currentVideoUrl?: string | null;
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

export type AiVideoTaskStatus =
  | 'PENDING'
  | 'SUBMITTING'
  | 'GENERATING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED';

export type AiVideoResult = {
  id: number;
  taskId: number;
  storyboardId: number;
  videoUrl: string;
  storagePath: string;
  coverUrl?: string | null;
  durationSeconds?: number | null;
  width?: number | null;
  height?: number | null;
  fileSize?: number | null;
  format?: string | null;
  materialId?: number | null;
  isSelected: boolean;
  status: string;
  createdAt?: string;
};

export type AiVideoTask = {
  id: number;
  projectId: number;
  storyboardId: number;
  serviceConfigId: number;
  providerCode: string;
  model: string;
  prompt: string;
  negativePrompt?: string | null;
  firstFrameUrl: string;
  durationSeconds: number;
  aspectRatio: string;
  resolution?: string | null;
  motionStrength?: string | null;
  cameraMovement?: string | null;
  externalTaskId?: string | null;
  externalStatus?: string | null;
  status: AiVideoTaskStatus;
  errorMessage?: string | null;
  submittedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
  results: AiVideoResult[];
};

export type CreateAiVideoTaskValues = {
  storyboardId: number;
  serviceConfigId?: number;
  prompt: string;
  negativePrompt?: string;
  firstFrameUrl?: string;
  durationSeconds?: number;
  aspectRatio: string;
  resolution?: string;
  cameraMovement?: string;
  motionStrength?: string;
  randomSeed?: number;
};

export const queryAiVideoTasks = async (
  projectId: number,
  params?: { status?: AiVideoTaskStatus; storyboardId?: number },
) =>
  request<ApiResponse<AiVideoTask[]>>(
    `/api/projects/${projectId}/ai-video-tasks`,
    { params },
  );

export const createAiVideoTask = async (
  projectId: number,
  values: CreateAiVideoTaskValues,
) =>
  request<ApiResponse<AiVideoTask>>(
    `/api/projects/${projectId}/ai-video-tasks`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const pollAiVideoTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<AiVideoTask>>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}/poll`,
    { method: 'POST' },
  );

export const cancelAiVideoTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<AiVideoTask>>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}/cancel`,
    { method: 'POST' },
  );

export const regenerateAiVideoTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<AiVideoTask>>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}/regenerate`,
    { method: 'POST' },
  );

export const deleteAiVideoTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<null>>(
    `/api/projects/${projectId}/ai-video-tasks/${taskId}`,
    { method: 'DELETE' },
  );

export const downloadAiVideoResult = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiVideoResult>>(
    `/api/projects/${projectId}/ai-video-results/${resultId}/download`,
  );

export const saveAiVideoResultAsMaterial = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiVideoResult>>(
    `/api/projects/${projectId}/ai-video-results/${resultId}/save-material`,
    { method: 'POST' },
  );

export const bindAiVideoResultToStoryboard = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiVideoResult>>(
    `/api/projects/${projectId}/ai-video-results/${resultId}/bind-storyboard`,
    { method: 'POST' },
  );

export const deleteAiVideoResult = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<null>>(
    `/api/projects/${projectId}/ai-video-results/${resultId}`,
    { method: 'DELETE' },
  );
