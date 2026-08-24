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

export type ScriptEpisode = {
  episodeNo: number;
  title: string;
  content: string;
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
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_REVIEW';
  mergeTargetId?: number | null;
};

export type SceneAsset = {
  id: number;
  name: string;
  sceneType: string;
  atmosphere: string;
  description: string;
  visualStyle: string;
  prompt: string;
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_REVIEW';
  mergeTargetId?: number | null;
};

export type PropAsset = {
  id: number;
  name: string;
  propType: string;
  appearance: string;
  plotFunction: string;
  prompt: string;
  status: 'DRAFT' | 'CONFIRMED' | 'PENDING_REVIEW';
  mergeTargetId?: number | null;
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
  currentAudioUrl?: string | null;
  currentSubtitleId?: number | null;
  currentSubtitleUrl?: string | null;
  currentShotResultId?: number | null;
  currentShotVideoUrl?: string | null;
};

export type ScriptWorkspace = {
  projectId: number;
  script: ScriptInfo | null;
  versions: ScriptVersion[];
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  storyboards: StoryboardShot[];
  episodes?: ScriptEpisode[];
  analysis?: ScriptAnalysisTask | null;
};

export type ScriptAnalysisStage = {
  id: number;
  stageCode: string;
  stageOrder: number;
  status: string;
  progressPercent: number;
  completedUnits: number;
  totalUnits: number;
  currentAction?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  retryable?: boolean;
  resultJson?: string | null;
  providerRequestId?: string | null;
  aiCallLogId?: number | null;
  durationMs?: number | null;
  resultErrorCode?: string | null;
  resultErrorMessage?: string | null;
  resultRetryable?: boolean | null;
};

