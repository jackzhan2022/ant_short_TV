import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Register from './index';

const mocks = vi.hoisted(() => ({
  fetchUserInfo: vi.fn(),
  registerByMobile: vi.fn(),
  replace: vi.fn(),
  setInitialState: vi.fn(),
  success: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  LockOutlined: () => <span />,
  MobileOutlined: () => <span />,
  UserOutlined: () => <span />,
}));

vi.mock('@umijs/max', () => ({
  Helmet: ({ children }: { children: ReactNode }) => <>{children}</>,
  Link: ({ children }: { children: ReactNode }) => <a href="/user/login">{children}</a>,
  SelectLang: () => <div data-testid="select-lang" />,
  history: { replace: mocks.replace },
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
  useModel: () => ({
    initialState: { fetchUserInfo: mocks.fetchUserInfo },
    setInitialState: mocks.setInitialState,
  }),
}));

vi.mock('@ant-design/pro-components', () => {
  const ProFormText = ({ name, placeholder }: any) => (
    <input aria-label={placeholder} name={name} placeholder={placeholder} />
  );
  ProFormText.Password = ({ name, placeholder }: any) => (
    <input
      aria-label={placeholder}
      name={name}
      placeholder={placeholder}
      type="password"
    />
  );

  return {
    LoginForm: ({ children, onFinish }: any) => (
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);
          onFinish?.({
            mobile: formData.get('mobile'),
            verificationCode: formData.get('verificationCode'),
            nickname: formData.get('nickname'),
            password: formData.get('password'),
          });
        }}
      >
        {children}
        <button type="submit">注册</button>
      </form>
    ),
    ProFormCaptcha: ({ name, placeholder }: any) => (
      <input aria-label={placeholder} name={name} placeholder={placeholder} />
    ),
    ProFormText,
  };
});

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.success } }),
  },
}));

vi.mock('@/components', () => ({
  Footer: () => <footer />,
}));

vi.mock('@/services/account-team/auth', () => ({
  registerByMobile: mocks.registerByMobile,
}));

describe('Register Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.registerByMobile.mockResolvedValue({ success: true, data: {} });
    mocks.fetchUserInfo.mockResolvedValue({ name: '新用户' });
  });

  it('refreshes current user before entering team pages after registration', async () => {
    render(<Register />);

    fireEvent.change(screen.getByPlaceholderText('手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.change(screen.getByPlaceholderText('验证码'), {
      target: { value: '123456' },
    });
    fireEvent.change(screen.getByPlaceholderText('昵称'), {
      target: { value: '新用户' },
    });
    fireEvent.change(screen.getByPlaceholderText('至少 8 位密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    await waitFor(() => {
      expect(mocks.registerByMobile).toHaveBeenCalledWith({
        mobile: '13800000000',
        verificationCode: '123456',
        nickname: '新用户',
        password: 'Password123',
      });
    });
    expect(mocks.fetchUserInfo).toHaveBeenCalled();
    expect(mocks.setInitialState).toHaveBeenCalled();
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });
});
