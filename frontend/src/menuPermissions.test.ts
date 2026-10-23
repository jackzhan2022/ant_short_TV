import { describe, expect, it } from 'vitest';
import routes from '../config/routes';
import access from './access';

const menus = [
  ['SHORT_DRAMA_CREATION', 'canUseShortDramaCreation', '/short-drama-creation'],
  ['FREE_CREATION', 'canUseFreeCreation', '/free-creation'],
  ['VIDEO_DECOMPOSITION', 'canUseVideoScriptDecomposition', '/video-script-decomposition'],
  ['SCRIPT_REVIEW', 'canViewScriptReview', '/script-review-library'],
  ['PROJECTS', 'canViewProjects', '/projects/list'],
  ['ASSET_LIBRARY', 'canViewAssetLibrary', '/asset-library'],
  ['PRODUCTION_TASKS', 'canViewProductionTasks', '/tasks'],
  ['TEAM_MANAGEMENT', 'canViewTeamManagement', '/team/my'],
  ['AI_MANAGEMENT', 'canViewAiManagement', '/ai-service-management/model-management'],
] as const;

const member = {
  currentUser: { userid: '1' },
  selectedTenant: { membership: { status: 'ACTIVE', memberType: 'MEMBER' }, roles: ['MEMBER'] },
  tenantPermissions: ['AI_SERVICE:USE'],
};

describe('team menu permissions', () => {
  it.each(menus)('%s only enables its own menu and route', (code, key, path) => {
    const permissions = access({ ...member, tenantPermissions: [...member.tenantPermissions, `MENU:${code}`] });
    expect(permissions[key]).toBe(true);
    for (const [otherCode, otherKey] of menus) {
      if (otherCode !== code) expect(permissions[otherKey]).toBe(false);
    }
    const route = routes.flatMap((item) => [item, ...(item.routes ?? [])]).find((item) => item.path === path);
    expect(route).toMatchObject({ access: key });
  });

  it('denies menus without an active team, regardless of assigned codes or legacy admin labels', () => {
    const tenantPermissions = menus.map(([code]) => `MENU:${code}`);
    for (const status of ['REMOVED', 'DISABLED']) {
      const permissions = access({
        ...member, tenantPermissions,
        currentUser: { userid: '1', access: 'admin' },
        selectedTenant: { membership: { status, memberType: 'OWNER' }, roles: ['ADMIN'] },
      });
      for (const [, key] of menus) expect(permissions[key]).toBe(false);
    }
    for (const [, key] of menus) expect(access(undefined)[key]).toBeFalsy();
  });

  it('preserves team onboarding without requiring a selected team', () => {
    expect(access({ currentUser: member.currentUser, selectedTenant: null }).canViewTeamManagement).toBe(true);
    expect(access(member).canViewTeamManagement).toBe(false);
  });

  it('grants menu functions while retaining platform restrictions', () => {
    const permissions = access({
      ...member, tenantPermissions: [...menus.map(([code]) => `MENU:${code}`), 'MENU:DRAMA_LIBRARY'],
    });
    expect(permissions.canCreateProject).toBe(true);
    expect(permissions.canManageRoles).toBe(true);
    expect(permissions.canViewPlatformAiProviders).toBe(false);
    expect(permissions.canViewDramas).toBe(false);
    expect(permissions.canUseVideoScriptDecomposition).toBe(true);
  });

  it('retains both menu and platform restrictions for drama management', () => {
    const state = {
      ...member,
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
      tenantPermissions: ['MENU:DRAMA_LIBRARY', 'DRAMA:VIEW'],
    };
    expect(access(state).canViewDramas).toBe(true);
    expect(access({ ...state, tenantPermissions: ['DRAMA:VIEW'] }).canViewDramas).toBe(false);
    expect(access({ ...state, bootstrap: { platform: { roles: [] } } }).canViewDramas).toBe(false);
  });
});
