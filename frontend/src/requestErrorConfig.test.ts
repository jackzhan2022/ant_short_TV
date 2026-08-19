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
});
