import { Helmet, Link, history, useIntl, useModel } from '@umijs/max';
import { App, Button, Form, Input } from 'antd';
import { startTransition, type FC } from 'react';
import { registerByMobile } from '@/services/account-team/auth';
import Settings from '../../../../config/defaultSettings';
import AuthPageLayout from '../components/AuthPageLayout';

const Register: FC = () => {
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
          await refreshCurrentUser();
          message.success('注册成功');
          history.replace('/team/my');
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
