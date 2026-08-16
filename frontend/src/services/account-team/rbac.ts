import { request } from '@umijs/max';
import type {
  ApiResponse,
  AuthPermissions,
  Permission,
  PermissionTreeNode,
  Role,
  RoleStatus,
} from './types';

export type RoleFormValues = {
  code?: string;
  name: string;
  description?: string;
  permissionCodes?: string[];
};

export async function queryTenantRoles(tenantId: number) {
  return request<ApiResponse<Role[]>>(`/api/tenants/${tenantId}/roles`);
}

export async function createTenantRole(
  tenantId: number,
  values: RoleFormValues,
) {
  return request<ApiResponse<Role>>(`/api/tenants/${tenantId}/roles`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function updateTenantRole(
  tenantId: number,
  roleId: number,
  values: RoleFormValues,
) {
  return request<ApiResponse<Role>>(`/api/tenants/${tenantId}/roles/${roleId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function deleteTenantRole(tenantId: number, roleId: number) {
  return request<ApiResponse<void>>(`/api/tenants/${tenantId}/roles/${roleId}`, {
    method: 'DELETE',
  });
}

export async function updateTenantRoleStatus(
  tenantId: number,
  roleId: number,
  status: RoleStatus,
) {
  return request<ApiResponse<Role>>(
    `/api/tenants/${tenantId}/roles/${roleId}/status`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { status },
    },
  );
}

export async function queryRolePermissions(tenantId: number, roleId: number) {
  return request<ApiResponse<Permission[]>>(
    `/api/tenants/${tenantId}/roles/${roleId}/permissions`,
  );
}

export async function updateRolePermissions(
  tenantId: number,
  roleId: number,
  permissionCodes: string[],
) {
  return request<ApiResponse<Permission[]>>(
    `/api/tenants/${tenantId}/roles/${roleId}/permissions`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { permissionCodes },
    },
  );
}

export async function queryMemberRoles(tenantId: number, memberId: number) {
  return request<ApiResponse<Role[]>>(
    `/api/tenants/${tenantId}/members/${memberId}/roles`,
  );
}

export async function updateMemberRoles(
  tenantId: number,
  memberId: number,
  roleIds: number[],
) {
  return request<ApiResponse<Role[]>>(
    `/api/tenants/${tenantId}/members/${memberId}/roles`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { roleIds },
    },
  );
}

export async function queryPermissionTree() {
  return request<ApiResponse<PermissionTreeNode[]>>('/api/permissions/tree');
}

export async function queryCurrentPermissions(options?: Record<string, unknown>) {
  return request<ApiResponse<AuthPermissions>>('/api/auth/permissions', {
    ...(options || {}),
  });
}
