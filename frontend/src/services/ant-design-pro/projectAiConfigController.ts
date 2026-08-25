// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai/config */
export async function config(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.configParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectAiConfigResponse>(
    `/api/projects/${param0}/ai/config`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/ai/config */
export async function save(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.saveParams,
  body: API.ProjectAiConfigRequest,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectAiConfigResponse>(
    `/api/projects/${param0}/ai/config`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/ai/models */
export async function models(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.modelsParams,
  options?: { [key: string]: any }
) {
  const { projectId: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectAiModelsResponse>(
    `/api/projects/${param0}/ai/models`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
