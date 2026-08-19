import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
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
        onFinish?.({
          mobile: fieldValue('mobile'),
          verificationCode: fieldValue('verificationCode'),
          nickname: fieldValue('nickname'),
          password: fieldValue('password'),
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
    expect(mocks.fetchUserInfo).toHaveBeenCalled();
    expect(mocks.setInitialState).toHaveBeenCalled();
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
