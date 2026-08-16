import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  PageContainer,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Popconfirm, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { TenantMember } from '@/services/account-team/types';
import {
  createInvitation,
  queryCurrentTenant,
  queryTenantMembers,
  removeTenantMember,
} from './service';

const TeamMembers = () => {
  const tenantId = getCurrentTenantId();
  const actionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const [isOwner, setIsOwner] = useState(false);

  useEffect(() => {
    if (!tenantId) return;

    queryCurrentTenant()
      .then((response) => {
        setIsOwner(response.data.memberType === 'OWNER');
      })
      .catch(() => {
        setIsOwner(false);
      });
  }, [tenantId]);

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

export default TeamMembers;
