// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-image-results/${param1} */
export async function deleteResult1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteResult1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-image-results/${param1}`,
    {
      method: "DELETE",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-image-results/${param1}/download */
export async function downloadResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<string>(
    `/api/projects/${param0}/ai-image-results/${param1}/download`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-image-results/${param1}/save-material */
export async function saveMaterial1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveMaterial1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiImageResultResponse>(
    `/api/projects/${param0}/ai-image-results/${param1}/save-material`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/ai-image-results/${param1}/selected */
export async function selectResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.selectResultParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, resultId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiImageResultResponse>(
    `/api/projects/${param0}/ai-image-results/${param1}/selected`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-image-tasks */
export async function list7(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list7Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseListAiImageTaskResponse>(
    `/api/projects/${param0}/ai-image-tasks`,
    {
      method: "GET",
      params: {
        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-image-tasks */
export async function create6(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create6Params,
  body: API.CreateAiImageTaskRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiImageTaskResponse>(
    `/api/projects/${param0}/ai-image-tasks`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai-image-tasks/${param1} */
export async function detail4(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail4Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiImageTaskResponse>(
    `/api/projects/${param0}/ai-image-tasks/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/ai-image-tasks/${param1} */
export async function delete1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/ai-image-tasks/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/ai-image-tasks/${param1}/cancel */
export async function cancel(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiImageTaskResponse>(
    `/api/projects/${param0}/ai-image-tasks/${param1}/cancel`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/ai-image-tasks/${param1}/regenerate */
export async function regenerate1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.regenerate1Params,
  options?: { [key: string]: any }
) {
  const { projectId: param0, taskId: param1, ...queryParams } = params;
  return request<API.ApiResponseAiImageTaskResponse>(
    `/api/projects/${param0}/ai-image-tasks/${param1}/regenerate`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
