import { PageContainer, ProDescriptions } from '@ant-design/pro-components';
import { useParams } from '@umijs/max';
import { App, Button, Empty, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import {
  acceptInvitation,
  queryInvitation,
  rejectInvitation,
} from '@/services/account-team/invitation';
import type { TenantInvitation } from '@/services/account-team/types';

const InvitationDetail = () => {
  const { token } = useParams<{ token: string }>();
  const { message } = App.useApp();
  const [invitation, setInvitation] = useState<TenantInvitation>();

  const load = async () => {
    if (!token) return;
    const response = await queryInvitation(token);
    setInvitation(response.data);
  };

  useEffect(() => {
    load();
  }, [token]);

  if (!token) {
    return (
      <PageContainer>
        <Empty description="邀请链接无效" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="团队邀请详情"
      extra={
        invitation?.status === 'PENDING' ? (
          <Space>
            <Button
              type="primary"
              onClick={async () => {
                await acceptInvitation(token);
                message.success('已加入团队');
                await load();
              }}
            >
              接受邀请
            </Button>
            <Button
              danger
              onClick={async () => {
                await rejectInvitation(token);
                message.success('已拒绝邀请');
                await load();
              }}
            >
              拒绝邀请
            </Button>
          </Space>
        ) : null
      }
    >
      {invitation ? (
        <ProDescriptions<TenantInvitation>
          bordered
          column={1}
          dataSource={invitation}
          columns={[
            { title: '团队', dataIndex: 'tenantName' },
            { title: '邀请手机号', dataIndex: 'inviteMobile' },
            {
              title: '状态',
              dataIndex: 'status',
              render: (_, record) => (
                <Tag color={record.status === 'PENDING' ? 'blue' : 'default'}>
                  {record.status}
                </Tag>
              ),
            },
            {
              title: '过期时间',
              dataIndex: 'expiredAt',
              valueType: 'dateTime',
            },
            {
              title: '创建时间',
              dataIndex: 'createdAt',
              valueType: 'dateTime',
            },
          ]}
        />
      ) : null}
    </PageContainer>
  );
};

export default InvitationDetail;
