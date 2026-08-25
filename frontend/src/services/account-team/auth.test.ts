import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearAuthSession,
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

  it('stores only the last validated tenant selection', () => {
    localStorage.setItem('accessToken', 'legacy-token');
    saveAuthSession({ currentTenantId: 12 });

    expect(localStorage.getItem('accessToken')).toBe('legacy-token');
    expect(localStorage.getItem('currentTenantId')).toBe('12');

    clearAuthSession();

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('currentTenantId')).toBeNull();
  });

  it('logs in by mobile without persisting a reusable credential', async () => {
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
  });
});
