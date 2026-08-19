import { Helmet, Link, history, useIntl, useModel } from '@umijs/max';
import { App, Button, Form, Input } from 'antd';
import React, { startTransition } from 'react';
import { loginByMobile, saveAuthSession } from '@/services/account-team/auth';
import { switchTenant } from '@/services/account-team/tenant';
import type { AuthSession } from '@/services/account-team/types';
import Settings from '../../../../config/defaultSettings';
import AuthPageLayout from '../components/AuthPageLayout';

const loginPath = '/user/login';

const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/') || redirect.startsWith('//'))
    return '/team/my';

  try {
    const parsed = new URL(redirect, window.location.origin);
    if (parsed.origin !== window.location.origin) return '/team/my';
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return '/team/my';
  }
};

const nextPathForSession = (session: AuthSession, redirectUrl: string) => {
  if (session.nextAction === 'SELECT_TENANT') {
    return '/team/select';
  }
  if (session.nextAction === 'CREATE_OR_JOIN_TEAM') {
    return '/team/my';
  }
  return redirectUrl === loginPath ? '/team/my' : redirectUrl;
};

const Login: React.FC = () => {
  const { initialState, setInitialState } = useModel('@@initialState');
  const { message } = App.useApp();
  const intl = useIntl();

  const refreshCurrentUser = async () => {
    const userInfo = await initialState?.fetchUserInfo?.();
    if (userInfo) {
      startTransition(() => {
        setInitialState((state) => ({ ...state, currentUser: userInfo }));
      });
    }
  };

  const handleSubmit = async (values: { mobile: string; password: string }) => {
    const response = await loginByMobile({
      mobile: values.mobile,
      password: values.password,
    });
    const session = response.data;
    if (session.tenants.length === 1) {
      await switchTenant(session.tenants[0].id);
      saveAuthSession({ currentTenantId: session.tenants[0].id });
    }
    await refreshCurrentUser();
    message.success('登录成功');

    const urlParams = new URL(window.location.href).searchParams;
    const redirectUrl = getSafeRedirectUrl(urlParams.get('redirect'));
    history.replace(nextPathForSession(session, redirectUrl));
  };

  return (
    <AuthPageLayout>
      <Helmet>
        <title>
          {intl.formatMessage({ id: 'menu.login', defaultMessage: '登录' })}
          {Settings.title && ` - ${Settings.title}`}
        </title>
      </Helmet>

      <h1
        style={{
          margin: '0 0 28px',
          textAlign: 'center',
          color: '#2b2d33',
          fontSize: 28,
          fontWeight: 700,
          lineHeight: 1.35,
          letterSpacing: 0,
        }}
      >
        开启您的AI创作之旅
      </h1>

      <div
        style={{
          marginBottom: 24,
          color: '#1f2329',
          fontSize: 16,
          lineHeight: 1.5,
          fontWeight: 500,
        }}
      >
        手机登录
      </div>

      <Form
        layout="vertical"
        requiredMark={false}
        onFinish={handleSubmit}
        style={{ width: '100%' }}
      >
        <Form.Item
          name="mobile"
          rules={[
            { required: true, message: '请输入手机号' },
            { pattern: /^1\d{10}$/, message: '手机号格式错误' },
          ]}
          style={{ marginBottom: 20 }}
        >
          <Input
            size="large"
            placeholder="请输入手机号"
            prefix={
              <span style={{ color: '#202124', marginRight: 8 }}>手机号:</span>
            }
            style={{
              height: 48,
              borderRadius: 8,
              background: 'rgba(255, 255, 255, 0.72)',
              borderColor: '#e3e5ee',
              boxShadow: 'none',
              fontSize: 14,
            }}
          />
        </Form.Item>

        <Form.Item
          name="password"
          rules={[{ required: true, message: '请输入密码' }]}
          style={{ marginBottom: 24 }}
        >
          <Input.Password
            size="large"
            placeholder="请输入密码"
            prefix={
              <span style={{ color: '#202124', marginRight: 8 }}>密码:</span>
            }
            style={{
              height: 48,
              borderRadius: 8,
              background: 'rgba(255, 255, 255, 0.72)',
              borderColor: '#e3e5ee',
              boxShadow: 'none',
              fontSize: 14,
            }}
          />
        </Form.Item>

        <Button
          type="primary"
          htmlType="submit"
          block
          size="large"
          style={{
            height: 48,
            borderRadius: 8,
            border: 0,
            background: 'linear-gradient(90deg, #c498f4 0%, #9c8bf1 100%)',
            boxShadow: 'none',
            fontSize: 16,
            fontWeight: 600,
          }}
        >
          登录
        </Button>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            marginTop: 10,
            color: '#8f949e',
            fontSize: 12,
            lineHeight: 1.7,
          }}
        >
          <span>
            登录即代表已阅读并同意&nbsp;
            <Link to="/docs/user-agreement" style={{ color: '#7a37ff' }}>
              《用户协议》
            </Link>
            &nbsp;和&nbsp;
            <Link to="/docs/privacy-policy" style={{ color: '#7a37ff' }}>
              《隐私政策》
            </Link>
            ，未注册手机号将自动注册
          </span>
        </div>

        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Link to="/user/register" style={{ color: '#202124', fontSize: 14 }}>
            注册账号
          </Link>
        </div>
      </Form>
    </AuthPageLayout>
  );
};

export default Login;
