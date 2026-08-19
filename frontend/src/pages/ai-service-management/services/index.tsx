import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import {
  App,
  Button,
  Dropdown,
  Form,
  Popconfirm,
  Space,
  Switch,
  Tag,
} from 'antd';
import type { ReactElement } from 'react';
import { useEffect, useRef, useState } from 'react';
import { useAccess } from '@umijs/max';
import type {
  AiProviderCode,
  AiServiceConfig,
  AiServiceConfigFormValues,
} from './data';
import { PROVIDER_TEXT, SERVICE_TYPE_TEXT } from './data';
import {
  createAiServiceConfig,
  deleteAiServiceConfig,
  queryAiProviders,
  queryAiServiceConfigs,
  setDefaultAiServiceConfig,
  testAiServiceConfig,
  updateAiServiceConfig,
  updateAiServiceConfigStatus,
} from './service';

type ProviderOption = {
  label: string;
  value: AiProviderCode;
  defaultBaseUrl?: string;
  supportedTypes: string;
};

const fallbackProviderOptions = Object.keys(PROVIDER_TEXT).map((key) => ({
  label: PROVIDER_TEXT[key as keyof typeof PROVIDER_TEXT],
  value: key as AiProviderCode,
  supportedTypes: '',
}));

const serviceTypeOptions: Array<{
  label: string;
  value: AiServiceConfigFormValues['serviceType'];
}> = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '语音', value: 'VOICE' },
];

const serviceTypeValueEnum = {
  TEXT: { text: '文本' },
  IMAGE: { text: '图片' },
  VIDEO: { text: '视频' },
  VOICE: { text: '语音' },
};

const defaultFormValues: AiServiceConfigFormValues = {
  name: '',
  provider: 'OpenAI',
  serviceType: 'TEXT',
  baseUrl: '',
  apiKey: '',
  model: '',
  priority: 100,
  isDefault: false,
  enabled: true,
  remark: '',
};

const testStatusColor: Record<string, string> = {
  UNTESTED: 'default',
  SUCCESS: 'green',
  FAILED: 'red',
};

