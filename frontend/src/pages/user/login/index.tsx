import { LockOutlined, MobileOutlined } from '@ant-design/icons';
import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { Helmet, Link, SelectLang, history, useIntl, useModel } from '@umijs/max';
import { App } from 'antd';
import React, { startTransition } from 'react';
import { Footer } from '@/components';
import {
  loginByMobile,
  saveAuthSession,
} from '@/services/account-team/auth';
import { switchTenant } from '@/services/account-team/tenant';
import type { AuthSession } from '@/services/account-team/types';
import Settings from '../../../../config/defaultSettings';

const loginPath = '/user/login';

const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/') || redirect.startsWith('//')) return '/team/my';

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
    const response = await loginByMobile(values);
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
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        background:
          "url('https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/V-_oS6r-i7wAAAAAAAAAAAAAFl94AQBr') center / cover",
      }}
    >
      <Helmet>
        <title>
          {intl.formatMessage({ id: 'menu.login', defaultMessage: '登录' })}
          {Settings.title && ` - ${Settings.title}`}
        </title>
      </Helmet>
      <div style={{ position: 'fixed', right: 16, top: 16 }}>
        <SelectLang />
      </div>
      <div style={{ flex: 1, padding: '48px 0' }}>
        <LoginForm
          logo={<img alt="logo" src="/logo.svg" />}
          title="Ant Short TV"
          subTitle="短剧制作 SaaS 平台"
          onFinish={async (values) => {
            await handleSubmit(values as { mobile: string; password: string });
          }}
        >
          <ProFormText
            name="mobile"
            fieldProps={{
              size: 'large',
              prefix: <MobileOutlined />,
            }}
            placeholder="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '手机号格式错误' },
            ]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{
              size: 'large',
              prefix: <LockOutlined />,
            }}
            placeholder="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          />
          <div style={{ textAlign: 'right' }}>
            <Link to="/user/register">注册新用户</Link>
          </div>
        </LoginForm>
      </div>
      <Footer />
    </div>
  );
};

export default Login;
