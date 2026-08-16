import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearAuthSession,
  getAccessToken,
  loginByMobile,
  saveAuthSession,
} from './auth';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('account team auth service', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('saves and clears auth session', () => {
    saveAuthSession({ accessToken: 'token-a', currentTenantId: 12 });

    expect(getAccessToken()).toBe('token-a');
    expect(localStorage.getItem('currentTenantId')).toBe('12');

    clearAuthSession();

    expect(getAccessToken()).toBeNull();
    expect(localStorage.getItem('currentTenantId')).toBeNull();
  });

  it('logs in by mobile and stores returned access token', async () => {
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
    expect(response.data.accessToken).toBe('token-b');
    expect(getAccessToken()).toBe('token-b');
  });
});
