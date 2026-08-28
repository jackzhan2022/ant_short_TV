import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Form, Popconfirm, Space, Tabs, Tag, Tree } from 'antd';
import type { TreeProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { RoleFormValues } from '@/services/account-team/rbac';
import type {
  Permission,
  PermissionTreeNode,
  Role,
} from '@/services/account-team/types';
import {
  createTenantRole,
  deleteTenantRole,
  queryPermissionTree,
  queryRolePermissions,
  queryTenantRoles,
  updateTenantRole,
  updateTenantRoleStatus,
} from './service';

const roleTypeText: Record<Role['roleType'], string> = {
  SYSTEM: '系统',
  CUSTOM: '自定义',
};

const roleStatusText: Record<Role['status'], string> = {
  ACTIVE: '正常',
  DISABLED: '停用',
};

const toPermissionCodes = (checked: Parameters<NonNullable<TreeProps['onCheck']>>[0]) => {
  const keys = Array.isArray(checked) ? checked : checked.checked;
  return keys.map(String).filter((key) => key.includes(':'));
};

const PermissionTreeField = ({ treeData }: { treeData: PermissionTreeNode[] }) => {
  const form = Form.useFormInstance<RoleFormValues>();
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

const RoleEditor = ({
  role,
  tenantId,
  permissionTree,
  onDone,
}: {
  role?: Role;
  tenantId: number;
  permissionTree: PermissionTreeNode[];
  onDone: () => void;
}) => {
  const [form] = Form.useForm<RoleFormValues>();
  const [open, setOpen] = useState(false);
  const { message } = App.useApp();
  const isEdit = Boolean(role);
  const ownerRole = role?.code === 'OWNER';

  const load = async () => {
    if (!role) {
      form.setFieldsValue({ permissionCodes: [] });
      return;
    }
    const response = await queryRolePermissions(tenantId, role.id);
    form.setFieldsValue({
      name: role.name,
      description: role.description || undefined,
      permissionCodes: response.data.map((permission: Permission) => permission.code),
    });
  };

  return (
    <ModalForm<RoleFormValues>
      title={isEdit ? '编辑角色' : '创建角色'}
      form={form}
      open={open}
      trigger={
        role ? (
          <Button type="link" icon={<EditOutlined />} disabled={ownerRole}>
            编辑
          </Button>
        ) : (
          <Button type="primary" icon={<PlusOutlined />}>
            创建角色
          </Button>
        )
      }
      modalProps={{ destroyOnHidden: true }}
      onOpenChange={async (nextOpen) => {
        setOpen(nextOpen);
        if (nextOpen) {
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
          await updateTenantRole(tenantId, role.id, payload);
          message.success('角色已更新');
        } else {
          await createTenantRole(tenantId, payload);
          message.success('角色已创建');
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
              pattern: /^[A-Za-z][A-Za-z0-9_]{1,63}$/,
              message: '使用2-64位字母、数字或下划线',
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
      <PermissionTreeField treeData={permissionTree} />
    </ModalForm>
  );
};

type RoleManagementProps = { mode?: 'roles' | 'permissions' };

export const RoleManagement = ({ mode }: RoleManagementProps = {}) => {
  const tenantId = getCurrentTenantId();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [permissionTree, setPermissionTree] = useState<PermissionTreeNode[]>([]);

  useEffect(() => {
    queryPermissionTree().then((response) => {
      setPermissionTree(response.data);
    });
  }, []);

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const reload = () => actionRef.current?.reload();

  const columns: ProColumns<Role>[] = [
    { title: '角色名称', dataIndex: 'name' },
    { title: '角色编码', dataIndex: 'code', search: false },
    {
      title: '类型',
      dataIndex: 'roleType',
      valueEnum: {
        SYSTEM: { text: '系统' },
        CUSTOM: { text: '自定义' },
      },
      render: (_, record) => (
        <Tag color={record.roleType === 'SYSTEM' ? 'blue' : 'default'}>
          {roleTypeText[record.roleType]}
        </Tag>
      ),
    },
    {
      title: '成员数',
      dataIndex: 'memberCount',
      search: false,
      align: 'right',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        ACTIVE: { text: '正常', status: 'Success' },
        DISABLED: { text: '停用', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
          {roleStatusText[record.status]}
        </Tag>
      ),
    },
    { title: '描述', dataIndex: 'description', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <RoleEditor
            role={record}
            tenantId={tenantId}
            permissionTree={permissionTree}
            onDone={reload}
          />
          {record.code !== 'OWNER' && (
            <Button
              type="link"
              onClick={async () => {
                await updateTenantRoleStatus(
                  tenantId,
                  record.id,
                  record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                );
                message.success('角色状态已更新');
                reload();
              }}
            >
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          )}
          {record.roleType === 'CUSTOM' && (
            <Popconfirm
              title="确认删除该角色？"
              onConfirm={async () => {
                await deleteTenantRole(tenantId, record.id);
                message.success('角色已删除');
                reload();
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

  const rolesContent = (
    <ProTable<Role>
      actionRef={actionRef}
      rowKey="id"
      headerTitle="权限与角色"
      search={false}
      columns={columns}
      request={async () => {
        const response = await queryTenantRoles(tenantId);
        return { data: response.data, success: response.success };
      }}
      toolBarRender={() => [
        <RoleEditor
          key="create"
          tenantId={tenantId}
          permissionTree={permissionTree}
          onDone={reload}
        />,
      ]}
    />
  );
  const permissionsContent = (
    <Tree defaultExpandAll treeData={permissionTree} selectable={false} />
  );

  if (mode === 'roles') return rolesContent;
  if (mode === 'permissions') return permissionsContent;

  return (
    <PageContainer>
      <Tabs
        items={[
          {
            key: 'roles',
            label: '角色管理',
            children: rolesContent,
          },
          {
            key: 'permissions',
            label: '权限资源树',
            children: permissionsContent,
          },
        ]}
      />
    </PageContainer>
  );
};

export default RoleManagement;
