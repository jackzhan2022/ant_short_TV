import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { history, useAccess, useModel } from '@umijs/max';
import { Alert, App, Button, Form, Space, Spin, Tabs, Tag } from 'antd';
import TeamIconPicker from '@/components/TeamIconPicker';
import { TeamIcon } from '@/components/TeamIconPicker/teamIcons';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  clearCurrentTenantId,
  getCurrentTenantId,
} from '@/services/account-team/auth';
import { applyBootstrapSelection } from '@/services/account-team/bootstrap';
import type { TenantSummary, TenantType } from '@/services/account-team/types';
import { InvitationTabContent } from '../invitations';
import { MemberTabContent } from '../members';
import { RoleManagement } from '../roles';
import { useTeamEntry } from '../useTeamEntry';
import { createTenant, queryMyTenants } from './service';
import TeamOnboarding from './TeamOnboarding';

const tenantTypeOptions = [
  { label: '企业', value: 'COMPANY' },
  { label: '工作室', value: 'STUDIO' },
  { label: '个人', value: 'PERSONAL' },
  { label: '其他', value: 'OTHER' },
];

const MyTeams = () => {
  const [teams, setTeams] = useState<TenantSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const requestId = useRef(0);
  const [activeTab, setActiveTab] = useState('teams');
  const { message } = App.useApp();
  const { setInitialState } = useModel('@@initialState');
  const access = useAccess();
  const enterTeam = useTeamEntry();

  const loadTeams = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setLoadError(false);
    try {
      const response = await queryMyTenants();
      if (currentRequest !== requestId.current) return;
      setTeams(response.data);
      if (
        !response.data.some(
          (team) =>
            team.id === getCurrentTenantId() && team.status === 'ACTIVE',
        )
      ) {
        clearCurrentTenantId();
      }
      await setInitialState((state) => ({
        ...state,
        tenants: response.data,
        ...(!response.data.some(
          (team) =>
            team.id === state?.currentTenantId && team.status === 'ACTIVE',
        )
          ? {
              currentTenantId: undefined,
              selectedTenant: null,
              tenantPermissions: [],
            }
          : {}),
      }));
    } catch {
      if (currentRequest === requestId.current) setLoadError(true);
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
  }, [setInitialState]);

  useEffect(() => {
    void loadTeams();
    return () => {
      requestId.current += 1;
    };
  }, [loadTeams]);

  const columns: ProColumns<TenantSummary>[] = [
    {
      title: '团队名称',
      dataIndex: 'name',
      render: (_, record) => (
        <Space>
          <TeamIcon value={record.logo} />
        <Button
          type="link"
          onClick={async () => {
            await applyBootstrapSelection(record.id, setInitialState);
            message.success(`已切换至 ${record.name}`);
            setActiveTab('members');
          }}
        >
          {record.name}
        </Button>
        </Space>
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
              setActiveTab('members');
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

  const createAction = (
    <ModalForm<{
      name: string;
      type: TenantType;
      logo?: string;
      description?: string;
    }>
      key="create"
      title="创建创作团队"
      initialValues={{ type: 'PERSONAL' }}
      trigger={
        <Button type="primary" icon={<PlusOutlined />}>
          创建创作团队
        </Button>
      }
      modalProps={{ destroyOnHidden: true }}
      onFinish={async (values) => {
        const response = await createTenant(values);
        try {
          await enterTeam(response.data.id);
        } catch {
          message.warning(
            '团队已创建，暂时无法进入。请在团队列表中重新选择，不必重复创建。',
          );
          await loadTeams();
          return true;
        }
        message.success('团队创建成功，开始你的第一个项目吧');
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="团队名称"
        placeholder="为你的创作空间起个名字"
        rules={[{ required: true, message: '请输入团队名称' }]}
      />
      <ProFormSelect
        name="type"
        label="团队类型"
        options={tenantTypeOptions}
        extra="一个人创作也可以选择个人，后续随时邀请伙伴加入。"
        rules={[{ required: true, message: '请选择团队类型' }]}
      />
      <Form.Item name="logo" label="团队图标（选填）" extra="从内置图标中选择，不选择时使用默认团队图标。">
        <TeamIconPicker />
      </Form.Item>
      <ProFormTextArea name="description" label="团队简介（选填）" />
    </ModalForm>
  );

  if (loading) {
    return (
      <PageContainer title="团队管理">
        <div role="status" style={{ padding: 80, textAlign: 'center' }}>
          <Spin />
          <p>正在加载团队...</p>
        </div>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer title="团队管理">
        <Alert
          type="error"
          showIcon
          title="团队加载失败"
          description="请检查网络连接后重试。"
          action={<Button onClick={() => void loadTeams()}>重新加载</Button>}
        />
      </PageContainer>
    );
  }

  if (teams.length === 0) {
    return (
      <PageContainer title="开始使用">
        <TeamOnboarding createAction={createAction} />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="团队管理">
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        destroyOnHidden
        items={[
          {
            key: 'teams',
            label: '团队列表',
            children: (
              <ProTable<TenantSummary>
                rowKey="id"
                headerTitle="我的团队"
                search={false}
                dataSource={teams}
                options={{ reload: () => void loadTeams() }}
                columns={columns}
                toolBarRender={() => [createAction]}
              />
            ),
          },
          { key: 'members', label: '成员管理', children: <MemberTabContent /> },
          {
            key: 'invitations',
            label: '邀请管理',
            children: <InvitationTabContent />,
          },
          ...(access.canManageRoles
            ? [
                {
                  key: 'roles',
                  label: '角色管理',
                  children: <RoleManagement mode="roles" />,
                },
                {
                  key: 'permissions',
                  label: '权限树',
                  children: <RoleManagement mode="permissions" />,
                },
              ]
            : []),
        ]}
      />
    </PageContainer>
  );
};

export default MyTeams;
