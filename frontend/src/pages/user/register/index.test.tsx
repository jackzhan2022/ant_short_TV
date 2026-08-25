import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Register from './index';

const mocks = vi.hoisted(() => ({
  fetchUserInfo: vi.fn(),
  acceptInvitation: vi.fn(),
  createTenant: vi.fn(),
  registerByMobile: vi.fn(),
  queryAuthBootstrap: vi.fn(),
  replace: vi.fn(),
  setInitialState: vi.fn(),
  setCurrentTenantId: vi.fn(),
  success: vi.fn(),
  switchTenant: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  LockOutlined: () => <span />,
  MobileOutlined: () => <span />,
  UserOutlined: () => <span />,
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
        const fieldValue = (name: string) =>
          (
            currentTarget.querySelector(
              `input[name="${name}"]`,
            ) as HTMLInputElement | null
          )?.value;
        const values = Object.fromEntries(
          Array.from(currentTarget.querySelectorAll('input')).map((input) => [
            input.name,
            input.value,
          ]),
        );
        onFinish?.({
          mobile: fieldValue('mobile'),
          verificationCode: fieldValue('verificationCode'),
          nickname: fieldValue('nickname'),
          password: fieldValue('password'),
          ...values,
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
    <button
      type={htmlType === 'submit' ? 'submit' : 'button'}
      onClick={onClick}
    >
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
  queryAuthBootstrap: mocks.queryAuthBootstrap,
  registerByMobile: mocks.registerByMobile,
  setCurrentTenantId: mocks.setCurrentTenantId,
}));

vi.mock('@/services/account-team/invitation', () => ({
  acceptInvitation: mocks.acceptInvitation,
}));

vi.mock('@/services/account-team/tenant', () => ({
  createTenant: mocks.createTenant,
  switchTenant: mocks.switchTenant,
}));

describe('Register Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.registerByMobile.mockResolvedValue({ success: true, data: {} });
    mocks.queryAuthBootstrap.mockResolvedValue({
      data: {
        user: { id: 1, mobile: '13800000000', nickname: '新用户', status: 'ACTIVE' },
        session: { sessionId: 'session-1', expiresAt: '2026-09-01T00:00:00' },
        platform: { roles: [], permissions: [] },
        tenants: [],
        selectedTenant: null,
        nextAction: 'CREATE_OR_JOIN_TEAM',
      },
    });
    mocks.fetchUserInfo.mockResolvedValue({ name: '新用户' });
    mocks.acceptInvitation.mockResolvedValue({
      success: true,
      data: { tenantId: 20 },
    });
    mocks.createTenant.mockResolvedValue({
      success: true,
      data: { id: 10, name: '新团队' },
    });
    mocks.switchTenant.mockResolvedValue({ success: true });
  });

  it('shows a completion step after registration before entering team pages', async () => {
    render(<Register />);

    fireEvent.change(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入验证码'), {
      target: { value: '123456' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入昵称'), {
      target: { value: '新用户' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入至少8位密码'), {
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
    expect(mocks.queryAuthBootstrap).toHaveBeenCalledWith(undefined, {
      skipErrorHandler: true,
    });
    expect(mocks.fetchUserInfo).not.toHaveBeenCalled();
    expect(mocks.setInitialState).toHaveBeenCalled();
    expect(await screen.findByText('开启您的AI创作之旅')).toBeInTheDocument();
    expect(screen.getByText('完善注册信息')).toBeInTheDocument();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it('creates a team from the completion step before entering workspace', async () => {
    render(<Register />);

    fireEvent.change(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入验证码'), {
      target: { value: '123456' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入昵称'), {
      target: { value: '新用户' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入至少8位密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    await screen.findByText('完善注册信息');

    mocks.queryAuthBootstrap.mockResolvedValueOnce({
      data: {
        user: { id: 1, mobile: '13800000000', nickname: '新用户', status: 'ACTIVE' },
        session: { sessionId: 'session-1', expiresAt: '2026-09-01T00:00:00' },
        platform: { roles: [], permissions: [] },
        tenants: [{ id: 10, name: '新团队' }],
        selectedTenant: {
          tenant: { id: 10, name: '新团队' },
          membership: { id: 100, memberType: 'OWNER', status: 'ACTIVE' },
          roles: ['OWNER'],
          permissions: ['PROJECT:CREATE'],
        },
        nextAction: 'ENTER_WORKSPACE',
      },
    });

    fireEvent.change(screen.getByPlaceholderText('可自定义您的团队名称（选填）'), {
      target: { value: '新团队' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始创作' }));

    await waitFor(() => {
      expect(mocks.createTenant).toHaveBeenCalledWith({
        name: '新团队',
        type: 'STUDIO',
      });
    });
    expect(mocks.queryAuthBootstrap).toHaveBeenLastCalledWith(10, {
      skipErrorHandler: true,
    });
    expect(mocks.switchTenant).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(10);
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });

  it('can skip registration completion and enter team pages', async () => {
    render(<Register />);

    fireEvent.change(screen.getByPlaceholderText('请输入手机号'), {
      target: { value: '13800000000' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入验证码'), {
      target: { value: '123456' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入昵称'), {
      target: { value: '新用户' },
    });
    fireEvent.change(screen.getByPlaceholderText('请输入至少8位密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '注册' }));

    await screen.findByText('完善注册信息');

    fireEvent.click(screen.getByRole('button', { name: '跳过' }));

    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
  });

  it('renders the same video-and-form layout as login', () => {
    render(<Register />);

    expect(screen.getByTestId('login-background-video')).toHaveAttribute(
      'src',
      'https://zy-dimnx.oss-cn-shenzhen.aliyuncs.com/posters/loginVideo.mp4',
    );
  });
});
