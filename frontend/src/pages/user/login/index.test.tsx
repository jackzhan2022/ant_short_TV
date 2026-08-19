import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AuthSession } from '@/services/account-team/types';
import Login from './index';

const mocks = vi.hoisted(() => ({
  fetchUserInfo: vi.fn(),
  loginByMobile: vi.fn(),
  replace: vi.fn(),
  saveAuthSession: vi.fn(),
  setInitialState: vi.fn(),
  success: vi.fn(),
  switchTenant: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  Helmet: ({ children }: { children: ReactNode }) => <>{children}</>,
  Link: ({ children, to }: { children: ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
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
            password: formData.get('password'),
          });
        }}
      >
        {children}
        <button type="submit">登录</button>
      </form>
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
  loginByMobile: mocks.loginByMobile,
  saveAuthSession: mocks.saveAuthSession,
}));

vi.mock('@/services/account-team/tenant', () => ({
  switchTenant: mocks.switchTenant,
}));

const session: AuthSession = {
  accessToken: 'token',
  user: {
    id: 1,
    mobile: '13800000000',
    nickname: '测试用户',
    status: 'ACTIVE',
  },
  tenants: [
    {
      id: 10,
      code: 'T0000001',
      name: '测试团队',
      type: 'STUDIO',
      status: 'ACTIVE',
      memberId: 100,
      memberType: 'OWNER',
    },
  ],
  nextAction: 'ENTER_WORKSPACE',
};

describe('Login Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.loginByMobile.mockResolvedValue({ success: true, data: session });
    mocks.switchTenant.mockResolvedValue({ success: true });
    mocks.fetchUserInfo.mockResolvedValue({ name: '测试用户' });
    window.history.replaceState({}, '', '/user/login');
  });

  it('submits mobile and password login', async () => {
    render(<Login />);

    fireEvent.change(screen.getByPlaceholderText('手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.change(screen.getByPlaceholderText('密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await waitFor(() => {
      expect(mocks.loginByMobile).toHaveBeenCalledWith({
        mobile: '13800000000',
        password: 'Password123',
      });
    });
    expect(mocks.switchTenant).toHaveBeenCalledWith(10);
    expect(mocks.saveAuthSession).toHaveBeenCalledWith({
      currentTenantId: 10,
    });
    expect(mocks.success).toHaveBeenCalledWith('登录成功');
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });

  it('shows a register entry that links to the register page', () => {
    render(<Login />);

    const registerLink = screen.getByRole('link', { name: '注册新用户' });

    expect(registerLink).toHaveAttribute('href', '/user/register');
  });

  it('renders the login background video', () => {
    render(<Login />);

    const video = screen.getByTestId('login-background-video');

    expect(video).toHaveAttribute(
      'src',
      'https://zy-dimnx.oss-cn-shenzhen.aliyuncs.com/posters/loginVideo.mp4',
    );
  });
});
