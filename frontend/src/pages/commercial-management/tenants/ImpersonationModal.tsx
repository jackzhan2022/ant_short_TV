import { Alert, Button, Form, Input, Modal, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { startTenantMemberImpersonation } from '@/services/account-team/impersonation';
import AuthenticatorBinding from './AuthenticatorBinding';
import { useStyles } from './index.style';
import { getAdminAuthenticator, grantErrorMessage } from './grantService';
import type { PlatformTenantMember, PlatformTenantSummary } from './service';

type Values = { reason: string; code: string };
type Props = {
  tenant: PlatformTenantSummary;
  member: PlatformTenantMember;
  onClose: () => void;
};

export default function ImpersonationModal({ tenant, member, onClose }: Props) {
  const { styles } = useStyles();
  const [form] = Form.useForm<Values>();
  const [ready, setReady] = useState(false);
  const [bound, setBound] = useState(false);
  const [justBound, setJustBound] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const submitting = useRef(false);

  useEffect(() => {
    let active = true;
    setReady(false);
    setError(undefined);
    getAdminAuthenticator()
      .then((response) => {
        if (!active) return;
        setBound(response.data.bound);
        setReady(true);
      })
      .catch((failure) => {
        if (active) setError(grantErrorMessage(failure));
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const submit = async (values: Values) => {
    if (
      submitting.current ||
      !ready ||
      !bound ||
      !member.canImpersonate ||
      tenant.status !== 'ACTIVE'
    )
      return;
    submitting.current = true;
    setBusy(true);
    setError(undefined);
    try {
      await startTenantMemberImpersonation(
        tenant.id,
        member.memberId,
        {
          code: values.code,
          reason: values.reason.trim(),
        },
        { tenantName: tenant.name, userName: member.nickname || member.mobile || `成员 #${member.memberId}` },
      );
    } catch (failure) {
      setError(grantErrorMessage(failure));
      form.setFieldValue('code', '');
      setBusy(false);
      submitting.current = false;
    }
  };

  return (
    <Modal
      open
      title="切换登录"
      centered
      width={560}
      className={styles.grantModal}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
      onOk={() => form.submit()}
      okText="确认切换登录"
      cancelText="取消"
      confirmLoading={busy}
      footer={ready && bound ? undefined : null}
      okButtonProps={{
        disabled: tenant.status !== 'ACTIVE' || !member.canImpersonate,
      }}
      cancelButtonProps={{ disabled: busy }}
      closable={!busy}
      keyboard={!busy}
      mask={{ closable: false }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <strong>
          租户：{tenant.name}（{tenant.code}）
        </strong>
        <span>
          目标成员：{member.nickname || '-'}（{member.mobile || '-'}）
        </span>
        <Alert
          type="warning"
          showIcon
          title="切换后将以该成员的实际权限操作。"
          description="可以修改数据、创建任务；相关任务将消耗目标租户积分。操作会记录管理员身份及切换原因，可通过页面顶部返回管理员。"
        />
        {error && <Alert type="error" showIcon title={error} />}
        {!ready && !error && <span>正在验证管理员身份…</span>}
        {!ready && error && (
          <Button onClick={() => setAttempt((value) => value + 1)}>
            重新加载
          </Button>
        )}
        {ready && !bound && (
          <AuthenticatorBinding
            description="切换登录前，需要使用本人验证器进行身份验证。"
            onBound={() => {
              setBound(true);
              setJustBound(true);
            }}
          />
        )}
        {ready && bound && (
          <>
            {justBound && (
              <Alert
                type="success"
                showIcon
                title="绑定成功，请等待验证器生成新的验证码后再切换登录。"
              />
            )}
            <Form
              form={form}
              layout="vertical"
              onFinish={submit}
              disabled={busy}
              style={{ width: '100%' }}
            >
              <Form.Item
                name="reason"
                label="切换原因"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    max: 500,
                    message: '请填写切换原因（最多 500 字）',
                  },
                ]}
              >
                <Input.TextArea
                  rows={3}
                  maxLength={500}
                  showCount
                  placeholder="例如：排查成员反馈的任务问题"
                />
              </Form.Item>
              <Form.Item
                name="code"
                label="验证器验证码"
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
                  placeholder="输入本人验证器的 6 位验证码"
                />
              </Form.Item>
            </Form>
          </>
        )}
      </Space>
    </Modal>
  );
}
