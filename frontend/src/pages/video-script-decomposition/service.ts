import { request } from '@umijs/max';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type VideoDecompositionUpload = {
  fileName: string;
  storagePath: string;
  mimeType?: string;
  fileSize: number;
  durationSeconds?: number | null;
};

export type VideoDecompositionEpisode = {
  id: number;
  executionId?: number;
  batchId: number;
  projectId?: number | null;
  episodeNo: number;
  sourceFileName: string;
  storagePath: string;
  mimeType?: string | null;
  fileSize: number;
  durationSeconds?: number | null;
  status: string;
  analysisVersion?: number | null;
  draftStatus?: string | null;
  draftVersion?: number | null;
  confirmedScriptVersionId?: number | null;
  executionPhase?: string | null;
  percentage: number;
  retryable?: boolean | null;
  errorCode?: string | null;
  errorMessage?: string | null;
};

export type VideoDecompositionAttempt = {
  id: number;
  attemptNo: number;
  phase: string;
  status: string;
  providerRequestId?: string | null;
  aiCallLogId?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
};

export type VideoDecompositionEpisodeDetail = {
  episode: VideoDecompositionEpisode;
  screenplayContent?: string | null;
  formatVersion?: string | null;
  draftContent?: string | null;
  currentScriptVersionId?: number | null;
  rawResponse?: string | null;
  normalizedJson?: string | null;
  attempts: VideoDecompositionAttempt[];
};

export type VideoDecompositionBatch = {
  id: number;
  projectId?: number | null;
  name: string;
  modelId?: number | null;
  status: string;
  totalEpisodes: number;
  completedEpisodes: number;
  failedEpisodes: number;
  succeededEpisodes: number;
  processingEpisodes: number;
  pendingEpisodes: number;
  percentage: number;
  createdAt?: string;
  updatedAt?: string;
  episodes: VideoDecompositionEpisode[];
};

export type VideoDecompositionScreenplayEpisode = {
  episode: VideoDecompositionEpisode;
  screenplayContent?: string | null;
  formatVersion?: string | null;
};

export type VideoDecompositionBatchScreenplays = {
  batchId: number;
  batchName: string;
  status: string;
  percentage: number;
  totalEpisodes: number;
  succeededEpisodes: number;
  failedEpisodes: number;
  processingEpisodes: number;
  pendingEpisodes: number;
  episodes: VideoDecompositionScreenplayEpisode[];
};

export type CreateVideoDecompositionBatchValues = {
  name: string;
  modelId?: number;
  videos: VideoDecompositionUpload[];
};

export type VideoUnderstandingModel = {
  id: number;
  name: string;
  modelCode: string;
  serviceType: string;
  status: string;
  isDefault?: boolean;
};

export const queryVideoUnderstandingModels = async () =>
  request<{ success: boolean; data: VideoUnderstandingModel[] }>(
    '/api/platform/ai/models',
  );

export const uploadEpisodeVideo = async (file: File) => {
  const data = new FormData();
  data.append('file', file);
  return request<ApiResponse<VideoDecompositionUpload>>(
    '/api/video-script-decomposition/uploads',
    {
      method: 'POST',
      data,
    },
  );
};

export const createVideoDecompositionBatch = async (
  values: CreateVideoDecompositionBatchValues,
) =>
  request<ApiResponse<VideoDecompositionBatch>>(
    '/api/video-script-decomposition/batches',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );

export const queryVideoDecompositionBatches = async (projectId?: number) =>
  request<ApiResponse<VideoDecompositionBatch[]>>(
    '/api/video-script-decomposition/batches',
    {
      params: projectId ? { projectId } : undefined,
    },
  );

export const queryVideoDecompositionEpisode = async (episodeId: number) =>
  request<ApiResponse<VideoDecompositionEpisodeDetail>>(
    `/api/video-script-decomposition/episodes/${episodeId}`,
  );

export const queryVideoDecompositionBatchScreenplays = async (
  batchId: number,
) =>
  request<ApiResponse<VideoDecompositionBatchScreenplays>>(
    `/api/video-script-decomposition/batches/${batchId}/screenplays`,
  );

export const retryVideoDecompositionEpisode = async (episodeId: number) =>
  request<ApiResponse<VideoDecompositionEpisode>>(
    `/api/video-script-decomposition/episodes/${episodeId}/retry`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { phase: 'VIDEO_ANALYSIS' },
    },
  );

export const updateVideoDecompositionDraft = async (
  episodeId: number,
  draftContent: string,
  expectedDraftVersion?: number | null,
) =>
  request<ApiResponse<VideoDecompositionEpisode>>(
    `/api/video-script-decomposition/episodes/${episodeId}/draft`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { draftContent, expectedDraftVersion },
    },
  );

export const confirmVideoDecompositionDraft = async (
  episodeId: number,
  draftContent: string,
  expectedDraftVersion?: number | null,
  expectedCurrentScriptVersionId?: number | null,
  projectId?: number | null,
) =>
  request<ApiResponse<VideoDecompositionEpisode>>(
    `/api/video-script-decomposition/episodes/${episodeId}/confirm`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: {
        draftContent,
        expectedDraftVersion,
        expectedCurrentScriptVersionId,
        projectId,
      },
    },
  );
