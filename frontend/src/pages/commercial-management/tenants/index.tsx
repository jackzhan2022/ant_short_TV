import {
  PageContainer,
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Button,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Popconfirm,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import { useRef, useState } from 'react';
import {
  getPlatformTenant,
  queryPlatformTenants,
  type PlatformTenantDetail,
  type PlatformTenantQuery,
  type PlatformTenantStatus,
  type PlatformTenantSummary,
  updatePlatformTenantStatus,
} from './service';

const statusText = (status: PlatformTenantStatus) =>
  status === 'ACTIVE' ? '已启用' : '已停用';

const packageTypeText = (type?: string) =>
  type === 'SUBSCRIPTION'
    ? '会员订阅'
    : type === 'POINT_PACKAGE'
      ? '积分包'
      : '-';

const dateTime = (value?: string) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';

const PlatformTenantManagementPage = () => {
  const access = useAccess();
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(undefined);
  const [detail, setDetail] = useState<PlatformTenantDetail>();
  const [detailLoading, setDetailLoading] = useState(false);

  const loadDetail = async (tenantId: number) => {
    setDetailLoading(true);
    try {
      const response = await getPlatformTenant(tenantId);
      setDetail(response.data);
    } finally {
      setDetailLoading(false);
    }
  };

  const changeStatus = async (tenant: PlatformTenantSummary) => {
    const status: PlatformTenantStatus =
      tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    await updatePlatformTenantStatus(tenant.id, status);
    message.success(status === 'ACTIVE' ? '租户已启用' : '租户已停用');
    actionRef.current?.reload();
    if (detail?.id === tenant.id) {
      await loadDetail(tenant.id);
    }
  };

  const columns: ProColumns<PlatformTenantSummary>[] = [
    {
      title: '租户名称或编码',
      dataIndex: 'keyword',
      hideInTable: true,
    },
    {
      title: '租户',
      dataIndex: 'name',
      search: false,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary">{record.code}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        ACTIVE: { text: '已启用', status: 'Success' },
        DISABLED: { text: '已停用', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={record.status === 'ACTIVE' ? 'success' : 'default'}>
          {statusText(record.status)}
        </Tag>
      ),
    },
    {
      title: '当前套餐类型',
      dataIndex: 'packageType',
      hideInTable: true,
      valueEnum: {
        SUBSCRIPTION: { text: '会员订阅' },
        POINT_PACKAGE: { text: '积分包' },
      },
    },
    {
      title: 'Owner',
      dataIndex: 'owner',
      search: false,
      render: (_, record) => record.owner?.nickname ?? '-',
    },
    {
      title: '成员数',
      dataIndex: 'activeMemberCount',
      search: false,
      width: 90,
    },
    {
      title: '积分余额',
      dataIndex: 'pointBalance',
      search: false,
      width: 110,
    },
    {
      title: '当前套餐',
      dataIndex: 'currentPackage',
      search: false,
      render: (_, record) =>
        record.currentPackage ? (
          <Space orientation="vertical" size={0}>
            <Typography.Text>{record.currentPackage.name}</Typography.Text>
            <Typography.Text type="secondary">
              {dateTime(record.currentPackage.endsAt)} 到期
            </Typography.Text>
          </Space>
        ) : (
          '暂无套餐'
        ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      render: (_, record) => dateTime(record.createdAt),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 190,
      render: (_, record) => [
        <Button key="detail" type="link" onClick={() => void loadDetail(record.id)}>
          查看详情
        </Button>,
        access.canEditPlatformTenantStatus ? (
          <Popconfirm
            key="status"
            title={`确认${record.status === 'ACTIVE' ? '停用' : '启用'}租户“${record.name}”？`}
            description={
              record.status === 'ACTIVE'
                ? '停用后，该租户成员将无法进入团队业务。'
                : '启用后，该租户的有效成员可重新进入团队业务。'
            }
            okText={record.status === 'ACTIVE' ? '停用' : '启用'}
            cancelText="取消"
            okButtonProps={{ danger: record.status === 'ACTIVE' }}
            onConfirm={() => changeStatus(record)}
          >
            <Button type="link" danger={record.status === 'ACTIVE'}>
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          </Popconfirm>
        ) : null,
      ],
    },
  ];

  return (
    <PageContainer title="租户管理">
      <ProTable<PlatformTenantSummary, PlatformTenantQuery>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          if (!access.canViewPlatformTenants) {
            return { data: [], total: 0, success: false };
          }
          const response = await queryPlatformTenants(params);
          return {
            data: response.data.records,
            total: response.data.total,
            success: response.success,
          };
        }}
      />

      <Drawer
        title={detail ? `${detail.name} · ${detail.code}` : '租户详情'}
        open={Boolean(detail)}
        loading={detailLoading}
        size="large"
        destroyOnHidden
        onClose={() => setDetail(undefined)}
      >
        {detail && (
          <>
            <Descriptions
              title="基本信息"
              column={{ xs: 1, sm: 2 }}
              items={[
                { key: 'status', label: '状态', children: statusText(detail.status) },
                { key: 'type', label: '租户类型', children: detail.type },
                { key: 'owner', label: 'Owner', children: detail.owner?.nickname ?? '-' },
                { key: 'mobile', label: 'Owner 手机号', children: detail.owner?.mobile ?? '-' },
                { key: 'createdAt', label: '创建时间', children: dateTime(detail.createdAt) },
                { key: 'updatedAt', label: '更新时间', children: dateTime(detail.updatedAt) },
                { key: 'description', label: '简介', children: detail.description ?? '-', span: 2 },
              ]}
            />
            <Divider>账户摘要</Divider>
            <Space size={48} wrap>
              <Statistic title="有效成员" value={detail.activeMemberCount} />
              <Statistic title="积分余额" value={detail.pointBalance} />
            </Space>
            <Divider>当前套餐</Divider>
            {detail.currentPackage ? (
              <Descriptions
                column={{ xs: 1, sm: 2 }}
                items={[
                  { key: 'name', label: '套餐名称', children: detail.currentPackage.name },
                  { key: 'type', label: '套餐类型', children: packageTypeText(detail.currentPackage.packageType) },
                  { key: 'startsAt', label: '开始时间', children: dateTime(detail.currentPackage.startsAt) },
                  { key: 'endsAt', label: '结束时间', children: dateTime(detail.currentPackage.endsAt) },
                ]}
              />
            ) : (
              <Empty description="暂无套餐" />
            )}
            <Divider>待生效套餐</Divider>
            {detail.queuedPackages.length > 0 ? (
              <Space orientation="vertical" size={12}>
                {detail.queuedPackages.map((item) => (
                  <Descriptions
                    key={item.subscriptionId}
                    size="small"
                    column={{ xs: 1, sm: 3 }}
                    items={[
                      { key: 'name', label: '套餐名称', children: item.name },
                      { key: 'type', label: '类型', children: packageTypeText(item.packageType) },
                      { key: 'startsAt', label: '生效时间', children: dateTime(item.startsAt) },
                    ]}
                  />
                ))}
              </Space>
            ) : (
              <Empty description="暂无待生效套餐" />
            )}
          </>
        )}
      </Drawer>
    </PageContainer>
  );
};

export default PlatformTenantManagementPage;
