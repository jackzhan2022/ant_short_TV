import { ClockCircleOutlined, GiftOutlined, WalletOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Typography } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { useStyles } from './index.style';
import { dateTime, OrderAmount, OrderStatus, packageTypeText } from './presentation';
import { getPlatformCommercialOrder, type PlatformCommercialOrderDetail } from './service';

const paymentStatusText = (value?: string) => {
  const labels: Record<string, string> = { PAID: '已支付', SUCCESS: '已支付', NOTPAY: '未支付', PENDING: '待支付', PENDING_PAYMENT: '待支付', CLOSED: '已关闭', FAILED: '支付失败', ERROR: '支付异常', REFUND: '退款中', REFUNDED: '已退款', USERPAYING: '支付中', REVOKED: '已撤销', PAYERROR: '支付失败' };
  return value ? labels[value] ?? value : '—';
};
const DetailField = ({ label, children, full }: { label: string; children: ReactNode; full?: boolean }) => <div style={full ? { gridColumn: '1 / -1' } : undefined}><dt>{label}</dt><dd>{children}</dd></div>;

const OrderDetailsDrawer = ({ orderId, onClose }: { orderId?: number; onClose: () => void }) => {
  const { styles } = useStyles();
  const [detail, setDetail] = useState<PlatformCommercialOrderDetail>();
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (orderId === undefined) return;
    let active = true;
    setDetail(undefined); setFailed(false); setLoading(true);
    void getPlatformCommercialOrder(orderId).then((response) => {
      if (!response.success) throw new Error('订单详情加载失败');
      if (active) setDetail(response.data);
    }).catch(() => { if (active) setFailed(true); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [orderId, revision]);
  const current = detail?.id === orderId ? detail : undefined;
  const paidAt = current?.payment?.paidAt || current?.paidAt;
  const events = current ? [
    { title: '创建订单', time: current.createdAt },
    ...(dateTime(paidAt) !== '—' ? [{ title: '支付成功', time: paidAt }] : []),
    ...(dateTime(current.completedAt) !== '—' ? [{ title: '订单完成', time: current.completedAt }] : []),
  ] : [];
  return <Drawer title="订单详情" open={orderId !== undefined} onClose={onClose} loading={loading} size={680} destroyOnHidden className={styles.drawer} closable={{ placement: 'end' }} footer={<div className={styles.drawerFooter}><Button onClick={onClose}>关闭</Button></div>}>
    {failed ? <Alert type="error" showIcon title="订单详情加载失败" action={<Button onClick={() => setRevision((value) => value + 1)}>重试</Button>} /> : current && <>
      <section className={styles.summary} aria-label="订单摘要"><div><div className={styles.metricLabel}>实付金额</div><OrderAmount amount={current.amount} currency={current.currency} large /></div><OrderStatus status={current.status} /></section>
      <div className={styles.detailNumber}><span>订单号</span><Typography.Text copyable={{ tooltips: ['复制订单号', '已复制'] }}>{current.merchantOrderNo}</Typography.Text></div>
      <section className={styles.detailSection}><h3><GiftOutlined />订单信息</h3><dl className={styles.detailGrid}>
        <DetailField label="租户">{current.tenantName || '—'}<code className={styles.secondaryCode}>{current.tenantCode}</code></DetailField>
        <DetailField label="套餐名称">{current.packageName}</DetailField>
        <DetailField label="套餐类型">{packageTypeText(current.packageType)}</DetailField>
        <DetailField label="套餐版本"><span className={styles.version}>V{current.packageVersionNo}</span></DetailField>
        <DetailField label="创建时间">{dateTime(current.createdAt)}</DetailField>
        <DetailField label="支付截止时间">{dateTime(current.expiresAt)}</DetailField>
      </dl></section>
      <section className={styles.detailSection}><h3><WalletOutlined />支付信息</h3><dl className={styles.detailGrid}>
        <DetailField label="支付渠道">{current.payment?.provider === 'WECHAT_NATIVE' ? <><span>微信扫码支付</span><code className={styles.secondaryCode}>WECHAT_NATIVE</code></> : current.payment?.provider || '—'}</DetailField>
        <DetailField label="支付状态">{paymentStatusText(current.payment?.status)}</DetailField>
        <DetailField label="支付时间">{dateTime(paidAt)}</DetailField>
        <DetailField label="完成时间">{dateTime(current.completedAt)}</DetailField>
        <DetailField label="微信支付单号" full>{current.payment?.providerTradeNo ? <Typography.Text className={styles.tradeNumber} copyable={{ tooltips: ['复制支付单号', '已复制'] }}>{current.payment.providerTradeNo}</Typography.Text> : '—'}</DetailField>
      </dl></section>
      <section className={styles.detailSection}><h3><ClockCircleOutlined />订单时间</h3><ol className={styles.timeline}>{events.map((event) => <li key={event.title}><span>{event.title}</span><time>{dateTime(event.time)}</time></li>)}</ol></section>
    </>}
  </Drawer>;
};

export default OrderDetailsDrawer;
