import { beforeEach, describe, expect, it, vi } from 'vitest';
import { errorConfig } from './requestErrorConfig';

const mocks = vi.hoisted(() => ({
  error: vi.fn(),
  warning: vi.fn(),
  open: vi.fn(),
}));

vi.mock('antd', () => ({
  message: {
    error: mocks.error,
    warning: mocks.warning,
  },
  notification: {
    open: mocks.open,
  },
}));

vi.mock('@umijs/max', () => ({
  getIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));

describe('requestErrorConfig', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('uses cookie credentials and copies the CSRF cookie for unsafe requests', () => {
    localStorage.setItem('accessToken', 'legacy-token');
    localStorage.setItem('currentTenantId', '21');
    document.cookie = 'XSRF-TOKEN=csrf%20value; path=/';
    const interceptor = errorConfig.requestInterceptors?.[0] as (
      config: any,
    ) => any;

    const config = interceptor({ method: 'POST', headers: {} });

    expect(config.withCredentials).toBe(true);
    expect(config.headers).toMatchObject({
      'X-XSRF-TOKEN': 'csrf value',
      'X-Tenant-Id': '21',
    });
    expect(config.headers.Authorization).toBeUndefined();
  });

  it('does not attach a CSRF header to safe requests', () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/';
    const interceptor = errorConfig.requestInterceptors?.[0] as (
      config: any,
    ) => any;

    const config = interceptor({ method: 'GET', headers: {} });

    expect(config.withCredentials).toBe(true);
    expect(config.headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('shows backend error code and message from non-2xx responses', () => {
    errorConfig.errorConfig?.errorHandler?.(
      {
        response: {
          status: 409,
          statusText: 'Conflict',
          headers: {},
          config: {} as any,
          data: {
            success: false,
            errorCode: 'DUPLICATE_MOBILE',
            errorMessage: '该手机号已注册。',
          },
        },
      } as any,
      {},
    );

    expect(mocks.error).toHaveBeenCalledWith(
      'DUPLICATE_MOBILE：该手机号已注册。',
    );
  });

  it('shows a service unavailable message when the request has no response', () => {
    errorConfig.errorConfig?.errorHandler?.(
      {
        request: {},
      } as any,
      {},
    );

    expect(mocks.error).toHaveBeenCalledWith(
      '服务暂不可达，请确认后端已启动后重试。',
    );
  });

  it('shows a service unavailable message for a zero status response', () => {
    errorConfig.errorConfig?.errorHandler?.(
      {
        response: {
          status: 0,
          data: undefined,
        },
      } as any,
      {},
    );

    expect(mocks.error).toHaveBeenCalledWith(
      '服务暂不可达，请确认后端已启动后重试。',
    );
  });
});
