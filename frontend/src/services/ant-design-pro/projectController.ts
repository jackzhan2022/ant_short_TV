// @ts-ignore
/* eslint-disable */
import { request } from "@umijs/max";

/** 此处后端没有提供注释 GET /api/projects */
export async function list5(options?: { [key: string]: any }) {
  return request<API.ApiResponseListProjectResponse>("/api/projects", {
    method: "GET",
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /api/projects */
export async function create4(
  body: API.CreateProjectRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseProjectResponse>("/api/projects", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/projects/${param0} */
export async function detail5(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.detail5Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectResponse>(`/api/projects/${param0}`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 PUT /api/projects/${param0} */
export async function update2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.update2Params,
  body: API.UpdateProjectRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectResponse>(`/api/projects/${param0}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 DELETE /api/projects/${param0} */
export async function delete2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete2Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/projects/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/members */
export async function members(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.membersParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseListProjectMemberResponse>(
    `/api/projects/${param0}/members`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/members */
export async function addMember(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.addMemberParams,
  body: API.AddProjectMemberRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectMemberResponse>(
    `/api/projects/${param0}/members`,
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

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/members/${param1} */
export async function removeMember(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.removeMemberParams,
  options?: { [key: string]: any }
) {
  const { id: param0, userId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/members/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/members/${param1}/role */
export async function updateMemberRole(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateMemberRoleParams,
  body: API.UpdateProjectMemberRoleRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, userId: param1, ...queryParams } = params;
  return request<API.ApiResponseProjectMemberResponse>(
    `/api/projects/${param0}/members/${param1}/role`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/owner */
export async function updateOwner(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateOwnerParams,
  body: API.UpdateProjectOwnerRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectResponse>(
    `/api/projects/${param0}/owner`,
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

/** 此处后端没有提供注释 GET /api/projects/${param0}/roles */
export async function roles(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rolesParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseListProjectRoleResponse>(
    `/api/projects/${param0}/roles`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 POST /api/projects/${param0}/roles */
export async function createRole1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createRole1Params,
  body: API.CreateProjectRoleRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectRoleResponse>(
    `/api/projects/${param0}/roles`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/roles/${param1} */
export async function updateRole1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRole1Params,
  body: API.UpdateProjectRoleRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseProjectRoleResponse>(
    `/api/projects/${param0}/roles/${param1}`,
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

/** 此处后端没有提供注释 DELETE /api/projects/${param0}/roles/${param1} */
export async function deleteRole1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteRole1Params,
  options?: { [key: string]: any }
) {
  const { id: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseVoid>(
    `/api/projects/${param0}/roles/${param1}`,
    {
      method: "DELETE",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /api/projects/${param0}/roles/${param1}/permissions */
export async function rolePermissions1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rolePermissions1Params,
  options?: { [key: string]: any }
) {
  const { id: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseListProjectRolePermissionResponse>(
    `/api/projects/${param0}/roles/${param1}/permissions`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 PUT /api/projects/${param0}/roles/${param1}/permissions */
export async function updateRolePermissions1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRolePermissions1Params,
  body: API.UpdateProjectRolePermissionsRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, roleId: param1, ...queryParams } = params;
  return request<API.ApiResponseListProjectRolePermissionResponse>(
    `/api/projects/${param0}/roles/${param1}/permissions`,
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

/** 此处后端没有提供注释 PUT /api/projects/${param0}/status */
export async function updateStatus3(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatus3Params,
  body: API.UpdateProjectStatusRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseProjectResponse>(
    `/api/projects/${param0}/status`,
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
