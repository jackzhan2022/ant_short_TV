import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { App, Button, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { TenantInvitation } from '@/services/account-team/types';
import {
  acceptInvitation,
  cancelInvitation,
  queryCurrentTenant,
  queryMyInvitations,
  queryTenantInvitations,
  rejectInvitation,
} from './service';

const TeamInvitations = () => {
  const actionRef = useRef<ActionType | null>(null);
  const sentActionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const tenantId = getCurrentTenantId();
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

  const columns: ProColumns<TenantInvitation>[] = [
    { title: '团队', dataIndex: 'tenantName' },
    { title: '邀请手机号', dataIndex: 'inviteMobile' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (_, record) => {
        const color = record.status === 'PENDING' ? 'blue' : 'default';
        return <Tag color={color}>{record.status}</Tag>;
      },
    },
    {
      title: '过期时间',
      dataIndex: 'expiredAt',
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) =>
        record.status === 'PENDING' ? (
          <Space>
            <Button
              type="link"
              onClick={async () => {
                await acceptInvitation(record.token);
                message.success('已加入团队');
                actionRef.current?.reload();
                sentActionRef.current?.reload();
              }}
            >
              接受
            </Button>
            <Button
              type="link"
              danger
              onClick={async () => {
                await rejectInvitation(record.token);
                message.success('已拒绝邀请');
                actionRef.current?.reload();
                sentActionRef.current?.reload();
              }}
            >
              拒绝
            </Button>
          </Space>
        ) : null,
    },
  ];

  const sentColumns: ProColumns<TenantInvitation>[] = [
    { title: '邀请手机号', dataIndex: 'inviteMobile' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (_, record) => {
        const color = record.status === 'PENDING' ? 'blue' : 'default';
        return <Tag color={color}>{record.status}</Tag>;
      },
    },
    {
      title: '过期时间',
      dataIndex: 'expiredAt',
      valueType: 'dateTime',
      search: false,
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
      render: (_, record) =>
        record.status === 'PENDING' ? (
          <Button
            type="link"
            danger
            onClick={async () => {
              await cancelInvitation(record.id);
              message.success('邀请已取消');
              sentActionRef.current?.reload();
            }}
          >
            取消
          </Button>
        ) : null,
    },
  ];

  return (
    <PageContainer>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        <ProTable<TenantInvitation>
          actionRef={actionRef}
          rowKey="id"
          headerTitle="收到的团队邀请"
          search={false}
          request={async () => {
            const response = await queryMyInvitations();
            return { data: response.data, success: response.success };
          }}
          columns={columns}
        />
        {tenantId && isOwner && (
          <ProTable<TenantInvitation>
            actionRef={sentActionRef}
            rowKey="id"
            headerTitle="团队已发邀请"
            search={false}
            request={async () => {
              const response = await queryTenantInvitations(tenantId);
              return { data: response.data, success: response.success };
            }}
            columns={sentColumns}
          />
        )}
      </div>
    </PageContainer>
  );
};

export default TeamInvitations;
