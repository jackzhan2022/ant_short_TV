import {
  EditOutlined,
  PlusOutlined,
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
import { App, Button, Form, Space, Switch, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import type { ReactElement } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useAccess } from '@umijs/max';
import type {
  PlatformModel,
  PlatformModelFormValues,
  PlatformModelServiceType,
  PlatformProvider,
} from '../platform-service';
import {
  createPlatformModel,
  queryPlatformModels,
  queryPlatformProviders,
  serviceTypeText,
  setDefaultPlatformModel,
  updatePlatformModel,
  updatePlatformModelStatus,
} from '../platform-service';
import ModelPricingDialog from '../billing/ModelPricingDialog';
import { billingHistory } from '@/services/ant-design-pro/platformAiAccountingController';

type ModelWithPrice = PlatformModel & {
  currentCostPrice?: string;
  currentPointPrice?: string;
};

const activePrice = <T extends { status?: string; effectiveFrom?: string; effectiveTo?: string }>(
  versions: T[] | undefined,
) => versions?.find((version) =>
  version.status !== 'REVOKED'
  && (!version.effectiveFrom || !dayjs().isBefore(version.effectiveFrom))
  && (!version.effectiveTo || dayjs().isBefore(version.effectiveTo)),
);

const formatCostPrice = (history: API.ModelBillingHistoryResponse) => {
  const version = activePrice(history.costPrices);
  if (!version?.components?.length) return '-';
  return version.components
    .map((component) => `${component.metric}: ${component.unitPrice} ${component.currency}`)
    .join('；');
};

const formatPointPrice = (history: API.ModelBillingHistoryResponse) => {
  const version = activePrice(history.pointPrices);
  if (!version?.components?.length) return '-';
  return version.components
    .map((component) => `${component.metric}: ${component.pointRate} 积分`)
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

const serviceTypeValueEnum = {
  TEXT: { text: '文本' },
  IMAGE: { text: '图片' },
  VIDEO: { text: '视频' },
  VIDEO_UNDERSTANDING: { text: '视频理解' },
  AUDIO: { text: '音频' },
};

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
      modalProps={{ destroyOnHidden: true }}
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
        label="AI 大模型 Code"
        rules={[{ required: true, message: '请输入 AI 大模型 Code' }]}
      />
      <ProFormText
        name="modelCode"
        label="真实模型 Code"
        rules={[{ required: true, message: '请输入真实模型 Code' }]}
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
      <ProFormTextArea name="configJson" label="扩展配置 JSON" />
    </ModalForm>
  );
};

const PlatformModelsPage = () => {
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

  const columns: ProColumns<ModelWithPrice>[] = [
    {
      title: '模型',
      dataIndex: 'name',
      width: 220,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary">{record.code}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '服务类型',
      dataIndex: 'serviceType',
      valueEnum: serviceTypeValueEnum,
      width: 110,
      render: (_, record) => (
        <Tag>{serviceTypeText[record.serviceType] ?? record.serviceType}</Tag>
      ),
    },
    {
      title: '模型服务商',
      dataIndex: 'providerName',
      search: false,
      width: 180,
      renderText: (value, record) =>
        value || providerNameMap.get(record.providerId) || '-',
    },
    {
      title: '真实模型 Code',
      dataIndex: 'modelCode',
      search: false,
      ellipsis: true,
      width: 220,
    },
    {
      title: '能力',
      dataIndex: 'capabilities',
      search: false,
      width: 220,
      render: (_, record) =>
        (record.capabilities || []).map((item) => <Tag key={item}>{item}</Tag>),
    },
    {
      title: '当前成本价',
      dataIndex: 'currentCostPrice',
      search: false,
      width: 180,
      ellipsis: true,
      renderText: (value) => value ?? '-',
    },
    {
      title: '当前积分价',
      dataIndex: 'currentPointPrice',
      search: false,
      width: 180,
      ellipsis: true,
      renderText: (value) => value ?? '-',
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      search: false,
      align: 'center',
      width: 100,
      render: (_, record) => (
        <Button
          type="text"
          disabled={!access.canEditPlatformAiModels || record.status !== 'ENABLED'}
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
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (_, record) => (
        <Switch
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
      title: '更新时间',
      dataIndex: 'updatedAt',
      valueType: 'dateTime',
      search: false,
      width: 180,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) => (
        <Space size={0}>
          {access.canViewModelBilling && (
            <Button type="link" onClick={() => setPricingModel(record)}>
              模型价格
            </Button>
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
    <>
      <ProTable<ModelWithPrice>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="AI 大模型"
        columns={columns}
        search={false}
        tableLayout="fixed"
        scroll={{ x: 1400 }}
        request={async () => {
          if (!access.canViewPlatformAiModels) {
            return { data: [], success: true };
          }
          const response = await queryPlatformModels();
          const models = response.data ?? [];
          const prices = access.canViewModelBilling
            ? await Promise.all(models.map(async (model) => {
              try {
                const response = await billingHistory({ modelId: model.id });
                return [model.id, response.data] as const;
              } catch {
                return [model.id, undefined] as const;
              }
            }))
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
    </>
  );
};

export default PlatformModelsPage;
