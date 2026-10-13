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
import { statusText } from '@/utils/fieldDictionary';
import { useTeamEntry } from '../../useTeamEntry';

const InvitationDetail = () => {
  const { token } = useParams<{ token: string }>();
  const { message } = App.useApp();
  const requestTeamEntry = useTeamEntry();
  const [invitation, setInvitation] = useState<TenantInvitation>();
  const [processing, setProcessing] = useState(false);

  const enterTeam = async (tenantId: number) => {
    try {
      await requestTeamEntry(tenantId);
      message.success('已加入团队');
    } catch {
      message.warning(
        '已加入团队，暂时无法进入。可点击进入团队重试，不必重复接受邀请。',
      );
    }
  };

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
              loading={processing}
              onClick={async () => {
                setProcessing(true);
                try {
                  const response = await acceptInvitation(token);
                  setInvitation(response.data);
                  await enterTeam(response.data.tenantId);
                } catch {
                  // The request layer reports acceptance errors; keep the invitation available to retry.
                } finally {
                  setProcessing(false);
                }
              }}
            >
              接受邀请
            </Button>
            <Button
              danger
              disabled={processing}
              onClick={async () => {
                await rejectInvitation(token);
                message.success('已拒绝邀请');
                await load();
              }}
            >
              拒绝邀请
            </Button>
          </Space>
        ) : invitation?.status === 'ACCEPTED' ? (
          <Button
            type="primary"
            loading={processing}
            onClick={async () => {
              setProcessing(true);
              try {
                await enterTeam(invitation.tenantId);
              } finally {
                setProcessing(false);
              }
            }}
          >
            进入团队
          </Button>
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
                  {statusText(record.status)}
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
