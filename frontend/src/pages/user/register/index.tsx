import { LockOutlined, MobileOutlined, UserOutlined } from '@ant-design/icons';
import {
  LoginForm,
  ProFormCaptcha,
  ProFormText,
} from '@ant-design/pro-components';
import { Helmet, Link, SelectLang, history, useIntl, useModel } from '@umijs/max';
import { App } from 'antd';
import { startTransition, type FC } from 'react';
import { Footer } from '@/components';
import { registerByMobile } from '@/services/account-team/auth';
import Settings from '../../../../config/defaultSettings';

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
          {intl.formatMessage({ id: 'menu.register', defaultMessage: '注册' })}
          {Settings.title && ` - ${Settings.title}`}
        </title>
      </Helmet>
      <div style={{ position: 'fixed', right: 16, top: 16 }}>
        <SelectLang />
      </div>
      <div style={{ flex: 1, padding: '48px 0' }}>
        <LoginForm
          logo={<img alt="logo" src="/logo.svg" />}
          title="注册账号"
          subTitle="创建平台账号后即可创建或加入创作团队"
          submitter={{
            searchConfig: {
              submitText: '注册',
            },
          }}
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
        >
          <ProFormText
            name="mobile"
            fieldProps={{ size: 'large', prefix: <MobileOutlined /> }}
            placeholder="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '手机号格式错误' },
            ]}
          />
          <ProFormCaptcha
            name="verificationCode"
            fieldProps={{ size: 'large', prefix: <LockOutlined /> }}
            captchaProps={{ size: 'large' }}
            placeholder="验证码"
            rules={[{ required: true, message: '请输入验证码' }]}
            onGetCaptcha={async () => {
              message.success('验证码为：123456');
            }}
          />
          <ProFormText
            name="nickname"
            fieldProps={{ size: 'large', prefix: <UserOutlined /> }}
            placeholder="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{ size: 'large', prefix: <LockOutlined /> }}
            placeholder="至少 8 位密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码至少 8 位' },
            ]}
          />
          <div style={{ textAlign: 'right' }}>
            <Link to="/user/login">使用已有账户登录</Link>
          </div>
        </LoginForm>
      </div>
      <Footer />
    </div>
  );
};

export default Register;
