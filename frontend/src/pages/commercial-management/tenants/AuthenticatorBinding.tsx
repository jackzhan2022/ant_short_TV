import { Alert, Button, Form, Input, QRCode, Space, Typography } from 'antd';
import { useState } from 'react';
import {
  type AuthenticatorEnrollment,
  confirmAdminAuthenticator,
  enrollAdminAuthenticator,
  grantErrorMessage,
} from './grantService';

export default function AuthenticatorBinding({
  onBound,
}: {
  onBound: () => void;
}) {
  const [passwordForm] = Form.useForm<{ password: string }>();
  const [codeForm] = Form.useForm<{ code: string }>();
  const [enrollment, setEnrollment] = useState<AuthenticatorEnrollment>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  const enroll = async ({ password }: { password: string }) => {
    if (busy) return;
    setBusy(true);
    setError(undefined);
    try {
      const response = await enrollAdminAuthenticator(password);
      passwordForm.resetFields();
      setEnrollment(response.data);
    } catch (failure) {
      setError(grantErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  };
  const confirm = async ({ code }: { code: string }) => {
    if (busy) return;
    setBusy(true);
    setError(undefined);
    try {
      await confirmAdminAuthenticator(code);
      codeForm.resetFields();
      setEnrollment(undefined);
      onBound();
    } catch (failure) {
      setError(grantErrorMessage(failure));
      codeForm.resetFields();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        title="请先绑定本人验证器"
        description="发放套餐或积分包前，需要使用本人验证器进行身份验证。"
      />
      {error && <Alert type="error" showIcon title={error} />}
      {!enrollment ? (
        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={enroll}
          disabled={busy}
        >
          <Form.Item
            name="password"
            label="当前登录密码"
            rules={[{ required: true, message: '请输入当前登录密码' }]}
          >
            <Input.Password autoComplete="current-password" maxLength={200} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy}>
            验证密码并绑定
          </Button>
        </Form>
      ) : (
        <>
          <Typography.Text>
            使用验证器应用扫描二维码，或手动输入下方密钥。绑定信息 10
            分钟内有效。
          </Typography.Text>
          <QRCode value={enrollment.uri} type="svg" size={180} />
          <Typography.Text code copyable>
            {enrollment.secret}
          </Typography.Text>
          <Form
            form={codeForm}
            layout="vertical"
            onFinish={confirm}
            disabled={busy}
          >
            <Form.Item
              name="code"
              label="绑定验证码"
              rules={[
                {
                  required: true,
                  pattern: /^[0-9]{6}$/,
                  message: '请输入 6 位验证码',
                },
              ]}
            >
              <Input
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
              />
            </Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={busy}>
                确认绑定
              </Button>
              <Button
                onClick={() => {
                  setEnrollment(undefined);
                  setError(undefined);
                  codeForm.resetFields();
                }}
              >
                重新绑定
              </Button>
            </Space>
          </Form>
        </>
      )}
    </Space>
  );
}
