import { request } from '@umijs/max';
import type { ApiResponse, AuthSession, UserProfile } from './types';

const ACCESS_TOKEN_KEY = 'accessToken';
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
  accessToken?: string;
  currentTenantId?: number;
}) {
  if (session.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  }
  if (session.currentTenantId) {
    localStorage.setItem(CURRENT_TENANT_ID_KEY, String(session.currentTenantId));
  }
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getCurrentTenantId() {
  const value = localStorage.getItem(CURRENT_TENANT_ID_KEY);
  return value ? Number(value) : undefined;
}

export function setCurrentTenantId(tenantId: number) {
  localStorage.setItem(CURRENT_TENANT_ID_KEY, String(tenantId));
}

export function clearAuthSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(CURRENT_TENANT_ID_KEY);
}

export async function loginByMobile(params: LoginByMobileParams) {
  const response = await request<ApiResponse<AuthSession>>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: params,
  });
  saveAuthSession({ accessToken: response.data.accessToken });
  return response;
}

export async function registerByMobile(params: RegisterParams) {
  const response = await request<ApiResponse<AuthSession>>(
    '/api/auth/register',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: params,
    },
  );
  saveAuthSession({ accessToken: response.data.accessToken });
  return response;
}

export async function logout() {
  try {
    await request<ApiResponse<void>>('/api/auth/logout', { method: 'POST' });
  } finally {
    clearAuthSession();
  }
}

export async function currentUser(options?: Record<string, unknown>) {
  return request<ApiResponse<UserProfile>>('/api/user/me', {
    ...(options || {}),
  });
}
