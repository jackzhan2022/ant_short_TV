import { request } from '@umijs/max';
import type { ApiResponse } from './service';

export type GrantOption = {
  packageId: number;
  packageVersionId: number;
  name: string;
  description?: string;
  packageType: 'SUBSCRIPTION' | 'POINT_PACKAGE';
  periodMonths?: number;
  entitlements: {
    type: string;
    value?: number;
    name?: string;
    category?: string;
  }[];
};
export type AuthenticatorEnrollment = {
  secret: string;
  uri: string;
  expiresAt: string;
};
export type GrantCommand = {
  packageVersionId: number;
  reason: string;
  requestId: string;
  code: string;
};
const authenticator = '/api/platform/admin-authenticator';

export const getAdminAuthenticator = () =>
  request<ApiResponse<{ bound: boolean }>>(authenticator, {
    skipErrorHandler: true,
  });
export const enrollAdminAuthenticator = (password: string) =>
  request<ApiResponse<AuthenticatorEnrollment>>(`${authenticator}/enrollment`, {
    method: 'POST',
    data: { password },
    skipErrorHandler: true,
  });
export const confirmAdminAuthenticator = (code: string) =>
  request<ApiResponse<boolean>>(`${authenticator}/confirmation`, {
    method: 'POST',
    data: { code },
    skipErrorHandler: true,
  });
export const getTenantGrantOptions = () =>
  request<ApiResponse<GrantOption[]>>('/api/platform/tenants/grant-options', {
    skipErrorHandler: true,
  });
export const grantTenantPackage = (tenantId: number, data: GrantCommand) =>
  request<ApiResponse<{ id: number; status: string }>>(
    `/api/platform/tenants/${tenantId}/package-grants`,
    {
      method: 'POST',
      data,
      skipErrorHandler: true,
    },
  );

export function grantErrorMessage(error: unknown): string {
  const failure = error as {
    info?: { errorMessage?: string };
    response?: { data?: { errorMessage?: string } };
    message?: string;
  };
  return (
    failure?.info?.errorMessage ??
    failure?.response?.data?.errorMessage ??
    failure?.message ??
    '操作失败，请稍后重试。'
  );
}
