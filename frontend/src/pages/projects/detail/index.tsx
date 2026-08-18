import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useParams } from '@umijs/max';
import { App, Button, Descriptions, Empty, Form, Popconfirm, Space, Tabs, Tag, Tree } from 'antd';
import type { TreeProps } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  Organization,
  Permission,
  PermissionTreeNode,
  Project,
  ProjectDataScope,
  ProjectMember,
  ProjectRole,
  ProjectRoleStatus,
  TenantMember,
} from '@/services/account-team/types';
import type {
  ProjectMemberFormValues,
  ProjectRoleFormValues,
} from '@/services/account-team/project';
import {
  addProjectMember,
  createProjectRole,
  deleteProjectRole,
  queryOrganizations,
  queryPermissionTree,
  queryProject,
  queryProjectMembers,
  queryProjectRolePermissions,
  queryProjectRoles,
  queryTenantMembers,
  removeProjectMember,
  updateProjectMemberRole,
  updateProjectRole,
} from './service';
import ScriptCreationWorkspace from './components/ScriptCreationWorkspace';
import AiImageProductionWorkspace from './components/AiImageProductionWorkspace';
import ShotProductionWorkspace from './components/ShotProductionWorkspace';

const statusText: Record<Project['status'], string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
};

const dataScopeText: Record<ProjectDataScope, string> = {
  ALL: '全部数据',
  ORGANIZATION: '所属组织',
  PROJECT: '当前项目',
};

const roleStatusText: Record<ProjectRoleStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

const flattenOrganizations = (items: Organization[]): Organization[] =>
  items.flatMap((item) => [item, ...flattenOrganizations(item.children || [])]);

const toPermissionCodes = (checked: Parameters<NonNullable<TreeProps['onCheck']>>[0]) => {
  const keys = Array.isArray(checked) ? checked : checked.checked;
  return keys.map(String).filter((key) => key.includes(':'));
};

const PermissionTreeField = ({ treeData }: { treeData: PermissionTreeNode[] }) => {
  const form = Form.useFormInstance<ProjectRoleFormValues>();
  const value = Form.useWatch('permissionCodes', form) ?? [];

  return (
    <Form.Item name="permissionCodes" label="权限">
      <Tree
        checkable
        defaultExpandAll
        checkedKeys={value}
        treeData={treeData}
        onCheck={(checked) => {
          form.setFieldValue('permissionCodes', toPermissionCodes(checked));
        }}
      />
    </Form.Item>
  );
};

const ProjectMemberEditor = ({
  projectId,
  members,
  organizations,
  roles,
  onDone,
}: {
  projectId: number;
  members: TenantMember[];
  organizations: Organization[];
  roles: ProjectRole[];
  onDone: () => void;
}) => {
  const { message } = App.useApp();
  return (
    <ModalForm<ProjectMemberFormValues>
      title="添加项目成员"
      trigger={
        <Button type="primary" icon={<PlusOutlined />}>
          添加成员
        </Button>
      }
      modalProps={{ destroyOnHidden: true }}
      onFinish={async (values) => {
        await addProjectMember(projectId, {
          ...values,
          organizationId: values.organizationId || null,
        });
        message.success('项目成员已添加');
        onDone();
        return true;
      }}
    >
      <ProFormSelect
        name="userId"
        label="成员"
        options={members.map((member) => ({
          label: member.nickname || member.mobile || String(member.userId),
          value: member.userId,
        }))}
        rules={[{ required: true, message: '请选择成员' }]}
      />
      <ProFormSelect
        name="organizationId"
        label="所属组织"
        allowClear
        options={organizations.map((item) => ({
          label: `${'　'.repeat(Math.max(item.level - 1, 0))}${item.name}`,
          value: item.id,
        }))}
      />
      <ProFormSelect
        name="roleId"
        label="项目角色"
        options={roles
          .filter((role) => role.status === 'ACTIVE')
          .map((role) => ({ label: `${role.name} (${role.code})`, value: role.id }))}
      />
    </ModalForm>
  );
};

