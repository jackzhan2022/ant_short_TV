import { request } from '@umijs/max';
import type {
  ApiResponse,
  Organization,
  OrganizationStatus,
  Permission,
  Project,
  ProjectMember,
  ProjectRole,
  ProjectStatus,
} from './types';

export type OrganizationFormValues = {
  parentId?: number | null;
  name: string;
  code: string;
  leaderId?: number | null;
  sort?: number;
};

export type ProjectFormValues = {
  organizationId?: number | null;
  name: string;
  code?: string;
  description?: string;
  coverUrl?: string;
  ownerId: number;
  startDate?: string;
  endDate?: string;
};

export type ProjectMemberFormValues = {
  userId: number;
  organizationId?: number | null;
  roleId?: number;
};

export type ProjectRoleFormValues = {
  code?: string;
  name: string;
  description?: string;
  status?: string;
  dataScope?: string;
  permissionCodes?: string[];
};

export async function queryOrganizations() {
  return request<ApiResponse<Organization[]>>('/api/organizations');
}

export async function createOrganization(values: OrganizationFormValues) {
  return request<ApiResponse<Organization>>('/api/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function updateOrganization(
  organizationId: number,
  values: OrganizationFormValues,
) {
  return request<ApiResponse<Organization>>(`/api/organizations/${organizationId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function deleteOrganization(organizationId: number) {
  return request<ApiResponse<void>>(`/api/organizations/${organizationId}`, {
    method: 'DELETE',
  });
}

export async function updateOrganizationStatus(
  organizationId: number,
  status: OrganizationStatus,
) {
  return request<ApiResponse<Organization>>(
    `/api/organizations/${organizationId}/status`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { status },
    },
  );
}

export async function queryProjects() {
  return request<ApiResponse<Project[]>>('/api/projects');
}

export async function createProject(values: ProjectFormValues) {
  return request<ApiResponse<Project>>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function queryProject(projectId: number) {
  return request<ApiResponse<Project>>(`/api/projects/${projectId}`);
}

export async function updateProject(projectId: number, values: ProjectFormValues) {
  return request<ApiResponse<Project>>(`/api/projects/${projectId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function deleteProject(projectId: number) {
  return request<ApiResponse<void>>(`/api/projects/${projectId}`, {
    method: 'DELETE',
  });
}

export async function updateProjectStatus(
  projectId: number,
  status: ProjectStatus,
) {
  return request<ApiResponse<Project>>(`/api/projects/${projectId}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: { status },
  });
}

export async function queryProjectMembers(projectId: number) {
  return request<ApiResponse<ProjectMember[]>>(`/api/projects/${projectId}/members`);
}

export async function addProjectMember(
  projectId: number,
  values: ProjectMemberFormValues,
) {
  return request<ApiResponse<ProjectMember>>(`/api/projects/${projectId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function removeProjectMember(projectId: number, userId: number) {
  return request<ApiResponse<void>>(
    `/api/projects/${projectId}/members/${userId}`,
    { method: 'DELETE' },
  );
}

export async function updateProjectMemberRole(
  projectId: number,
  userId: number,
  roleId: number,
) {
  return request<ApiResponse<ProjectMember>>(
    `/api/projects/${projectId}/members/${userId}/role`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { roleId },
    },
  );
}

export async function queryProjectRoles(projectId: number) {
  return request<ApiResponse<ProjectRole[]>>(`/api/projects/${projectId}/roles`);
}

export async function createProjectRole(
  projectId: number,
  values: ProjectRoleFormValues,
) {
  return request<ApiResponse<ProjectRole>>(`/api/projects/${projectId}/roles`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function updateProjectRole(
  projectId: number,
  roleId: number,
  values: ProjectRoleFormValues,
) {
  return request<ApiResponse<ProjectRole>>(
    `/api/projects/${projectId}/roles/${roleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: values,
    },
  );
}

export async function deleteProjectRole(projectId: number, roleId: number) {
  return request<ApiResponse<void>>(`/api/projects/${projectId}/roles/${roleId}`, {
    method: 'DELETE',
  });
}

export async function queryProjectRolePermissions(
  projectId: number,
  roleId: number,
) {
  return request<ApiResponse<Permission[]>>(
    `/api/projects/${projectId}/roles/${roleId}/permissions`,
  );
}

export async function updateProjectRolePermissions(
  projectId: number,
  roleId: number,
  permissionCodes: string[],
) {
  return request<ApiResponse<Permission[]>>(
    `/api/projects/${projectId}/roles/${roleId}/permissions`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      data: { permissionCodes },
    },
  );
}
