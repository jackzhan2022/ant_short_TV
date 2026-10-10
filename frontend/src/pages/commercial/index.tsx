import { LeftOutlined, WalletOutlined } from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import { App, Button, Empty, Spin, Table, Tag } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryTeamPointAccount, queryTeamPointTransactions } from '@/services/account-team/points';
import type { TeamPointTransaction } from '@/services/account-team/types';
import { statusText } from '@/utils/fieldDictionary';
import PaymentModal, { type PaymentOrder } from './PaymentModal';
import PurchasePanel, { initialPurchaseSelection } from './PurchasePanel';
import { formatCommercialDate, formatCommercialMoney } from './purchasePresentation';
import {
  createCommercialOrder,
  queryActiveCommercialOrders,
  queryCommercialCatalog,
  queryCurrentSubscription,
  queryQueuedSubscriptions,
  refreshCommercialOrder,
  type CommercialCatalogItem,
  type CommercialOrder,
  type TeamSubscription,
} from './service';
import styles from './index.less';

const pointTransactionTypeText: Record<string, string> = {
  ENTITLEMENT_GRANT: '权益发放',
  ADJUST_GRANT: '手动增加',
  RESERVE: '积分预扣',
  INCREMENTAL_RESERVE: '追加预扣',
  SETTLE: '积分消耗',
  RELEASE: '预扣释放',
  REFUND: '积分退回',
};

const snapshotName = (snapshot?: string) => {
  if (!snapshot) return '-';
  try {
    const parsed: unknown = JSON.parse(snapshot);
    if (parsed && typeof parsed === 'object' && 'name' in parsed
      && typeof parsed.name === 'string' && parsed.name.trim()) return parsed.name;
  } catch {
    // Historical snapshots may not contain valid JSON.
  }
  return '-';
};

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