const ProjectRoleEditor = ({
  projectId,
  role,
  permissionTree,
  onDone,
}: {
  projectId: number;
  role?: ProjectRole;
  permissionTree: PermissionTreeNode[];
  onDone: () => void;
}) => {
  const [form] = Form.useForm<ProjectRoleFormValues>();
  const { message } = App.useApp();
  const isEdit = Boolean(role);

  const load = async () => {
    if (!role) {
      form.setFieldsValue({ dataScope: 'PROJECT', permissionCodes: [] });
      return;
    }
    const response = await queryProjectRolePermissions(projectId, role.id);
    form.setFieldsValue({
      name: role.name,
      description: role.description || undefined,
      status: role.status,
      dataScope: role.dataScope,
      permissionCodes: response.data.map((permission: Permission) => permission.code),
    });
  };

  return (
    <ModalForm<ProjectRoleFormValues>
      title={isEdit ? '编辑项目角色' : '创建项目角色'}
      form={form}
      trigger={
        role ? (
          <Button type="link" icon={<EditOutlined />} disabled={role.isSystem}>
            编辑
          </Button>
        ) : (
          <Button type="primary" icon={<PlusOutlined />}>
            创建角色
          </Button>
        )
      }
      modalProps={{ destroyOnHidden: true }}
      onOpenChange={async (open) => {
        if (open) {
          await load();
        } else {
          form.resetFields();
        }
      }}
      onFinish={async (values) => {
        const payload = {
          ...values,
          code: values.code?.trim().toUpperCase(),
          permissionCodes: (values.permissionCodes || []).filter((code) =>
            code.includes(':'),
          ),
        };
        if (role) {
          await updateProjectRole(projectId, role.id, payload);
          message.success('项目角色已更新');
        } else {
          await createProjectRole(projectId, payload);
          message.success('项目角色已创建');
        }
        onDone();
        return true;
      }}
    >
      {!isEdit && (
        <ProFormText
          name="code"
          label="角色编码"
          fieldProps={{ style: { textTransform: 'uppercase' } }}
          rules={[
            { required: true, message: '请输入角色编码' },
            {
              pattern: /^[A-Za-z][A-Za-z0-9_]{1,49}$/,
              message: '使用2-50位字母、数字或下划线',
            },
          ]}
        />
      )}
      <ProFormText
        name="name"
        label="角色名称"
        rules={[{ required: true, message: '请输入角色名称' }]}
      />
      <ProFormTextArea name="description" label="角色描述" />
      {isEdit && (
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '启用', value: 'ACTIVE' },
            { label: '停用', value: 'DISABLED' },
          ]}
        />
      )}
      <ProFormSelect
        name="dataScope"
        label="数据范围"
        options={[
          { label: '全部数据', value: 'ALL' },
          { label: '所属组织', value: 'ORGANIZATION' },
          { label: '当前项目', value: 'PROJECT' },
        ]}
      />
      <PermissionTreeField treeData={permissionTree} />
    </ModalForm>
  );
};

