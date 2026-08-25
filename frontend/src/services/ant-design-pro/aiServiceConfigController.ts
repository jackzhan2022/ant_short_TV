// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/ai-providers */
export async function providers1(options?: { [key: string]: any }) {
  return request<API.ApiResponseListAiProviderResponse>("/api/ai-providers", {
    method: "GET",
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/ai-service-configs */
export async function listGlobal(options?: { [key: string]: any }) {
  return request<API.ApiResponseListAiServiceConfigResponse>(
    "/api/ai-service-configs",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/ai-service-configs */
export async function createGlobal(
  body: API.AiServiceConfigRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseAiServiceConfigResponse>(
    "/api/ai-service-configs",
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

/** 此处后端没有提供注释 PUT /api/ai-service-configs/${param0} */
export async function updateGlobal(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateGlobalParams,
  body: API.AiServiceConfigRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/ai-service-configs/${param0}`,
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

/** 此处后端没有提供注释 DELETE /api/ai-service-configs/${param0} */
export async function deleteGlobal(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteGlobalParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/ai-service-configs/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 PUT /api/ai-service-configs/${param0}/default */
export async function setDefaultGlobal(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.setDefaultGlobalParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/ai-service-configs/${param0}/default`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/ai-service-configs/${param0}/status */
export async function updateStatusGlobal(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatusGlobalParams,
  body: API.AiServiceStatusRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/ai-service-configs/${param0}/status`,
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

/** 此处后端没有提供注释 POST /api/ai-service-configs/${param0}/test */
export async function testGlobal(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testGlobalParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceTestResponse>(
    `/api/ai-service-configs/${param0}/test`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/ai-service-configs */
export async function list2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list2Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListAiServiceConfigResponse>(
    `/api/tenants/${param0}/ai-service-configs`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/ai-service-configs */
export async function create3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create3Params,
  body: API.AiServiceConfigRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/tenants/${param0}/ai-service-configs`,
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

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/ai-service-configs/${param1} */
export async function update(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateParams,
  body: API.AiServiceConfigRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, id: param1, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/tenants/${param0}/ai-service-configs/${param1}`,
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

/** 此处后端没有提供注释 DELETE /api/tenants/${param0}/ai-service-configs/${param1} */
export async function deleteUsingDelete(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteUsingDELETEParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, id: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/tenants/${param0}/ai-service-configs/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/ai-service-configs/${param1}/default */
export async function setDefault(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.setDefaultParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, id: param1, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/tenants/${param0}/ai-service-configs/${param1}/default`,
    {
      method: "PUT",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/ai-service-configs/${param1}/status */
export async function updateStatus1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatus1Params,
  body: API.AiServiceStatusRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, id: param1, ...queryParams } = params;
  return request<API.ApiResponseAiServiceConfigResponse>(
    `/api/tenants/${param0}/ai-service-configs/${param1}/status`,
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

/** 此处后端没有提供注释 POST /api/tenants/${param0}/ai-service-configs/${param1}/test */
export async function test(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, id: param1, ...queryParams } = params;
  return request<API.ApiResponseAiServiceTestResponse>(
    `/api/tenants/${param0}/ai-service-configs/${param1}/test`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}
