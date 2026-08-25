// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 POST /api/tenants */
export async function create1(
  body: API.CreateTenantRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseTenantSummaryResponse>("/api/tenants", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/tenants/${param0} */
export async function detail2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail2Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantSummaryResponse>(
    `/api/tenants/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0} */
export async function update1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update1Params,
  body: API.UpdateTenantRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantSummaryResponse>(
    `/api/tenants/${param0}`,
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

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/status */
export async function updateStatus2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatus2Params,
  body: API.UpdateTenantStatusRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantSummaryResponse>(
    `/api/tenants/${param0}/status`,
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

/** 此处后端没有提供注释 GET /api/tenants/my */
export async function myTenants(options?: { [key: string]: any }) {
  return request<API.ApiResponseListTenantSummaryResponse>("/api/tenants/my", {
    method: "GET",
    ...(options || {}),
  });
}
