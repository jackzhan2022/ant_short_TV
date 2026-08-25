import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AuthSession } from '@/services/account-team/types';
import Login from './index';

const mocks = vi.hoisted(() => ({
  fetchUserInfo: vi.fn(),
  loginByMobile: vi.fn(),
  queryAuthBootstrap: vi.fn(),
  queryCurrentPermissions: vi.fn(),
  replace: vi.fn(),
  saveAuthSession: vi.fn(),
  setInitialState: vi.fn(),
  setCurrentTenantId: vi.fn(),
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

  const Button = ({ children, htmlType, onClick }: any) => (
    <button type={htmlType === 'submit' ? 'submit' : 'button'} onClick={onClick}>
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
  queryAuthBootstrap: mocks.queryAuthBootstrap,
  saveAuthSession: mocks.saveAuthSession,
  setCurrentTenantId: mocks.setCurrentTenantId,
}));

vi.mock('@/services/account-team/rbac', () => ({
  queryCurrentPermissions: mocks.queryCurrentPermissions,
}));

vi.mock('@/services/account-team/tenant', () => ({
  switchTenant: mocks.switchTenant,
}));

const session: AuthSession = {
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
  expiresAt: '2026-09-01T00:00:00',
};

const secondTenant = {
  ...session.tenants[0],
  id: 11,
  code: 'T0000002',
  name: '第二团队',
  type: 'COMPANY' as const,
  memberId: 101,
  memberType: 'MEMBER' as const,
};

const multipleTenantBootstrap = (selected = false) => ({
  user: session.user,
  session: { sessionId: 'session-1', expiresAt: session.expiresAt },
  platform: { roles: [], permissions: [] },
  tenants: [session.tenants[0], secondTenant],
  selectedTenant: selected
    ? {
        tenant: secondTenant,
        membership: { id: 101, memberType: 'MEMBER', status: 'ACTIVE' },
        roles: ['MEMBER'],
        permissions: ['PROJECT:VIEW'],
      }
    : null,
  nextAction: selected ? 'ENTER_WORKSPACE' : 'SELECT_TENANT',
});

describe('Login Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.loginByMobile.mockResolvedValue({ success: true, data: session });
    mocks.switchTenant.mockResolvedValue({ success: true });
    mocks.fetchUserInfo.mockResolvedValue({ name: '测试用户' });
    mocks.queryCurrentPermissions.mockResolvedValue({
      data: { menus: ['PROJECT'], permissions: ['PROJECT:VIEW'] },
    });
    mocks.queryAuthBootstrap.mockResolvedValue({
      data: {
        user: session.user,
        session: { sessionId: 'session-1', expiresAt: session.expiresAt },
        platform: { roles: [], permissions: [] },
        tenants: session.tenants,
        selectedTenant: {
          tenant: session.tenants[0],
          membership: { id: 100, memberType: 'OWNER', status: 'ACTIVE' },
          roles: ['OWNER'],
          permissions: ['PROJECT:VIEW'],
        },
        nextAction: 'ENTER_WORKSPACE',
      },
    });
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
    expect(mocks.queryAuthBootstrap).toHaveBeenCalledWith(undefined, {
      skipErrorHandler: true,
    });
    expect(mocks.switchTenant).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(10);
    expect(mocks.fetchUserInfo).not.toHaveBeenCalled();
    expect(mocks.queryCurrentPermissions).not.toHaveBeenCalled();
    expect(mocks.setInitialState).toHaveBeenCalled();
    expect(mocks.setInitialState.mock.calls[0][0]({})).toMatchObject({
      currentTenantId: 10,
      currentUser: {
        name: '测试用户',
        phone: '13800000000',
      },
      tenantPermissions: ['PROJECT:VIEW'],
    });
    expect(mocks.success).toHaveBeenCalledWith('登录成功');
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });

  it('asks users with multiple teams to choose a team before entering', async () => {
    mocks.queryAuthBootstrap
      .mockResolvedValueOnce({ data: multipleTenantBootstrap() })
      .mockResolvedValueOnce({ data: multipleTenantBootstrap(true) });
    mocks.loginByMobile.mockResolvedValue({
      success: true,
      data: {
        ...session,
        tenants: [
          session.tenants[0],
          {
            id: 11,
            code: 'T0000002',
            name: '第二团队',
            type: 'COMPANY',
            status: 'ACTIVE',
            memberId: 101,
            memberType: 'MEMBER',
          },
        ],
        nextAction: 'SELECT_TENANT',
      },
    });

    render(<Login />);

    fireEvent.input(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.input(screen.getByPlaceholderText('请输入密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('请选择登录团队');

    expect(screen.getByText('当前登录账号可访问2个团队空间，请选择')).toBeInTheDocument();
    expect(screen.getByText('测试团队')).toBeInTheDocument();
    expect(screen.getByText('第二团队')).toBeInTheDocument();
    expect(mocks.replace).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText('第二团队'));
    fireEvent.click(screen.getByRole('button', { name: '立即登录' }));

    await waitFor(() => expect(mocks.queryAuthBootstrap).toHaveBeenLastCalledWith(11, {
      skipErrorHandler: true,
    }));
    expect(mocks.switchTenant).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(11);
    expect(mocks.fetchUserInfo).not.toHaveBeenCalled();
    expect(mocks.queryCurrentPermissions).not.toHaveBeenCalled();
    expect(mocks.setInitialState.mock.calls[0][0]({})).toMatchObject({
      currentTenantId: 11,
      currentUser: {
        name: '测试用户',
        phone: '13800000000',
      },
      tenantPermissions: ['PROJECT:VIEW'],
    });
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });

  it('waits for initial state to apply before navigating after team selection', async () => {
    mocks.queryAuthBootstrap
      .mockResolvedValueOnce({ data: multipleTenantBootstrap() })
      .mockResolvedValueOnce({ data: multipleTenantBootstrap(true) });
    let resolveInitialState: (() => void) | undefined;
    mocks.setInitialState.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveInitialState = resolve;
        }),
    );
    mocks.loginByMobile.mockResolvedValue({
      success: true,
      data: {
        ...session,
        tenants: [
          session.tenants[0],
          {
            id: 11,
            code: 'T0000002',
            name: '第二团队',
            type: 'COMPANY',
            status: 'ACTIVE',
            memberId: 101,
            memberType: 'MEMBER',
          },
        ],
        nextAction: 'SELECT_TENANT',
      },
    });

    render(<Login />);

    fireEvent.input(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.input(screen.getByPlaceholderText('请输入密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '登录' }));

    await screen.findByText('请选择登录团队');

    fireEvent.click(screen.getByText('第二团队'));
    fireEvent.click(screen.getByRole('button', { name: '立即登录' }));

    await waitFor(() => {
      expect(mocks.setInitialState).toHaveBeenCalled();
    });
    expect(mocks.replace).not.toHaveBeenCalled();

    resolveInitialState?.();

    await waitFor(() => {
      expect(mocks.replace).toHaveBeenCalledWith('/team/my');
    });
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
