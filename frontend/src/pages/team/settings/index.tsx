import {
  ModalForm,
  PageContainer,
  ProForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { App, Button, Empty, Popconfirm, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  TenantMember,
  TenantSummary,
  TenantType,
} from '@/services/account-team/types';
import {
  leaveTenant,
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

  const load = async () => {
    if (!tenantId) return;
    const tenantResponse = await queryTenant(tenantId);
    setTenant(tenantResponse.data);
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
    </PageContainer>
  );
};

export default TeamSettings;
