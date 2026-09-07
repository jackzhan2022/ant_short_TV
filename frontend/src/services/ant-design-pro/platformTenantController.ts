// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/platform/tenants */
export async function list8(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list8Params,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePlatformTenantPageResponse>(
    "/api/platform/tenants",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/platform/tenants/${param0} */
export async function detail8(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail8Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformTenantDetailResponse>(
    `/api/platform/tenants/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/platform/tenants/${param0}/status */
export async function updateStatus3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatus3Params,
  body: API.UpdatePlatformTenantStatusRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponsePlatformTenantSummaryResponse>(
    `/api/platform/tenants/${param0}/status`,
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
