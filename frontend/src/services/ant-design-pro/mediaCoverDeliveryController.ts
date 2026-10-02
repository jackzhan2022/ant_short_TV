// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-results/${param1}/cover */
export async function aiVideo1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.aiVideo1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/ai-video-results/${param1}/cover`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/episode-video-versions/${param1}/cover */
export async function episode2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.episode2Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, versionId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/episode-video-versions/${param1}/cover`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/shot-compose-results/${param1}/cover */
export async function shot1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.shot1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/shot-compose-results/${param1}/cover`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/storyboards/${param1}/first-frame */
export async function storyboardFirstFrame(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.storyboardFirstFrameParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, storyboardId: param1, ...queryParams } = params;
  return request<any>(
    `/api/projects/${param0}/storyboards/${param1}/first-frame`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
