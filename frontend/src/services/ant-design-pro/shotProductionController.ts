// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-voice-results/${param1} */
export async function deleteVoiceResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteVoiceResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-voice-results/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-voice-results/${param1}/bind-storyboard */
export async function bindVoiceResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bindVoiceResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceResultResponse>(
    `/api/projects/${param0}/ai-voice-results/${param1}/bind-storyboard`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-voice-results/${param1}/download */
export async function downloadVoiceResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadVoiceResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceResultResponse>(
    `/api/projects/${param0}/ai-voice-results/${param1}/download`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-voice-results/${param1}/save-material */
export async function saveVoiceMaterial(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveVoiceMaterialParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceResultResponse>(
    `/api/projects/${param0}/ai-voice-results/${param1}/save-material`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-voice-tasks */
export async function voiceTasks(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.voiceTasksParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListAiVoiceTaskResponse>(
    `/api/projects/${param0}/ai-voice-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-voice-tasks */
export async function createVoiceTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createVoiceTaskParams,
  body: API.CreateAiVoiceTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceTaskResponse>(
    `/api/projects/${param0}/ai-voice-tasks`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-voice-tasks/${param1} */
export async function voiceTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.voiceTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceTaskResponse>(
    `/api/projects/${param0}/ai-voice-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-voice-tasks/${param1} */
export async function deleteVoiceTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteVoiceTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-voice-tasks/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-voice-tasks/${param1}/cancel */
export async function cancelVoiceTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelVoiceTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceTaskResponse>(
    `/api/projects/${param0}/ai-voice-tasks/${param1}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-voice-tasks/${param1}/regenerate */
export async function regenerateVoiceTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateVoiceTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVoiceTaskResponse>(
    `/api/projects/${param0}/ai-voice-tasks/${param1}/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-voice-tasks/${param1}/results */
export async function voiceTaskResults(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.voiceTaskResultsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseListAiVoiceResultResponse>(
    `/api/projects/${param0}/ai-voice-tasks/${param1}/results`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-compose-tasks */
export async function episodeComposeTasks(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeComposeTasksParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListEpisodeComposeTaskResponse>(
    `/api/projects/${param0}/episode-compose-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episode-compose-tasks */
export async function createEpisodeComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createEpisodeComposeTaskParams,
  body: API.CreateEpisodeComposeTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseEpisodeComposeTaskResponse>(
    `/api/projects/${param0}/episode-compose-tasks`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-compose-tasks/${param1} */
export async function episodeComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeComposeTaskResponse>(
    `/api/projects/${param0}/episode-compose-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/episode-compose-tasks/${param1} */
export async function deleteEpisodeComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteEpisodeComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/episode-compose-tasks/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episode-compose-tasks/${param1}/cancel */
export async function cancelEpisodeComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelEpisodeComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeComposeTaskResponse>(
    `/api/projects/${param0}/episode-compose-tasks/${param1}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episode-compose-tasks/${param1}/regenerate */
export async function regenerateEpisodeComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateEpisodeComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeComposeTaskResponse>(
    `/api/projects/${param0}/episode-compose-tasks/${param1}/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-export-records */
export async function episodeExportRecords(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeExportRecordsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListEpisodeExportRecordResponse>(
    `/api/projects/${param0}/episode-export-records`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions */
export async function episodeVideoVersions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeVideoVersionsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListEpisodeVideoVersionResponse>(
    `/api/projects/${param0}/episode-video-versions`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions/${param1} */
export async function episodeVideoVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeVideoVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeVideoVersionResponse>(
    `/api/projects/${param0}/episode-video-versions/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/episode-video-versions/${param1} */
export async function renameEpisodeVideoVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.renameEpisodeVideoVersionParams,
  body: API.RenameEpisodeVideoVersionRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeVideoVersionResponse>(
    `/api/projects/${param0}/episode-video-versions/${param1}`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/episode-video-versions/${param1} */
export async function deleteEpisodeVideoVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteEpisodeVideoVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/episode-video-versions/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions/${param1}/cover */
export async function episodeVideoCover(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episodeVideoCoverParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<string>(
    `/api/projects/${param0}/episode-video-versions/${param1}/cover`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episode-video-versions/${param1}/current */
export async function setCurrentEpisodeVideoVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.setCurrentEpisodeVideoVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeVideoVersionResponse>(
    `/api/projects/${param0}/episode-video-versions/${param1}/current`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions/${param1}/download */
export async function downloadEpisodeVideoVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadEpisodeVideoVersionParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<string>(
    `/api/projects/${param0}/episode-video-versions/${param1}/download`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/episode-video-versions/${param1}/save-material */
export async function saveEpisodeVideoMaterial(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveEpisodeVideoMaterialParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<API.ApiResponseEpisodeVideoVersionResponse>(
    `/api/projects/${param0}/episode-video-versions/${param1}/save-material`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/shot-compose-results/${param1} */
export async function deleteComposeResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteComposeResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/shot-compose-results/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/shot-compose-results/${param1}/bind-storyboard */
export async function bindComposeResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bindComposeResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeResultResponse>(
    `/api/projects/${param0}/shot-compose-results/${param1}/bind-storyboard`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-results/${param1}/download */
export async function downloadComposeResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadComposeResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeResultResponse>(
    `/api/projects/${param0}/shot-compose-results/${param1}/download`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/shot-compose-results/${param1}/save-material */
export async function saveComposeMaterial(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveComposeMaterialParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeResultResponse>(
    `/api/projects/${param0}/shot-compose-results/${param1}/save-material`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-tasks */
export async function composeTasks(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.composeTasksParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListShotComposeTaskResponse>(
    `/api/projects/${param0}/shot-compose-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/shot-compose-tasks */
export async function createComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createComposeTaskParams,
  body: API.CreateShotComposeTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseShotComposeTaskResponse>(
    `/api/projects/${param0}/shot-compose-tasks`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-tasks/${param1} */
export async function composeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.composeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeTaskResponse>(
    `/api/projects/${param0}/shot-compose-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/shot-compose-tasks/${param1} */
export async function deleteComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/shot-compose-tasks/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/shot-compose-tasks/${param1}/cancel */
export async function cancelComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeTaskResponse>(
    `/api/projects/${param0}/shot-compose-tasks/${param1}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/shot-compose-tasks/${param1}/regenerate */
export async function regenerateComposeTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerateComposeTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseShotComposeTaskResponse>(
    `/api/projects/${param0}/shot-compose-tasks/${param1}/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-tasks/${param1}/results */
export async function composeTaskResults(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.composeTaskResultsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseListShotComposeResultResponse>(
    `/api/projects/${param0}/shot-compose-tasks/${param1}/results`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-subtitles */
export async function subtitles(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.subtitlesParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListStoryboardSubtitleResponse>(
    `/api/projects/${param0}/storyboard-subtitles`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/storyboard-subtitles */
export async function createSubtitle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createSubtitleParams,
  body: API.CreateStoryboardSubtitleRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseStoryboardSubtitleResponse>(
    `/api/projects/${param0}/storyboard-subtitles`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboard-subtitles/${param1} */
export async function subtitle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.subtitleParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, subtitleId: param1, ...queryParams } = params;
  return request<API.ApiResponseStoryboardSubtitleResponse>(
    `/api/projects/${param0}/storyboard-subtitles/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboard-subtitles/${param1} */
export async function updateSubtitle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateSubtitleParams,
  body: API.UpdateStoryboardSubtitleRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, subtitleId: param1, ...queryParams } = params;
  return request<API.ApiResponseStoryboardSubtitleResponse>(
    `/api/projects/${param0}/storyboard-subtitles/${param1}`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      params: { ...queryParams },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/storyboard-subtitles/${param1} */
export async function deleteSubtitle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteSubtitleParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, subtitleId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/storyboard-subtitles/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/storyboard-subtitles/${param1}/selected */
export async function selectSubtitle(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.selectSubtitleParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, subtitleId: param1, ...queryParams } = params;
  return request<API.ApiResponseStoryboardSubtitleResponse>(
    `/api/projects/${param0}/storyboard-subtitles/${param1}/selected`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
