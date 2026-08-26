export type CommercialPackage = {
  id: string;
  packageType: 'POINT_PACKAGE' | 'SUBSCRIPTION';
  name: string;
  subtitle: string;
  price: number;
  originalPrice?: number;
  billingPeriod?: string;
  points?: number;
  periodicPoints?: number;
  totalPoints?: number;
  discountRate?: number;
  discountLabel?: string;
  badge?: string;
  recommended?: boolean;
  entitlements: string[];
};

export const mockCommercialPackages: CommercialPackage[] = [
  { id: 'points-1000', packageType: 'POINT_PACKAGE', name: '积分补给包', subtitle: '适合临时增加 AI 使用额度', price: 19.9, originalPrice: 22.9, points: 1000, entitlements: ['一次性到账 1,000 积分', '积分永久有效'] },
  { id: 'points-5000', packageType: 'POINT_PACKAGE', name: '积分增强包', subtitle: '适合高频使用 AI 功能', price: 89.9, originalPrice: 109.9, points: 5000, badge: '性价比之选', recommended: true, entitlements: ['一次性到账 5,000 积分', '积分永久有效'] },
  { id: 'points-20000', packageType: 'POINT_PACKAGE', name: '积分旗舰包', subtitle: '适合团队批量使用', price: 299.9, originalPrice: 399.9, points: 20000, entitlements: ['一次性到账 20,000 积分', '积分永久有效'] },
  { id: 'pro-monthly', packageType: 'SUBSCRIPTION', name: '专业版月卡', subtitle: '适合持续使用 AI 创作能力', price: 99, originalPrice: 129, billingPeriod: '月', periodicPoints: 3000, discountRate: 0.9, discountLabel: 'AI 积分 9 折', badge: '热门', recommended: true, entitlements: ['每月赠送 3,000 积分', '全局 AI 积分 9 折', '已发放积分永久有效'] },
  { id: 'pro-quarterly', packageType: 'SUBSCRIPTION', name: '专业版季卡', subtitle: '连续创作更划算', price: 269, originalPrice: 387, billingPeriod: '季', periodicPoints: 3500, totalPoints: 10500, discountRate: 0.85, discountLabel: 'AI 积分 85 折', badge: '推荐', entitlements: ['每月赠送 3,500 积分', '周期总计 10,500 积分', '全局 AI 积分 85 折', '已发放积分永久有效'] },
  { id: 'pro-yearly', packageType: 'SUBSCRIPTION', name: '专业版年卡', subtitle: '全年稳定使用，综合成本最低', price: 899, originalPrice: 1548, billingPeriod: '年', periodicPoints: 4500, totalPoints: 54000, discountRate: 0.8, discountLabel: 'AI 积分 8 折', badge: '年度最优', entitlements: ['每月赠送 4,500 积分', '年度总计 54,000 积分', '全局 AI 积分 8 折', '已发放积分永久有效'] },
];

export const mockCurrentSubscription = { name: '专业版月卡', status: '生效中', endsAt: '2026-09-30 23:59', points: 2140, discount: 'AI 积分 9 折' };
export const mockQueuedSubscriptions = [{ name: '专业版季卡', status: '待生效', startsAt: '2026-10-01 00:00', endsAt: '2026-12-31 23:59' }];
