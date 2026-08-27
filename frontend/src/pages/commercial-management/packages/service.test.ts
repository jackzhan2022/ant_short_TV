import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createCommercialPackageDraft } from './service';

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
});
