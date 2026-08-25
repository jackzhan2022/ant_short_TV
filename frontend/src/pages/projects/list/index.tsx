import {
  EditOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
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
import { history, useAccess } from '@umijs/max';
import { App, Button, Empty, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  Project,
  ProjectStatus,
  TenantMember,
} from '@/services/account-team/types';
import type { ProjectFormValues } from '@/services/account-team/project';
import {
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

const ProjectEditor = ({
  project,
  members,
  onDone,
}: {
  project: Project;
  members: TenantMember[];
  onDone: () => void;
}) => {
  const { message } = App.useApp();
  const memberOptions = members.map((member) => ({
    label: member.nickname || member.mobile || String(member.userId),
    value: member.userId,
  }));

  return (
    <ModalForm<ProjectFormValues>
      title="编辑项目"
      trigger={
        <Button type="link" icon={<EditOutlined />}>
          编辑
        </Button>
      }
      modalProps={{ destroyOnHidden: true }}
      initialValues={{
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
          code: values.code?.trim().toUpperCase(),
        };
        await updateProject(project.id, payload);
        message.success('项目已更新');
        onDone();
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="项目名称"
        rules={[{ required: true, message: '请输入项目名称' }]}
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
  const access = useAccess();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [members, setMembers] = useState<TenantMember[]>([]);

  const loadOptions = async () => {
    const memberResponse = tenantId
      ? await queryTenantMembers(tenantId)
      : { data: [] };
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
            onClick={() =>
              history.push(
                `/projects/${record.id}/production-workbench/script`,
              )
            }
          >
            进入
          </Button>
          <Button
            type="link"
            icon={<ProfileOutlined />}
            onClick={() =>
              history.push(`/projects/${record.id}/production-workbench`)
            }
          >
            进度
          </Button>
          {record.capabilities.canEdit && (
            <>
              <ProjectEditor project={record} members={members} onDone={reload} />
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
            </>
          )}
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
        toolBarRender={() =>
          access.canCreateProject
            ? [
                <Button
                  key="create"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => history.push('/short-drama-creation')}
                >
                  创建项目
                </Button>,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default ProjectList;
