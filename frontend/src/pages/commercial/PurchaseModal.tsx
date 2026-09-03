import { Button, Card, Modal, QRCode, Tabs, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { entitlementTypeText } from '@/utils/fieldDictionary';
import type { CommercialCatalogItem, CommercialEntitlement, CommercialOrder } from './service';
import styles from './index.less';

type PaymentOrder = CommercialOrder & { packageName?: string };
type PurchaseModalProps = {
  open: boolean;
  catalog: CommercialCatalogItem[];
  payment?: PaymentOrder;
  canManageBilling: boolean;
  onClose: () => void;
  onPurchase: (item: CommercialCatalogItem) => void;
};

type Category = 'points' | 'monthly' | 'quarterly' | 'halfYear' | 'yearly';

const entitlementLabel = (entitlement: CommercialEntitlement) => {
  if (entitlement.type === 'GLOBAL_DISCOUNT') return `全局 AI 积分 ${Number((entitlement.value * 10).toFixed(1))} 折`;
  if (entitlement.type === 'PERIODIC_POINTS') return `每月发放 ${Number(entitlement.value).toLocaleString()} 积分`;
  if (entitlement.type === 'ONE_TIME_POINTS') return `一次性发放 ${Number(entitlement.value).toLocaleString()} 积分`;
  return `${entitlementTypeText(entitlement.type)}：${entitlement.value}`;
};

const subscriptionCategory = (item: CommercialCatalogItem): Exclude<Category, 'points'> => {
  if (item.billingPeriod === 'HALF_YEAR' || item.periodMonths === 6) return 'halfYear';
  if (item.periodMonths === 3) return 'quarterly';
  if (item.periodMonths === 12) return 'yearly';
  return 'monthly';
};

const matchesCategory = (item: CommercialCatalogItem, category: Category) => {
  if (category === 'points') return item.packageType === 'POINT_PACKAGE';
  return item.packageType === 'SUBSCRIPTION' && subscriptionCategory(item) === category;
};
const remainingPaymentTime = (expiresAt: string, now: number) => {
  const seconds = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now) / 1000));
  return `剩余 ${Math.floor(seconds / 60)} 分 ${String(seconds % 60).padStart(2, '0')} 秒`;
};

const PurchaseModal = ({ open, catalog, payment, canManageBilling, onClose, onPurchase }: PurchaseModalProps) => {
  const [category, setCategory] = useState<Category>('points');
  const [showPayment, setShowPayment] = useState(Boolean(payment?.codeUrl));
  const [now, setNow] = useState(() => Date.now());
  const paymentKey = `${payment?.id ?? ''}:${payment?.codeUrl ?? ''}`;

  useEffect(() => { setShowPayment(Boolean(payment?.codeUrl)); }, [paymentKey]);
  useEffect(() => {
    if (!payment?.codeUrl) return undefined;
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [paymentKey]);

  const packages = useMemo(() => catalog.filter((item) => matchesCategory(item, category)), [catalog, category]);
  const inPaymentStep = Boolean(payment?.codeUrl) && showPayment;

  return <Modal
    title={inPaymentStep ? '扫码支付' : '选择套餐'}
    open={open}
    footer={null}
    width={1080}
    onCancel={onClose}
    destroyOnHidden
    className={styles.purchaseModal}
  >
    {inPaymentStep && payment?.codeUrl ? <section className={styles.paymentPanel}>
      <Typography.Text type="secondary">请在 {payment.expiresAt} 前完成支付 · {remainingPaymentTime(payment.expiresAt, now)}</Typography.Text>
      <Typography.Title level={3}>{payment.packageName ?? '套餐订单'}</Typography.Title>
      <Typography.Title level={2}>扫码支付 <span>¥{payment.amount}</span></Typography.Title>
      <QRCode value={payment.codeUrl} size={260} />
      <Typography.Text>请使用微信扫码付款</Typography.Text>
      <Tag>{payment.status}</Tag>
      <Button type="link" onClick={() => setShowPayment(false)}>返回选择套餐</Button>
      <Typography.Paragraph className={styles.paymentNotice} type="secondary">
        支付成功后，积分或会员权益将自动发放至当前团队。
      </Typography.Paragraph>
    </section> : <section>
      <Tabs
        activeKey={category}
        onChange={(key) => setCategory(key as Category)}
        items={[
          { key: 'points', label: '积分包' },
          { key: 'monthly', label: '月度会员' },
          { key: 'quarterly', label: '季度会员' },
          { key: 'halfYear', label: '半年会员' },
          { key: 'yearly', label: '年度会员' },
        ]}
      />
      {packages.length === 0 ? <Typography.Text type="secondary">暂无可售套餐</Typography.Text> : <div className={styles.purchaseGrid}>
        {packages.map((item) => {
          const primaryEntitlement = item.entitlements[0];
          const recommended = Boolean(item.listPrice && item.listPrice > item.price);
          return <Card key={item.packageVersionId} className={`${styles.purchaseCard} ${recommended ? styles.recommendedCard : ''}`}>
            {recommended && <Tag color="processing">推荐</Tag>}
            <Typography.Title level={3}>{item.name}</Typography.Title>
            {item.description && <Typography.Paragraph type="secondary">{item.description}</Typography.Paragraph>}
            <div className={styles.price}><span>¥</span>{item.price}<small> {item.currency}</small>{item.listPrice && <del>¥{item.listPrice}</del>}</div>
            {primaryEntitlement && <div className={styles.pointSummary}>{entitlementLabel(primaryEntitlement)}</div>}
            <div className={styles.entitlements}>
              {item.entitlements.map((entitlement) => <Typography.Text key={entitlement.type}>{entitlementLabel(entitlement)}</Typography.Text>)}
            </div>
            <Button type="primary" block disabled={!canManageBilling} onClick={() => onPurchase(item)}>
              {canManageBilling ? `订购${item.name}` : '仅团队管理员可购买'}
            </Button>
          </Card>;
        })}
      </div>}
    </section>}
  </Modal>;
};

export default PurchaseModal;
