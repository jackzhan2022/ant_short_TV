import { Helmet, Link, history, useIntl, useModel } from '@umijs/max';
import { App, Button, Form, Input } from 'antd';
import { useState, type FC } from 'react';
import {
  queryAuthBootstrap,
  registerByMobile,
  setCurrentTenantId,
} from '@/services/account-team/auth';
import { toBootstrapState } from '@/services/account-team/bootstrap';
import { acceptInvitation } from '@/services/account-team/invitation';
import { createTenant } from '@/services/account-team/tenant';
import Settings from '../../../../config/defaultSettings';
import AuthPageLayout from '../components/AuthPageLayout';

const Register: FC = () => {
  const { setInitialState } = useModel('@@initialState');
  const { message } = App.useApp();
  const intl = useIntl();
  const [registered, setRegistered] = useState(false);
  const [completing, setCompleting] = useState(false);

  const applyBootstrap = async (tenantId?: number) => {
    const bootstrap = (
      await queryAuthBootstrap(tenantId, { skipErrorHandler: true })
    ).data;
    if (tenantId && bootstrap.selectedTenant?.tenant.id !== tenantId) {
      throw new Error(
        bootstrap.unavailableSelectionReason || 'Selected tenant is unavailable',
      );
    }
    await setInitialState((state) => ({ ...state, ...toBootstrapState(bootstrap) }));
    if (bootstrap.selectedTenant?.tenant.id) {
      setCurrentTenantId(bootstrap.selectedTenant.tenant.id);
    }
  };

  const enterTeamPages = () => {
    history.replace('/team/my');
  };

  const handleComplete = async (values: {
    teamName?: string;
    invitationCode?: string;
  }) => {
    const teamName = values.teamName?.trim();
    const invitationCode = values.invitationCode?.trim();

    setCompleting(true);
    try {
      if (invitationCode) {
        const response = await acceptInvitation(invitationCode);
        await applyBootstrap(response.data.tenantId);
        message.success('已加入团队');
      } else if (teamName) {
        const response = await createTenant({ name: teamName, type: 'STUDIO' });
        await applyBootstrap(response.data.id);
        message.success('团队创建成功');
      }
      enterTeamPages();
    } finally {
      setCompleting(false);
    }
  };

  if (registered) {
    return (
      <AuthPageLayout>
        <Helmet>
          <title>
            完善注册信息
            {Settings.title && ` - ${Settings.title}`}
          </title>
        </Helmet>

        <h1
          style={{
            margin: '0 0 52px',
            textAlign: 'center',
            color: '#252830',
            fontSize: 28,
            fontWeight: 800,
            lineHeight: 1.35,
            letterSpacing: 0,
          }}
        >
          开启您的AI创作之旅
        </h1>

        <div
          style={{
            marginBottom: 22,
            color: '#252830',
            fontSize: 16,
            lineHeight: 1.5,
            fontWeight: 700,
          }}
        >
          完善注册信息
        </div>

        <Form
          layout="vertical"
          requiredMark={false}
          onFinish={handleComplete}
          style={{ width: '100%' }}
        >
          <Form.Item name="teamName" style={{ marginBottom: 20 }}>
            <Input
              size="large"
              placeholder="可自定义您的团队名称（选填）"
              prefix={
                <span style={{ color: '#202124', marginRight: 8 }}>
                  团队名称:
                </span>
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

          <Form.Item name="invitationCode" style={{ marginBottom: 42 }}>
            <Input
              size="large"
              placeholder="若有邀请码，可填写（选填）"
              prefix={
                <span style={{ color: '#202124', marginRight: 8 }}>
                  邀请码:
                </span>
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
            loading={completing}
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
            开始创作
          </Button>

          <Button
            type="text"
            block
            size="large"
            onClick={enterTeamPages}
            style={{
              height: 46,
              marginTop: 14,
              color: '#8f949e',
              fontSize: 16,
              fontWeight: 500,
            }}
          >
            跳过
          </Button>
        </Form>
      </AuthPageLayout>
    );
  }

  return (
    <AuthPageLayout>
      <Helmet>
        <title>
          {intl.formatMessage({ id: 'menu.register', defaultMessage: '注册' })}
          {Settings.title && ` - ${Settings.title}`}
        </title>
      </Helmet>
      <h1
        style={{
          margin: '0 0 8px',
          textAlign: 'center',
          color: '#2b2d33',
          fontSize: 28,
          fontWeight: 700,
          lineHeight: 1.35,
          letterSpacing: 0,
        }}
      >
        注册账号
      </h1>

      <div
        style={{
          marginBottom: 28,
          textAlign: 'center',
          color: '#6b7280',
          fontSize: 14,
          lineHeight: 1.5,
        }}
      >
        创建平台账号后即可创建或加入创作团队
      </div>

      <div
        style={{
          marginBottom: 24,
          color: '#1f2329',
          fontSize: 16,
          lineHeight: 1.5,
          fontWeight: 500,
        }}
      >
        注册账号
      </div>

      <Form
        layout="vertical"
        requiredMark={false}
        onFinish={async (values) => {
          const params = values as {
            mobile: string;
            verificationCode: string;
            nickname: string;
            password: string;
          };
          await registerByMobile(params);
          await applyBootstrap();
          message.success('注册成功');
          setRegistered(true);
        }}
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
          name="verificationCode"
          rules={[{ required: true, message: '请输入验证码' }]}
          style={{ marginBottom: 20 }}
        >
          <Input
            size="large"
            placeholder="请输入验证码"
            prefix={
              <span style={{ color: '#202124', marginRight: 8 }}>验证码:</span>
            }
            suffix={
              <Button
                type="text"
                size="small"
                style={{
                  height: 28,
                  padding: '0 0 0 12px',
                  color: '#b7b7bd',
                  fontSize: 14,
                }}
                onClick={() => message.success('验证码为：123456')}
              >
                获取验证码
              </Button>
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
          name="nickname"
          rules={[{ required: true, message: '请输入昵称' }]}
          style={{ marginBottom: 20 }}
        >
          <Input
            size="large"
            placeholder="请输入昵称"
            prefix={
              <span style={{ color: '#202124', marginRight: 8 }}>昵称:</span>
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
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]}
          style={{ marginBottom: 24 }}
        >
          <Input.Password
            size="large"
            placeholder="请输入至少8位密码"
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
          注册
        </Button>

        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Link to="/user/login" style={{ color: '#202124', fontSize: 14 }}>
            使用已有账户登录
          </Link>
        </div>
      </Form>
    </AuthPageLayout>
  );
};

export default Register;
