import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createCommercialPackageDraft,
  createDisplayEntitlement,
  disableDisplayEntitlement,
  enableDisplayEntitlement,
  listCommercialEntitlements,
  updateDisplayEntitlement,
} from './service';

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

describe('commercial package service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
  });

  it('sends package effective times as ISO local date-times', async () => {
    await createCommercialPackageDraft({
      packageType: 'POINT_PACKAGE',
      name: '支付冒烟测试',
      price: 0.01,
      currency: 'CNY',
      effectiveFrom: '2026-08-27 00:20:59',
      effectiveTo: '2026-08-28 00:20:59',
      entitlements: [{ type: 'ONE_TIME_POINTS', value: 1 }],
    });

    expect(request).toHaveBeenCalledWith('/api/platform/commercial/packages', {
      method: 'POST',
      data: expect.objectContaining({
        effectiveFrom: '2026-08-27T00:20:59',
        effectiveTo: '2026-08-28T00:20:59',
      }),
    });
  });

  it('uses the platform entitlement catalog endpoints', async () => {
    await listCommercialEntitlements();
    expect(request).toHaveBeenLastCalledWith('/api/platform/commercial/entitlements');

    const payload = { name: '使用全部模型', description: '展示权益', sortOrder: 40 };
    await createDisplayEntitlement(payload);
    expect(request).toHaveBeenLastCalledWith('/api/platform/commercial/entitlements', {
      method: 'POST',
      data: payload,
    });
    await updateDisplayEntitlement(8, payload);
    expect(request).toHaveBeenLastCalledWith('/api/platform/commercial/entitlements/8', {
      method: 'PUT',
      data: payload,
    });
    await disableDisplayEntitlement(8);
    expect(request).toHaveBeenLastCalledWith('/api/platform/commercial/entitlements/8/disable', {
      method: 'POST',
    });
    await enableDisplayEntitlement(8);
    expect(request).toHaveBeenLastCalledWith('/api/platform/commercial/entitlements/8/enable', {
      method: 'POST',
    });
  });
});
