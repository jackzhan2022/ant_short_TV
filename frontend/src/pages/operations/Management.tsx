import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useEffect, useState } from 'react';
import { time } from './presentation';
import {
  type AlertEvent,
  operationsApi,
  type Rule,
  type RuleInput,
  type Settings,
  type SettingsInput,
} from './service';

dayjs.extend(utc);
dayjs.extend(timezone);

export const ruleMetrics = [
  ['http.errorRate', 'HTTP技术失败率（%）'],
  ['http.p95Ms', 'HTTP近似P95（ms）'],
  ['ai.technicalFailureRate', 'AI技术失败率（%）'],
  ['ai.businessFailureRate', 'AI业务失败率（%）'],
  ['queue.oldestRunnableSeconds', '最老可运行等待（秒）'],
  ['execution.expiredLeases', '过期执行租约数'],
  ['execution.phaseAgeSeconds', '活跃阶段尝试耗时（秒）'],
  ['jobs.stalled', '停滞作业数'],
  ['db.pending', '数据库等待连接数'],
  ['jvm.heapPercent', 'JVM堆占用（%）'],
  ['settlement.overdue', '结算滞留数'],
  ['monitoring.faults', '监控自身故障数'],
].map(([value, label]) => ({ value, label }));
const errorText = (error: unknown) =>
  error instanceof Error ? error.message : '操作失败，请重试';

