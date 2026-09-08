import { LeftOutlined, WalletOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { App, Button, Card, Empty, Space, Spin, Statistic, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount, queryTeamPointTransactions } from '@/services/account-team/points';
import type { TeamPointTransaction } from '@/services/account-team/types';
import { statusText } from '@/utils/fieldDictionary';
import { createCommercialOrder, queryActiveCommercialOrders, queryCommercialCatalog, queryCurrentSubscription, queryQueuedSubscriptions, refreshCommercialOrder, type CommercialCatalogItem, type CommercialOrder, type TeamSubscription } from './service';
import PurchaseModal from './PurchaseModal';
import styles from './index.less';

const { Text, Title } = Typography;
type PaymentOrder = CommercialOrder & { packageName?: string };
const snapshotName = (snapshot?: string) => {
  if (!snapshot) return '-';
  try { return JSON.parse(snapshot).name ?? '-'; } catch { return '-'; }
};
const pointTransactionTypeText: Record<string, string> = {
  ENTITLEMENT_GRANT: '权益发放',
  ADJUST_GRANT: '手动增加',
  RESERVE: '积分预扣',
  INCREMENTAL_RESERVE: '追加预扣',
  SETTLE: '积分消耗',
  RELEASE: '预扣释放',
  REFUND: '积分退回',
};

const CommercialPage = () => {
  const tenantId = getCurrentTenantId();
  const access = useAccess();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(true);
  const [catalog, setCatalog] = useState<CommercialCatalogItem[]>([]);
  const [current, setCurrent] = useState<TeamSubscription | null>(null);
  const [queued, setQueued] = useState<TeamSubscription[]>([]);
  const [pointTransactions, setPointTransactions] = useState<TeamPointTransaction[]>([]);
  const [orders, setOrders] = useState<CommercialOrder[]>([]);
  const [balance, setBalance] = useState(0);
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [payment, setPayment] = useState<PaymentOrder>();

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    try {
      const [catalogResult, currentResult, queuedResult, ordersResult, pointsResult, transactionsResult] = await Promise.all([
        queryCommercialCatalog(tenantId), queryCurrentSubscription(tenantId), queryQueuedSubscriptions(tenantId),
        queryActiveCommercialOrders(tenantId), queryTeamPointAccount(tenantId), queryTeamPointTransactions(tenantId),
      ]);
      setCatalog(catalogResult.data ?? []); setCurrent(currentResult.data ?? null);
      setQueued(queuedResult.data ?? []); setPointTransactions(transactionsResult.data?.records ?? []);
      setOrders(ordersResult.data ?? []); setBalance(pointsResult.data?.balance ?? 0);
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
        if (result.data.status !== 'PENDING_PAYMENT') {
          setPayment(undefined);
          if (result.data.status === 'COMPLETED') {
            message.success('支付成功，权益已到账');
            setPurchaseOpen(false);
          }
          await load();
          return;
        }
        setPayment({ ...result.data, packageName: payment.packageName });
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
    setPayment({ ...result.data, packageName: item.name });
  };
  const pendingOrder = useMemo(() => orders.find((order) => order.status === 'PENDING_PAYMENT' && order.codeUrl), [orders]);
  const closePurchase = () => {
    setPurchaseOpen(false);
    setPayment(undefined);
  };

  if (!tenantId) return <Empty description="请先选择团队" />;
  return <Spin spinning={loading}><main className={styles.page}>
    <header className={styles.header}>
      <button className={styles.brand} type="button" onClick={() => history.push('/')}><span className={styles.brandMark}>剧</span><span>剧智创</span></button>
      <div className={styles.headerRight}><Statistic title="团队积分余额" value={balance} suffix="积分" prefix={<WalletOutlined />} /><Button type="text" icon={<LeftOutlined />} onClick={() => history.back()}>返回工作台</Button></div>
    </header>
    <section className={styles.content}>
      <div className={styles.hero}><Title level={2}>充值中心</Title><Text type="secondary">选择适合团队的积分包或会员订阅，已发放积分永久有效</Text></div>
      <Card className={styles.currentCard} variant="borderless">
        <div><Text className={styles.label}>当前会员</Text><Title level={4}>{current ? snapshotName(current.snapshotJson) : '暂无会员'} {current && <Tag color="success">{statusText(current.status)}</Tag>}</Title></div>
        <div><Text className={styles.label}>有效期至</Text><strong>{current?.endsAt ?? '-'}</strong></div>
        <div><Text className={styles.label}>下一次积分发放</Text><strong>{current?.nextGrantAt ?? '-'}</strong></div>
        <Button onClick={() => setPurchaseOpen(true)}>购买套餐</Button>
      </Card>
      <Card className={styles.queueCard} variant="borderless" title="订阅与订单">
        {queued.length === 0 && orders.length === 0 ? <Empty description="暂无排队订阅或待处理订单" /> : <Space orientation="vertical" style={{ width: '100%' }}>
          {queued.map((item) => <div key={item.id}>{snapshotName(item.snapshotJson)} <Tag>{statusText(item.status)}</Tag> {item.startsAt} 至 {item.endsAt}</div>)}
          {orders.map((item) => <div key={item.id}>{item.merchantOrderNo} <Tag>{statusText(item.status)}</Tag> ¥{item.amount} {item.id === pendingOrder?.id && <Button type="link" onClick={() => { setPayment(item); setPurchaseOpen(true); }}>继续支付</Button>}</div>)}
        </Space>}
      </Card>
      <Card className={styles.queueCard} variant="borderless" title="积分明细"><Table<TeamPointTransaction> rowKey="id" size="small" pagination={false} dataSource={pointTransactions} columns={[
        { title: '变化类型', dataIndex: 'transactionType', render: (value) => pointTransactionTypeText[value] ?? value },
        { title: '变动积分', dataIndex: 'changeAmount', render: (value) => <Tag color={value > 0 ? 'green' : value < 0 ? 'red' : undefined}>{Number(value).toLocaleString()}</Tag> },
        { title: '变动后余额', dataIndex: 'balanceAfter', render: (value) => Number(value).toLocaleString() },
        { title: '说明', dataIndex: 'description', ellipsis: true, render: (value) => value ?? '-' },
        { title: '时间', dataIndex: 'createdAt' },
      ]} /></Card>
    </section>
    <PurchaseModal open={purchaseOpen} catalog={catalog} payment={payment} canManageBilling={Boolean(access.canManageBilling)} onClose={closePurchase} onPurchase={(item) => void buy(item)} />
  </main></Spin>;
};

export default CommercialPage;
