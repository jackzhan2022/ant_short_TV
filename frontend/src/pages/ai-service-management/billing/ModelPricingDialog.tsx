import { PlusOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDateTimePicker,
  ProFormDigit,
  ProFormList,
  ProFormSelect,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Form, Modal, Popconfirm, Space, Table, Tabs, Tag, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import {
  billingHistory,
  publishModelPrice,
  publishPointPrice,
  revokeCostPrice,
  revokePointPrice,
} from '@/services/ant-design-pro/platformAiAccountingController';
import { metricText } from '@/utils/fieldDictionary';
import {
  buildPointComponents,
  findEffectiveCostVersion,
  formatBillingUnit,
} from './pricingEntry';

type PriceKind = 'cost' | 'point';
type PriceVersion = API.ModelPriceVersionResponse | API.ModelPointPriceVersionResponse;
type FormValues = {
  effectiveFrom: string | Dayjs;
  effectiveTo?: string | Dayjs;
  multiplier?: number;
  components: Array<{
    metric: string;
    unitSize: number;
    unitPrice?: number;
    pointRate?: number;
    currency?: string;
  }>;
};

type ModelPricingDialogProps = {
  model: { id: number; name: string; code: string };
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
};

const metricOptions = [
  ['CALL', '调用次数'],
  ['INPUT_TOKEN', '普通输入 Token'],
  ['CACHED_INPUT_TOKEN', '缓存读取 Token'],
  ['CACHE_WRITE_TOKEN', '缓存写入 Token'],
  ['OUTPUT_TOKEN', '输出 Token'],
  ['IMAGE', '图片数'],
  ['VIDEO_SECOND', '视频秒数'],
  ['AUDIO_SECOND', '音频秒数'],
  ['CHARACTER', '字符数'],
].map(([value, label]) => ({ value, label }));

function isoLocalDateTime(value: string | Dayjs): string;
function isoLocalDateTime(value: string | Dayjs | undefined): string | undefined;
function isoLocalDateTime(value?: string | Dayjs) {
  if (dayjs.isDayjs(value)) return value.format('YYYY-MM-DDTHH:mm:ss');
  return value?.replace(
    /^(\d{4}-\d{2}-\d{2}) (?=\d{2}:\d{2}:\d{2}(?:\.\d+)?$)/,
    '$1T',
  );
}

const lifecycle = (version: PriceVersion) => {
  if (version.status === 'REVOKED') return { text: '已撤销', color: 'default' };
  const now = dayjs();
  if (version.effectiveFrom && now.isBefore(version.effectiveFrom)) return { text: '待生效', color: 'blue' };
  if (version.effectiveTo && !now.isBefore(version.effectiveTo)) return { text: '已过期', color: 'default' };
  return { text: '生效中', color: 'green' };
};

const ModelPricingDialog = ({ model, open, onClose, onChanged }: ModelPricingDialogProps) => {
  const { message } = App.useApp();
  const access = useAccess();
  const [history, setHistory] = useState<API.ModelBillingHistoryResponse>();
  const [loading, setLoading] = useState(false);
  const [costForm] = Form.useForm<FormValues>();
  const [pointForm] = Form.useForm<FormValues>();

  const load = async () => {
    setLoading(true);
    try {
      const response = await billingHistory({ modelId: model.id });
      setHistory(response.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) void load();
  }, [model.id, open]);

  const publish = async (kind: PriceKind, values: FormValues) => {
    if (kind === 'cost') {
      await publishModelPrice(
        { modelId: model.id },
        {
          effectiveFrom: isoLocalDateTime(values.effectiveFrom),
          effectiveTo: isoLocalDateTime(values.effectiveTo),
          components: values.components.map((component) => ({
            metric: component.metric,
            unitSize: component.unitSize,
            unitPrice: component.unitPrice ?? 0,
            currency: component.currency ?? 'USD',
            dimensions: {},
          })),
        },
      );
    } else {
      await publishPointPrice(
        { modelId: model.id },
        {
          effectiveFrom: isoLocalDateTime(values.effectiveFrom),
          effectiveTo: isoLocalDateTime(values.effectiveTo),
          components: values.components.map((component) => ({
            metric: component.metric,
            unitSize: component.unitSize,
            pointRate: component.pointRate ?? 0,
            dimensions: {},
          })),
        },
      );
    }
    message.success('价格版本已发布');
    await load();
    onChanged();
    return true;
  };

  const revoke = async (kind: PriceKind, versionId: number) => {
    if (kind === 'cost') await revokeCostPrice({ modelId: model.id, versionId });
    else await revokePointPrice({ modelId: model.id, versionId });
    message.success('未来版本已撤销');
    await load();
    onChanged();
  };

  const renderVersions = (kind: PriceKind, versions: PriceVersion[]) => (
    <Table<PriceVersion>
      rowKey={(record) => record.id ?? `${record.versionNo}`}
      size="small"
      loading={loading}
      pagination={false}
      dataSource={versions}
      columns={[
        { title: '版本', dataIndex: 'versionNo', width: 90, render: (value) => `v${value}` },
        {
          title: '状态', width: 100, render: (_, record) => {
            const state = lifecycle(record);
            return <Tag color={state.color}>{state.text}</Tag>;
          },
        },
        { title: '生效时间', dataIndex: 'effectiveFrom', width: 190 },
        { title: '失效时间', dataIndex: 'effectiveTo', width: 190, render: (value) => value ?? '-' },
        {
          title: '计费项', render: (_, record) => (
            <Space orientation="vertical" size={2}>
              {(record.components ?? []).map((component) => (
                <Typography.Text key={component.id ?? component.metric}>
                  {metricText(component.metric ?? '')} / {formatBillingUnit(component.metric ?? '', component.unitSize ?? 0)}:{' '}
                  {kind === 'cost'
                    ? `${(component as API.ModelPriceComponentResponse).unitPrice} ${(component as API.ModelPriceComponentResponse).currency}`
                    : `${(component as API.PointPolicyComponentResponse).pointRate} 积分`}
                </Typography.Text>
              ))}
            </Space>
          ),
        },
        {
          title: '操作', width: 90, render: (_, record) => {
            const versionId = record.id;
            const canRevoke = versionId != null && lifecycle(record).text === '待生效'
              && access.canPublishModelBilling;
            return canRevoke ? (
              <Popconfirm title="确认撤销该未来版本？" onConfirm={() => revoke(kind, versionId)}>
                <Button type="link" danger>撤销</Button>
              </Popconfirm>
            ) : '-';
          },
        },
      ]}
    />
  );

  const generatePointComponents = () => {
    const values = pointForm.getFieldsValue();
    const effectiveFrom = isoLocalDateTime(values.effectiveFrom);
    const costVersion = effectiveFrom && findEffectiveCostVersion(
      history?.costPrices ?? [],
      effectiveFrom,
    );

    if (!costVersion) {
      message.warning('该生效时间没有可用的已发布成本价，请先发布成本价或调整生效时间');
      return;
    }

    pointForm.setFieldsValue({
      components: buildPointComponents(
        (costVersion.components ?? []).map((component) => ({
          metric: component.metric ?? '',
          unitSize: component.unitSize ?? 0,
          unitPrice: component.unitPrice ?? 0,
        })),
        values.multiplier ?? 1,
      ),
    });
  };

  const editor = (kind: PriceKind) => (
    <ModalForm<FormValues>
      title={kind === 'cost' ? '发布成本价' : '发布积分价'}
      trigger={<Button type="primary" icon={<PlusOutlined />}>{kind === 'cost' ? '发布成本价' : '发布积分价'}</Button>}
      onFinish={(values) => publish(kind, values)}
      modalProps={{ destroyOnHidden: true }}
      form={kind === 'cost' ? costForm : pointForm}
      onOpenChange={(visible) => {
        if (visible) {
          const form = kind === 'cost' ? costForm : pointForm;
          form.resetFields();
          form.setFieldsValue({
            effectiveFrom: dayjs(),
            components: [{ unitSize: 1, currency: 'USD' }],
            ...(kind === 'point' ? { multiplier: 1 } : {}),
          });
        }
      }}
      initialValues={{ effectiveFrom: dayjs(), components: [{ unitSize: 1, currency: 'USD' }] }}
    >
      <ProFormDateTimePicker name="effectiveFrom" label="生效时间" rules={[{ required: true }]} />
      <ProFormDateTimePicker name="effectiveTo" label="失效时间" />
      {kind === 'point' && <Space align="start">
        <ProFormDigit name="multiplier" label="倍率" min={0} initialValue={1} rules={[{ required: true }]} />
        <Button onClick={generatePointComponents}>按倍率生成积分项</Button>
      </Space>}
      <ProFormList name="components" label="计费项" min={1} creatorButtonProps={{ creatorButtonText: '添加计费项' }}>
        <Space align="start" wrap>
          <ProFormSelect name="metric" label="指标" options={metricOptions} rules={[{ required: true }]} />
          <ProFormDigit
            name="unitSize"
            label={`计费单位（Token：${formatBillingUnit('INPUT_TOKEN', 1_000_000)}；调用：${formatBillingUnit('CALL', 1)}）`}
            min={0.00000001}
            rules={[{ required: true }]}
          />
          {kind === 'cost' ? (
            <>
              <ProFormDigit name="unitPrice" label="成本单价" min={0} rules={[{ required: true }]} />
              <ProFormSelect
                name="currency"
                label="币种"
                options={[
                  { label: '人民币（CNY）', value: 'CNY' },
                  { label: '美元（USD）', value: 'USD' },
                ]}
                rules={[{ required: true }]}
              />
            </>
          ) : (
            <ProFormDigit name="pointRate" label="积分单价" min={0} rules={[{ required: true }]} />
          )}
        </Space>
      </ProFormList>
    </ModalForm>
  );

  return (
    <Modal title={`${model.name} 模型价格`} open={open} onCancel={onClose} footer={null} width={1000} destroyOnHidden>
      <Tabs items={[
        {
          key: 'cost', label: '供应商成本价',
          children: <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            {access.canPublishModelBilling && editor('cost')}
            {renderVersions('cost', history?.costPrices ?? [])}
          </Space>,
        },
        {
          key: 'point', label: '用户积分价',
          children: <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            {access.canPublishModelBilling && editor('point')}
            {renderVersions('point', history?.pointPrices ?? [])}
          </Space>,
        },
      ]} />
    </Modal>
  );
};

export default ModelPricingDialog;
