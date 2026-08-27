import { CheckOutlined, LeftOutlined, WalletOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { App, Button, Card, Empty, Modal, QRCode, Space, Spin, Statistic, Table, Tabs, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount } from '@/services/account-team/points';
import { createCommercialOrder, queryActiveCommercialOrders, queryCommercialCatalog, queryCommercialGrants, queryCurrentSubscription, queryQueuedSubscriptions, refreshCommercialOrder, type CommercialCatalogItem, type CommercialGrant, type CommercialOrder, type TeamSubscription } from './service';
import styles from './index.less';

const { Text, Title } = Typography;
const snapshotName = (snapshot?: string) => {
  if (!snapshot) return '-';
  try { return JSON.parse(snapshot).name ?? '-'; } catch { return '-'; }
};
const entitlementLabel = (type: string, value: number) => {
  if (type === 'GLOBAL_DISCOUNT') return `全局 AI 积分 ${Number((value * 10).toFixed(1))} 折`;
  if (type === 'PERIODIC_POINTS') return `每月发放 ${Number(value).toLocaleString()} 积分`;
  return `一次性发放 ${Number(value).toLocaleString()} 积分`;
};

const CommercialPage = () => {
  const tenantId = getCurrentTenantId();
  const access = useAccess();
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState('subscriptions');
  const [loading, setLoading] = useState(true);
  const [catalog, setCatalog] = useState<CommercialCatalogItem[]>([]);
  const [current, setCurrent] = useState<TeamSubscription | null>(null);
  const [queued, setQueued] = useState<TeamSubscription[]>([]);
  const [grants, setGrants] = useState<CommercialGrant[]>([]);
  const [orders, setOrders] = useState<CommercialOrder[]>([]);
  const [balance, setBalance] = useState(0);
  const [payment, setPayment] = useState<CommercialOrder>();

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    try {
      const [catalogResult, currentResult, queuedResult, grantsResult, ordersResult, pointsResult] = await Promise.all([
        queryCommercialCatalog(tenantId), queryCurrentSubscription(tenantId), queryQueuedSubscriptions(tenantId),
        queryCommercialGrants(tenantId), queryActiveCommercialOrders(tenantId), queryTeamPointAccount(tenantId),
      ]);
      setCatalog(catalogResult.data ?? []); setCurrent(currentResult.data ?? null);
      setQueued(queuedResult.data ?? []); setGrants(grantsResult.data ?? []);
      setOrders(ordersResult.data ?? []); setBalance(pointsResult.data?.balance ?? 0);
      const pending = (ordersResult.data ?? []).find((order) => order.status === 'PENDING_PAYMENT' && order.codeUrl);
      if (pending) setPayment(pending);
    } finally { setLoading(false); }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!tenantId || !payment || payment.status !== 'PENDING_PAYMENT') return;
    let cancelled = false;
    let timer: number | undefined;
    const poll = async () => {
      if (cancelled) return;
      if (Date.now() >= new Date(payment.expiresAt).getTime()) {
        message.error('支付二维码已过期，请重新下单');
        setPayment(undefined);
        await load();
        return;
      }
      try {
        const result = await refreshCommercialOrder(tenantId, payment.id);
        if (cancelled) return;
        setPayment(result.data);
        if (result.data.status !== 'PENDING_PAYMENT') {
          if (result.data.status === 'COMPLETED') message.success('支付成功，权益已到账');
          await load();
          return;
        }
      } catch {
        if (!cancelled) message.error('订单状态查询失败，稍后将自动重试');
      }
      if (!cancelled) timer = window.setTimeout(() => void poll(), 3000);
    };
    timer = window.setTimeout(() => void poll(), 3000);
    return () => { cancelled = true; if (timer) window.clearTimeout(timer); };
  }, [load, message, payment, tenantId]);

  const buy = async (item: CommercialCatalogItem) => {
    if (!tenantId) return;
    const result = await createCommercialOrder(tenantId, item.packageVersionId);
    if (!result.data.codeUrl) { message.error('微信支付尚未配置，请联系平台运营'); return; }
    setPayment(result.data);
  };
  const packages = useMemo(() => catalog.filter((item) => activeTab === 'subscriptions' ? item.packageType === 'SUBSCRIPTION' : item.packageType === 'POINT_PACKAGE'), [activeTab, catalog]);

  if (!tenantId) return <Empty description="请先选择团队" />;
  return <Spin spinning={loading}><main className={styles.page}>
    <header className={styles.header}>
      <button className={styles.brand} type="button" onClick={() => history.push('/')}><span className={styles.brandMark}>A</span><span>Ant Short TV</span></button>
      <div className={styles.headerRight}><Statistic title="团队积分余额" value={balance} suffix="积分" prefix={<WalletOutlined />} /><Button type="text" onClick={() => history.push('/team/settings')}>积分明细</Button><Button type="text" icon={<LeftOutlined />} onClick={() => history.back()}>返回工作台</Button></div>
    </header>
    <section className={styles.content}>
      <div className={styles.hero}><Title level={2}>充值中心</Title><Text type="secondary">选择适合团队的积分包或会员订阅，已发放积分永久有效</Text></div>
      <Card className={styles.currentCard} variant="borderless">
        <div><Text className={styles.label}>当前会员</Text><Title level={4}>{current ? snapshotName(current.snapshotJson) : '暂无会员'} {current && <Tag color="success">{current.status}</Tag>}</Title></div>
        <div><Text className={styles.label}>有效期至</Text><strong>{current?.endsAt ?? '-'}</strong></div>
        <div><Text className={styles.label}>下一次积分发放</Text><strong>{current?.nextGrantAt ?? '-'}</strong></div>
        <Button onClick={() => setActiveTab('subscriptions')}>查看会员套餐</Button>
      </Card>
      <Tabs className={styles.tabs} activeKey={activeTab} onChange={setActiveTab} items={[{ key: 'subscriptions', label: '会员订阅' }, { key: 'points', label: '积分包' }]} />
      {packages.length === 0 ? <Empty description="暂无可售套餐" /> : <div className={styles.packageGrid}>{packages.map((item) => <Card key={item.packageVersionId} className={styles.packageCard} variant="borderless">
        <Text className={styles.packageType}>{item.packageType === 'SUBSCRIPTION' ? '会员订阅' : '积分充值'}</Text><Title level={3}>{item.name}</Title><Text type="secondary">{item.description}</Text>
        <div className={styles.price}><span>¥</span>{item.price}<small> {item.currency}</small>{item.listPrice && <del>¥{item.listPrice}</del>}</div>
        <Space orientation="vertical" className={styles.entitlements}>{item.entitlements.map((entitlement) => <Text key={entitlement.type}><CheckOutlined /> {entitlementLabel(entitlement.type, entitlement.value)}</Text>)}</Space>
        <Button className={styles.buyButton} type="primary" block disabled={!access.canManageBilling} onClick={() => void buy(item)}>{access.canManageBilling ? '立即购买' : '仅团队管理员可购买'}</Button>
      </Card>)}</div>}
      <Card className={styles.queueCard} variant="borderless" title="订阅与订单">
        {queued.length === 0 && orders.length === 0 ? <Empty description="暂无排队订阅或待处理订单" /> : <Space orientation="vertical" style={{ width: '100%' }}>
          {queued.map((item) => <div key={item.id}>{snapshotName(item.snapshotJson)} <Tag>{item.status}</Tag> {item.startsAt} 至 {item.endsAt}</div>)}
          {orders.map((item) => <div key={item.id}>{item.merchantOrderNo} <Tag>{item.status}</Tag> ¥{item.amount}</div>)}
        </Space>}
      </Card>
      <Card className={styles.queueCard} variant="borderless" title="权益发放记录"><Table<CommercialGrant> rowKey="id" size="small" pagination={false} dataSource={grants} columns={[
        { title: '权益', dataIndex: 'entitlementType' }, { title: '数量', dataIndex: 'amount', render: (value) => value == null ? '-' : Number(value).toLocaleString() },
        { title: '状态', dataIndex: 'status', render: (value) => <Tag>{value}</Tag> }, { title: '发放时间', dataIndex: 'grantedAt', render: (value) => value ?? '-' },
      ]} /></Card>
    </section>
    <Modal title="微信扫码支付" open={Boolean(payment)} footer={null} onCancel={() => setPayment(undefined)} destroyOnHidden>
      {payment?.codeUrl && <Space orientation="vertical" align="center" style={{ width: '100%' }}><QRCode value={payment.codeUrl} status={payment.status === 'PENDING_PAYMENT' ? 'active' : 'scanned'} /><Text>订单 {payment.merchantOrderNo}</Text><Text type="secondary">请在 {payment.expiresAt} 前完成支付</Text><Tag>{payment.status}</Tag></Space>}
    </Modal>
  </main></Spin>;
};

export default CommercialPage;
