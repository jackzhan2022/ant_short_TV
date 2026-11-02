import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getImpersonationDisplay, startTenantMemberImpersonation, stopImpersonation } from './impersonation';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('impersonation session transitions', () => {
  const assign = vi.fn();
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    vi.spyOn(window.location, 'assign').mockImplementation(assign);
    vi.mocked(request).mockResolvedValue({ success: true, data: { tenantId: 9 } });
  });

  it('switches only after server acceptance and fully reloads the target workspace', async () => {
    await startTenantMemberImpersonation(9, 11, { code: '123456', reason: '排查任务' }, { tenantName: '测试团队', userName: '小王' });
    expect(request).toHaveBeenCalledWith('/api/platform/tenants/9/members/11/impersonation', expect.objectContaining({ method: 'POST', data: { code: '123456', reason: '排查任务' } }));
    expect(localStorage.getItem('currentTenantId')).toBe('9');
    expect(getImpersonationDisplay()).toEqual({ tenantName: '测试团队', userName: '小王' });
    expect(assign).toHaveBeenCalledWith('/');
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('preserves the current session display and selection on rejected start', async () => {
    localStorage.setItem('currentTenantId', '3');
    vi.mocked(request).mockRejectedValue(new Error('验证码无效'));
    await expect(startTenantMemberImpersonation(9, 11, { code: '000000', reason: '排查' }, { tenantName: '团队', userName: '成员' })).rejects.toThrow();
    expect(localStorage.getItem('currentTenantId')).toBe('3');
    expect(getImpersonationDisplay()).toBeUndefined();
    expect(assign).not.toHaveBeenCalled();
  });

  it('clears target selection and fully reloads administrator tenants after stop', async () => {
    await startTenantMemberImpersonation(9, 11, { code: '123456', reason: '排查' }, { tenantName: '团队', userName: '成员' });
    assign.mockClear();
    await stopImpersonation();
    expect(request).toHaveBeenLastCalledWith('/api/auth/impersonation/stop', expect.objectContaining({ method: 'POST' }));
    expect(localStorage.getItem('currentTenantId')).toBeNull();
    expect(getImpersonationDisplay()).toBeUndefined();
    expect(assign).toHaveBeenCalledWith('/commercial-management/tenants');
  });

  it('keeps recovery available after a failed stop', async () => {
    await startTenantMemberImpersonation(9, 11, { code: '123456', reason: '排查' }, { tenantName: '团队', userName: '成员' });
    assign.mockClear();
    vi.mocked(request).mockRejectedValue(new Error('网络中断'));
    await expect(stopImpersonation()).rejects.toThrow();
    expect(getImpersonationDisplay()).toBeDefined();
    expect(localStorage.getItem('currentTenantId')).toBe('9');
    expect(assign).not.toHaveBeenCalled();
  });
});
