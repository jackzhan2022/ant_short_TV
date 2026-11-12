import { Tag } from 'antd';
import dayjs from 'dayjs';
import { useStyles } from './index.style';
import type { CommercialOrderStatus } from './service';

export const orderStatuses: { value: CommercialOrderStatus; label: string }[] = [
  { value: 'PENDING_PAYMENT', label: '待支付' }, { value: 'PAID', label: '已支付' },
  { value: 'ENTITLEMENT_PENDING', label: '权益发放中' }, { value: 'COMPLETED', label: '已完成' },
  { value: 'CLOSED', label: '已关闭' }, { value: 'PAYMENT_EXCEPTION', label: '支付异常' },
];
export const packageTypeText = (value?: string) => value === 'SUBSCRIPTION' ? '会员订阅' : value === 'POINT_PACKAGE' ? '积分包' : '—';
export const dateTime = (value?: string) => value && dayjs(value).isValid() ? dayjs(value).format('YYYY/MM/DD HH:mm:ss') : '—';

export const DateCell = ({ value }: { value?: string }) => {
  const { styles } = useStyles();
  if (!value || !dayjs(value).isValid()) return <span className={styles.muted}>—</span>;
  return <div className={styles.date}><span>{dayjs(value).format('YYYY/MM/DD')}</span><small>{dayjs(value).format('HH:mm:ss')}</small></div>;
};
export const OrderStatus = ({ status }: { status: CommercialOrderStatus }) => {
  const { styles } = useStyles();
  return <Tag className={styles.status} data-status={status}>{orderStatuses.find((item) => item.value === status)?.label ?? status}</Tag>;
};
export const OrderAmount = ({ amount, currency, large = false }: { amount: string | number; currency: string; large?: boolean }) => {
  const { styles, cx } = useStyles();
  return <span className={cx(styles.amount, large && styles.largeAmount)}>{currency === 'CNY' && <small>¥</small>}<strong>{Number.isFinite(Number(amount)) ? Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'}</strong>{(large || currency !== 'CNY') && <small>{currency}</small>}</span>;
};
