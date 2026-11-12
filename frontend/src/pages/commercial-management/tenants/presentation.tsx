import { CrownOutlined, GiftOutlined } from '@ant-design/icons';
import { Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useStyles } from './index.style';
import type { PlatformTenantPackage, PlatformTenantSummary } from './service';

export const dateTime = (value?: string) => value && dayjs(value).isValid() ? dayjs(value).format('YYYY/MM/DD HH:mm:ss') : '—';
export const packageTypeText = (value?: string) => ({ SUBSCRIPTION: '会员订阅', POINT_PACKAGE: '积分包', TRIAL: '试用套餐' })[value ?? ''] ?? '—';
export const tenantTypeText = (value: string) => ({ STUDIO: '工作室', PERSONAL: '个人', ENTERPRISE: '企业' })[value] ?? value;

export const DateCell = ({ value }: { value?: string }) => {
  const { styles } = useStyles();
  return dateTime(value) === '—' ? <span className={styles.muted}>—</span> : <div className={styles.date}><span>{dayjs(value).format('YYYY/MM/DD')}</span><small>{dayjs(value).format('HH:mm:ss')}</small></div>;
};
export const TenantStatus = ({ active, children }: { active: boolean; children: React.ReactNode }) => {
  const { styles } = useStyles();
  return <Tag className={styles.status} data-active={active}>{children}</Tag>;
};
export const TenantMark = ({ tenant, large = false }: { tenant: Pick<PlatformTenantSummary, 'id' | 'name'>; large?: boolean }) => {
  const { styles, cx } = useStyles();
  return <span aria-hidden className={cx(styles.mark, large && styles.largeMark)} data-tone={tenant.id % 4}>{tenant.name.slice(0, 1).toUpperCase()}</span>;
};
export const TenantCode = ({ code }: { code: string }) => {
  const { styles } = useStyles();
  return <Typography.Text className={styles.code} copyable={{ text: code, tooltips: ['复制租户编码', '已复制'] }}>{code}</Typography.Text>;
};
export const PackageCell = ({ value }: { value?: PlatformTenantPackage | null }) => {
  const { styles } = useStyles();
  if (!value) return <span className={styles.muted}>暂无套餐</span>;
  const trial = value.packageType === 'TRIAL';
  return <div><div className={styles.packageName}>{trial ? <span className={styles.trial}>试用套餐</span> : <><CrownOutlined />{value.name}</>}</div><div className={styles.packageNote}>{trial ? '无到期时间' : dateTime(value.endsAt) === '—' ? '—' : `${dayjs(value.endsAt).format('YYYY/MM/DD')} 到期`}</div></div>;
};
export const PackageGlance = ({ value }: { value?: PlatformTenantPackage | null }) => {
  const { styles } = useStyles();
  if (!value) return <div className={styles.quietEmpty}><GiftOutlined />暂无套餐</div>;
  return <div className={styles.packageGlance}><div><CrownOutlined /><strong>{value.name}</strong></div><span>{value.packageType === 'TRIAL' ? '无到期时间' : dateTime(value.endsAt) === '—' ? '—' : `${dayjs(value.endsAt).format('YYYY/MM/DD')} 到期`}</span></div>;
};
