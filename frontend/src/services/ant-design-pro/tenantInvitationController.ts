// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/invitations */
export async function myInvitations(options?: { [key: string]: any }) {
  return request<API.ApiResponseListTenantInvitationResponse>(
    "/api/invitations",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/invitations/${param0} */
export async function detail7(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail7Params,
  options?: { [key: string]: any }
) {
  const { token: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantInvitationResponse>(
    `/api/invitations/${param0}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/invitations/${param0}/accept */
export async function accept(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.acceptParams,
  options?: { [key: string]: any }
) {
  const { token: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantInvitationResponse>(
    `/api/invitations/${param0}/accept`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/invitations/${param0}/cancel */
export async function cancel3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancel3Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantInvitationResponse>(
    `/api/invitations/${param0}/cancel`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/invitations/${param0}/reject */
export async function reject(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rejectParams,
  options?: { [key: string]: any }
) {
  const { token: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantInvitationResponse>(
    `/api/invitations/${param0}/reject`,
    {
      method: "POST",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/invitations */
export async function tenantInvitations(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.tenantInvitationsParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListTenantInvitationResponse>(
    `/api/tenants/${param0}/invitations`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/invitations */
export async function create2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.create2Params,
  body: API.CreateInvitationRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseTenantInvitationResponse>(
    `/api/tenants/${param0}/invitations`,
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
