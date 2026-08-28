import {
  ModalForm,
  PageContainer,
  ProForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { App, Button, Card, Empty, Popconfirm, Space, Statistic, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  TeamPointAccount,
  TeamPointTransaction,
  TenantMember,
  TenantSummary,
  TenantType,
} from '@/services/account-team/types';
import {
  leaveTenant,
  queryTeamPointAccount,
  queryTeamPointTransactions,
  queryTenant,
  queryTenantMembers,
  transferOwner,
  updateTenant,
  updateTenantStatus,
} from './service';

const tenantTypeOptions = [
  { label: '企业', value: 'COMPANY' },
  { label: '工作室', value: 'STUDIO' },
  { label: '个人', value: 'PERSONAL' },
  { label: '其他', value: 'OTHER' },
];

const TeamSettings = () => {
  const tenantId = getCurrentTenantId();
  const { message } = App.useApp();
  const [tenant, setTenant] = useState<TenantSummary>();
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [pointAccount, setPointAccount] = useState<TeamPointAccount>();
  const [pointTransactions, setPointTransactions] = useState<
    TeamPointTransaction[]
  >([]);

  const load = async () => {
    if (!tenantId) return;
    const [tenantResponse, pointResponse, transactionResponse] =
      await Promise.all([
        queryTenant(tenantId),
        queryTeamPointAccount(tenantId),
        queryTeamPointTransactions(tenantId),
      ]);
    setTenant(tenantResponse.data);
    setPointAccount(pointResponse.data);
    setPointTransactions(transactionResponse.data.records);
    if (tenantResponse.data.memberType === 'OWNER') {
      const memberResponse = await queryTenantMembers(tenantId);
      setMembers(memberResponse.data);
    }
  };

  useEffect(() => {
    load();
  }, [tenantId]);

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const isOwner = tenant?.memberType === 'OWNER';
  const transferOptions = members
    .filter((member) => member.memberType === 'MEMBER')
    .map((member) => ({
      label: `${member.nickname || member.mobile} (${member.mobile})`,
      value: member.id,
    }));

  return (
    <PageContainer
      extra={
        tenant ? (
          <Space>
            <Tag color={tenant.status === 'ACTIVE' ? 'green' : 'red'}>
              {tenant.status === 'ACTIVE' ? '正常' : '停用'}
            </Tag>
            {isOwner && (
              <Button
                onClick={async () => {
                  await updateTenantStatus(
                    tenant.id,
                    tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                  );
                  message.success('团队状态已更新');
                  await load();
                }}
              >
                {tenant.status === 'ACTIVE' ? '停用团队' : '启用团队'}
              </Button>
            )}
            {isOwner && (
              <ModalForm<{ targetMemberId: number }>
                title="转让团队所有权"
                trigger={<Button>转让 Owner</Button>}
                modalProps={{ destroyOnHidden: true }}
                onFinish={async (values) => {
                  await transferOwner(tenant.id, values.targetMemberId);
                  message.success('所有权已转让');
                  await load();
                  return true;
                }}
              >
                <ProFormSelect
                  name="targetMemberId"
                  label="目标成员"
                  options={transferOptions}
                  rules={[{ required: true, message: '请选择目标成员' }]}
                />
              </ModalForm>
            )}
            <Popconfirm
              title="确认退出当前团队？"
              onConfirm={async () => {
                await leaveTenant(tenant.id);
                message.success('已退出团队');
                localStorage.removeItem('currentTenantId');
                setTenant(undefined);
              }}
            >
              <Button danger>退出团队</Button>
            </Popconfirm>
          </Space>
        ) : null
      }
    >
      {tenant && (
        <ProForm<{
          name: string;
          type: TenantType;
          logo?: string;
          description?: string;
        }>
          key={tenant.id}
          layout="vertical"
          readonly={!isOwner}
          initialValues={tenant}
          submitter={isOwner ? undefined : false}
          onFinish={async (values) => {
            await updateTenant(tenant.id, values);
            message.success('团队信息已更新');
            await load();
          }}
        >
          <ProFormText
            name="name"
            label="团队名称"
            rules={[{ required: true, message: '请输入团队名称' }]}
          />
          <ProFormSelect
            name="type"
            label="团队类型"
            options={tenantTypeOptions}
            rules={[{ required: true, message: '请选择团队类型' }]}
          />
          <ProFormText name="logo" label="Logo 地址" />
          <ProFormTextArea name="description" label="团队简介" />
        </ProForm>
      )}
      {tenant && pointAccount && (
        <Card
          title="团队积分"
          style={{ marginTop: 24 }}
        >
          <Space size={48} wrap>
            <Statistic title="可用积分" value={pointAccount.balance} suffix="点" />
            <Statistic title="累计获得" value={pointAccount.totalGranted} suffix="点" />
            <Statistic
              title="累计消耗"
              value={pointAccount.totalConsumed}
              suffix="点"
            />
          </Space>
          <Table<TeamPointTransaction>
            rowKey="id"
            size="small"
            style={{ marginTop: 24 }}
            dataSource={pointTransactions}
            pagination={false}
            columns={[
              {
                title: '类型',
                dataIndex: 'transactionType',
                render: (value) =>
                  value === 'AI_CONSUME'
                    ? 'AI 消耗'
                    : value === 'ADJUST_GRANT'
                      ? '手动增加'
                      : '手动扣减',
              },
              {
                title: '变动',
                dataIndex: 'changeAmount',
                render: (value: number) => (
                  <Tag color={value > 0 ? 'green' : 'red'}>{value}</Tag>
                ),
              },
              { title: '余额', dataIndex: 'balanceAfter' },
              { title: '说明', dataIndex: 'description', ellipsis: true },
              { title: '时间', dataIndex: 'createdAt' },
            ]}
          />
        </Card>
      )}
    </PageContainer>
  );
};

export default TeamSettings;
