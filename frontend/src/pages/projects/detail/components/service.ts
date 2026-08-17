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

export type ScriptElementType = 'CHARACTER' | 'SCENE' | 'PROP' | 'ALL';

export type RewriteScriptValues = {
  rewriteType: string;
  requirement?: string;
  outputLength?: string;
};

export type SaveScriptValues = {
  title?: string;
  content: string;
  status: 'DRAFT' | 'CONFIRMED';
};

export type UpdateScriptElementValues = {
  name: string;
  roleType?: string;
  gender?: string;
  ageRange?: string;
  identity?: string;
  personality?: string[];
  appearance?: string;
  sceneType?: string;
  atmosphere?: string;
  description?: string;
  visualStyle?: string;
  propType?: string;
  plotFunction?: string;
  relatedCharacter?: string;
  prompt?: string;
  status?: 'DRAFT' | 'CONFIRMED';
};

export type SaveStoryboardValues = {
  episodeNo?: number;
  shotNo?: number;
  sceneNo?: string;
  shotType?: string;
  visualDescription: string;
  characters?: string;
  actions?: string;
  dialogue?: string;
  scene?: string;
  props?: string;
  mood?: string;
  durationSeconds?: number;
  imagePrompt?: string;
  videoPrompt?: string;
  status?: 'DRAFT' | 'CONFIRMED';
};

export type PromptTargetType =
  | 'ALL'
  | 'CHARACTER'
  | 'SCENE'
  | 'PROP'
  | 'STORYBOARD';

export type AiImageResult = {
  id: number;
  taskId: number;
  targetType: string;
  targetId: number;
  imageUrl: string;
  thumbnailUrl?: string | null;
  width?: number | null;
  height?: number | null;
  fileSize?: number | null;
  materialId?: number | null;
  selected: boolean;
  status: string;
  createdAt?: string;
};

export type AiImageTask = {
  id: number;
  projectId: number;
  taskType: string;
  targetType: string;
  targetId: number;
  serviceConfigId: number;
  providerCode: string;
  model: string;
  prompt: string;
  negativePrompt?: string | null;
  referenceImages: string[];
  aspectRatio: string;
  imageCount: number;
  style?: string | null;
  quality?: string | null;
  seed?: string | null;
  status: string;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdBy: number;
  createdAt?: string;
  results: AiImageResult[];
};

export type CreateAiImageTaskValues = {
  taskType: string;
  targetType: string;
  targetId: number;
  serviceConfigId?: number;
  prompt: string;
  negativePrompt?: string;
  referenceImages?: string[];
  aspectRatio: string;
  imageCount: number;
  style?: string;
  quality?: string;
  seed?: string;
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

export const rewriteScript = async (
  projectId: number,
  values: RewriteScriptValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/scripts/ai-rewrite`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const saveCurrentScript = async (
  projectId: number,
  values: SaveScriptValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/scripts/current`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const applyScriptVersion = async (projectId: number, versionId: number) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/scripts/versions/${versionId}/apply`,
    { method: 'PUT' },
  );

export const extractScriptElements = async (
  projectId: number,
  values: { elementType: ScriptElementType },
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/scripts/ai-extract-elements`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const updateScriptElement = async (
  projectId: number,
  elementType: Exclude<ScriptElementType, 'ALL'>,
  elementId: number,
  values: UpdateScriptElementValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/script-elements/${elementType}/${elementId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const confirmScriptElement = async (
  projectId: number,
  elementType: Exclude<ScriptElementType, 'ALL'>,
  elementId: number,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/script-elements/${elementType}/${elementId}/confirm`,
    { method: 'PUT' },
  );

export const deleteScriptElement = async (
  projectId: number,
  elementType: Exclude<ScriptElementType, 'ALL'>,
  elementId: number,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/script-elements/${elementType}/${elementId}`,
    { method: 'DELETE' },
  );

export const breakdownStoryboards = async (
  projectId: number,
  values: { scope: string; episodeNo?: number; selectedText?: string },
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/storyboards/ai-breakdown`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const createStoryboard = async (
  projectId: number,
  values: SaveStoryboardValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(`/api/projects/${projectId}/storyboards`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const updateStoryboard = async (
  projectId: number,
  storyboardId: number,
  values: SaveStoryboardValues,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/storyboards/${storyboardId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const moveStoryboard = async (
  projectId: number,
  storyboardId: number,
  values: { shotNo: number },
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/storyboards/${storyboardId}/move`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const confirmStoryboards = async (projectId: number) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/storyboards/confirm`,
    { method: 'PUT' },
  );

export const deleteStoryboard = async (
  projectId: number,
  storyboardId: number,
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/storyboards/${storyboardId}`,
    { method: 'DELETE' },
  );

export const generateWorkflowPrompts = async (
  projectId: number,
  values: { targetType: PromptTargetType; targetId?: number },
) =>
  request<ApiResponse<ScriptWorkspace>>(
    `/api/projects/${projectId}/prompts/ai-generate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const queryAiImageTasks = async (
  projectId: number,
  params?: { taskType?: string; status?: string },
) =>
  request<ApiResponse<AiImageTask[]>>(`/api/projects/${projectId}/ai-image-tasks`, {
    params,
  });

export const queryAiImageTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<AiImageTask>>(
    `/api/projects/${projectId}/ai-image-tasks/${taskId}`,
  );

export const createAiImageTask = async (
  projectId: number,
  values: CreateAiImageTaskValues,
) =>
  request<ApiResponse<AiImageTask>>(`/api/projects/${projectId}/ai-image-tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const regenerateAiImageTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<AiImageTask>>(
    `/api/projects/${projectId}/ai-image-tasks/${taskId}/regenerate`,
    { method: 'POST' },
  );

export const cancelAiImageTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<AiImageTask>>(
    `/api/projects/${projectId}/ai-image-tasks/${taskId}/cancel`,
    { method: 'PUT' },
  );

export const deleteAiImageTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/ai-image-tasks/${taskId}`,
    { method: 'DELETE' },
  );

export const getAiImageResultDownloadUrl = (
  projectId: number,
  resultId: number,
) => `/api/projects/${projectId}/ai-image-results/${resultId}/download`;

export const deleteAiImageResult = async (
  projectId: number,
  resultId: number,
  force = false,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/ai-image-results/${resultId}`,
    {
      method: 'DELETE',
      params: { force },
    },
  );

export const saveAiImageResultAsMaterial = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiImageResult>>(
    `/api/projects/${projectId}/ai-image-results/${resultId}/save-material`,
    { method: 'POST' },
  );

export const selectAiImageResult = async (projectId: number, resultId: number) =>
  request<ApiResponse<AiImageResult>>(
    `/api/projects/${projectId}/ai-image-results/${resultId}/selected`,
    { method: 'PUT' },
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
