import { request } from '@umijs/max';
import { clearCurrentTenantId, setCurrentTenantId } from './auth';
import { setImpersonationDisplay, type ImpersonationDisplay } from './sessionIdentity';
import type { ApiResponse } from './types';
export { getImpersonationDisplay, setImpersonationDisplay } from './sessionIdentity';

export async function startTenantMemberImpersonation(
  tenantId: number,
  memberId: number,
  data: { code: string; reason: string },
  display: ImpersonationDisplay,
) {
  const response = await request<ApiResponse<{ tenantId: number }>>(
    `/api/platform/tenants/${tenantId}/members/${memberId}/impersonation`,
    { method: 'POST', data, skipErrorHandler: true },
  );
  setImpersonationDisplay(display);
  setCurrentTenantId(response.data.tenantId);
  window.location.assign('/');
}

export async function stopImpersonation() {
  await request<ApiResponse<void>>('/api/auth/impersonation/stop', {
    method: 'POST', skipErrorHandler: true,
  });
  setImpersonationDisplay(undefined);
  clearCurrentTenantId();
  window.location.assign('/commercial-management/tenants');
}
