import { request } from '@umijs/max';
import { setCurrentTenantId } from './auth';
import type {
  ApiResponse,
  CurrentTenant,
  TenantStatus,
  TenantSummary,
  TenantType,
} from './types';

export type TenantFormValues = {
  name: string;
  type: TenantType;
  logo?: string;
  description?: string;
};

export async function createTenant(values: TenantFormValues) {
  return request<ApiResponse<TenantSummary>>('/api/tenants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function queryMyTenants() {
  return request<ApiResponse<TenantSummary[]>>('/api/tenants/my');
}

export async function queryTenant(id: number) {
  return request<ApiResponse<TenantSummary>>(`/api/tenants/${id}`);
}

export async function updateTenant(id: number, values: TenantFormValues) {
  return request<ApiResponse<TenantSummary>>(`/api/tenants/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: values,
  });
}

export async function updateTenantStatus(id: number, status: TenantStatus) {
  return request<ApiResponse<TenantSummary>>(`/api/tenants/${id}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: { status },
  });
}

export async function switchTenant(tenantId: number) {
  const response = await request<ApiResponse<CurrentTenant>>(
    '/api/tenants/current',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { tenantId },
    },
  );
  setCurrentTenantId(tenantId);
  return response;
}

export async function queryCurrentTenant() {
  return request<ApiResponse<CurrentTenant>>('/api/tenants/current');
}
