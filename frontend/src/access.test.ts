import routes from '../config/routes';
import access from './access';

describe('access', () => {
  it('自由创作允许所有有效团队成员访问', () => {
    const state = {
      currentUser: { userid: '1', access: 'user' },
      selectedTenant: { membership: { status: 'ACTIVE' } },
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
    };
    expect(access(state).canUseFreeCreation).toBe(true);
    expect(access({ ...state, currentUser: undefined }).canUseFreeCreation).toBe(false);
    expect(access({ ...state, selectedTenant: null }).canUseFreeCreation).toBe(false);
    expect(access({
      ...state,
      selectedTenant: { membership: { status: 'REMOVED' } },
    }).canUseFreeCreation).toBe(false);
    for (const roles of [[], ['ADMIN'], ['OWNER'], ['PLATFORM_OPERATOR']]) {
      expect(access({
        ...state,
        currentUser: { userid: '2', access: 'user' },
        selectedTenant: { membership: { status: 'ACTIVE', memberType: 'MEMBER' }, roles: [] },
        bootstrap: { platform: { roles } },
      }).canUseFreeCreation).toBe(true);
    }
    expect(access(undefined).canUseFreeCreation).toBe(false);
    expect(routes.find((route) => route.path === '/free-creation')).toMatchObject({
      access: 'canUseFreeCreation', component: './free-creation',
    });
  });

  it('requires the platform administrator role for tenant package grants', () => {
    const user = { currentUser: { userid: '1' } };
    expect(access({ ...user, bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } } }).canGrantPlatformTenantPackage).toBe(true);
    expect(access({ ...user, selectedTenant: { membership: { status: 'ACTIVE', memberType: 'OWNER' }, roles: ['ADMIN'] } }).canGrantPlatformTenantPackage).toBe(false);
    expect(access({ ...user, platformPermissions: ['PLATFORM_TENANT_VIEW', 'PLATFORM_TENANT_TRIAL_ASSIGN'] }).canGrantPlatformTenantPackage).toBe(false);
    expect(access({ bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } } }).canGrantPlatformTenantPackage).toBe(false);
  });

  it('grants the existing management entry to a concurrency-only platform viewer', () => {
    const result = access({
      currentUser: { userid: '1' },
      platformPermissions: ['PLATFORM_AI_CONCURRENCY_VIEW'],
    });
    expect(result.canViewAiManagement).toBe(true);
    expect(result.canViewPlatformAiConcurrency).toBe(true);
    expect(result.canEditPlatformAiConcurrency).toBe(false);
    expect(result.canViewPlatformAiProviders).toBe(false);
    expect(result.canViewTenantWorkflowSkills).toBe(false);
  });

  it('keeps concurrency platform view and edit permissions independent', () => {
    const state = { currentUser: { userid: '1' } };
    const editor = access({
      ...state,
      platformPermissions: ['PLATFORM_AI_CONCURRENCY_EDIT'],
    });
    expect(editor.canEditPlatformAiConcurrency).toBe(true);
    expect(editor.canViewPlatformAiConcurrency).toBe(false);
    expect(editor.canViewAiManagement).toBe(false);
    const tenantOnly = access({
      ...state,
      tenantPermissions: ['PLATFORM_AI_CONCURRENCY_VIEW', 'PLATFORM_AI_CONCURRENCY_EDIT'],
    });
    expect(tenantOnly.canViewPlatformAiConcurrency).toBe(false);
    expect(tenantOnly.canEditPlatformAiConcurrency).toBe(false);
    expect(access({
      platformPermissions: ['PLATFORM_AI_CONCURRENCY_VIEW', 'PLATFORM_AI_CONCURRENCY_EDIT'],
    }).canEditPlatformAiConcurrency).toBe(false);
  });
  it('limits tenant Skill management to an active owner or administrator', () => {
    const ordinary = access({
      currentUser: { userid: '1' },
      selectedTenant: {
        membership: { status: 'ACTIVE', memberType: 'MEMBER' },
        roles: ['MEMBER'],
      },
    });
    expect(ordinary.canViewTenantWorkflowSkills).toBe(true);
    expect(ordinary.canViewAiManagement).toBe(true);
    expect(ordinary.canManageTenantWorkflowSkills).toBe(false);
    expect(ordinary.canViewWorkflowSkills).toBe(false);
    expect(ordinary.canEditWorkflowSkills).toBe(false);
    const base = {
      currentUser: { userid: '1' },
      platformPermissions: ['PLATFORM_AI_WORKFLOW_SKILL_EDIT'],
    };
    expect(access(base).canManageTenantWorkflowSkills).toBe(false);
    for (const membership of [
      { status: 'ACTIVE', memberType: 'OWNER' },
      { status: 'ACTIVE', memberType: 'MEMBER' },
    ]) {
      const state = {
        ...base,
        selectedTenant: {
          membership,
          roles: membership.memberType === 'MEMBER' ? ['ADMIN'] : [],
        },
      };
      expect(access(state).canManageTenantWorkflowSkills).toBe(true);
      expect(access(state).canViewAiManagement).toBe(true);
      expect(
        access({
          ...state,
          selectedTenant: {
            membership: { ...membership, status: 'REMOVED' },
            roles: ['ADMIN'],
          },
        }).canManageTenantWorkflowSkills,
      ).toBe(false);
    }
    expect(
      access({
        ...base,
        selectedTenant: {
          membership: { status: 'ACTIVE', memberType: 'MEMBER' },
          roles: ['MEMBER'],
        },
      }).canManageTenantWorkflowSkills,
    ).toBe(false);
  });
  it('剧集管理菜单与直达路由使用同一权限门禁', () => {
    expect(routes.find((route) => route.path === '/drama-library')).toMatchObject({
      access: 'canViewDramas', component: './drama-library',
    });
  });

  const dramaPermissions = ['DRAMA:VIEW', 'DRAMA:MANAGE', 'DRAMA:PUBLISH', 'MINIAPP:MANAGE'];
  const dramaKeys = ['canViewDramas', 'canManageDramas', 'canPublishDramas', 'canManageMiniapps'] as const;

  it('仅真实平台管理员在有效团队内拥有剧库管理能力', () => {
    const state = {
      currentUser: { userid: '1', access: 'user' },
      tenantPermissions: dramaPermissions,
      selectedTenant: { membership: { status: 'ACTIVE' } },
      bootstrap: { platform: { roles: ['PLATFORM_ADMIN'] } },
    };
    for (const key of dramaKeys) {
      expect(access(state)[key]).toBe(true);
      expect(access({ ...state, selectedTenant: null })[key]).toBe(false);
      expect(access({ ...state, tenantPermissions: [] })[key]).toBe(false);
      expect(access({ ...state, currentUser: undefined })[key]).toBe(false);
    }
  });

  it('普通成员即使有全部功能权限或旧 admin 标记仍不可访问', () => {
    for (const roles of [[], ['ADMIN'], ['OWNER'], ['PLATFORM_OPERATOR']]) {
      const result = access({
        currentUser: { userid: '2', access: 'admin' },
        tenantPermissions: dramaPermissions,
        platformPermissions: ['PLATFORM_INSPIRATION_MANAGE'],
        selectedTenant: { membership: { status: 'ACTIVE' } },
        bootstrap: { platform: { roles } },
      });
      for (const key of dramaKeys) expect(result[key]).toBe(false);
    }
    for (const key of dramaKeys) {
      expect(access(undefined)[key]).toBe(false);
      expect(access({
        currentUser: { userid: '3', access: 'admin' },
        selectedTenant: { membership: { status: 'ACTIVE' } },
        tenantPermissions: dramaPermissions,
      })[key]).toBe(false);
    }
  });

  it('separates platform tenant view and status permissions', () => {
    const viewer = access({
      currentUser: { userid: '1', name: 'Viewer', access: 'user' },
      platformPermissions: ['PLATFORM_TENANT_VIEW'],
    });
    const operator = access({
      currentUser: { userid: '2', name: 'Operator', access: 'user' },
      platformPermissions: [
        'PLATFORM_TENANT_VIEW',
        'PLATFORM_TENANT_STATUS_EDIT',
      ],
    });

    expect(viewer.canViewPlatformTenants).toBe(true);
    expect(viewer.canEditPlatformTenantStatus).toBe(false);
    expect(operator.canViewPlatformTenants).toBe(true);
    expect(operator.canEditPlatformTenantStatus).toBe(true);
  });
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

  it('separates tenant call-log access from platform configuration', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'AI Operator',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
      tenantPermissions: ['AI_CALL_LOG:VIEW'],
    };

    const result = access(initialState);

    expect(result.canViewAiCallLogs).toBe(true);
    expect(result.canViewPlatformAiProviders).toBe(false);
    expect(result.canViewPlatformAiModels).toBe(false);
    expect(result.canViewAiManagement).toBe(true);
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

  it('should expose the AI management entry for platform permissions', () => {
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

  it('keeps workflow Agent and Skill view/edit permissions independent', () => {
    const result = access({
      currentUser: { userid: '1', name: 'Workflow Editor', access: 'user' },
      platformPermissions: [
        'PLATFORM_AI_WORKFLOW_AGENT_VIEW',
        'PLATFORM_AI_WORKFLOW_AGENT_EDIT',
        'PLATFORM_AI_WORKFLOW_SKILL_VIEW',
      ],
    });

    expect(result.canViewAiManagement).toBe(true);
    expect(result.canViewWorkflowAgents).toBe(true);
    expect(result.canEditWorkflowAgents).toBe(true);
    expect(result.canViewWorkflowSkills).toBe(true);
    expect(result.canEditWorkflowSkills).toBe(false);
    expect(result).not.toHaveProperty('canViewBuiltInAiAgents');
  });

  it('does not grant current configuration access from retired Agent permissions', () => {
    const result = access({
      currentUser: { userid: '1', name: 'Legacy Viewer', access: 'user' },
      platformPermissions: ['PLATFORM_AI_AGENT_VIEW'],
    });
    expect(result.canViewAiManagement).toBe(false);
    expect(result.canViewWorkflowAgents).toBe(false);
    expect(result.canViewWorkflowSkills).toBe(false);
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

  it('requires AI usage permission for provider-backed task creation', () => {
    const withoutUsage = access({
      currentUser: { userid: '1', name: 'Workflow Creator', access: 'user' },
      tenantPermissions: ['AI_IMAGE_TASK:CREATE', 'AI_VIDEO_TASK:CREATE'],
    });
    const withUsage = access({
      currentUser: { userid: '1', name: 'AI Creator', access: 'user' },
      tenantPermissions: [
        'AI_IMAGE_TASK:CREATE',
        'AI_VIDEO_TASK:CREATE',
        'AI_SERVICE:USE',
      ],
    });

    expect(withoutUsage.canCreateAiImageTasks).toBe(false);
    expect(withoutUsage.canCreateAiVideoTasks).toBe(false);
    expect(withUsage.canCreateAiImageTasks).toBe(true);
    expect(withUsage.canCreateAiVideoTasks).toBe(true);
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