const CommercialPage = () => {
  const tenantId = getCurrentTenantId();
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const teamName = initialState?.tenants?.find((tenant) => tenant.id === tenantId)?.name ?? '当前团队';
  const { message } = App.useApp();
  const messageRef = useRef(message);
  messageRef.current = message;
  const currentTenantRef = useRef(tenantId);
  currentTenantRef.current = tenantId;
  const scopeRef = useRef(0);
  const submittingRef = useRef(false);

  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState<string>();
  const [catalog, setCatalog] = useState<CommercialCatalogItem[]>([]);
  const [selection, setSelection] = useState(initialPurchaseSelection);
  const [current, setCurrent] = useState<TeamSubscription | null>(null);
  const [queued, setQueued] = useState<TeamSubscription[]>([]);
  const [pointTransactions, setPointTransactions] = useState<TeamPointTransaction[]>([]);
  const [orders, setOrders] = useState<CommercialOrder[]>([]);
  const [balance, setBalance] = useState(0);
  const [payment, setPayment] = useState<PaymentOrder>();
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!tenantId || currentTenantRef.current !== tenantId) return;
    const scope = scopeRef.current;
    setLoading(true);
    const [catalogResult, currentResult, queuedResult, ordersResult, pointsResult, transactionsResult] = await Promise.allSettled([
      queryCommercialCatalog(tenantId),
      queryCurrentSubscription(tenantId),
      queryQueuedSubscriptions(tenantId),
      queryActiveCommercialOrders(tenantId),
      queryTeamPointAccount(tenantId),
      queryTeamPointTransactions(tenantId),
    ]);
    if (scope !== scopeRef.current || currentTenantRef.current !== tenantId) return;
    if (catalogResult.status === 'fulfilled') {
      setCatalog(catalogResult.value.data ?? []);
      setCatalogError(undefined);
    } else {
      setCatalog([]);
      setCatalogError(errorText(catalogResult.reason, '套餐加载失败'));
    }
    if (currentResult.status === 'fulfilled') setCurrent(currentResult.value.data ?? null);
    if (queuedResult.status === 'fulfilled') setQueued(queuedResult.value.data ?? []);
    if (ordersResult.status === 'fulfilled') setOrders(ordersResult.value.data ?? []);
    if (pointsResult.status === 'fulfilled') setBalance(pointsResult.value.data?.balance ?? 0);
    if (transactionsResult.status === 'fulfilled') {
      setPointTransactions(transactionsResult.value.data?.records ?? []);
    }
    setLoading(false);
  }, [tenantId]);

  useEffect(() => {
    scopeRef.current += 1;
    submittingRef.current = false;
    setLoading(true);
    setCatalogError(undefined);
    setCatalog([]);
    setSelection(initialPurchaseSelection);
    setCurrent(null);
    setQueued([]);
    setOrders([]);
    setBalance(0);
    setPointTransactions([]);
    setPayment(undefined);
    setSubmitting(false);
    void load();
    return () => { scopeRef.current += 1; };
  }, [tenantId, load]);

  useEffect(() => {
    if (!tenantId || !payment || !['PENDING_PAYMENT', 'ENTITLEMENT_PENDING'].includes(payment.status)) return undefined;
    const scope = scopeRef.current;
    let disposed = false;
    let timer: number | undefined;
    const follow = async () => {
      if (disposed || scope !== scopeRef.current || currentTenantRef.current !== tenantId) return;
      const qrExpired = Date.now() >= new Date(payment.expiresAt).getTime();
      try {
        const response = await refreshCommercialOrder(tenantId, payment.id);
        if (disposed || scope !== scopeRef.current || currentTenantRef.current !== tenantId) return;
        if (response.data.status === 'COMPLETED') {
          setPayment(undefined);
          messageRef.current.success('支付成功，权益已到账');
          await load();
          return;
        }
        if (response.data.status === 'ENTITLEMENT_PENDING') {
          if (payment.status !== 'ENTITLEMENT_PENDING') {
            messageRef.current.info('支付已确认，权益发放中');
            setOrders((previous) => previous.map((order) => order.id === response.data.id ? response.data : order));
          }
          setPayment({ ...response.data, codeUrl: undefined, packageName: payment.packageName });
        } else if (response.data.status === 'PENDING_PAYMENT') {
          if (qrExpired && payment.codeUrl) messageRef.current.info('二维码已到期，正在核对订单状态');
          setPayment({
            ...response.data,
            codeUrl: qrExpired || !payment.codeUrl ? undefined : response.data.codeUrl,
            packageName: payment.packageName,
          });
        } else {
          setPayment(undefined);
          messageRef.current.error(`订单${statusText(response.data.status)}`);
          await load();
          return;
        }
      } catch {
        if (!disposed) messageRef.current.error('订单状态查询失败，稍后将自动重试');
      }
      if (!disposed) timer = window.setTimeout(() => void follow(), 3000);
    };
    timer = window.setTimeout(() => void follow(), 3000);
    return () => {
      disposed = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [tenantId, payment?.id, payment?.codeUrl, payment?.status, payment?.expiresAt, payment?.packageName, load]);

  const buy = async (item: CommercialCatalogItem) => {
    if (!tenantId || !access.canManageBilling || submittingRef.current
      || !catalog.some((candidate) => candidate.packageVersionId === item.packageVersionId)) return;
    const scope = scopeRef.current;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const response = await createCommercialOrder(tenantId, item.packageVersionId);
      if (scope !== scopeRef.current || currentTenantRef.current !== tenantId) return;
      if (!response.data.codeUrl) {
        messageRef.current.error('微信支付尚未配置，请联系平台运营');
        await load();
        return;
      }
      setPayment({ ...response.data, packageName: item.name });
      setOrders((previous) => [response.data, ...previous.filter((order) => order.id !== response.data.id)]);
    } catch (error) {
      if (scope !== scopeRef.current || currentTenantRef.current !== tenantId) return;
      messageRef.current.error(errorText(error, '订单创建失败'));
      await load();
    } finally {
      if (scope === scopeRef.current && currentTenantRef.current === tenantId) {
        submittingRef.current = false;
        setSubmitting(false);
      }
    }
  };

  const pendingOrders = orders.filter((order) => access.canManageBilling
    && order.status === 'PENDING_PAYMENT' && order.codeUrl
    && new Date(order.expiresAt).getTime() > Date.now());

  if (!tenantId) return <Empty description="请先选择团队" />;

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <button className={styles.brand} type="button" aria-label="剧智创" onClick={() => history.push('/')}>
          <img className={styles.brandMark} src="/juzhichuang-logo-mark.png" alt="剧智创 Logo" /><span>剧智创</span>
        </button>
        <div className={styles.headerRight}>
          <span className={styles.headerBalance}><WalletOutlined /> 团队积分 <strong>{balance.toLocaleString('zh-CN')}</strong></span>
          <Button type="text" icon={<LeftOutlined />} onClick={() => history.back()}>返回工作台</Button>
        </div>
      </header>
      <div className={styles.content}>
        <section className={styles.membershipSummary} aria-label="当前会员">
          <div><span className={styles.label}>当前会员</span><strong>{current ? snapshotName(current.snapshotJson) : '暂无会员'}</strong>
            {current ? <Tag color="success">{statusText(current.status)}</Tag> : null}</div>
          {current ? <div><span className={styles.label}>有效期至</span><strong>{formatCommercialDate(current.endsAt)}</strong></div> : null}
          {current?.nextGrantAt ? <div><span className={styles.label}>下一次积分发放</span><strong>{formatCommercialDate(current.nextGrantAt)}</strong></div> : null}
        </section>

        <Spin spinning={loading}>
          <PurchasePanel
            catalog={catalog}
            selection={selection}
            onSelectionChange={setSelection}
            canManageBilling={Boolean(access.canManageBilling)}
            teamName={teamName}
            loading={loading}
            error={catalogError}
            submitting={submitting}
            onReload={() => { void load(); }}
            onPurchase={(item) => { void buy(item); }}
          />
        </Spin>

        <section className={styles.accountSection} aria-label="订阅与订单">
          <h2>订阅与订单</h2>
          {queued.length === 0 && orders.length === 0 ? <Empty description="暂无排队订阅或待处理订单" /> : (
            <div className={styles.orderList}>
              {queued.map((item) => (
                <div key={item.id} className={styles.orderRow}>
                  <span>{snapshotName(item.snapshotJson)} <Tag>{statusText(item.status)}</Tag></span>
                  <span>{formatCommercialDate(item.startsAt)} 至 {formatCommercialDate(item.endsAt)}</span>
                </div>
              ))}
              {orders.map((item) => (
                <div key={item.id} className={styles.orderRow}>
                  <span>{item.merchantOrderNo} <Tag>{statusText(item.status)}</Tag></span>
                  <span>{formatCommercialMoney(item.amount, item.currency)}</span>
                  {pendingOrders.some((candidate) => candidate.id === item.id) ? (
                    <Button type="link" onClick={() => {
                      if (new Date(item.expiresAt).getTime() <= Date.now()) {
                        messageRef.current.error('订单已过期，请重新下单');
                        void load();
                        return;
                      }
                      setPayment(item);
                    }}>继续支付</Button>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </section>

        <section className={styles.accountSection} aria-label="积分明细" id="point-history">
          <h2>积分明细</h2>
          <Table<TeamPointTransaction>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={pointTransactions}
            columns={[
              { title: '变化类型', dataIndex: 'transactionType', render: (value: string) => pointTransactionTypeText[value] ?? value },
              { title: '变动积分', dataIndex: 'changeAmount', render: (value: number) => <Tag color={value > 0 ? 'green' : value < 0 ? 'red' : undefined}>{Number(value).toLocaleString('zh-CN')}</Tag> },
              { title: '变动后余额', dataIndex: 'balanceAfter', render: (value: number) => Number(value).toLocaleString('zh-CN') },
              { title: '说明', dataIndex: 'description', ellipsis: true, render: (value?: string) => value ?? '-' },
              { title: '时间', dataIndex: 'createdAt', render: (value?: string) => formatCommercialDate(value) },
            ]}
          />
        </section>
      </div>
      <PaymentModal payment={payment} onClose={() => setPayment(undefined)} />
    </main>
  );
};

export default CommercialPage;
