import { Helmet, Link, history, useIntl, useModel } from '@umijs/max';
import { App, Button, Form, Input } from 'antd';
import React, { useState } from 'react';
import {
  loginByMobile,
  queryAuthBootstrap,
  setCurrentTenantId,
} from '@/services/account-team/auth';
import { toBootstrapState } from '@/services/account-team/bootstrap';
import type {
  AuthBootstrap,
  AuthSession,
  TenantSummary,
} from '@/services/account-team/types';
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
  const { setInitialState } = useModel('@@initialState');
  const { message } = App.useApp();
  const intl = useIntl();
  const [pendingSession, setPendingSession] = useState<AuthBootstrap>();
  const [selectedTenantId, setSelectedTenantId] = useState<number>();
  const [selectingTenant, setSelectingTenant] = useState(false);

  const applyAuthenticatedState = async (
    bootstrap: AuthBootstrap,
    expectedTenantId?: number,
  ) => {
    const selectedTenantId = bootstrap.selectedTenant?.tenant.id;
    if (expectedTenantId && selectedTenantId !== expectedTenantId) {
      throw new Error(
        bootstrap.unavailableSelectionReason || 'Selected tenant is unavailable',
      );
    }
    await setInitialState((state) => ({
      ...state,
      ...toBootstrapState(bootstrap),
    }));
    if (selectedTenantId) setCurrentTenantId(selectedTenantId);
  };

  const handleSubmit = async (values: { mobile: string; password: string }) => {
    const response = await loginByMobile({
      mobile: values.mobile,
      password: values.password,
    });
    void response;
    const bootstrap = (
      await queryAuthBootstrap(undefined, { skipErrorHandler: true })
    ).data;
    if (bootstrap.nextAction === 'SELECT_TENANT' && bootstrap.tenants.length > 1) {
      setPendingSession(bootstrap);
      setSelectedTenantId(bootstrap.tenants[0].id);
      message.success('登录成功');
      return;
    }
    await applyAuthenticatedState(bootstrap);
    message.success('登录成功');

    const urlParams = new URL(window.location.href).searchParams;
    const redirectUrl = getSafeRedirectUrl(urlParams.get('redirect'));
    history.replace(nextPathForSession(response.data, redirectUrl));
  };

  const handleEnterSelectedTenant = async () => {
    if (!pendingSession || !selectedTenantId) return;

    setSelectingTenant(true);
    try {
      const bootstrap = (
        await queryAuthBootstrap(selectedTenantId, { skipErrorHandler: true })
      ).data;
      await applyAuthenticatedState(bootstrap, selectedTenantId);
      message.success('登录成功');

      const urlParams = new URL(window.location.href).searchParams;
      const redirectUrl = getSafeRedirectUrl(urlParams.get('redirect'));
      history.replace(redirectUrl === loginPath ? '/team/my' : redirectUrl);
    } finally {
      setSelectingTenant(false);
    }
  };

  const renderTenantCard = (tenant: TenantSummary) => {
    const active = tenant.id === selectedTenantId;
    return (
      <button
        key={tenant.id}
        type="button"
        onClick={() => setSelectedTenantId(tenant.id)}
        style={{
          width: '100%',
          minHeight: 86,
          padding: '18px 22px',
          border: active ? '1px solid transparent' : '1px solid #e5e7f0',
          borderRadius: 8,
          background: active
            ? 'linear-gradient(90deg, #8b10ff 0%, #b711f4 100%)'
            : '#f8f9fd',
          color: active ? '#fff' : '#252830',
          cursor: 'pointer',
          textAlign: 'left',
          boxShadow: active ? '0 8px 20px rgba(142, 30, 246, 0.16)' : 'none',
        }}
      >
        <div
          style={{
            marginBottom: 12,
            fontSize: 16,
            fontWeight: 700,
            lineHeight: 1.2,
          }}
        >
          {tenant.name}
        </div>
        <div
          style={{
            color: active ? 'rgba(255, 255, 255, 0.82)' : '#777b86',
            fontSize: 14,
            lineHeight: 1.2,
          }}
        >
          租户码: {tenant.code}
        </div>
      </button>
    );
  };

  if (pendingSession) {
    return (
      <AuthPageLayout>
        <Helmet>
          <title>
            选择团队
            {Settings.title && ` - ${Settings.title}`}
          </title>
        </Helmet>

        <button
          type="button"
          onClick={() => setPendingSession(undefined)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            marginBottom: 28,
            padding: 0,
            border: 0,
            background: 'transparent',
            color: '#40424a',
            cursor: 'pointer',
            fontSize: 14,
            lineHeight: 1.5,
          }}
        >
          <span style={{ fontSize: 22, lineHeight: 1 }}>‹</span>
          返回
        </button>

        <h1
          style={{
            margin: '0 0 14px',
            color: '#2b2d33',
            fontSize: 20,
            fontWeight: 700,
            lineHeight: 1.4,
            letterSpacing: 0,
          }}
        >
          请选择登录团队
        </h1>
        <p
          style={{
            margin: '0 0 28px',
            color: '#7b7f8a',
            fontSize: 14,
            lineHeight: 1.6,
          }}
        >
          当前登录账号可访问{pendingSession.tenants.length}个团队空间，请选择
        </p>

        <div
          style={{
            minHeight: 356,
            marginBottom: 24,
            padding: 16,
            borderRadius: 8,
            background: '#fff',
          }}
        >
          <div style={{ display: 'grid', gap: 12 }}>
            {pendingSession.tenants.map(renderTenantCard)}
          </div>
        </div>

        <Button
          type="primary"
          block
          size="large"
          loading={selectingTenant}
          onClick={handleEnterSelectedTenant}
          style={{
            height: 48,
            borderRadius: 8,
            border: 0,
            background: 'linear-gradient(90deg, #b469f3 0%, #6548ef 100%)',
            boxShadow: 'none',
            fontSize: 16,
            fontWeight: 700,
          }}
        >
          立即登录
        </Button>
      </AuthPageLayout>
    );
  }

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
