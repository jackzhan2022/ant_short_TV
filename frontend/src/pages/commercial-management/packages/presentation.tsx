import { CrownOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Tag } from 'antd';
import { entitlementTypeText } from '@/utils/fieldDictionary';
import type { CommercialEntitlement, CommercialPackageVersion } from './service';
import { useStyles } from './index.style';

export const PackageMark = ({ subscription = false }: { subscription?: boolean }) => {
  const { styles } = useStyles();
  return <span className={styles.mark}>{subscription ? <CrownOutlined /> : <ThunderboltOutlined />}</span>;
};

export const PackagePrice = ({ value, currency = 'CNY' }: { value?: number; currency?: string }) => {
  const { styles } = useStyles();
  if (value == null) return <span>—</span>;
  return <span className={styles.price}><small>{currency === 'CNY' ? '¥' : currency}</small><strong>{value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></span>;
};

export const PackageStatus = ({ status }: { status?: CommercialPackageVersion['status'] }) => {
  const { styles } = useStyles();
  return <Tag className={styles.status} color={status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'default'}><span aria-hidden="true">• </span>{status === 'PUBLISHED' ? '销售中' : status === 'DRAFT' ? '草稿' : status === 'OFF_SALE' ? '已下架' : '未建版本'}</Tag>;
};

export const PackageBenefits = ({ items = [] }: { items?: CommercialEntitlement[] }) => {
  const { styles } = useStyles();
  if (!items.length) return <span className={styles.muted}>暂无权益</span>;
  return <div className={styles.benefits}>{items.map((item) => {
    const name = item.name ?? entitlementTypeText(item.type);
    const points = item.category !== 'DISPLAY' && ['ONE_TIME_POINTS', 'PERIODIC_POINTS'].includes(item.type) && item.value != null;
    return <div key={item.type}>{points ? <><strong>{item.value?.toLocaleString('zh-CN')} <small>积分</small></strong><span>{name}</span></> : <span className={styles.benefitText}>{item.category === 'DISPLAY' || item.value == null ? name : `${name}：${item.value}`}</span>}</div>;
  })}</div>;
};