const ProjectDetail = () => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const tenantId = getCurrentTenantId();
  const memberActionRef = useRef<ActionType | null>(null);
  const roleActionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [project, setProject] = useState<Project>();
  const [organizationsTree, setOrganizationsTree] = useState<Organization[]>([]);
  const [tenantMembers, setTenantMembers] = useState<TenantMember[]>([]);
  const [roles, setRoles] = useState<ProjectRole[]>([]);
  const [permissionTree, setPermissionTree] = useState<PermissionTreeNode[]>([]);

  const organizations = useMemo(
    () => flattenOrganizations(organizationsTree),
    [organizationsTree],
  );

  const loadBase = async () => {
    const [projectResponse, orgResponse, memberResponse, permissionResponse] =
      await Promise.all([
        queryProject(projectId),
        queryOrganizations(),
        tenantId ? queryTenantMembers(tenantId) : Promise.resolve({ data: [] }),
        queryPermissionTree(),
      ]);
    setProject(projectResponse.data);
    setOrganizationsTree(orgResponse.data);
    setTenantMembers(memberResponse.data as TenantMember[]);
    setPermissionTree(permissionResponse.data);
  };

  const loadRoles = async () => {
    const response = await queryProjectRoles(projectId);
    setRoles(response.data);
    roleActionRef.current?.reload();
  };

  useEffect(() => {
    if (projectId) {
      loadBase();
      loadRoles();
    }
  }, [projectId, tenantId]);

  if (!projectId) {
    return (
      <PageContainer>
        <Empty description="项目不存在" />
      </PageContainer>
    );
  }

  const memberColumns: ProColumns<ProjectMember>[] = [
    { title: '成员', dataIndex: 'nickname' },
    { title: '手机号', dataIndex: 'mobile', search: false },
    { title: '所属组织', dataIndex: 'organizationName', search: false },
    { title: '项目角色', dataIndex: 'roleName', search: false },
    {
      title: '加入时间',
      dataIndex: 'joinedAt',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <ModalForm<{ roleId: number }>
            title="调整项目角色"
            trigger={
              <Button type="link" icon={<SafetyCertificateOutlined />}>
                角色
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            initialValues={{ roleId: record.roleId }}
            onFinish={async (values) => {
              await updateProjectMemberRole(projectId, record.userId, values.roleId);
              message.success('成员角色已更新');
              memberActionRef.current?.reload();
              return true;
            }}
          >
            <ProFormSelect
              name="roleId"
              label="项目角色"
              options={roles
                .filter((role) => role.status === 'ACTIVE')
                .map((role) => ({
                  label: `${role.name} (${role.code})`,
                  value: role.id,
                }))}
              rules={[{ required: true, message: '请选择项目角色' }]}
            />
          </ModalForm>
          <Popconfirm
            title="确认移除该项目成员？"
            onConfirm={async () => {
              await removeProjectMember(projectId, record.userId);
              message.success('项目成员已移除');
              memberActionRef.current?.reload();
              loadBase();
            }}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              移除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const roleColumns: ProColumns<ProjectRole>[] = [
    { title: '角色名称', dataIndex: 'name' },
    { title: '角色编码', dataIndex: 'code', search: false },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      search: false,
      renderText: (_, record) => dataScopeText[record.dataScope],
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
          {roleStatusText[record.status]}
        </Tag>
      ),
    },
    { title: '说明', dataIndex: 'description', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <ProjectRoleEditor
            projectId={projectId}
            role={record}
            permissionTree={permissionTree}
            onDone={loadRoles}
          />
          {!record.isSystem && (
            <Popconfirm
              title="确认删除该项目角色？"
              onConfirm={async () => {
                await deleteProjectRole(projectId, record.id);
                message.success('项目角色已删除');
                await loadRoles();
              }}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title={project?.name || '项目详情'}>
      <Tabs
        items={[
          {
            key: 'base',
            label: '基本信息',
            children: project ? (
              <Descriptions
                bordered
                column={{ xs: 1, md: 2 }}
                items={[
                  { label: '项目名称', children: project.name },
                  { label: '项目编码', children: project.code },
                  { label: '所属组织', children: project.organizationName || '-' },
                  { label: '负责人', children: project.ownerName || '-' },
                  {
                    label: '项目状态',
                    children: <Tag>{statusText[project.status]}</Tag>,
                  },
                  { label: '成员数', children: project.memberCount },
                  { label: '开始时间', children: project.startDate || '-' },
                  { label: '结束时间', children: project.endDate || '-' },
                  {
                    label: '项目描述',
                    span: 'filled',
                    children: project.description || '-',
                  },
                ]}
              />
            ) : (
              <Empty />
            ),
          },
          {
            key: 'members',
            label: '项目成员',
            children: (
              <ProTable<ProjectMember>
                actionRef={memberActionRef}
                rowKey="id"
                search={false}
                columns={memberColumns}
                request={async () => {
                  const response = await queryProjectMembers(projectId);
                  return { data: response.data, success: response.success };
                }}
                toolBarRender={() => [
                  <ProjectMemberEditor
                    key="add"
                    projectId={projectId}
                    members={tenantMembers}
                    organizations={organizations}
                    roles={roles}
                    onDone={() => {
                      memberActionRef.current?.reload();
                      loadBase();
                    }}
                  />,
                ]}
              />
            ),
          },
          {
            key: 'roles',
            label: '项目角色',
            children: (
              <ProTable<ProjectRole>
                actionRef={roleActionRef}
                rowKey="id"
                search={false}
                columns={roleColumns}
                request={async () => {
                  const response = await queryProjectRoles(projectId);
                  setRoles(response.data);
                  return { data: response.data, success: response.success };
                }}
                toolBarRender={() => [
                  <ProjectRoleEditor
                    key="create"
                    projectId={projectId}
                    permissionTree={permissionTree}
                    onDone={loadRoles}
                  />,
                ]}
              />
            ),
          },
          {
            key: 'creation',
            label: '剧本创作',
            children: (
              <ScriptCreationWorkspace
                projectId={projectId}
                projectName={project?.name}
              />
            ),
          },
          {
            key: 'image-production',
            label: '图片生产',
            children: <AiImageProductionWorkspace projectId={projectId} />,
          },
          {
            key: 'shot-production',
            label: '语音字幕与单镜头',
            children: <ShotProductionWorkspace projectId={projectId} />,
          },
          {
            key: 'logs',
            label: '操作记录',
            children: <Empty description="暂无操作记录" />,
          },
        ]}
      />
    </PageContainer>
  );
};

export default ProjectDetail;
