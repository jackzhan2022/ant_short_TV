// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/tenants/${param0}/members */
export async function list1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.list1Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListTenantMemberResponse>(
    `/api/tenants/${param0}/members`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 DELETE /api/tenants/${param0}/members/${param1} */
export async function remove(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.removeParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, memberId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/tenants/${param0}/members/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/members/leave */
export async function leave(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.leaveParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/tenants/${param0}/members/leave`, {
    method: "POST",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/transfer-owner */
export async function transferOwner(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.transferOwnerParams,
  body: API.TransferOwnerRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantSummaryResponse>(
    `/api/tenants/${param0}/transfer-owner`,
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
