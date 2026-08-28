import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { App, Button, Space, Tag } from 'antd';
import { useModel } from '@umijs/max';
import { useRef } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { statusText } from '@/utils/fieldDictionary';
import type { TenantInvitation } from '@/services/account-team/types';
import {
  acceptInvitation,
  cancelInvitation,
  queryMyInvitations,
  queryTenantInvitations,
  rejectInvitation,
} from './service';

export const InvitationTabContent = () => {
  const actionRef = useRef<ActionType | null>(null);
  const sentActionRef = useRef<ActionType | null>(null);
  const { message } = App.useApp();
  const tenantId = getCurrentTenantId();
  const { initialState } = useModel('@@initialState');
  const isOwner = initialState?.selectedTenant?.membership.memberType === 'OWNER';

  const columns: ProColumns<TenantInvitation>[] = [
    { title: '团队', dataIndex: 'tenantName' },
    { title: '邀请手机号', dataIndex: 'inviteMobile' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (_, record) => {
        const color = record.status === 'PENDING' ? 'blue' : 'default';
        return <Tag color={color}>{statusText(record.status)}</Tag>;
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
        return <Tag color={color}>{statusText(record.status)}</Tag>;
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

export default InvitationTabContent;
