import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applyBootstrapSelection } from './bootstrap';

const mocks = vi.hoisted(() => ({
  queryAuthBootstrap: vi.fn(),
  setCurrentTenantId: vi.fn(),
}));

vi.mock('./auth', () => ({
  queryAuthBootstrap: mocks.queryAuthBootstrap,
  setCurrentTenantId: mocks.setCurrentTenantId,
}));

describe('applyBootstrapSelection', () => {
  beforeEach(() => vi.clearAllMocks());

  it('applies validated state before persisting the selected tenant', async () => {
    mocks.queryAuthBootstrap.mockResolvedValue({
      data: {
        user: { id: 1, mobile: '13800000000', nickname: '用户', status: 'ACTIVE' },
        session: { sessionId: 's1', expiresAt: '2026-09-01T00:00:00' },
        platform: { roles: [], permissions: [] },
        tenants: [{ id: 12, name: '团队' }],
        selectedTenant: {
          tenant: { id: 12, name: '团队' },
          membership: { id: 1, memberType: 'MEMBER', status: 'ACTIVE' },
          roles: ['MEMBER'], permissions: ['PROJECT:CREATE'],
        },
        nextAction: 'ENTER_WORKSPACE',
      },
    });
    const setInitialState = vi.fn().mockResolvedValue(undefined);

    await applyBootstrapSelection(12, setInitialState);

    expect(setInitialState).toHaveBeenCalledOnce();
    expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(12);
    expect(setInitialState.mock.invocationCallOrder[0]).toBeLessThan(
      mocks.setCurrentTenantId.mock.invocationCallOrder[0],
    );
  });

  it('does not mutate state or storage when the requested tenant is unavailable', async () => {
    mocks.queryAuthBootstrap.mockResolvedValue({
      data: {
        selectedTenant: null,
        unavailableSelectionReason: 'TENANT_MEMBERSHIP_INACTIVE',
      },
    });
    const setInitialState = vi.fn();

    await expect(applyBootstrapSelection(12, setInitialState)).rejects.toThrow(
      'TENANT_MEMBERSHIP_INACTIVE',
    );
    expect(setInitialState).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).not.toHaveBeenCalled();
  });
});
