import { describe, expect, it } from 'vitest';
import type { CommercialCatalogItem } from './service';
import {
  formatCommercialDate,
  groupCatalog,
  presentOffer,
} from './purchasePresentation';

const offer = (values: Partial<CommercialCatalogItem>): CommercialCatalogItem => ({
  packageId: 1,
  packageVersionId: 11,
  code: 'OFFER',
  packageType: 'SUBSCRIPTION',
  name: '专业版',
  price: 269,
  listPrice: 387,
  currency: 'CNY',
  entitlements: [],
  ...values,
});

describe('purchase presentation', () => {
  it('groups by package type and gives valid month counts priority over billing labels', () => {
    const points = offer({ packageVersionId: 1, packageType: 'POINT_PACKAGE', billingPeriod: 'YEAR' });
    const yearly = offer({ packageVersionId: 2, periodMonths: 12, billingPeriod: 'MONTH' });
    const firstQuarter = offer({ packageVersionId: 3, periodMonths: 3 });
    const secondQuarter = offer({ packageVersionId: 4, periodMonths: 3 });
    const halfYear = offer({ packageVersionId: 5, periodMonths: undefined, billingPeriod: 'HALF_YEAR' });
    const unsupported = offer({ packageVersionId: 6, periodMonths: 5, billingPeriod: 'MONTH' });

    const grouped = groupCatalog([points, yearly, firstQuarter, secondQuarter, halfYear, unsupported]);

    expect(grouped.points.map((item) => item.packageVersionId)).toEqual([1]);
    expect(grouped.membership.yearly.map((item) => item.packageVersionId)).toEqual([2]);
    expect(grouped.membership.quarterly.map((item) => item.packageVersionId)).toEqual([3, 4]);
    expect(grouped.membership.halfYear.map((item) => item.packageVersionId)).toEqual([5]);
    expect(grouped.unsupported.map((item) => item.packageVersionId)).toEqual([6]);
  });

  it('keeps recurring and one-time points separate and does not repeat the primary benefit', () => {
    const quarter = offer({
      periodMonths: 3,
      entitlements: [
        { type: 'PERIODIC_POINTS', value: 3500 },
        { type: 'ONE_TIME_POINTS', value: 200 },
        { type: 'GLOBAL_DISCOUNT', value: 0.85 },
        { type: 'DISPLAY_COMMERCIAL', category: 'DISPLAY', name: '商用授权' },
      ],
    });

    const view = presentOffer(quarter);

    expect(view.primaryBenefit).toBe('每月发放 3,500 积分');
    expect(view.periodTotal).toBe('周期发放合计 10,500 积分');
    expect(view.discount).toBe('AI 积分 8.5 折');
    expect(view.extraBenefits).toContain('一次性发放 200 积分');
    expect(view.extraBenefits).toContain('商用授权');
    expect(view.extraBenefits).not.toContain(view.primaryBenefit);
    expect(view.price).toBe('¥269.00');
    expect(view.monthlyEquivalent).toBe('折合 ¥89.67 / 月');
  });

  it('only shows valid discount and unit price for a positive point amount', () => {
    const points = offer({
      packageType: 'POINT_PACKAGE',
      price: 59,
      listPrice: 59,
      entitlements: [{ type: 'ONE_TIME_POINTS', value: 2000 }],
    });

    expect(presentOffer(points)).toMatchObject({
      primaryBenefit: '一次性发放 2,000 积分',
      unitPrice: '¥29.50 / 千积分',
      listPrice: null,
    });
    expect(presentOffer({ ...points, listPrice: 69 }).listPrice).toBe('¥69.00');
    expect(presentOffer({ ...points, entitlements: [{ type: 'ONE_TIME_POINTS', value: 0 }] }).unitPrice)
      .toBeNull();
    expect(presentOffer({ ...points, entitlements: [{ type: 'ONE_TIME_POINTS', value: 0 }] }).primaryBenefit)
      .toBeNull();
    expect(presentOffer({ ...points, entitlements: [] }).unitPrice).toBeNull();
  });

  it('does not advertise a zero-value membership grant', () => {
    const membership = offer({
      periodMonths: 1,
      entitlements: [{ type: 'PERIODIC_POINTS', value: 100 }, { type: 'ONE_TIME_POINTS', value: 0 }],
    });
    expect(presentOffer(membership).extraBenefits).not.toContain('一次性发放 0 积分');
  });

  it('blocks a point package carrying subscription-only benefits', () => {
    const invalid = offer({
      packageType: 'POINT_PACKAGE',
      entitlements: [
        { type: 'ONE_TIME_POINTS', value: 2000 },
        { type: 'PERIODIC_POINTS', value: 1000 },
        { type: 'GLOBAL_DISCOUNT', value: 0.8 },
      ],
    });
    const view = presentOffer(invalid);
    expect(view.canPurchase).toBe(false);
    expect(view.primaryBenefit).toBe('一次性发放 2,000 积分');
    expect(view.extraBenefits).not.toContain('每月发放 1,000 积分');
    expect(view.discount).toBeNull();
  });

  it('keeps absent fields absent and formats account dates without raw timestamps', () => {
    const missing = presentOffer(offer({ periodMonths: undefined, entitlements: [] }));
    expect(missing.primaryBenefit).toBeNull();
    expect(missing.periodTotal).toBeNull();
    expect(missing.discount).toBeNull();
    expect(missing.monthlyEquivalent).toBeNull();
    expect(formatCommercialDate('2027-08-27T23:42:17')).not.toContain('T');
    expect(formatCommercialDate('invalid')).toBe('-');
    expect(formatCommercialDate(undefined)).toBe('-');
  });
});
