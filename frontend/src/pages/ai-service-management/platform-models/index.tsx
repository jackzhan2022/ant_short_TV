import {
  EditOutlined,
  PlusOutlined,
  SettingOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import type { ReactElement } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { billingHistory } from '@/services/ant-design-pro/platformAiAccountingController';
import {
  serviceTypeText as displayServiceTypeText,
  metricText,
} from '@/utils/fieldDictionary';
import { AdminHeading, useAdminStyles } from '../admin-ui';
import ModelPricingDialog from '../billing/ModelPricingDialog';
import {
  formatBillingUnit,
  formatPricingDimensions,
  type PricingComponent,
} from '../billing/pricingEntry';
import type {
  AiModelParameterFormValues,
  PlatformModel,
  PlatformModelFormValues,
  PlatformModelServiceType,
  PlatformProvider,
} from '../platform-service';
import {
  createPlatformModel,
  queryModelParameters,
  queryPlatformModels,
  queryPlatformProviders,
  setDefaultPlatformModel,
  updateModelParameters,
  updatePlatformModel,
  updatePlatformModelStatus,
} from '../platform-service';

type ModelWithPrice = PlatformModel & {
  currentCostPrice?: string;
  currentPointPrice?: string;
};

const activePrice = <
  T extends { status?: string; effectiveFrom?: string; effectiveTo?: string },
>(
  versions: T[] | undefined,
) =>
  versions?.find(
    (version) =>
      version.status !== 'REVOKED' &&
      (!version.effectiveFrom || !dayjs().isBefore(version.effectiveFrom)) &&
      (!version.effectiveTo || dayjs().isBefore(version.effectiveTo)),
  );

const formatCostPrice = (history: API.ModelBillingHistoryResponse) => {
  const version = activePrice(history.costPrices);
  if (!version?.components?.length) return '-';
  return version.components
    .map(
      (component: PricingComponent) =>
        `${metricText(component.metric)} ${component.unitPrice} ${component.currency} / ${formatBillingUnit(component.metric ?? '', component.unitSize ?? 0)}${Object.keys(component.dimensions ?? {}).length ? `（${formatPricingDimensions(component.dimensions)}）` : ''}`,
    )
    .join('；');
};

const formatPointPrice = (history: API.ModelBillingHistoryResponse) => {
  const version = activePrice(history.pointPrices);
  if (!version?.components?.length) return '-';
  return version.components
    .map(
      (component: PricingComponent) =>
        `${metricText(component.metric)} ${component.pointRate} 积分 / ${formatBillingUnit(component.metric ?? '', component.unitSize ?? 0)}${Object.keys(component.dimensions ?? {}).length ? `（${formatPricingDimensions(component.dimensions)}）` : ''}`,
    )
    .join('；');
};

const defaultFormValues: PlatformModelFormValues = {
  providerId: 0,
  code: '',
  name: '',
  modelCode: '',
  serviceType: 'TEXT',
  description: '',
  enabled: true,
  isDefault: false,
  sort: 0,
  configJson: '',
};

const serviceTypeOptions: Array<{
  label: string;
  value: PlatformModelServiceType;
}> = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '视频理解', value: 'VIDEO_UNDERSTANDING' },
  { label: '音频', value: 'AUDIO' },
];

