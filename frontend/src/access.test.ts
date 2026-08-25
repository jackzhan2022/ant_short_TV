import { describe, expect, it } from 'vitest';
import access from './access';

describe('access', () => {
  it('should return canAdmin true when user has admin access', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'Admin User',
        avatar: 'https://example.com/avatar.png',
        access: 'admin',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(true);
  });

  it('should allow role management when ROLE:VIEW is present', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'Owner User',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      tenantPermissions: ['ROLE:VIEW'],
    };

    const result = access(initialState);

    expect(result.canManageRoles).toBe(true);
  });

  it('should expose AI service permissions from current tenant permissions', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'AI Operator',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      tenantPermissions: [
        'AI_SERVICE:VIEW',
        'AI_SERVICE:CREATE',
        'AI_SERVICE:EDIT',
        'AI_SERVICE:DELETE',
        'AI_SERVICE:TEST',
      ],
    };

    const result = access(initialState);

    expect(result.canViewAiServices).toBe(true);
    expect(result.canCreateAiServices).toBe(true);
    expect(result.canEditAiServices).toBe(true);
    expect(result.canDeleteAiServices).toBe(true);
    expect(result.canTestAiServices).toBe(true);
  });

  it('should expose platform AI and project AI config permissions', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'AI Admin',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      platformPermissions: [
        'PLATFORM_AI_PROVIDER_VIEW',
        'PLATFORM_AI_PROVIDER_CREATE',
        'PLATFORM_AI_PROVIDER_EDIT',
        'PLATFORM_AI_PROVIDER_ENABLE',
        'PLATFORM_AI_PROVIDER_TEST',
        'PLATFORM_AI_MODEL_VIEW',
        'PLATFORM_AI_MODEL_CREATE',
        'PLATFORM_AI_MODEL_EDIT',
        'PLATFORM_AI_MODEL_ENABLE',
      ],
      tenantPermissions: ['PROJECT_AI_CONFIG_VIEW', 'PROJECT_AI_CONFIG_EDIT'],
    };

    const result = access(initialState);

    expect(result.canViewPlatformAiProviders).toBe(true);
    expect(result.canCreatePlatformAiProviders).toBe(true);
    expect(result.canEditPlatformAiProviders).toBe(true);
    expect(result.canEnablePlatformAiProviders).toBe(true);
    expect(result.canTestPlatformAiProviders).toBe(true);
    expect(result.canViewPlatformAiModels).toBe(true);
    expect(result.canCreatePlatformAiModels).toBe(true);
    expect(result.canEditPlatformAiModels).toBe(true);
    expect(result.canEnablePlatformAiModels).toBe(true);
    expect(result.canViewProjectAiConfig).toBe(true);
    expect(result.canEditProjectAiConfig).toBe(true);
  });

  it('should expose the combined AI management entry for legacy and platform permissions', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'AI Viewer',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      platformPermissions: ['PLATFORM_AI_PROVIDER_VIEW'],
    };

    const result = access(initialState);

    expect(result.canViewAiManagement).toBe(true);
  });

  it('should allow authenticated users to enter project center routes', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'Project Member',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      tenantPermissions: [],
    };

    const result = access(initialState);

    expect(result.canUseProjectCenter).toBe(true);
    expect(result.canViewProjects).toBe(true);
  });

  it('does not grant tenant project navigation to a platform-only user', () => {
    const result = access({
      currentUser: { userid: '1', name: 'Platform Operator', access: 'user' },
      platformPermissions: ['PLATFORM_AI_PROVIDER_VIEW'],
      tenantPermissions: [],
    });

    expect(result.canViewProjects).toBe(false);
    expect(result.canViewPlatformAiProviders).toBe(true);
  });

  it('does not derive platform access from tenant permissions', () => {
    const result = access({
      currentUser: { userid: '1', name: 'Tenant Owner', access: 'user' },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      tenantPermissions: ['PLATFORM_AI_PROVIDER_VIEW'],
      platformPermissions: [],
    });

    expect(result.canViewPlatformAiProviders).toBe(false);
  });

  it('uses tenant PROJECT:CREATE only for the project creation entry', () => {
    const result = access({
      currentUser: { userid: '1', name: 'Creator', access: 'user' },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      tenantPermissions: ['PROJECT:CREATE'],
      platformPermissions: [],
    });

    expect(result.canCreateProject).toBe(true);
  });

  it('should allow authenticated users to view the public style library', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'Style Viewer',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      tenantPermissions: [],
    };

    const result = access(initialState);

    expect(result.canViewStyleLibrary).toBe(true);
  });

  it('should return canAdmin false when user has non-admin access', () => {
    const initialState = {
      currentUser: {
        userid: '2',
        name: 'Regular User',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(false);
  });

  it('should return canAdmin false when user access is undefined', () => {
    const initialState = {
      currentUser: {
        userid: '3',
        name: 'Guest User',
        avatar: 'https://example.com/avatar.png',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(false);
  });

  it('should return canAdmin false when currentUser is undefined', () => {
    const initialState = {
      currentUser: undefined,
    };

    const result = access(initialState);

    expect(result.canAdmin).toBeFalsy();
  });

  it('should return canAdmin false when initialState is undefined', () => {
    const result = access(undefined);

    expect(result.canAdmin).toBeFalsy();
  });
});
