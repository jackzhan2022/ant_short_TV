import {
  EditOutlined,
  PlusOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Form, Space, Switch, Tag, Typography } from 'antd';
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
  { label: '音频', value: 'AUDIO' },
];

const serviceTypeValueEnum = {
  TEXT: { text: '文本' },
  IMAGE: { text: '图片' },
  VIDEO: { text: '视频' },
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
      title={isEdit ? '编辑平台 Model' : '新增平台 Model'}
      form={form}
      trigger={trigger}
      modalProps={{ destroyOnHidden: true }}
      onFinish={async (values) => {
        if (record) {
          await updatePlatformModel(record.id, values);
          message.success('Model 已更新');
        } else {
          await createPlatformModel(values);
          message.success('Model 已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormSelect
        name="providerId"
        label="所属 Provider"
        options={providerOptions}
        rules={[{ required: true, message: '请选择所属 Provider' }]}
      />
      <ProFormText
        name="name"
        label="模型名称"
        rules={[{ required: true, message: '请输入模型名称' }]}
      />
      <ProFormText
        name="code"
        label="平台模型 Code"
        rules={[{ required: true, message: '请输入平台模型 Code' }]}
      />
      <ProFormText
        name="modelCode"
        label="真实 Model Code"
        rules={[{ required: true, message: '请输入真实 Model Code' }]}
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

  useEffect(() => {
    queryPlatformProviders()
      .then((response) => setProviders(response.data ?? []))
      .catch(() => message.error('Provider 列表加载失败'));
  }, [message]);

  const reload = () => actionRef.current?.reload();

  const providerNameMap = useMemo(
    () => new Map(providers.map((item) => [item.id, item.name])),
    [providers],
  );

  const columns: ProColumns<PlatformModel>[] = [
    {
      title: '模型',
      dataIndex: 'name',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
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
      title: 'Provider',
      dataIndex: 'providerName',
      search: false,
      width: 180,
      renderText: (value, record) =>
        value || providerNameMap.get(record.providerId) || '-',
    },
    {
      title: '真实 Model Code',
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
            message.success(checked ? 'Model 已启用' : 'Model 已停用');
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
      render: (_, record) =>
        access.canEditPlatformAiModels ? (
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
        ) : null,
    },
  ];

  return (
    <PageContainer>
      <ProTable<PlatformModel>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="平台 Model"
        columns={columns}
        search={false}
        tableLayout="fixed"
        scroll={{ x: 1400 }}
        request={async () => {
          const response = await queryPlatformModels();
          return {
            data: response.data,
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
                      新增 Model
                    </Button>
                  }
                  onDone={reload}
                />,
              ]
            : []
        }
        pagination={{ pageSize: 10 }}
      />
    </PageContainer>
  );
};

export default PlatformModelsPage;
