import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Form, Popconfirm, Space, Tag, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useEffect, useMemo, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  Organization,
  TenantMember,
} from '@/services/account-team/types';
import { queryTenantMembers } from '@/services/account-team/member';
import type { OrganizationFormValues } from '@/services/account-team/project';
import {
  createOrganization,
  deleteOrganization,
  queryOrganizations,
  updateOrganization,
  updateOrganizationStatus,
} from './service';

const statusText: Record<Organization['status'], string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
};

const flattenOrganizations = (items: Organization[]): Organization[] =>
  items.flatMap((item) => [item, ...flattenOrganizations(item.children || [])]);

const toTreeNodes = (items: Organization[]): DataNode[] =>
  items.map((item) => ({
    key: item.id,
    title: item.name,
    children: toTreeNodes(item.children || []),
  }));

const OrganizationEditor = ({
  organization,
  organizations,
  members,
  onDone,
}: {
  organization?: Organization;
  organizations: Organization[];
  members: TenantMember[];
  onDone: () => void;
}) => {
  const [form] = Form.useForm<OrganizationFormValues>();
  const { message } = App.useApp();
  const isEdit = Boolean(organization);
  const parentOptions = organizations
    .filter((item) => item.id !== organization?.id && item.level < 5)
    .map((item) => ({
      label: `${'　'.repeat(Math.max(item.level - 1, 0))}${item.name}`,
      value: item.id,
    }));

  return (
    <ModalForm<OrganizationFormValues>
      title={isEdit ? '编辑组织' : '新增组织'}
      form={form}
      trigger={
        organization ? (
          <Button type="link" icon={<EditOutlined />}>
            编辑
          </Button>
        ) : (
          <Button type="primary" icon={<PlusOutlined />}>
            新增组织
          </Button>
        )
      }
      modalProps={{ destroyOnHidden: true }}
      onOpenChange={(open) => {
        if (open) {
          form.setFieldsValue({
            parentId: organization?.parentId,
            name: organization?.name,
            code: organization?.code,
            leaderId: organization?.leaderId,
            sort: organization?.sort ?? 0,
          });
        } else {
          form.resetFields();
        }
      }}
      onFinish={async (values) => {
        const payload = {
          ...values,
          parentId: values.parentId || null,
          leaderId: values.leaderId || null,
          code: values.code?.trim().toUpperCase(),
        };
        if (organization) {
          await updateOrganization(organization.id, payload);
          message.success('组织已更新');
        } else {
          await createOrganization(payload);
          message.success('组织已创建');
        }
        onDone();
        return true;
      }}
    >
      <ProFormSelect
        name="parentId"
        label="上级组织"
        allowClear
        options={parentOptions}
      />
      <ProFormText
        name="name"
        label="组织名称"
        rules={[{ required: true, message: '请输入组织名称' }]}
      />
      {!isEdit && (
        <ProFormText
          name="code"
          label="组织编码"
          fieldProps={{ style: { textTransform: 'uppercase' } }}
          rules={[
            { required: true, message: '请输入组织编码' },
            {
              pattern: /^[A-Za-z][A-Za-z0-9_]{1,49}$/,
              message: '使用2-50位字母、数字或下划线',
            },
          ]}
        />
      )}
      <ProFormSelect
        name="leaderId"
        label="负责人"
        allowClear
        options={members.map((member) => ({
          label: member.nickname || member.mobile || String(member.userId),
          value: member.userId,
        }))}
      />
      <ProFormDigit name="sort" label="排序" min={0} initialValue={0} />
    </ModalForm>
  );
};

const OrganizationManagement = () => {
  const tenantId = getCurrentTenantId();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [treeData, setTreeData] = useState<Organization[]>([]);
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [selectedId, setSelectedId] = useState<number>();

  const organizations = useMemo(() => flattenOrganizations(treeData), [treeData]);
  const treeNodes = useMemo(() => toTreeNodes(treeData), [treeData]);

  const load = async () => {
    const [orgResponse, memberResponse] = await Promise.all([
      queryOrganizations(),
      tenantId ? queryTenantMembers(tenantId) : Promise.resolve({ data: [] }),
    ]);
    setTreeData(orgResponse.data);
    setMembers(memberResponse.data as TenantMember[]);
    actionRef.current?.reload();
  };

  useEffect(() => {
    if (tenantId) {
      load();
    }
  }, [tenantId]);

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const visibleOrganizations = selectedId
    ? organizations.filter((item) => item.id === selectedId || item.parentId === selectedId)
    : organizations;

  const columns: ProColumns<Organization>[] = [
    { title: '组织名称', dataIndex: 'name' },
    { title: '组织编码', dataIndex: 'code', search: false },
    { title: '层级', dataIndex: 'level', search: false, width: 80 },
    {
      title: '负责人',
      dataIndex: 'leaderId',
      search: false,
      renderText: (_, record) =>
        members.find((member) => member.userId === record.leaderId)?.nickname || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
          {statusText[record.status]}
        </Tag>
      ),
    },
    { title: '排序', dataIndex: 'sort', search: false, width: 80 },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <OrganizationEditor
            organization={record}
            organizations={organizations}
            members={members}
            onDone={load}
          />
          <Button
            type="link"
            onClick={async () => {
              await updateOrganizationStatus(
                record.id,
                record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
              );
              message.success('组织状态已更新');
              await load();
            }}
          >
            {record.status === 'ACTIVE' ? '停用' : '启用'}
          </Button>
          <Popconfirm
            title="确认删除该组织？"
            onConfirm={async () => {
              await deleteOrganization(record.id);
              message.success('组织已删除');
              await load();
            }}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <div style={{ display: 'grid', gap: 16, gridTemplateColumns: '280px minmax(0, 1fr)' }}>
        <div style={{ background: '#fff', padding: 16 }}>
          <Tree
            defaultExpandAll
            selectedKeys={selectedId ? [selectedId] : []}
            treeData={treeNodes}
            onSelect={(keys) => setSelectedId(keys[0] ? Number(keys[0]) : undefined)}
          />
        </div>
        <ProTable<Organization>
          actionRef={actionRef}
          rowKey="id"
          headerTitle="组织架构"
          search={false}
          columns={columns}
          dataSource={visibleOrganizations}
          pagination={{ pageSize: 10 }}
          toolBarRender={() => [
            <Button key="all" onClick={() => setSelectedId(undefined)}>
              全部组织
            </Button>,
            <OrganizationEditor
              key="create"
              organizations={organizations}
              members={members}
              onDone={load}
            />,
          ]}
        />
      </div>
    </PageContainer>
  );
};

export default OrganizationManagement;
