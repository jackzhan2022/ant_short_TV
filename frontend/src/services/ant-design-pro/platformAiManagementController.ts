// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/ai/models */
export async function models1(options?: { [key: string]: any }) {
  return request<API.ApiResponseListPlatformModelResponse>(
    "/api/platform/ai/models",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models */
export async function createModel(
  body: API.PlatformModelRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePlatformModelResponse>(
    "/api/platform/ai/models",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/models/${param0} */
export async function updateModel(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateModelParams,
  body: API.PlatformModelRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformModelResponse>(
    `/api/platform/ai/models/${param0}`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/default */
export async function defaultModel(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.defaultModelParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformModelResponse>(
    `/api/platform/ai/models/${param0}/default`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/disable */
export async function disableModel(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.disableModelParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformModelResponse>(
    `/api/platform/ai/models/${param0}/disable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/models/${param0}/enable */
export async function enableModel(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.enableModelParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformModelResponse>(
    `/api/platform/ai/models/${param0}/enable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/ai/providers */
export async function providers(options?: { [key: string]: any }) {
  return request<API.ApiResponseListPlatformProviderResponse>(
    "/api/platform/ai/providers",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/providers */
export async function createProvider(
  body: API.PlatformProviderRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePlatformProviderResponse>(
    "/api/platform/ai/providers",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/ai/providers/${param0} */
export async function updateProvider(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateProviderParams,
  body: API.PlatformProviderRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformProviderResponse>(
    `/api/platform/ai/providers/${param0}`,
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

/** 此处后端没有提供注释 POST /api/platform/ai/providers/${param0}/disable */
export async function disableProvider(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.disableProviderParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformProviderResponse>(
    `/api/platform/ai/providers/${param0}/disable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/providers/${param0}/enable */
export async function enableProvider(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.enableProviderParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformProviderResponse>(
    `/api/platform/ai/providers/${param0}/enable`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/platform/ai/providers/${param0}/test */
export async function testProvider(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testProviderParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceTestResponse>(
    `/api/platform/ai/providers/${param0}/test`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
