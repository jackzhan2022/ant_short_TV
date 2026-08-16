import { request } from '@umijs/max';
import type { ApiResponse, TenantMember, TenantSummary } from './types';

export async function queryTenantMembers(tenantId: number) {
  return request<ApiResponse<TenantMember[]>>(
    `/api/tenants/${tenantId}/members`,
  );
}

export async function removeTenantMember(tenantId: number, memberId: number) {
  return request<ApiResponse<void>>(
    `/api/tenants/${tenantId}/members/${memberId}`,
    { method: 'DELETE' },
  );
}

export async function leaveTenant(tenantId: number) {
  return request<ApiResponse<void>>(`/api/tenants/${tenantId}/members/leave`, {
    method: 'POST',
  });
}

export async function transferOwner(tenantId: number, targetMemberId: number) {
  return request<ApiResponse<TenantSummary>>(
    `/api/tenants/${tenantId}/transfer-owner`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { targetMemberId },
    },
  );
}
