import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Button, Descriptions, Drawer, Space, Tag, Typography } from 'antd';
import { useRef, useState } from 'react';
import {
  getPlatformCommercialOrder,
  queryPlatformCommercialOrders,
  type CommercialOrderStatus,
  type PlatformCommercialOrderDetail,
  type PlatformCommercialOrderQuery,
  type PlatformCommercialOrderSummary,
} from './service';

const orderStatusText = (status?: CommercialOrderStatus) => (Object({
  PENDING_PAYMENT: '待支付', PAID: '已支付', ENTITLEMENT_PENDING: '权益发放中',
  COMPLETED: '已完成', CLOSED: '已关闭', PAYMENT_EXCEPTION: '支付异常',
}) as Record<CommercialOrderStatus, string>)[status ?? 'PENDING_PAYMENT'] ?? '-';

const orderStatusColor = (status?: CommercialOrderStatus) => (
  status === 'COMPLETED' ? 'success' : status === 'PAYMENT_EXCEPTION' ? 'error' : status === 'PENDING_PAYMENT' ? 'warning' : 'default'
);

const packageTypeText = (type?: string) => type === 'SUBSCRIPTION' ? '会员订阅' : type === 'POINT_PACKAGE' ? '积分包' : '-';
const dateTime = (value?: string) => {
  const date = value ? new Date(value) : undefined;
  return date && !Number.isNaN(date.getTime())
    ? date.toLocaleString('zh-CN', { hour12: false })
    : '-';
};

const PlatformCommercialOrderPage = () => {
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [detail, setDetail] = useState<PlatformCommercialOrderDetail>();
  const [detailLoading, setDetailLoading] = useState(false);

  const loadDetail = async (orderId: number) => {
    setDetailLoading(true);
    try {
      const response = await getPlatformCommercialOrder(orderId);
      setDetail(response.data);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ProColumns<PlatformCommercialOrderSummary>[] = [
    { title: '订单号或租户名称', dataIndex: 'keyword', hideInTable: true },
    { title: '订单状态', dataIndex: 'status', hideInTable: true, valueType: 'select', valueEnum: { PENDING_PAYMENT: { text: '待支付' }, PAID: { text: '已支付' }, ENTITLEMENT_PENDING: { text: '权益发放中' }, COMPLETED: { text: '已完成' }, CLOSED: { text: '已关闭' }, PAYMENT_EXCEPTION: { text: '支付异常' } } },
    { title: '套餐类型', dataIndex: 'packageType', hideInTable: true, valueType: 'select', valueEnum: { POINT_PACKAGE: { text: '积分包' }, SUBSCRIPTION: { text: '会员订阅' } } },
    { title: '订单号', dataIndex: 'merchantOrderNo', search: false },
    { title: '租户', search: false, render: (_, record) => <Space orientation="vertical" size={0}><Typography.Text strong>{record.tenantName ?? '-'}</Typography.Text><Typography.Text type="secondary">{record.tenantCode ?? '-'}</Typography.Text></Space> },
    { title: '套餐', search: false, render: (_, record) => <Space orientation="vertical" size={0}><Typography.Text>{record.packageName}</Typography.Text><Typography.Text type="secondary">V{record.packageVersionNo} · {packageTypeText(record.packageType)}</Typography.Text></Space> },
    { title: '实付金额', search: false, render: (_, record) => `${record.amount} ${record.currency}` },
    { title: '订单状态', dataIndex: 'status', search: false, render: (value) => <Tag color={orderStatusColor(value as CommercialOrderStatus)}>{orderStatusText(value as CommercialOrderStatus)}</Tag> },
    { title: '支付时间', dataIndex: 'paidAt', search: false, render: (value) => dateTime(value as string | undefined) },
    { title: '创建时间', dataIndex: 'createdAt', search: false, render: (value) => dateTime(value as string | undefined) },
    { title: '操作', valueType: 'option', search: false, render: (_, record) => <Button type="link" onClick={() => void loadDetail(record.id)}>查看详情</Button> },
  ];

  return <PageContainer title="订单管理">
    <ProTable<PlatformCommercialOrderSummary, PlatformCommercialOrderQuery>
      actionRef={actionRef}
      rowKey="id"
      columns={columns}
      search={{ labelWidth: 'auto' }}
      pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      request={async (params) => {
        if (!access.canViewCommercialOrders) return { data: [], total: 0, success: false };
        const response = await queryPlatformCommercialOrders(params);
        return { data: response.data.records, total: response.data.total, success: response.success };
      }}
    />
    <Drawer title="订单详情" open={Boolean(detail)} loading={detailLoading} size="large" destroyOnHidden onClose={() => setDetail(undefined)}>
      {detail && <>
        <Descriptions title="订单信息" column={{ xs: 1, sm: 2 }} items={[
          { key: 'orderNo', label: '订单号', children: detail.merchantOrderNo },
          { key: 'status', label: '订单状态', children: orderStatusText(detail.status) },
          { key: 'tenant', label: '租户', children: detail.tenantName ?? '-' },
          { key: 'amount', label: '实付金额', children: `${detail.amount} ${detail.currency}` },
          { key: 'createdAt', label: '创建时间', children: dateTime(detail.createdAt) },
          { key: 'completedAt', label: '完成时间', children: dateTime(detail.completedAt) },
        ]} />
        <Descriptions title="套餐版本" column={{ xs: 1, sm: 2 }} items={[
          { key: 'packageName', label: '套餐名称', children: detail.packageName },
          { key: 'version', label: '版本', children: `V${detail.packageVersionNo}` },
          { key: 'type', label: '套餐类型', children: packageTypeText(detail.packageType) },
          { key: 'expiresAt', label: '支付截止时间', children: dateTime(detail.expiresAt) },
        ]} />
        <Descriptions title="支付信息" column={{ xs: 1, sm: 2 }} items={[
          { key: 'provider', label: '支付渠道', children: detail.payment?.provider ?? '-' },
          { key: 'paymentStatus', label: '支付状态', children: detail.payment?.status ?? '-' },
          { key: 'tradeNo', label: '微信支付单号', children: detail.payment?.providerTradeNo ?? '-' },
          { key: 'paidAt', label: '支付时间', children: dateTime(detail.payment?.paidAt) },
        ]} />
      </>}
    </Drawer>
  </PageContainer>;
};

export default PlatformCommercialOrderPage;
