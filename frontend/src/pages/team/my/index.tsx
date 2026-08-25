import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { history, useModel } from '@umijs/max';
import { App, Button, Space, Tag } from 'antd';
import { useRef } from 'react';
import type {
  TenantSummary,
  TenantType,
} from '@/services/account-team/types';
import { applyBootstrapSelection } from '@/services/account-team/bootstrap';
import { createTenant, queryMyTenants } from './service';

const tenantTypeOptions = [
  { label: '企业', value: 'COMPANY' },
  { label: '工作室', value: 'STUDIO' },
  { label: '个人', value: 'PERSONAL' },
  { label: '其他', value: 'OTHER' },
];

const MyTeams = () => {
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const { setInitialState } = useModel('@@initialState');

  const columns: ProColumns<TenantSummary>[] = [
    {
      title: '团队名称',
      dataIndex: 'name',
      render: (_, record) => (
        <Button
          type="link"
          onClick={async () => {
            await applyBootstrapSelection(record.id, setInitialState);
            message.success(`已切换至 ${record.name}`);
            history.push('/team/members');
          }}
        >
          {record.name}
        </Button>
      ),
    },
    { title: '团队编码', dataIndex: 'code', search: false },
    {
      title: '团队类型',
      dataIndex: 'type',
      valueEnum: {
        COMPANY: { text: '企业' },
        STUDIO: { text: '工作室' },
        PERSONAL: { text: '个人' },
        OTHER: { text: '其他' },
      },
    },
    {
      title: '身份',
      dataIndex: 'memberType',
      search: false,
      render: (_, record) => (
        <Tag color={record.memberType === 'OWNER' ? 'blue' : 'default'}>
          {record.memberType === 'OWNER' ? 'Owner' : 'Member'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'red'}>
          {record.status === 'ACTIVE' ? '正常' : '停用'}
        </Tag>
      ),
    },
      {
        title: '操作',
        valueType: 'option',
        render: (_, record) => (
          <Space>
            <Button
              type="link"
              onClick={async () => {
                await applyBootstrapSelection(record.id, setInitialState);
                message.success(`已切换至 ${record.name}`);
              }}
            >
              切换
            </Button>
            <Button
              type="link"
              onClick={async () => {
                await applyBootstrapSelection(record.id, setInitialState);
                history.push('/team/members');
              }}
            >
              成员管理
            </Button>
            <Button
              type="link"
              onClick={async () => {
                await applyBootstrapSelection(record.id, setInitialState);
                history.push('/team/settings');
              }}
            >
              团队设置
            </Button>
          </Space>
        ),
      },
  ];

  return (
    <PageContainer>
      <ProTable<TenantSummary>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="团队管理"
        search={false}
        request={async () => {
          const response = await queryMyTenants();
          return { data: response.data, success: response.success };
        }}
        columns={columns}
        toolBarRender={() => [
          <ModalForm<{
            name: string;
            type: TenantType;
            logo?: string;
            description?: string;
          }>
            key="create"
            title="创建创作团队"
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                创建创作团队
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            onFinish={async (values) => {
              await createTenant(values);
              message.success('团队创建成功');
              actionRef.current?.reload();
              return true;
            }}
          >
            <ProFormText
              name="name"
              label="团队名称"
              rules={[{ required: true, message: '请输入团队名称' }]}
            />
            <ProFormSelect
              name="type"
              label="团队类型"
              options={tenantTypeOptions}
              rules={[{ required: true, message: '请选择团队类型' }]}
            />
            <ProFormText name="logo" label="Logo 地址" />
            <ProFormTextArea name="description" label="团队简介" />
          </ModalForm>,
        ]}
      />
    </PageContainer>
  );
};

export default MyTeams;
