import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getPlatformTenant,
  queryPlatformTenants,
  updatePlatformTenantStatus,
} from './service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('platform tenant service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
  });

  it('uses platform tenant list, detail, and status endpoints', async () => {
    await queryPlatformTenants({
      current: 2,
      pageSize: 20,
      keyword: 'alpha',
      status: 'ACTIVE',
      packageType: 'SUBSCRIPTION',
    });
    await getPlatformTenant(9);
    await updatePlatformTenantStatus(9, 'DISABLED');

    expect(request).toHaveBeenNthCalledWith(1, '/api/platform/tenants', {
      params: {
        current: 2,
        pageSize: 20,
        keyword: 'alpha',
        status: 'ACTIVE',
        packageType: 'SUBSCRIPTION',
      },
    });
    expect(request).toHaveBeenNthCalledWith(2, '/api/platform/tenants/9');
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/api/platform/tenants/9/status',
      { method: 'PUT', data: { status: 'DISABLED' } },
    );
  });
});