const ModelEditor = ({
  record,
  providers,
  trigger,
  onDone,
}: {
  record?: PlatformModel;
  providers: PlatformProvider[];
  trigger: ReactElement;
  onDone: () => void;
}) => {
  const { styles } = useAdminStyles();
  const [form] = Form.useForm<PlatformModelFormValues>();
  const { message } = App.useApp();
  const isEdit = Boolean(record);
  const providerOptions = useMemo(
    () =>
      providers
        .filter(
          (item) => item.status === 'ENABLED' || item.id === record?.providerId,
        )
        .map((item) => ({
          label: `${item.name}（${item.code}）`,
          value: item.id,
        })),
    [providers, record?.providerId],
  );

  useEffect(() => {
    form.setFieldsValue(
      record
        ? {
            providerId: record.providerId,
            code: record.code,
            name: record.name,
            modelCode: record.modelCode,
            serviceType: record.serviceType,
            description: record.description,
            enabled: record.status === 'ENABLED',
            isDefault: record.isDefault,
            sort: record.sort,
          }
        : {
            ...defaultFormValues,
            providerId: providerOptions[0]?.value ?? 0,
          },
    );
  }, [form, providerOptions, record]);

  return (
    <ModalForm<PlatformModelFormValues>
      title={isEdit ? '编辑 AI 大模型' : '新增 AI 大模型'}
      form={form}
      trigger={trigger}
      modalProps={{ destroyOnHidden: true, rootClassName: styles.overlay }}
      onFinish={async (values) => {
        if (record) {
          await updatePlatformModel(record.id, values);
          message.success('AI 大模型已更新');
        } else {
          await createPlatformModel(values);
          message.success('AI 大模型已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormSelect
        name="providerId"
        label="所属模型服务商"
        options={providerOptions}
        rules={[{ required: true, message: '请选择所属模型服务商' }]}
      />
      <ProFormText
        name="name"
        label="模型名称"
        rules={[{ required: true, message: '请输入模型名称' }]}
      />
      <ProFormText
        name="code"
        label="AI 大模型编码"
        rules={[{ required: true, message: '请输入 AI 大模型编码' }]}
      />
      <ProFormText
        name="modelCode"
        label="真实模型编码"
        rules={[{ required: true, message: '请输入真实模型编码' }]}
      />
      <ProFormSelect
        name="serviceType"
        label="服务类型"
        options={serviceTypeOptions}
        rules={[{ required: true, message: '请选择服务类型' }]}
      />
      <ProFormDigit name="sort" label="排序" min={0} max={9999} />
      <ProFormSwitch
        name="enabled"
        label="状态"
        checkedChildren="启用"
        unCheckedChildren="停用"
      />
      <ProFormSwitch
        name="isDefault"
        label="默认模型"
        checkedChildren="默认"
        unCheckedChildren="默认"
      />
      <ProFormTextArea name="description" label="模型描述" />
      <ProFormTextArea name="configJson" label="扩展配置" />
    </ModalForm>
  );
};

const ModelParameterEditor = ({
  model,
  onDone,
}: {
  model: PlatformModel;
  onDone: () => void;
}) => {
  const { styles } = useAdminStyles();
  const [form] = Form.useForm<AiModelParameterFormValues>();
  const { message } = App.useApp();
  return (
    <ModalForm<AiModelParameterFormValues>
      title={`配置参数：${model.name}`}
      trigger={
        <Button type="link" icon={<SettingOutlined />}>
          参数
        </Button>
      }
      form={form}
      modalProps={{ destroyOnHidden: true, rootClassName: styles.overlay }}
      request={async () => {
        const response = await queryModelParameters(model.id);
        const data = response.data;
        if (data) form.setFieldsValue(data);
        return data;
      }}
      onFinish={async (values) => {
        await updateModelParameters(model.id, values);
        message.success('模型参数已更新');
        onDone();
        return true;
      }}
    >
      <ProFormDigit
        name="temperature"
        label="Temperature"
        min={0}
        max={2}
        step={0.1}
        rules={[{ required: true }]}
      />
      <ProFormDigit name="topP" label="Top P" min={0} max={1} step={0.1} />
      <ProFormDigit
        name="maxTokens"
        label="最大输出 Token"
        min={256}
        max={128000}
        rules={[{ required: true }]}
      />
      <ProFormSwitch name="jsonMode" label="JSON 输出模式" />
      <ProFormDigit
        name="timeoutSeconds"
        label="超时（秒）"
        min={5}
        max={180}
        rules={[{ required: true }]}
      />
      <ProFormDigit
        name="retryCount"
        label="重试次数"
        min={0}
        max={3}
        rules={[{ required: true }]}
      />
    </ModalForm>
  );
};

const PlatformModelsPage = () => {
  const { styles } = useAdminStyles();
  const [search, setSearch] = useState('');
  const [serviceType, setServiceType] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [detailModel, setDetailModel] = useState<ModelWithPrice>();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const access = useAccess();
  const [providers, setProviders] = useState<PlatformProvider[]>([]);
  const [pricingModel, setPricingModel] = useState<PlatformModel>();

  useEffect(() => {
    if (!access.canViewPlatformAiModels) return;
    queryPlatformProviders()
      .then((response) => setProviders(response.data ?? []))
      .catch(() => message.error('Provider 列表加载失败'));
  }, [access.canViewPlatformAiModels, message]);

  const reload = () => actionRef.current?.reload();

  const providerNameMap = useMemo(
    () => new Map(providers.map((item) => [item.id, item.name])),
    [providers],
  );

  const priceSummary = (value?: string) => {
    const parts = (value || '-').split('；');
    return (
      <Tooltip
        title={<div style={{ whiteSpace: 'pre-wrap' }}>{parts.join('\n')}</div>}
      >
        <div className={styles.summary}>
          {[...new Set(parts)].slice(0, 2).map((part) => {
            const text = part.split('（')[0];
            const [price, unit] = text.split(' / ');
            return (
              <div key={part}>
                <span>{price}</span>
                {unit && (
                  <div>
                    <small>
                      / {unit}
                      {part.includes('（') ? ' · 条件价' : ''}
                    </small>
                  </div>
                )}
              </div>
            );
          })}
          {parts.length > 2 && (
            <small>另 {parts.length - 2} 项 · 查看完整价格</small>
          )}
        </div>
      </Tooltip>
    );
  };

  const columns: ProColumns<ModelWithPrice>[] = [
    {
      title: '模型名称 / 编码',
      dataIndex: 'name',
      width: 200,
      render: (_, record) => (
        <div className={styles.name}>
          <span className={styles.mark}>
            {displayServiceTypeText(record.serviceType).slice(0, 1)}
          </span>
          <div>
            <Space size={2}>
              <Typography.Text strong>{record.name}</Typography.Text>
              <Button
                aria-label={
                  record.isDefault
                    ? `当前默认模型：${record.name}`
                    : `设为默认模型：${record.name}`
                }
                size="small"
                type="text"
                disabled={
                  !access.canEditPlatformAiModels || record.status !== 'ENABLED'
                }
                icon={
                  record.isDefault ? (
                    <StarFilled style={{ color: '#faad14' }} />
                  ) : (
                    <StarOutlined />
                  )
                }
                onClick={async () => {
                  if (record.isDefault) {
                    return;
                  }
                  await setDefaultPlatformModel(record.id);
                  message.success('已设为默认模型');
                  reload();
                }}
              />
            </Space>
            <div className={styles.secondary}>{record.code}</div>
          </div>
        </div>
      ),
    },
    {
      title: '服务商 / 能力',
      dataIndex: 'providerName',
      width: 125,
      render: (_, record) => (
        <div className={styles.summary}>
          <span>
            {record.providerName ||
              providerNameMap.get(record.providerId) ||
              '-'}
          </span>
          <div>
            <Tag>{displayServiceTypeText(record.serviceType)}</Tag>
            {(record.capabilities || []).map((item) => (
              <Tag key={item}>{item}</Tag>
            ))}
          </div>
        </div>
      ),
    },
    {
      title: '当前成本价',
      dataIndex: 'currentCostPrice',
      width: 175,
      renderText: (value) => value ?? '-',
      render: (_, record) => priceSummary(record.currentCostPrice),
    },
    {
      title: '当前积分价',
      dataIndex: 'currentPointPrice',
      width: 175,
      renderText: (value) => value ?? '-',
      render: (_, record) => priceSummary(record.currentPointPrice),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 70,
      render: (_, record) => (
        <Switch
          size="small"
          checked={record.status === 'ENABLED'}
          checkedChildren="启用"
          unCheckedChildren="停用"
          disabled={!access.canEnablePlatformAiModels}
          onChange={async (checked) => {
            await updatePlatformModelStatus(record.id, checked);
            message.success(checked ? 'AI 大模型已启用' : 'AI 大模型已停用');
            reload();
          }}
        />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 185,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0} wrap>
          <Button type="link" onClick={() => setDetailModel(record)}>
            详情
          </Button>
          {access.canViewModelBilling && (
            <Button type="link" onClick={() => setPricingModel(record)}>
              模型价格
            </Button>
          )}
          {access.canEditPlatformAiModels && record.serviceType === 'TEXT' && (
            <ModelParameterEditor model={record} onDone={reload} />
          )}
          {access.canEditPlatformAiModels && (
            <ModelEditor
              record={record}
              providers={providers}
              trigger={
                <Button type="link" icon={<EditOutlined />}>
                  编辑
                </Button>
              }
              onDone={reload}
            />
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <AdminHeading
        title="AI 大模型"
        description="配置模型能力、调用状态与计费规则。"
      />
      <ProTable<ModelWithPrice>
        className={styles.compactTable}
        params={{ search, serviceType, status }}
        toolbar={{
          filter: (
            <Space wrap>
              <Select
                allowClear
                aria-label="模型服务类型"
                placeholder="全部类型"
                options={serviceTypeOptions}
                value={serviceType}
                onChange={setServiceType}
                style={{ width: 135 }}
              />
              <Input.Search
                allowClear
                placeholder="搜索模型名称或编码"
                onSearch={setSearch}
                style={{ width: 245 }}
              />
              <Select
                allowClear
                aria-label="模型状态"
                placeholder="全部状态"
                value={status}
                onChange={setStatus}
                style={{ width: 120 }}
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' },
                ]}
              />
            </Space>
          ),
        }}
        options={{
          density: false,
          setting: false,
          fullScreen: false,
          reload: true,
        }}
        actionRef={actionRef}
        rowKey="id"
        headerTitle={false}
        columns={columns}
        search={false}
        tableLayout="fixed"
        scroll={{ x: 930 }}
        request={async () => {
          if (!access.canViewPlatformAiModels) {
            return { data: [], success: true };
          }
          const response = await queryPlatformModels();
          const models = (response.data ?? []).filter(
            (model) =>
              (!serviceType || model.serviceType === serviceType) &&
              (!status || model.status === status) &&
              (!search ||
                `${model.name} ${model.code} ${model.modelCode}`
                  .toLowerCase()
                  .includes(search.toLowerCase())),
          );
          const prices = access.canViewModelBilling
            ? await Promise.all(
                models.map(async (model) => {
                  try {
                    const response = await billingHistory({
                      modelId: model.id,
                    });
                    return [model.id, response.data] as const;
                  } catch {
                    return [model.id, undefined] as const;
                  }
                }),
              )
            : [];
          const priceMap = new Map(prices);
          return {
            data: models.map((model) => {
              const history = priceMap.get(model.id);
              return {
                ...model,
                currentCostPrice: history ? formatCostPrice(history) : '-',
                currentPointPrice: history ? formatPointPrice(history) : '-',
              };
            }),
            success: response.success,
          };
        }}
        toolBarRender={() =>
          access.canCreatePlatformAiModels
            ? [
                <ModelEditor
                  key="create"
                  providers={providers}
                  trigger={
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      disabled={!providers.length}
                    >
                      新增 AI 大模型
                    </Button>
                  }
                  onDone={reload}
                />,
              ]
            : []
        }
        pagination={{ pageSize: 10 }}
      />
      {pricingModel && (
        <ModelPricingDialog
          model={pricingModel}
          open
          onClose={() => setPricingModel(undefined)}
          onChanged={reload}
        />
      )}
      <Drawer
        title="模型详情"
        rootClassName={styles.overlay}
        open={Boolean(detailModel)}
        onClose={() => setDetailModel(undefined)}
        size={640}
        destroyOnHidden
      >
        {detailModel && (
          <>
            <AdminHeading
              title={detailModel.name}
              description={detailModel.code}
            />
            <Descriptions
              column={1}
              items={[
                {
                  key: 'provider',
                  label: '服务商',
                  children:
                    detailModel.providerName ||
                    providerNameMap.get(detailModel.providerId) ||
                    '-',
                },
                {
                  key: 'modelCode',
                  label: '真实模型编码',
                  children: detailModel.modelCode,
                },
                {
                  key: 'type',
                  label: '服务类型',
                  children: displayServiceTypeText(detailModel.serviceType),
                },
                {
                  key: 'capabilities',
                  label: '能力',
                  children: detailModel.capabilities?.join('、') || '-',
                },
                {
                  key: 'status',
                  label: '状态',
                  children: detailModel.status === 'ENABLED' ? '启用' : '停用',
                },
                {
                  key: 'default',
                  label: '默认模型',
                  children: detailModel.isDefault ? '是' : '否',
                },
                { key: 'sort', label: '排序', children: detailModel.sort },
                {
                  key: 'description',
                  label: '描述',
                  children: detailModel.description || '-',
                },
                {
                  key: 'updatedAt',
                  label: '更新时间',
                  children: detailModel.updatedAt,
                },
                ...(access.canViewModelBilling
                  ? [
                      {
                        key: 'cost',
                        label: '当前成本价',
                        children: (
                          <div style={{ whiteSpace: 'pre-wrap' }}>
                            {detailModel.currentCostPrice
                              ?.split('；')
                              .join('\n')}
                          </div>
                        ),
                      },
                      {
                        key: 'point',
                        label: '当前积分价',
                        children: (
                          <div style={{ whiteSpace: 'pre-wrap' }}>
                            {detailModel.currentPointPrice
                              ?.split('；')
                              .join('\n')}
                          </div>
                        ),
                      },
                    ]
                  : []),
              ]}
            />
            {access.canViewModelBilling && (
              <Button onClick={() => setPricingModel(detailModel)}>
                查看价格版本与完整计费条件
              </Button>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default PlatformModelsPage;
