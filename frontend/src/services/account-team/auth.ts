import { request } from '@umijs/max';
import type { ApiResponse, AuthBootstrap, AuthSession } from './types';

const CURRENT_TENANT_ID_KEY = 'currentTenantId';

export type LoginByMobileParams = {
  mobile: string;
  password: string;
};

export type RegisterParams = LoginByMobileParams & {
  verificationCode: string;
  nickname: string;
};

export function saveAuthSession(session: {
  currentTenantId?: number;
}) {
  if (session.currentTenantId) {
    localStorage.setItem(CURRENT_TENANT_ID_KEY, String(session.currentTenantId));
  }
}

export function getCurrentTenantId() {
  const value = localStorage.getItem(CURRENT_TENANT_ID_KEY);
  return value ? Number(value) : undefined;
}

export function setCurrentTenantId(tenantId: number) {
  localStorage.setItem(CURRENT_TENANT_ID_KEY, String(tenantId));
}

export function clearAuthSession() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem(CURRENT_TENANT_ID_KEY);
}

export async function loginByMobile(params: LoginByMobileParams) {
  return request<ApiResponse<AuthSession>>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: params,
  });
}

export async function registerByMobile(params: RegisterParams) {
  return request<ApiResponse<AuthSession>>(
    '/api/auth/register',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: params,
    },
  );
}

export async function logout() {
  try {
    await request<ApiResponse<void>>('/api/auth/logout', { method: 'POST' });
  } finally {
    clearAuthSession();
  }
}

export async function queryAuthBootstrap(
  tenantId?: number,
  options?: Record<string, unknown>,
) {
  return request<ApiResponse<AuthBootstrap>>('/api/auth/bootstrap', {
    ...(options || {}),
    headers: {
      ...((options?.headers as Record<string, string> | undefined) || {}),
      ...(tenantId ? { 'X-Tenant-Id': String(tenantId) } : {}),
    },
  });
}
