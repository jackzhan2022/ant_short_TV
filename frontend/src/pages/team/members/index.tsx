import { PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormSelect,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Form, Popconfirm, Space, Tag } from 'antd';
import { useModel } from '@umijs/max';
import { useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { Role, TenantMember } from '@/services/account-team/types';
import {
  createInvitation,
  queryMemberRoles,
  queryTenantRoles,
  queryTenantMembers,
  removeTenantMember,
  updateMemberRoles,
} from './service';

const MemberRoleEditor = ({
  tenantId,
  member,
  onDone,
}: {
  tenantId: number;
  member: TenantMember;
  onDone: () => void;
}) => {
  const [form] = Form.useForm<{ roleIds: number[] }>();
  const { message } = App.useApp();
  const [roles, setRoles] = useState<Role[]>([]);

  const load = async () => {
    const [roleResponse, memberRoleResponse] = await Promise.all([
      queryTenantRoles(tenantId),
      queryMemberRoles(tenantId, member.id),
    ]);
    setRoles(roleResponse.data);
    form.setFieldsValue({
      roleIds: memberRoleResponse.data.map((role) => role.id),
    });
  };

  return (
    <ModalForm<{ roleIds: number[] }>
      title="分配成员角色"
      form={form}
      trigger={
        <Button type="link" icon={<SafetyCertificateOutlined />}>
          角色
        </Button>
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
        await updateMemberRoles(tenantId, member.id, values.roleIds || []);
        message.success('成员角色已更新');
        onDone();
        return true;
      }}
    >
      <ProFormSelect
        name="roleIds"
        label="角色"
        mode="multiple"
        options={roles
          .filter((role) => role.status === 'ACTIVE')
          .map((role) => ({
            label: `${role.name} (${role.code})`,
            value: role.id,
          }))}
        rules={[{ required: true, message: '请选择角色' }]}
      />
    </ModalForm>
  );
};

export const MemberTabContent = () => {
  const tenantId = getCurrentTenantId();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const { initialState } = useModel('@@initialState');
  const isOwner = initialState?.selectedTenant?.membership.memberType === 'OWNER';

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const columns: ProColumns<TenantMember>[] = [
    { title: '昵称', dataIndex: 'nickname' },
    { title: '手机号', dataIndex: 'mobile' },
    {
      title: '身份',
      dataIndex: 'memberType',
      render: (_, record) => (
        <Tag color={record.memberType === 'OWNER' ? 'blue' : 'default'}>
          {record.memberType}
        </Tag>
      ),
    },
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
          {isOwner && record.memberType === 'MEMBER' && (
            <>
              <MemberRoleEditor
                tenantId={tenantId}
                member={record}
                onDone={() => actionRef.current?.reload()}
              />
              <Popconfirm
                title="确认移除该成员？"
                onConfirm={async () => {
                  await removeTenantMember(tenantId, record.id);
                  message.success('成员已移除');
                  actionRef.current?.reload();
                }}
              >
                <Button type="link" danger>
                  移除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <ProTable<TenantMember>
        actionRef={actionRef}
        rowKey="id"
        headerTitle="团队成员"
        search={false}
        request={async () => {
          const response = await queryTenantMembers(tenantId);
          return { data: response.data, success: response.success };
        }}
        columns={columns}
        toolBarRender={() =>
          isOwner
            ? [
                <ModalForm<{ mobile: string }>
                  key="invite"
                  title="邀请成员"
                  trigger={
                    <Button type="primary" icon={<PlusOutlined />}>
                      邀请成员
                    </Button>
                  }
                  modalProps={{ destroyOnHidden: true }}
                  onFinish={async (values) => {
                    await createInvitation(tenantId, values.mobile);
                    message.success('邀请已创建');
                    return true;
                  }}
                >
                  <ProFormText
                    name="mobile"
                    label="手机号"
                    rules={[
                      { required: true, message: '请输入手机号' },
                      { pattern: /^1\d{10}$/, message: '手机号格式错误' },
                    ]}
                  />
                </ModalForm>,
              ]
            : []
        }
      />
    </PageContainer>
  );
};

export default MemberTabContent;
