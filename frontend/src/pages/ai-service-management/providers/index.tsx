import {
  EditOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Form, Space, Switch, Tag, Typography } from 'antd';
import type { ReactElement } from 'react';
import { useEffect, useRef } from 'react';
import { useAccess } from '@umijs/max';
import type {
  PlatformProvider,
  PlatformProviderFormValues,
  PlatformProviderPayload,
} from '../platform-service';
import {
  createPlatformProvider,
  queryPlatformProviders,
  testPlatformProvider,
  updatePlatformProvider,
  updatePlatformProviderStatus,
} from '../platform-service';

const defaultFormValues: PlatformProviderFormValues = {
  name: '',
  code: '',
  supportedTypes: ['TEXT'],
  defaultBaseUrl: '',
  baseUrl: '',
  apiKey: '',
  description: '',
  enabled: true,
};

const serviceTypeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '视频理解', value: 'VIDEO_UNDERSTANDING' },
  { label: '音频', value: 'AUDIO' },
];

const testStatusColor: Record<string, string> = {
  UNTESTED: 'default',
  SUCCESS: 'green',
  FAILED: 'red',
};

const normalizeSupportedTypes = (value?: string | string[]) =>
  Array.isArray(value) ? value.join(',') : value;

const splitSupportedTypes = (value?: string) =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

const ProviderEditor = ({
  record,
  trigger,
  onDone,
}: {
  record?: PlatformProvider;
  trigger: ReactElement;
  onDone: () => void;
}) => {
  const [form] = Form.useForm<PlatformProviderFormValues>();
  const { message } = App.useApp();
  const isEdit = Boolean(record);

  useEffect(() => {
    form.setFieldsValue(
      record
        ? {
            name: record.name,
            code: record.code,
            supportedTypes: splitSupportedTypes(record.supportedTypes),
            defaultBaseUrl: record.defaultBaseUrl,
            baseUrl: record.baseUrl,
            apiKey: '',
            description: record.description,
            enabled: record.status === 'ENABLED',
          }
        : defaultFormValues,
    );
  }, [form, record]);

  return (
    <ModalForm<PlatformProviderFormValues>
      title={isEdit ? '编辑平台 Provider' : '新增平台 Provider'}
      form={form}
      trigger={trigger}
      modalProps={{ destroyOnHidden: true }}
      onFinish={async (values) => {
        const payload: PlatformProviderPayload = {
          ...values,
          supportedTypes: normalizeSupportedTypes(values.supportedTypes),
          apiKey: values.apiKey?.trim(),
        };
        if (record) {
          await updatePlatformProvider(record.id, payload);
          message.success('Provider 已更新');
        } else {
          await createPlatformProvider(payload);
          message.success('Provider 已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="Provider 名称"
        rules={[{ required: true, message: '请输入 Provider 名称' }]}
      />
      <ProFormText
        name="code"
        label="Provider Code"
        rules={[{ required: true, message: '请输入 Provider Code' }]}
      />
      <ProFormSelect
        name="supportedTypes"
        label="支持类型"
        mode="multiple"
        options={serviceTypeOptions}
        rules={[{ required: true, message: '请选择支持类型' }]}
      />
      <ProFormText
        name="baseUrl"
        label="Base URL"
        rules={[{ required: true, message: '请输入 Base URL' }]}
      />
      <ProFormText name="defaultBaseUrl" label="默认 Base URL" />
      <ProFormText
        name="apiKey"
        label={isEdit ? 'API Key（留空表示不修改）' : 'API Key'}
        fieldProps={{ type: 'password', placeholder: record?.apiKey || '' }}
        rules={isEdit ? [] : [{ required: true, message: '请输入 API Key' }]}
      />
      <ProFormSwitch
        name="enabled"
        label="状态"
        checkedChildren="启用"
        unCheckedChildren="停用"
      />
      <ProFormTextArea name="description" label="描述" />
    </ModalForm>
  );
};

const PlatformProvidersPage = () => {
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const access = useAccess();
  const reload = () => actionRef.current?.reload();

  const columns: ProColumns<PlatformProvider>[] = [
    {
      title: 'Provider',
      dataIndex: 'name',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary">{record.code}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '支持类型',
      dataIndex: 'supportedTypes',
      search: false,
      width: 180,
      render: (_, record) =>
        splitSupportedTypes(record.supportedTypes).map((item) => (
          <Tag key={item}>{item}</Tag>
        )),
    },
    {
      title: 'Base URL',
      dataIndex: 'baseUrl',
      search: false,
      ellipsis: true,
      width: 260,
    },
    {
      title: 'API Key',
      dataIndex: 'apiKey',
      search: false,
      width: 180,
      renderText: (value) => value || '未配置',
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
          disabled={!access.canEnablePlatformAiProviders}
          onChange={async (checked) => {
            await updatePlatformProviderStatus(record.id, checked);
            message.success(checked ? 'Provider 已启用' : 'Provider 已停用');
            reload();
          }}
        />
      ),
    },
    {
      title: '测试',
      dataIndex: 'lastTestStatus',
      search: false,
      width: 130,
      render: (_, record) => (
        <Tag color={testStatusColor[record.lastTestStatus || 'UNTESTED']}>
          {record.lastTestStatus === 'SUCCESS'
            ? '成功'
            : record.lastTestStatus === 'FAILED'
              ? '失败'
              : '未测试'}
        </Tag>
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
      width: 180,
      render: (_, record) => (
        <Space>
          {access.canEditPlatformAiProviders && (
            <ProviderEditor
              record={record}
              trigger={
                <Button type="link" icon={<EditOutlined />}>
                  编辑
                </Button>
              }
              onDone={reload}
            />
          )}
          {access.canTestPlatformAiProviders && (
            <Button
              type="link"
              icon={<ThunderboltOutlined />}
              onClick={async () => {
                const response = await testPlatformProvider(record.id);
                if (response.data.status === 'SUCCESS') {
                  message.success(response.data.message || '测试成功');
                } else {
                  message.error(response.data.message || '测试失败');
                }
                reload();
              }}
            >
              测试
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <ProTable<PlatformProvider>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="平台 Provider"
        columns={columns}
        search={false}
        tableLayout="fixed"
        scroll={{ x: 1300 }}
        request={async () => {
          if (!access.canViewPlatformAiProviders) {
            return { data: [], success: true };
          }
          const response = await queryPlatformProviders();
          return {
            data: response.data,
            success: response.success,
          };
        }}
        toolBarRender={() =>
          access.canCreatePlatformAiProviders
            ? [
                <ProviderEditor
                  key="create"
                  trigger={
                    <Button type="primary" icon={<PlusOutlined />}>
                      新增 Provider
                    </Button>
                  }
                  onDone={reload}
                />,
              ]
            : []
        }
        options={{ density: true, fullScreen: true, reload: true }}
        pagination={{ pageSize: 10 }}
      />
    </PageContainer>
  );
};

export default PlatformProvidersPage;
