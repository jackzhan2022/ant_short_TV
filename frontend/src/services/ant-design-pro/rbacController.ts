// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/permissions/tree */
export async function permissionTree(options?: { [key: string]: any }) {
  return request<API.ApiResponseListPermissionTreeNodeResponse>(
    "/api/permissions/tree",
    {
      method: "GET",
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/members/${param1}/roles */
export async function memberRoles(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.memberRolesParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, memberId: param1, ...queryParams } = params;
  return request<API.ApiResponseListRoleResponse>(
    `/api/tenants/${param0}/members/${param1}/roles`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/members/${param1}/roles */
export async function updateMemberRoles(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateMemberRolesParams,
  body: API.UpdateMemberRolesRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, memberId: param1, ...queryParams } = params;
  return request<API.ApiResponseListRoleResponse>(
    `/api/tenants/${param0}/members/${param1}/roles`,
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

/** 此处后端没有提供注释 GET /api/tenants/${param0}/roles */
export async function listRoles(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listRolesParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseListRoleResponse>(
    `/api/tenants/${param0}/roles`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/tenants/${param0}/roles */
export async function createRole(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createRoleParams,
  body: API.CreateRoleRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, ...queryParams } = params;
  return request<API.ApiResponseRoleResponse>(`/api/tenants/${param0}/roles`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/roles/${param1} */
export async function detail1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail1Params,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseRoleResponse>(
    `/api/tenants/${param0}/roles/${param1}`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/roles/${param1} */
export async function updateRole(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRoleParams,
  body: API.UpdateRoleRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseRoleResponse>(
    `/api/tenants/${param0}/roles/${param1}`,
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

/** 此处后端没有提供注释 DELETE /api/tenants/${param0}/roles/${param1} */
export async function deleteRole(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteRoleParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/tenants/${param0}/roles/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/tenants/${param0}/roles/${param1}/permissions */
export async function rolePermissions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rolePermissionsParams,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseListPermissionResponse>(
    `/api/tenants/${param0}/roles/${param1}/permissions`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/roles/${param1}/permissions */
export async function updateRolePermissions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRolePermissionsParams,
  body: API.UpdateRolePermissionsRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseListPermissionResponse>(
    `/api/tenants/${param0}/roles/${param1}/permissions`,
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

/** 此处后端没有提供注释 PUT /api/tenants/${param0}/roles/${param1}/status */
export async function updateStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatusParams,
  body: API.UpdateRoleStatusRequest,
  options?: { [key: string]: any }
) {
  const { tenantId: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseRoleResponse>(
    `/api/tenants/${param0}/roles/${param1}/status`,
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
