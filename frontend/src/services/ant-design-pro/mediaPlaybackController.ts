// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-results/${param1}/download-file */
export async function videoDownload(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.videoDownloadParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/ai-video-results/${param1}/download-file`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-results/${param1}/playback */
export async function aiVideo(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.aiVideoParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/ai-video-results/${param1}/playback`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions/${param1}/playback */
export async function episode1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episode1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/episode-video-versions/${param1}/playback`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-results/${param1}/playback */
export async function shot(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.shotParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/shot-compose-results/${param1}/playback`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
