import { EditOutlined, FolderOpenOutlined, PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormDatePicker,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { App, Button, Empty, Space, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  Organization,
  Project,
  ProjectStatus,
  TenantMember,
} from '@/services/account-team/types';
import type { ProjectFormValues } from '@/services/account-team/project';
import {
  createProject,
  queryOrganizations,
  queryProjects,
  queryTenantMembers,
  updateProject,
  updateProjectStatus,
} from './service';

const statusText: Record<ProjectStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
};

const statusColor: Record<ProjectStatus, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  PAUSED: 'warning',
  COMPLETED: 'success',
  ARCHIVED: 'default',
};

const flattenOrganizations = (items: Organization[]): Organization[] =>
  items.flatMap((item) => [item, ...flattenOrganizations(item.children || [])]);

const ProjectEditor = ({
  project,
  organizations,
  members,
  onDone,
}: {
  project?: Project;
  organizations: Organization[];
  members: TenantMember[];
  onDone: () => void;
}) => {
  const { message } = App.useApp();
  const isEdit = Boolean(project);
  const organizationOptions = organizations.map((item) => ({
    label: `${'　'.repeat(Math.max(item.level - 1, 0))}${item.name}`,
    value: item.id,
  }));
  const memberOptions = members.map((member) => ({
    label: member.nickname || member.mobile || String(member.userId),
    value: member.userId,
  }));

  return (
    <ModalForm<ProjectFormValues>
      title={isEdit ? '编辑项目' : '创建项目'}
      trigger={
        project ? (
          <Button type="link" icon={<EditOutlined />}>
            编辑
          </Button>
        ) : (
          <Button type="primary" icon={<PlusOutlined />}>
            创建项目
          </Button>
        )
      }
      modalProps={{ destroyOnHidden: true }}
      initialValues={{
        organizationId: project?.organizationId,
        name: project?.name,
        code: project?.code,
        description: project?.description || undefined,
        coverUrl: project?.coverUrl || undefined,
        ownerId: project?.ownerId,
        startDate: project?.startDate || undefined,
        endDate: project?.endDate || undefined,
      }}
      onFinish={async (values) => {
        const payload = {
          ...values,
          organizationId: values.organizationId || null,
          code: values.code?.trim().toUpperCase(),
        };
        if (project) {
          await updateProject(project.id, payload);
          message.success('项目已更新');
        } else {
          await createProject(payload);
          message.success('项目已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="项目名称"
        rules={[{ required: true, message: '请输入项目名称' }]}
      />
      {!isEdit && (
        <ProFormText
          name="code"
          label="项目编码"
          fieldProps={{ style: { textTransform: 'uppercase' } }}
          rules={[
            { required: true, message: '请输入项目编码' },
            {
              pattern: /^[A-Za-z][A-Za-z0-9_]{1,49}$/,
              message: '使用2-50位字母、数字或下划线',
            },
          ]}
        />
      )}
      <ProFormSelect
        name="organizationId"
        label="所属组织"
        allowClear
        options={organizationOptions}
      />
      <ProFormSelect
        name="ownerId"
        label="负责人"
        options={memberOptions}
        rules={[{ required: true, message: '请选择负责人' }]}
      />
      <ProFormTextArea name="description" label="项目描述" />
      <ProFormText name="coverUrl" label="封面地址" />
      <ProFormDatePicker name="startDate" label="开始时间" />
      <ProFormDatePicker name="endDate" label="结束时间" />
    </ModalForm>
  );
};

const ProjectList = () => {
  const tenantId = getCurrentTenantId();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [organizationsTree, setOrganizationsTree] = useState<Organization[]>([]);
  const [members, setMembers] = useState<TenantMember[]>([]);

  const organizations = useMemo(
    () => flattenOrganizations(organizationsTree),
    [organizationsTree],
  );

  const loadOptions = async () => {
    const [orgResponse, memberResponse] = await Promise.all([
      queryOrganizations(),
      tenantId ? queryTenantMembers(tenantId) : Promise.resolve({ data: [] }),
    ]);
    setOrganizationsTree(orgResponse.data);
    setMembers(memberResponse.data as TenantMember[]);
  };

  useEffect(() => {
    if (tenantId) {
      loadOptions();
    }
  }, [tenantId]);

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const reload = () => actionRef.current?.reload();

  const columns: ProColumns<Project>[] = [
    { title: '项目名称', dataIndex: 'name' },
    { title: '项目编码', dataIndex: 'code' },
    {
      title: '所属组织',
      dataIndex: 'organizationId',
      valueEnum: Object.fromEntries(
        organizations.map((item) => [item.id, { text: item.name }]),
      ),
      renderText: (_, record) => record.organizationName || '-',
    },
    { title: '负责人', dataIndex: 'ownerName', search: false },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        NOT_STARTED: { text: '未开始', status: 'Default' },
        IN_PROGRESS: { text: '进行中', status: 'Processing' },
        PAUSED: { text: '已暂停', status: 'Warning' },
        COMPLETED: { text: '已完成', status: 'Success' },
        ARCHIVED: { text: '已归档', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={statusColor[record.status]}>{statusText[record.status]}</Tag>
      ),
    },
    {
      title: '成员数',
      dataIndex: 'memberCount',
      search: false,
      align: 'right',
      width: 90,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<FolderOpenOutlined />}
            onClick={() => history.push(`/projects/${record.id}`)}
          >
            进入
          </Button>
          <ProjectEditor
            project={record}
            organizations={organizations}
            members={members}
            onDone={reload}
          />
          <Button
            type="link"
            disabled={record.status === 'ARCHIVED'}
            onClick={async () => {
              const nextStatus =
                record.status === 'NOT_STARTED' ? 'IN_PROGRESS' : 'ARCHIVED';
              await updateProjectStatus(record.id, nextStatus);
              message.success('项目状态已更新');
              reload();
            }}
          >
            {record.status === 'NOT_STARTED' ? '启动' : '归档'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <ProTable<Project>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="项目列表"
        columns={columns}
        request={async () => {
          const response = await queryProjects();
          return { data: response.data, success: response.success };
        }}
        toolBarRender={() => [
          <ProjectEditor
            key="create"
            organizations={organizations}
            members={members}
            onDone={reload}
          />,
        ]}
      />
    </PageContainer>
  );
};

export default ProjectList;
