// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-video-results/${param1} */
export async function deleteResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-video-results/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-results/${param1}/bind-storyboard */
export async function bindStoryboard(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bindStoryboardParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoResultResponse>(
    `/api/projects/${param0}/ai-video-results/${param1}/bind-storyboard`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-results/${param1}/download */
export async function download(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoResultResponse>(
    `/api/projects/${param0}/ai-video-results/${param1}/download`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-results/${param1}/save-material */
export async function saveMaterial(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveMaterialParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoResultResponse>(
    `/api/projects/${param0}/ai-video-results/${param1}/save-material`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-tasks */
export async function list5(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list5Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-tasks */
export async function create4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create4Params,
  body: API.CreateAiVideoTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-tasks/${param1} */
export async function detail4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail4Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-video-tasks/${param1} */
export async function deleteTask(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteTaskParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-video-tasks/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-tasks/${param1}/cancel */
export async function cancel2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancel2Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks/${param1}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-tasks/${param1}/poll */
export async function poll(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.pollParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks/${param1}/poll`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-video-tasks/${param1}/regenerate */
export async function regenerate1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerate1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiVideoTaskResponse>(
    `/api/projects/${param0}/ai-video-tasks/${param1}/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-video-tasks/${param1}/results */
export async function results(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resultsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseListAiVideoResultResponse>(
    `/api/projects/${param0}/ai-video-tasks/${param1}/results`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
