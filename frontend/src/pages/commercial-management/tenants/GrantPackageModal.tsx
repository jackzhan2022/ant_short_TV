import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { formatPoints } from '@/utils/points';
import AuthenticatorBinding from './AuthenticatorBinding';
import {
  getAdminAuthenticator,
  getTenantGrantOptions,
  grantErrorMessage,
  grantTenantPackage,
  type GrantOption,
} from './grantService';
import type { PlatformTenantSummary } from './service';

type GrantValues = { packageVersionId: number; reason: string; code: string };
type Props = {
  tenant: PlatformTenantSummary;
  onClose: () => void;
  onGranted: () => void;
};

export default function GrantPackageModal({
  tenant,
  onClose,
  onGranted,
}: Props) {
  const [form] = Form.useForm<GrantValues>();
  const [options, setOptions] = useState<GrantOption[]>([]);
  const [bound, setBound] = useState(false);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [justBound, setJustBound] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [type, setType] = useState<GrantOption['packageType']>('SUBSCRIPTION');
  const submission = useRef<{ fingerprint: string; id: string } | undefined>(
    undefined,
  );
  const submitting = useRef(false);
  const versionId = Form.useWatch('packageVersionId', form);
  const selected = options.find((item) => item.packageVersionId === versionId);

  useEffect(() => {
    let active = true;
    setReady(false);
    setError(undefined);
    Promise.all([getAdminAuthenticator(), getTenantGrantOptions()])
      .then(([auth, catalog]) => {
        if (!active) return;
        setBound(auth.data.bound);
        setOptions(catalog.data);
        setReady(true);
      })
      .catch((failure) => {
        if (active) setError(grantErrorMessage(failure));
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const submit = async (values: GrantValues) => {
    if (submitting.current || !ready || !bound) return;
    submitting.current = true;
    setBusy(true);
    setError(undefined);
    const reason = values.reason.trim();
    const fingerprint = JSON.stringify([
      tenant.id,
      values.packageVersionId,
      reason,
    ]);
    if (submission.current?.fingerprint !== fingerprint) {
      submission.current = { fingerprint, id: crypto.randomUUID() };
    }
    try {
      await grantTenantPackage(tenant.id, {
        ...values,
        reason,
        requestId: submission.current.id,
      });
      onGranted();
    } catch (failure) {
      setError(grantErrorMessage(failure));
      form.setFieldValue('code', '');
    } finally {
      setBusy(false);
      submitting.current = false;
    }
  };

  const effect = !selected
    ? ''
    : selected.packageType === 'POINT_PACKAGE'
      ? '积分立即到账，不改变当前会员套餐。'
      : tenant.currentPackage?.packageVersionId === selected.packageVersionId
        ? `当前套餐续期 ${selected.periodMonths} 个月，沿用周期积分发放规则。`
        : tenant.currentPackage && tenant.currentPackage.packageType !== 'TRIAL'
          ? `立即切换为“${selected.name}”，有效期 ${selected.periodMonths} 个月；原套餐尚未发完的周期积分仍按原计划发放。`
          : `立即开通“${selected.name}”，有效期 ${selected.periodMonths} 个月。`;

  return (
    <Modal
      open
      title="添加套餐/积分包"
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
      onOk={() => form.submit()}
      okText="确认发放"
      cancelText="取消"
      confirmLoading={busy}
      footer={ready && bound ? undefined : null}
      okButtonProps={{ disabled: !selected || tenant.status !== 'ACTIVE' }}
      cancelButtonProps={{ disabled: busy }}
      closable={!busy}
      keyboard={!busy}
      mask={{ closable: false }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Typography.Text strong>
          租户：{tenant.name}（{tenant.code}）
        </Typography.Text>
        {error && <Alert type="error" showIcon title={error} />}
        {!ready && !error && (
          <Typography.Text>正在加载发放信息…</Typography.Text>
        )}
        {!ready && error && (
          <Button onClick={() => setAttempt((value) => value + 1)}>
            重新加载
          </Button>
        )}
        {ready && !bound && (
          <AuthenticatorBinding
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
                title="绑定成功，请等待验证器生成新的验证码后再发放。"
              />
            )}
            <Alert
              type="info"
              showIcon
              title="管理员免费发放，无需租户付款。每次提交需要本人验证器验证码。"
            />
            <Form
              form={form}
              layout="vertical"
              onFinish={submit}
              disabled={busy}
              style={{ width: '100%' }}
            >
              <Form.Item label="发放类型">
                <Select
                  aria-label="发放类型"
                  value={type}
                  onChange={(value) => {
                    setType(value);
                    form.setFieldValue('packageVersionId', undefined);
                  }}
                  options={[
                    { label: '会员套餐', value: 'SUBSCRIPTION' },
                    { label: '积分包', value: 'POINT_PACKAGE' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="packageVersionId"
                label="发放商品"
                rules={[{ required: true, message: '请选择发放商品' }]}
              >
                <Select
                  placeholder="请选择已发布商品"
                  notFoundContent="暂无可发放商品"
                  options={options
                    .filter((item) => item.packageType === type)
                    .map((item) => ({
                      value: item.packageVersionId,
                      label: `${item.name}${item.packageType === 'SUBSCRIPTION' ? `（${item.periodMonths} 个月）` : ''}`,
                    }))}
                />
              </Form.Item>
              {selected && (
                <Space
                  orientation="vertical"
                  size={4}
                  style={{ marginBottom: 16 }}
                >
                  {selected.entitlements.map((item) => (
                    <Typography.Text key={item.type}>
                      {item.type === 'ONE_TIME_POINTS'
                        ? `一次性积分：${formatPoints(item.value ?? 0)}`
                        : item.type === 'PERIODIC_POINTS'
                          ? `每月积分：${formatPoints(item.value ?? 0)}`
                          : item.type === 'GLOBAL_DISCOUNT'
                            ? `AI 积分 ${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format((item.value ?? 1) * 10)} 折`
                            : (item.name ?? item.type)}
                    </Typography.Text>
                  ))}
                  <Alert type="warning" showIcon title={effect} />
                </Space>
              )}
              <Form.Item
                name="reason"
                label="发放原因"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    max: 500,
                    message: '请填写发放原因（最多 500 字）',
                  },
                ]}
              >
                <Input.TextArea
                  rows={3}
                  maxLength={500}
                  showCount
                  placeholder="例如：客户补偿、运营赠送"
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
