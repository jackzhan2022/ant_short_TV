import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
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

vi.mock('antd', () => {
  const Form = ({ children, onFinish }: any) => (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        const currentTarget = event.currentTarget as HTMLFormElement;
        const mobileInput = currentTarget.querySelector(
          'input[name="mobile"]',
        ) as HTMLInputElement | null;
        const passwordInput = currentTarget.querySelector(
          'input[name="password"]',
        ) as HTMLInputElement | null;
        onFinish?.({
          mobile: mobileInput?.value,
          password: passwordInput?.value,
        });
      }}
    >
      {children}
    </form>
  );
  Form.Item = ({ children, name }: any) =>
    React.cloneElement(children, { name });

  const Input = ({ name, placeholder, prefix, suffix }: any) => {
    const [value, setValue] = React.useState('');
    return (
      <label>
        {prefix}
        <input
          aria-label={placeholder}
          name={name}
          placeholder={placeholder}
          value={value}
          onChange={(event) => setValue(event.target.value)}
        />
        {suffix}
      </label>
    );
  };
  Input.Password = ({ name, placeholder, prefix }: any) => {
    const [value, setValue] = React.useState('');
    return (
      <label>
        {prefix}
        <input
          aria-label={placeholder}
          name={name}
          placeholder={placeholder}
          type="password"
          value={value}
          onChange={(event) => setValue(event.target.value)}
        />
      </label>
    );
  };

  const Button = ({ children, htmlType }: any) => (
    <button type={htmlType === 'submit' ? 'submit' : 'button'}>
      {children}
    </button>
  );

  return {
    App: {
      useApp: () => ({ message: { success: mocks.success } }),
    },
    Button,
    Form,
    Input,
  };
});

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

    fireEvent.input(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.input(screen.getByPlaceholderText('请输入密码'), {
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

  it('shows a register entry', () => {
    render(<Login />);

    const registerLink = screen.getByRole('link', { name: '注册账号' });

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
