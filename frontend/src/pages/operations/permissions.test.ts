import { describe, expect, it } from 'vitest';
import routes from '../../../config/routes';
import access from '../../access';

describe('operations platform permissions', () => {
  const permissions = [
    'PLATFORM_OPERATIONS_VIEW',
    'PLATFORM_OPERATIONS_LOG_VIEW',
    'PLATFORM_OPERATIONS_RULE_EDIT',
    'PLATFORM_OPERATIONS_ALERT_MANAGE',
    'PLATFORM_OPERATIONS_SETTINGS_EDIT',
  ];
  const keys = [
    'canViewOperations',
    'canViewOperationsLogs',
    'canEditOperationsRules',
    'canManageOperationsAlerts',
    'canEditOperationsSettings',
  ] as const;
  it('does not inherit tenant admin privileges and requires a signed-in platform permission', () => {
    for (const key of keys) {
      expect(
        access({
          currentUser: { userid: '1', access: 'admin' },
          selectedTenant: {
            roles: ['ADMIN'],
            membership: { status: 'ACTIVE' },
          },
        })[key],
      ).toBe(false);
      expect(access({ platformPermissions: permissions })[key]).toBe(false);
      expect(
        access({
          currentUser: { userid: '1' },
          platformPermissions: permissions,
        })[key],
      ).toBe(true);
    }
  });
  it('rejects impersonation even when origin permissions appear in state', () => {
    const result = access({
      currentUser: { userid: '1' },
      platformPermissions: permissions,
      bootstrap: {
        platform: { roles: ['PLATFORM_ADMIN'] },
        session: {
          impersonation: {
            administratorId: 1,
            administratorName: 'admin',
            tenantId: 9,
          },
        },
      },
    });
    for (const key of keys) expect(result[key]).toBe(false);
  });
  it('keeps view separate from edit/log grants and gates the route', () => {
    const result = access({
      currentUser: { userid: '1' },
      platformPermissions: ['PLATFORM_OPERATIONS_VIEW'],
    });
    expect(result.canViewOperations).toBe(true);
    for (const key of keys.slice(1)) expect(result[key]).toBe(false);
    expect(routes.find((route) => route.path === '/operations')).toMatchObject({
      access: 'canViewOperations',
      component: './operations',
    });
  });
});