export function RuleEditor({
  rule,
  open,
  close,
  saved,
}: {
  rule?: Rule;
  open: boolean;
  close: () => void;
  saved: () => void;
}) {
  const [form] = Form.useForm<RuleInput>();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(
        rule ?? {
          name: '',
          description: '',
          enabled: true,
          severity: 'WARNING',
          metric: 'http.errorRate',
          operator: 'GT',
          thresholdValue: 5,
          recoveryThreshold: 2,
          windowMinutes: 5,
          sustainedMinutes: 3,
          minimumSamples: 20,
          cooldownMinutes: 30,
          dimensionsJson: '{}',
          notify: false,
          version: 0,
        },
      );
      setError('');
    }
  }, [rule, open, form]);
  const submit = async () => {
    const values = await form.validateFields().catch(() => undefined);
    if (!values) return;
    setSaving(true);
    setError('');
    try {
      await operationsApi.saveRule(
        { ...values, version: rule?.version ?? 0 },
        rule?.id,
      );
      close();
      saved();
    } catch (error) {
      setError(errorText(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={rule ? '编辑告警规则' : '新增告警规则'}
      open={open}
      onCancel={close}
      onOk={() => {
        void submit();
      }}
      confirmLoading={saving}
      destroyOnHidden
      width={720}
    >
      {error && (
        <Alert
          type="error"
          title={error}
          description="版本冲突时请关闭表单并重新加载规则；当前输入已保留。"
          showIcon
        />
      )}
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="规则名称"
          rules={[{ required: true, max: 120 }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea maxLength={1000} />
        </Form.Item>
        <Space wrap>
          <Form.Item name="enabled" label="启用规则" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="notify" label="发送通知" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="severity" label="级别" rules={[{ required: true }]}>
            <Select
              style={{ width: 150 }}
              options={['WARNING', 'CRITICAL'].map((value) => ({
                value,
                label: value,
              }))}
            />
          </Form.Item>
        </Space>
        <Form.Item
          name="metric"
          label="指标及单位"
          rules={[{ required: true }]}
        >
          <Select options={ruleMetrics} />
        </Form.Item>
        <Form.Item
          name="operator"
          label="触发条件"
          rules={[{ required: true }]}
        >
          <Select
            options={['GT', 'GTE', 'LT', 'LTE'].map((value) => ({
              value,
              label: value,
            }))}
          />
        </Form.Item>
        <div className="grid grid-cols-2 gap-x-4">
          <Form.Item
            name="thresholdValue"
            label="触发阈值"
            rules={[{ required: true }]}
          >
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="recoveryThreshold" label="恢复阈值">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          {(
            [
              ['windowMinutes', '窗口（分钟）', 1, 60],
              ['sustainedMinutes', '持续（分钟）', 1, 1440],
              ['minimumSamples', '最小样本数', 0, 1000000000],
              ['cooldownMinutes', '冷却（分钟）', 1, 1440],
            ] as const
          ).map(([name, label, min, max]) => (
            <Form.Item
              key={name}
              name={name}
              label={label}
              rules={[{ required: true }]}
            >
              <InputNumber
                min={min}
                max={max}
                precision={0}
                style={{ width: '100%' }}
              />
            </Form.Item>
          ))}
        </div>
        <Form.Item
          name="dimensionsJson"
          label="匹配维度（JSON；只允许团队/项目/服务商/模型/场景/实例/阶段/HTTP路由及方法）"
          extra={'阶段耗时指标必填 phase，例如 {"phase":"SUBMIT","scene":"FREE_CREATION"}；route、method 仅用于 HTTP 指标。'}
          dependencies={['metric']}
          rules={[
            {
              validator: async (_, text: string) => {
                const dimensions: unknown = JSON.parse(text);
                if (
                  !dimensions ||
                  typeof dimensions !== 'object' ||
                  Array.isArray(dimensions) ||
                  Object.keys(dimensions).some(
                    (key) =>
                      ![
                        'tenantId',
                        'projectId',
                        'providerId',
                        'modelId',
                        'scene',
                        'instanceId',
                        'phase',
                        'route',
                        'method',
                      ].includes(key),
                  )
                )
                  throw new Error('请输入仅含支持维度的JSON对象');
                if (
                  form.getFieldValue('metric') === 'execution.phaseAgeSeconds' &&
                  (!('phase' in dimensions) ||
                    typeof dimensions.phase !== 'string' ||
                    !dimensions.phase.trim())
                )
                  throw new Error('活跃阶段尝试耗时指标必须填写 phase');
              },
            },
          ]}
        >
          <Input.TextArea rows={2} />
        </Form.Item>
        <Typography.Text type="secondary">
          关闭规则会停止后续评估；确认和静默不代表故障恢复。窗口不足或样本缺失不会视为健康。
        </Typography.Text>
      </Form>
    </Modal>
  );
}

type SettingsForm = Omit<SettingsInput, 'observationUntil'> & {
  observationUntil: ReturnType<typeof dayjs>;
};
export function SettingsPanel({
  settings,
  editable,
  saved,
}: {
  settings?: Settings;
  editable: boolean;
  saved: () => void;
}) {
  const [form] = Form.useForm<SettingsForm>();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (settings)
      form.setFieldsValue({
        ...settings,
        observationUntil: dayjs(settings.observationUntil).tz('Asia/Shanghai'),
      });
  }, [settings, form]);
  const submit = async (values: SettingsForm) => {
    if (!settings) return;
    if (
      values.hourlyRetentionDays < values.minuteRetentionDays ||
      values.alertRetentionDays < values.notificationRetentionDays
    ) {
      setError('小时保留期不得短于分钟保留期，告警保留期不得短于通知保留期');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await operationsApi.saveSettings({
        ...values,
        version: settings.version,
        observationUntil: values.observationUntil.toISOString(),
      });
      saved();
    } catch (error) {
      setError(errorText(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Space orientation="vertical" style={{ width: '100%' }} size="middle">
      <Typography.Title level={4}>
        {editable ? '监控配置' : '只读配置'}
      </Typography.Title>
      <Descriptions
        column={2}
        items={[
          {
            key: 'channel',
            label: '飞书通道',
            children: settings
              ? settings.channelConfigured
                ? '已配置'
                : '未配置'
              : '无数据',
          },
          {
            key: 'signature',
            label: '签名',
            children: settings
              ? settings.signatureConfigured
                ? '已配置'
                : '未配置'
              : '无数据',
          },
          {
            key: 'version',
            label: '配置版本',
            children: settings?.version ?? '无数据',
          },
          {
            key: 'instance',
            label: '实例',
            children: settings?.instanceId ?? '无数据',
          },
        ]}
      />
      <Alert
        type="info"
        title="采集与通知独立控制；初始24小时观察期不会自动启用即时通知"
        description="飞书Webhook与签名仅由服务端环境配置。此页不提供真实通道试发。外部探测必须运行在独立主机。"
        showIcon
      />
      {error && (
        <Alert
          type="error"
          title={error}
          description="输入已保留。版本冲突时请重新加载设置后再编辑。"
          showIcon
        />
      )}
      <Form
        form={form}
        layout="vertical"
        disabled={!editable || !settings}
        onFinish={submit}
      >
        <Space wrap>
          {(
            [
              ['telemetryEnabled', '采集启用'],
              ['notificationsEnabled', '即时通知启用'],
              ['persistentDigestEnabled', '持久化小时摘要启用'],
              ['externalProbeConfigured', '独立外部探测已配置'],
            ] as const
          ).map(([name, label]) => (
            <Form.Item
              key={name}
              name={name}
              label={label}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          ))}
        </Space>
        <Form.Item
          name="observationUntil"
          label="观察期截止（北京时间）"
          rules={[{ required: true }]}
        >
          <DatePicker showTime />
        </Form.Item>
        <Form.Item
          name="refreshSeconds"
          label="页面刷新间隔（秒）"
          rules={[{ required: true }]}
        >
          <InputNumber min={5} max={300} precision={0} />
        </Form.Item>
        <div className="grid grid-cols-2 gap-x-4 lg:grid-cols-3">
          {(
            [
              ['minuteRetentionDays', '分钟指标/资源'],
              ['hourlyRetentionDays', '小时指标'],
              ['eventRetentionDays', '错误事件'],
              ['projectionRetentionDays', 'AI/执行明细'],
              ['alertRetentionDays', '告警历史'],
              ['notificationRetentionDays', '通知记录'],
            ] as const
          ).map(([name, label]) => (
            <Form.Item
              key={name}
              name={name}
              label={`${label}保留（天）`}
              rules={[{ required: true }]}
            >
              <InputNumber min={1} max={90} precision={0} />
            </Form.Item>
          ))}
        </div>
        {editable && (
          <Button
            type="primary"
            htmlType="submit"
            loading={saving}
            disabled={!settings}
          >
            保存设置
          </Button>
        )}
      </Form>
    </Space>
  );
}

export function AlertActionDialog({
  target,
  close,
  saved,
}: {
  target?: { event: AlertEvent; action: 'acknowledge' | 'silence' };
  close: () => void;
  saved: () => void;
}) {
  const [form] = Form.useForm<{
    reason: string;
    until: ReturnType<typeof dayjs>;
  }>();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    form.resetFields();
    if (target?.action === 'silence')
      form.setFieldsValue({
        until: dayjs().tz('Asia/Shanghai').add(1, 'hour'),
      });
    setError('');
  }, [target, form]);
  const submit = async () => {
    if (!target) return;
    const values = await form.validateFields().catch(() => undefined);
    if (!values) return;
    setSaving(true);
    setError('');
    try {
      if (target.action === 'acknowledge')
        await operationsApi.acknowledge(target.event.id, values.reason);
      else
        await operationsApi.silence(
          target.event.id,
          values.until.toISOString(),
          values.reason,
        );
      close();
      saved();
    } catch (error) {
      setError(errorText(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      open={!!target}
      title={target?.action === 'silence' ? '静默告警' : '确认告警'}
      onCancel={close}
      onOk={() => {
        void submit();
      }}
      confirmLoading={saving}
      destroyOnHidden
    >
      <Typography.Paragraph>
        {target?.event.ruleName} · 触发于 {time(target?.event.firedAt)}
        。此操作不改变故障状态。
      </Typography.Paragraph>
      {error && <Alert type="error" title={error} />}
      <Form form={form} layout="vertical">
        <Form.Item
          name="reason"
          label="处理原因"
          rules={[{ required: true, whitespace: true, max: 500 }]}
        >
          <Input.TextArea />
        </Form.Item>
        {target?.action === 'silence' && (
          <Form.Item
            name="until"
            label="静默截止（北京时间）"
            rules={[
              { required: true },
              {
                validator: async (_, date) => {
                  if (!date || date.valueOf() <= Date.now())
                    throw new Error('静默截止时间必须在未来');
                },
              },
            ]}
          >
            <DatePicker showTime />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
