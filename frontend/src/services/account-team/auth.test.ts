import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearAuthSession,
  loginByMobile,
  registerByMobile,
  saveAuthSession,
} from './auth';
import { getExpectedSessionId, setExpectedSessionId } from './sessionIdentity';
import { getImpersonationDisplay, setImpersonationDisplay } from './impersonation';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('account team auth service', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('stores only the last validated tenant selection', () => {
    setExpectedSessionId('old-session');
    setImpersonationDisplay({ tenantName: '旧团队', userName: '旧成员' });
    localStorage.setItem('accessToken', 'legacy-token');
    saveAuthSession({ currentTenantId: 12 });

    expect(localStorage.getItem('accessToken')).toBe('legacy-token');
    expect(localStorage.getItem('currentTenantId')).toBe('12');

    clearAuthSession();

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('currentTenantId')).toBeNull();
    expect(getExpectedSessionId()).toBeUndefined();
    expect(getImpersonationDisplay()).toBeUndefined();
  });

  it('logs in by mobile without persisting a reusable credential', async () => {
    setExpectedSessionId('previous-session');
    vi.mocked(request).mockResolvedValue({
      success: true,
      data: {
        accessToken: 'token-b',
        user: { id: 1, mobile: '13800000000', nickname: '用户' },
        tenants: [],
        nextAction: 'CREATE_OR_JOIN_TEAM',
      },
    });

    const response = await loginByMobile({
      mobile: '13800000000',
      password: 'Password123',
    });

    expect(request).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { mobile: '13800000000', password: 'Password123' },
    });
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(getExpectedSessionId()).toBeUndefined();
  });

  it('clears the old session pin after successful registration', async () => {
    setExpectedSessionId('previous-session');
    setImpersonationDisplay({ tenantName: '团队', userName: '成员' });
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
    await registerByMobile({ mobile: '13800000000', password: 'Password123', verificationCode: '123456', nickname: '新用户' });
    expect(getExpectedSessionId()).toBeUndefined();
    expect(getImpersonationDisplay()).toBeUndefined();
  });

  it('preserves the tab session pin when authentication is rejected', async () => {
    setExpectedSessionId('previous-session');
    vi.mocked(request).mockRejectedValue(new Error('验证失败'));
    await expect(loginByMobile({ mobile: '13800000000', password: 'bad' })).rejects.toThrow();
    expect(getExpectedSessionId()).toBe('previous-session');
    await expect(registerByMobile({ mobile: '13800000000', password: 'bad', verificationCode: '000000', nickname: '用户' })).rejects.toThrow();
    expect(getExpectedSessionId()).toBe('previous-session');
  });
});