export type ScriptAnalysisTask = {
  id: number;
  scriptVersionId: number;
  status: string;
  currentStage?: string | null;
  overallProgress: number;
  currentAction?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  stages: ScriptAnalysisStage[];
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
  status?: 'DRAFT' | 'CONFIRMED' | 'PENDING_REVIEW';
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

export const retryScriptAnalysis = async (
  projectId: number,
  stageCode: string,
) =>
  request<ApiResponse<ScriptAnalysisTask>>(
    `/api/projects/${projectId}/script-analysis/current/retry/${stageCode}`,
    { method: 'POST' },
  );

export const reanalyzeScript = async (projectId: number) =>
  request<ApiResponse<ScriptAnalysisTask>>(
    `/api/projects/${projectId}/script-analysis/current/reanalyze`,
    { method: 'POST' },
  );

export const reanalyzeScriptVersion = async (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<ScriptAnalysisTask>>(
    `/api/projects/${projectId}/script-analysis/versions/${versionId}/reanalyze`,
    { method: 'POST' },
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

export type ShotTaskStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED';

export type AiVoiceResult = {
  id: number;
  taskId: number;
  storyboardId: number;
  audioUrl: string;
  storagePath: string;
  durationSeconds?: number | null;
  fileSize?: number | null;
  format?: string | null;
  materialId?: number | null;
  selected: boolean;
  status: string;
  createdAt?: string;
};

export type AiVoiceTask = {
  id: number;
  projectId: number;
  storyboardId: number;
  serviceConfigId?: number | null;
  providerCode?: string | null;
  model?: string | null;
  voiceType: string;
  speakerName?: string | null;
  voiceId: string;
  textContent: string;
  speed?: number | null;
  pitch?: number | null;
  volume?: number | null;
  status: ShotTaskStatus;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
  results: AiVoiceResult[];
};

export type CreateAiVoiceTaskValues = {
  storyboardId: number;
  serviceConfigId?: number;
  voiceType: string;
  speakerName?: string;
  voiceId: string;
  textContent: string;
  speed?: number;
  pitch?: number;
  volume?: number;
};

export type SubtitleSegment = {
  text: string;
  startTime: number;
  endTime: number;
};

export type StoryboardSubtitle = {
  id: number;
  storyboardId: number;
  voiceResultId?: number | null;
  subtitleType: string;
  textContent: string;
  srtUrl?: string | null;
  styleConfig?: string | null;
  selected: boolean;
  status: string;
  createdAt?: string;
  segments: SubtitleSegment[];
};

export type CreateStoryboardSubtitleValues = {
  storyboardId: number;
  voiceResultId?: number;
  subtitleType: string;
  textContent: string;
  startTime?: number;
  endTime?: number;
  styleConfig?: Record<string, unknown>;
};

export type UpdateStoryboardSubtitleValues = {
  textContent: string;
  startTime?: number;
  endTime?: number;
  styleConfig?: Record<string, unknown>;
};

export type ShotComposeResult = {
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
  selected: boolean;
  status: string;
  createdAt?: string;
};

export type ShotComposeTask = {
  id: number;
  projectId: number;
  storyboardId: number;
  voiceResultId?: number | null;
  subtitleId?: number | null;
  composeConfig?: string | null;
  status: ShotTaskStatus;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
  results: ShotComposeResult[];
};

export type CreateShotComposeTaskValues = {
  storyboardId: number;
  voiceResultId?: number;
  subtitleId?: number;
  includeSubtitle?: boolean;
  audioVolume?: number;
  outputFormat?: string;
};

export type EpisodeComposeTaskStatus =
  | 'PENDING_VALIDATION'
  | 'VALIDATION_FAILED'
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED';

export type EpisodeComposeItem = {
  id: number;
  taskId: number;
  episodeNo: number;
  storyboardId: number;
  storyboardOrder: number;
  shotResultId?: number | null;
  videoUrl?: string | null;
  durationSeconds?: number | null;
  width?: number | null;
  height?: number | null;
  status: string;
  errorMessage?: string | null;
  createdAt?: string;
};

export type EpisodeVideoVersion = {
  id: number;
  episodeNo: number;
  composeTaskId: number;
  versionNo: number;
  versionName: string;
  videoUrl: string;
  storagePath: string;
  coverUrl?: string | null;
  durationSeconds?: number | null;
  width?: number | null;
  height?: number | null;
  fileSize?: number | null;
  format?: string | null;
  materialId?: number | null;
  current: boolean;
  status: string;
  createdAt?: string;
};

export type EpisodeExportRecord = {
  id: number;
  episodeNo: number;
  videoVersionId: number;
  exportType: string;
  exportStatus: string;
  fileName?: string | null;
  fileSize?: number | null;
  downloadUrl?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
};

export type EpisodeComposeTask = {
  id: number;
  projectId: number;
  episodeNo: number;
  taskName: string;
  composeConfig?: string | null;
  storyboardCount: number;
  totalDurationSeconds?: number | null;
  status: EpisodeComposeTaskStatus;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
  items: EpisodeComposeItem[];
  videoVersion?: EpisodeVideoVersion | null;
};

export type CreateEpisodeComposeTaskValues = {
  episodeNo: number;
  taskName?: string;
  versionName?: string;
  outputFormat?: string;
  quality?: string;
  generateCover?: boolean;
};

export type RenameEpisodeVideoVersionValues = {
  versionName: string;
};

export const queryAiVoiceTasks = async (
  projectId: number,
  params?: { status?: string; storyboardId?: number },
) =>
  request<ApiResponse<AiVoiceTask[]>>(
    `/api/projects/${projectId}/ai-voice-tasks`,
    { params },
  );

export const createAiVoiceTask = async (
  projectId: number,
  values: CreateAiVoiceTaskValues,
) =>
  request<ApiResponse<AiVoiceTask>>(`/api/projects/${projectId}/ai-voice-tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });

export const bindAiVoiceResultToStoryboard = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiVoiceResult>>(
    `/api/projects/${projectId}/ai-voice-results/${resultId}/bind-storyboard`,
    { method: 'POST' },
  );

export const saveAiVoiceResultAsMaterial = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<AiVoiceResult>>(
    `/api/projects/${projectId}/ai-voice-results/${resultId}/save-material`,
    { method: 'POST' },
  );

export const deleteAiVoiceResult = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/ai-voice-results/${resultId}`,
    { method: 'DELETE' },
  );

export const createStoryboardSubtitle = async (
  projectId: number,
  values: CreateStoryboardSubtitleValues,
) =>
  request<ApiResponse<StoryboardSubtitle>>(
    `/api/projects/${projectId}/storyboard-subtitles`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const queryStoryboardSubtitles = async (
  projectId: number,
  params?: { storyboardId?: number; status?: string },
) =>
  request<ApiResponse<StoryboardSubtitle[]>>(
    `/api/projects/${projectId}/storyboard-subtitles`,
    { params },
  );

export const updateStoryboardSubtitle = async (
  projectId: number,
  subtitleId: number,
  values: UpdateStoryboardSubtitleValues,
) =>
  request<ApiResponse<StoryboardSubtitle>>(
    `/api/projects/${projectId}/storyboard-subtitles/${subtitleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const deleteStoryboardSubtitle = async (
  projectId: number,
  subtitleId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/storyboard-subtitles/${subtitleId}`,
    { method: 'DELETE' },
  );

export const selectStoryboardSubtitle = async (
  projectId: number,
  subtitleId: number,
) =>
  request<ApiResponse<StoryboardSubtitle>>(
    `/api/projects/${projectId}/storyboard-subtitles/${subtitleId}/selected`,
    { method: 'PUT' },
  );

export const queryShotComposeTasks = async (
  projectId: number,
  params?: { status?: string; storyboardId?: number },
) =>
  request<ApiResponse<ShotComposeTask[]>>(
    `/api/projects/${projectId}/shot-compose-tasks`,
    { params },
  );

export const createShotComposeTask = async (
  projectId: number,
  values: CreateShotComposeTaskValues,
) =>
  request<ApiResponse<ShotComposeTask>>(
    `/api/projects/${projectId}/shot-compose-tasks`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const queryEpisodeComposeTasks = async (
  projectId: number,
  params?: { episodeNo?: number; status?: EpisodeComposeTaskStatus },
) =>
  request<ApiResponse<EpisodeComposeTask[]>>(
    `/api/projects/${projectId}/episode-compose-tasks`,
    { params },
  );

export const queryEpisodeComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<EpisodeComposeTask>>(
    `/api/projects/${projectId}/episode-compose-tasks/${taskId}`,
  );

export const createEpisodeComposeTask = async (
  projectId: number,
  values: CreateEpisodeComposeTaskValues,
) =>
  request<ApiResponse<EpisodeComposeTask>>(
    `/api/projects/${projectId}/episode-compose-tasks`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const cancelEpisodeComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<EpisodeComposeTask>>(
    `/api/projects/${projectId}/episode-compose-tasks/${taskId}/cancel`,
    { method: 'POST' },
  );

export const regenerateEpisodeComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<EpisodeComposeTask>>(
    `/api/projects/${projectId}/episode-compose-tasks/${taskId}/regenerate`,
    { method: 'POST' },
  );

export const deleteEpisodeComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/episode-compose-tasks/${taskId}`,
    { method: 'DELETE' },
  );

export const queryEpisodeVideoVersions = async (
  projectId: number,
  episodeNo: number,
) =>
  request<ApiResponse<EpisodeVideoVersion[]>>(
    `/api/projects/${projectId}/episode-video-versions`,
    { params: { episodeNo } },
  );

export const queryEpisodeVideoVersion = async (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<EpisodeVideoVersion>>(
    `/api/projects/${projectId}/episode-video-versions/${versionId}`,
  );

export const renameEpisodeVideoVersion = async (
  projectId: number,
  versionId: number,
  values: RenameEpisodeVideoVersionValues,
) =>
  request<ApiResponse<EpisodeVideoVersion>>(
    `/api/projects/${projectId}/episode-video-versions/${versionId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const setCurrentEpisodeVideoVersion = async (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<EpisodeVideoVersion>>(
    `/api/projects/${projectId}/episode-video-versions/${versionId}/current`,
    { method: 'POST' },
  );

export const downloadEpisodeVideoVersion = async (
  projectId: number,
  versionId: number,
) =>
  (async () => {
    const token = localStorage.getItem('accessToken');
    const currentTenantId = localStorage.getItem('currentTenantId');
    const response = await fetch(
      `/api/projects/${projectId}/episode-video-versions/${versionId}/download`,
      {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(currentTenantId ? { 'X-Tenant-Id': currentTenantId } : {}),
        },
      },
    );
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`);
    }
    const blob = await response.blob();
    const disposition = response.headers.get('content-disposition') || '';
    const fileName =
      disposition.match(/filename="?([^";]+)"?/i)?.[1] ||
      `episode_${versionId}.mp4`;
    const url = URL.createObjectURL(blob);
    try {
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      link.rel = 'noreferrer';
      link.click();
    } finally {
      URL.revokeObjectURL(url);
    }
  })();

export const saveEpisodeVideoMaterial = async (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<EpisodeVideoVersion>>(
    `/api/projects/${projectId}/episode-video-versions/${versionId}/save-material`,
    { method: 'POST' },
  );

export const deleteEpisodeVideoVersion = async (
  projectId: number,
  versionId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/episode-video-versions/${versionId}`,
    { method: 'DELETE' },
  );

export const queryEpisodeExportRecords = async (
  projectId: number,
  params?: { episodeNo?: number },
) =>
  request<ApiResponse<EpisodeExportRecord[]>>(
    `/api/projects/${projectId}/episode-export-records`,
    { params },
  );

export const cancelAiVoiceTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<AiVoiceTask>>(
    `/api/projects/${projectId}/ai-voice-tasks/${taskId}/cancel`,
    { method: 'POST' },
  );

export const regenerateAiVoiceTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<AiVoiceTask>>(
    `/api/projects/${projectId}/ai-voice-tasks/${taskId}/regenerate`,
    { method: 'POST' },
  );

export const deleteAiVoiceTask = async (projectId: number, taskId: number) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/ai-voice-tasks/${taskId}`,
    { method: 'DELETE' },
  );

export const queryAiVoiceTaskResults = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<AiVoiceResult[]>>(
    `/api/projects/${projectId}/ai-voice-tasks/${taskId}/results`,
  );

export const cancelShotComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<ShotComposeTask>>(
    `/api/projects/${projectId}/shot-compose-tasks/${taskId}/cancel`,
    { method: 'POST' },
  );

export const regenerateShotComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<ShotComposeTask>>(
    `/api/projects/${projectId}/shot-compose-tasks/${taskId}/regenerate`,
    { method: 'POST' },
  );

export const deleteShotComposeTask = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/shot-compose-tasks/${taskId}`,
    { method: 'DELETE' },
  );

export const queryShotComposeTaskResults = async (
  projectId: number,
  taskId: number,
) =>
  request<ApiResponse<ShotComposeResult[]>>(
    `/api/projects/${projectId}/shot-compose-tasks/${taskId}/results`,
  );

export const saveShotComposeResultAsMaterial = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<ShotComposeResult>>(
    `/api/projects/${projectId}/shot-compose-results/${resultId}/save-material`,
    { method: 'POST' },
  );

export const bindShotComposeResultToStoryboard = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<ShotComposeResult>>(
    `/api/projects/${projectId}/shot-compose-results/${resultId}/bind-storyboard`,
    { method: 'POST' },
  );

export const deleteShotComposeResult = async (
  projectId: number,
  resultId: number,
) =>
  request<ApiResponse<void>>(
    `/api/projects/${projectId}/shot-compose-results/${resultId}`,
    { method: 'DELETE' },
  );