const ServiceEditor = ({
  record,
  serviceType,
  trigger,
  autoOpen,
  providerOptions,
  onDone,
  onClose,
}: {
  record?: AiServiceConfig;
  serviceType?: AiServiceConfigFormValues['serviceType'];
  trigger: ReactElement;
  autoOpen?: boolean;
  providerOptions: ProviderOption[];
  onDone: () => void;
  onClose?: () => void;
}) => {
  const [form] = Form.useForm<AiServiceConfigFormValues>();
  const { message } = App.useApp();
  const [open, setOpen] = useState(false);
  const isEdit = Boolean(record);

  useEffect(() => {
    if (autoOpen) {
      setOpen(true);
    }
  }, [autoOpen]);

  useEffect(() => {
    if (record) {
      form.setFieldsValue({
        name: record.name,
        provider: record.provider,
        serviceType: record.serviceType,
        baseUrl: record.baseUrl,
        apiKey: '',
        model: record.model,
        priority: record.priority,
        isDefault: record.isDefault,
        enabled: record.enabled,
        remark: record.remark,
      });
    } else {
      form.setFieldsValue({
        ...defaultFormValues,
        serviceType: serviceType ?? defaultFormValues.serviceType,
      });
    }
  }, [form, record, serviceType]);

  return (
    <ModalForm<AiServiceConfigFormValues>
      title={isEdit ? '编辑AI服务' : '新增AI服务'}
      form={form}
      open={open}
      trigger={trigger}
      modalProps={{ destroyOnHidden: true }}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen && !record) {
          form.resetFields();
          onClose?.();
        }
      }}
      onFinish={async (values) => {
        const payload = {
          ...values,
          serviceType: record?.serviceType ?? serviceType ?? 'TEXT',
          endpoint: record?.endpoint,
          queryEndpoint: record?.queryEndpoint,
          isDefault: record?.isDefault ?? false,
          enabled: record?.enabled ?? true,
          apiKey: values.apiKey?.trim(),
        };
        if (record) {
          await updateAiServiceConfig(record.id, payload);
          message.success('AI 服务已更新');
        } else {
          await createAiServiceConfig(payload);
          message.success('AI 服务已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="服务名称"
        rules={[{ required: true, message: '请输入服务名称' }]}
      />
      <ProFormSelect
        name="provider"
        label="服务商"
        options={providerOptions}
        fieldProps={{
          onChange: (value) => {
            const option = providerOptions.find((item) => item.value === value);
            if (option?.defaultBaseUrl && !form.getFieldValue('baseUrl')) {
              form.setFieldValue('baseUrl', option.defaultBaseUrl);
            }
          },
        }}
        rules={[{ required: true, message: '请选择服务商' }]}
      />
      <ProFormText
        name="baseUrl"
        label="接口地址"
        rules={[{ required: true, message: '请输入接口地址' }]}
      />
      <ProFormText
        name="apiKey"
        label={isEdit ? 'API Key（留空表示不修改）' : 'API Key'}
        fieldProps={{ type: 'password' }}
        rules={isEdit ? [] : [{ required: true, message: '请输入 API Key' }]}
      />
      <ProFormText
        name="model"
        label="模型"
        rules={[{ required: true, message: '请输入模型名称' }]}
      />
      <ProFormDigit
        name="priority"
        label="优先级"
        min={1}
        max={999}
        rules={[{ required: true, message: '请输入优先级' }]}
      />
      <ProFormTextArea name="remark" label="备注" />
    </ModalForm>
  );
};

const CreateServiceDropdown = ({
  providerOptions,
  onDone,
}: {
  providerOptions: ProviderOption[];
  onDone: () => void;
}) => {
  const [serviceType, setServiceType] =
    useState<AiServiceConfigFormValues['serviceType']>('TEXT');
  const [open, setOpen] = useState(false);

  return (
    <>
      <Dropdown
        trigger={['click']}
        menu={{
          items: serviceTypeOptions.map((item) => ({
            key: item.value,
            label: `新增${item.label}服务`,
          })),
          onClick: ({ key }) => {
            setServiceType(key as AiServiceConfigFormValues['serviceType']);
            setOpen(true);
          },
        }}
      >
        <Button type="primary" icon={<PlusOutlined />}>
          新增服务
          <DownOutlined />
        </Button>
      </Dropdown>
      {open && (
        <ServiceEditor
          serviceType={serviceType}
          providerOptions={providerOptions}
          trigger={<span />}
          autoOpen
          onDone={() => {
            setOpen(false);
            onDone();
          }}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
};

const AiServiceManagement = () => {
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const access = useAccess();
  const [providerOptions, setProviderOptions] = useState<ProviderOption[]>(
    fallbackProviderOptions,
  );

  useEffect(() => {
    queryAiProviders()
      .then((response) => {
        const options = (response.data ?? [])
          .filter((provider) => provider.status === 'ENABLED')
          .map((provider) => ({
            label: provider.name,
            value: provider.code,
            defaultBaseUrl: provider.defaultBaseUrl,
            supportedTypes: provider.supportedTypes,
          }));
        if (options.length > 0) {
          setProviderOptions(options);
        }
      })
      .catch(() => {
        message.warning('服务商列表加载失败，已使用默认服务商');
      });
  }, [message]);

  const reload = () => actionRef.current?.reload();

  const columns: ProColumns<AiServiceConfig>[] = [
    {
      title: '服务名称',
      dataIndex: 'name',
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
    },
    {
      title: '服务类型',
      dataIndex: 'serviceType',
      valueEnum: serviceTypeValueEnum,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      render: (_, record) => <Tag>{SERVICE_TYPE_TEXT[record.serviceType]}</Tag>,
    },
    {
      title: '服务商',
      dataIndex: 'provider',
      responsive: ['md', 'lg', 'xl', 'xxl'],
      render: (_, record) => PROVIDER_TEXT[record.provider],
    },
    {
      title: '模型',
      dataIndex: 'model',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
    },
    {
      title: '接口地址',
      dataIndex: 'baseUrl',
      search: false,
      responsive: ['xl', 'xxl'],
      ellipsis: true,
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      align: 'center',
      width: 96,
      render: (_, record) => (
        <Switch
          checked={record.isDefault}
          checkedChildren="默认"
          unCheckedChildren="默认"
          disabled={!access.canEditAiServices}
          onChange={async (checked) => {
            if (!checked || record.isDefault) return;
            await setDefaultAiServiceConfig(record.id);
            message.success('已设为默认');
            reload();
          }}
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      responsive: ['md', 'lg', 'xl', 'xxl'],
      align: 'center',
      width: 96,
      valueEnum: {
        true: { text: '启用', status: 'Success' },
        false: { text: '停用', status: 'Default' },
      },
      render: (_, record) => (
        <Switch
          checked={record.enabled}
          checkedChildren="启用"
          unCheckedChildren="停用"
          disabled={!access.canEditAiServices}
          onChange={async (checked) => {
            await updateAiServiceConfigStatus(record.id, checked);
            message.success(checked ? '已启用' : '已停用');
            reload();
          }}
        />
      ),
    },
    {
      title: '测试状态',
      dataIndex: 'lastTestStatus',
      search: false,
      responsive: ['lg', 'xl', 'xxl'],
      render: (_, record) => (
        <Tag color={testStatusColor[record.lastTestStatus]}>
          {record.lastTestStatus === 'SUCCESS'
            ? '测试成功'
            : record.lastTestStatus === 'FAILED'
              ? '测试失败'
              : '未测试'}
        </Tag>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      valueType: 'dateTime',
      search: false,
      responsive: ['xl', 'xxl'],
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      fixed: 'right',
      align: 'center',
      render: (_, record) => {
        const actions = [
          access.canEditAiServices && (
            <ServiceEditor
              key="edit"
              record={record}
              providerOptions={providerOptions}
              trigger={
                <Button type="link" icon={<EditOutlined />}>
                  编辑
                </Button>
              }
              onDone={reload}
            />
          ),
          access.canTestAiServices && (
            <Button
              key="test"
              type="link"
              icon={<ThunderboltOutlined />}
              onClick={async () => {
                await testAiServiceConfig(record.id);
                message.success('测试已完成');
                reload();
              }}
            >
              测试
            </Button>
          ),
          access.canDeleteAiServices && (
            <Popconfirm
              key="delete"
              title="确认删除该服务配置？"
              onConfirm={async () => {
                await deleteAiServiceConfig(record.id);
                message.success('AI 服务已删除');
                reload();
              }}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          ),
        ].filter(Boolean);
        return <Space>{actions}</Space>;
      },
    },
  ];

  return (
    <PageContainer>
      <ProTable<AiServiceConfig>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="AI服务配置"
        search={false}
        tableLayout="fixed"
        scroll={{ x: 1400 }}
        request={async () => {
          const response = await queryAiServiceConfigs();
          return { data: response.data, success: response.success };
        }}
        columns={columns}
        toolBarRender={() =>
          access.canCreateAiServices
            ? [
                <CreateServiceDropdown
                  key="create"
                  providerOptions={providerOptions}
                  onDone={reload}
                />,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default AiServiceManagement;
