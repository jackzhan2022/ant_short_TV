import { Button, Modal, QRCode, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import type { CommercialOrder } from './service';
import { formatCommercialDate, formatCommercialMoney } from './purchasePresentation';
import styles from './index.less';

export type PaymentOrder = CommercialOrder & { packageName?: string };

type PaymentModalProps = {
  payment?: PaymentOrder;
  onClose: () => void;
};

const remainingTime = (expiresAt: string, now: number) => {
  const expiry = new Date(expiresAt).getTime();
  if (!Number.isFinite(expiry)) return '剩余时间待确认';
  const seconds = Math.max(0, Math.ceil((expiry - now) / 1000));
  return `剩余 ${Math.floor(seconds / 60)} 分 ${String(seconds % 60).padStart(2, '0')} 秒`;
};

const PaymentModal = ({ payment, onClose }: PaymentModalProps) => {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!payment?.codeUrl) return undefined;
    setNow(Date.now());
    const expiresAt = new Date(payment.expiresAt).getTime();
    if (!Number.isFinite(expiresAt) || Date.now() >= expiresAt) return undefined;
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= expiresAt) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [payment?.id, payment?.codeUrl, payment?.expiresAt]);

  const paymentCodeValid = Boolean(payment?.codeUrl && now < new Date(payment.expiresAt).getTime());

  return (
    <Modal
      title="扫码支付"
      open={paymentCodeValid}
      footer={null}
      onCancel={onClose}
      destroyOnHidden
      focusable={{ focusTriggerAfterClose: true }}
      className={styles.paymentModal}
    >
      {paymentCodeValid && payment?.codeUrl ? (
        <section className={styles.paymentPanel}>
          <Typography.Text type="secondary">
            请在 {formatCommercialDate(payment.expiresAt)} 前完成支付 · {remainingTime(payment.expiresAt, now)}
          </Typography.Text>
          <Typography.Title level={4}>{payment.packageName || '套餐订单'}</Typography.Title>
          <Typography.Text type="secondary">订单号 {payment.merchantOrderNo}</Typography.Text>
          <Typography.Title level={3}>{formatCommercialMoney(payment.amount, payment.currency)}</Typography.Title>
          <QRCode value={payment.codeUrl} size={236} bordered={false} bgColor="#fff" color="#27283a" marginSize={2} />
          <Typography.Text>请使用微信扫码付款</Typography.Text>
          <Tag>待支付</Tag>
          <Button type="link" onClick={onClose}>返回选购</Button>
          <Typography.Paragraph className={styles.paymentNotice} type="secondary">
            支付结果以服务器确认状态为准，完成后权益将发放至当前团队。
          </Typography.Paragraph>
        </section>
      ) : null}
    </Modal>
  );
};

export default PaymentModal;
