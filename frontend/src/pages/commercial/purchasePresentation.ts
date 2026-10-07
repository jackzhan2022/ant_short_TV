import type { CommercialCatalogItem, CommercialEntitlement } from './service';

export type MembershipPeriod = 'monthly' | 'quarterly' | 'halfYear' | 'yearly';

const periods: Record<number, MembershipPeriod> = {
  1: 'monthly',
  3: 'quarterly',
  6: 'halfYear',
  12: 'yearly',
};

const billingPeriods: Record<string, MembershipPeriod> = {
  MONTH: 'monthly',
  MONTHLY: 'monthly',
  QUARTER: 'quarterly',
  QUARTERLY: 'quarterly',
  HALF_YEAR: 'halfYear',
  YEAR: 'yearly',
  YEARLY: 'yearly',
};

const pointValue = (entitlement?: CommercialEntitlement) => {
  const value = entitlement?.value;
  return typeof value === 'number' && Number.isFinite(value) && value > 0
    ? value : null;
};

const points = (value: number) => new Intl.NumberFormat('zh-CN', {
  maximumFractionDigits: 2,
}).format(value);

export const formatCommercialMoney = (value?: number | null, currency = 'CNY') => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return '-';
  try {
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${currency}`;
  }
};

export const formatCommercialDate = (value?: string | null) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};

export const membershipPeriod = (item: CommercialCatalogItem): MembershipPeriod | null => {
  if (item.packageType !== 'SUBSCRIPTION') return null;
  if (item.periodMonths != null) {
    return Number.isInteger(item.periodMonths) ? periods[item.periodMonths] ?? null : null;
  }
  return billingPeriods[item.billingPeriod ?? ''] ?? null;
};

export const groupCatalog = (catalog: CommercialCatalogItem[]) => {
  const grouped: {
    points: CommercialCatalogItem[];
    membership: Record<MembershipPeriod, CommercialCatalogItem[]>;
    unsupported: CommercialCatalogItem[];
  } = {
    points: [],
    membership: { monthly: [], quarterly: [], halfYear: [], yearly: [] },
    unsupported: [],
  };
  for (const item of catalog) {
    if (item.packageType === 'POINT_PACKAGE') {
      grouped.points.push(item);
      continue;
    }
    const period = membershipPeriod(item);
    if (period) grouped.membership[period].push(item);
    else grouped.unsupported.push(item);
  }
  return grouped;
};

export const presentOffer = (item: CommercialCatalogItem) => {
  const invalidPointBenefits = item.packageType === 'POINT_PACKAGE'
    && item.entitlements.some((entry) => entry.type === 'PERIODIC_POINTS' || entry.type === 'GLOBAL_DISCOUNT');
  const recurring = item.packageType === 'SUBSCRIPTION'
    ? item.entitlements.find((entry) => entry.type === 'PERIODIC_POINTS') : undefined;
  const oneTime = item.entitlements.find((entry) => entry.type === 'ONE_TIME_POINTS');
  const discount = item.packageType === 'SUBSCRIPTION'
    ? item.entitlements.find((entry) => entry.type === 'GLOBAL_DISCOUNT') : undefined;
  const monthlyPoints = pointValue(recurring);
  const oneTimePoints = pointValue(oneTime);
  const months = item.packageType === 'SUBSCRIPTION'
    ? Object.entries(periods).find(([, period]) => period === membershipPeriod(item))?.[0]
    : undefined;
  const periodMonths = months ? Number(months) : null;
  const primaryType = item.packageType === 'SUBSCRIPTION' && monthlyPoints !== null
    ? 'PERIODIC_POINTS' : oneTimePoints !== null ? 'ONE_TIME_POINTS' : null;
  const primaryBenefit = primaryType === 'PERIODIC_POINTS' && monthlyPoints !== null
    ? `每月发放 ${points(monthlyPoints)} 积分`
    : primaryType === 'ONE_TIME_POINTS' && oneTimePoints !== null
      ? `一次性发放 ${points(oneTimePoints)} 积分`
      : null;
  const rate = discount?.value;
  const discountLabel = typeof rate === 'number' && Number.isFinite(rate)
    && rate >= 0 && rate <= 1
    ? `AI 积分 ${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(rate * 10)} 折`
    : null;
  const extraBenefits = item.entitlements.flatMap((entry) => {
    if (item.packageType === 'POINT_PACKAGE'
      && (entry.type === 'PERIODIC_POINTS' || entry.type === 'GLOBAL_DISCOUNT')) return [];
    if (entry.type === primaryType || entry.type === 'GLOBAL_DISCOUNT') return [];
    if (entry.type === 'ONE_TIME_POINTS' && oneTimePoints !== null) {
      return [`一次性发放 ${points(oneTimePoints)} 积分`];
    }
    if (entry.type === 'PERIODIC_POINTS' && monthlyPoints !== null) {
      return [`每月发放 ${points(monthlyPoints)} 积分`];
    }
    return entry.name?.trim() ? [entry.name.trim()] : [];
  });
  const validPrice = Number.isFinite(item.price) && item.price >= 0;
  const listPrice = validPrice && item.listPrice != null
    && Number.isFinite(item.listPrice) && item.listPrice > item.price
    ? formatCommercialMoney(item.listPrice, item.currency) : null;
  return {
    price: formatCommercialMoney(item.price, item.currency),
    listPrice,
    primaryBenefit,
    extraBenefits: [...new Set(extraBenefits)],
    discount: discountLabel,
    periodTotal: monthlyPoints !== null && periodMonths !== null
      ? `周期发放合计 ${points(monthlyPoints * periodMonths)} 积分` : null,
    monthlyEquivalent: validPrice && periodMonths !== null
      ? `折合 ${formatCommercialMoney(item.price / periodMonths, item.currency)} / 月` : null,
    unitPrice: validPrice && item.packageType === 'POINT_PACKAGE'
      && oneTimePoints !== null && oneTimePoints > 0
      ? `${formatCommercialMoney(item.price * 1000 / oneTimePoints, item.currency)} / 千积分`
      : null,
    canPurchase: validPrice && Number.isSafeInteger(item.packageVersionId)
      && item.packageVersionId > 0
      && !invalidPointBenefits
      && (item.packageType === 'POINT_PACKAGE' || periodMonths !== null),
  };
};
