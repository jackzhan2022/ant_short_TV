import { request } from '@umijs/max';
import type { ApiResponse, TenantInvitation } from './types';

export async function createInvitation(tenantId: number, mobile: string) {
  return request<ApiResponse<TenantInvitation>>(
    `/api/tenants/${tenantId}/invitations`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { mobile },
    },
  );
}

export async function queryMyInvitations() {
  return request<ApiResponse<TenantInvitation[]>>('/api/invitations');
}

export async function queryTenantInvitations(tenantId: number) {
  return request<ApiResponse<TenantInvitation[]>>(
    `/api/tenants/${tenantId}/invitations`,
  );
}

export async function queryInvitation(token: string) {
  return request<ApiResponse<TenantInvitation>>(`/api/invitations/${token}`);
}

export async function acceptInvitation(token: string) {
  return request<ApiResponse<TenantInvitation>>(
    `/api/invitations/${token}/accept`,
    { method: 'POST' },
  );
}

export async function rejectInvitation(token: string) {
  return request<ApiResponse<TenantInvitation>>(
    `/api/invitations/${token}/reject`,
    { method: 'POST' },
  );
}

export async function cancelInvitation(id: number) {
  return request<ApiResponse<TenantInvitation>>(`/api/invitations/${id}/cancel`, {
    method: 'POST',
  });
}
