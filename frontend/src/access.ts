/**
 * @see https://umijs.org/docs/max/access#access
 * */
import type { LayoutCurrentUser } from '@/services/account-team/types';

export default function access(
  initialState:
    | {
        currentUser?: LayoutCurrentUser;
        tenantPermissions?: string[];
        platformPermissions?: string[];
        bootstrap?: { platform: { roles: string[] } };
        selectedTenant?: {
          membership?: { status?: string; memberType?: string };
          roles?: string[];
        } | null;
      }
    | undefined,
) {
  const { currentUser } = initialState ?? {};
  const tenantPermissions = initialState?.tenantPermissions ?? [];
  const platformPermissions = initialState?.platformPermissions ?? [];
  const hasActiveTenant =
    Boolean(currentUser) &&
    initialState?.selectedTenant?.membership?.status === 'ACTIVE';
  const hasMenu = (name: string) =>
    hasActiveTenant && tenantPermissions.includes(`MENU:${name}`);
  const canUseProjectFeatures = hasMenu('PROJECTS') || hasMenu('SHORT_DRAMA_CREATION');
  const canUseDramaAdministration =
    hasActiveTenant &&
    initialState?.bootstrap?.platform.roles.includes('PLATFORM_ADMIN') === true;
  return {
    canAdmin: currentUser && currentUser.access === 'admin',
    canManageTenantWorkflowSkills: hasMenu('AI_MANAGEMENT'),
    canViewTenantWorkflowSkills: hasMenu('AI_MANAGEMENT'),
    canManageRoles: currentUser && hasMenu('TEAM_MANAGEMENT'),
    canUseProjectCenter: hasMenu('PROJECTS'),
    canUseShortDramaCreation: hasMenu('SHORT_DRAMA_CREATION'),
    canViewAssetLibrary: hasMenu('ASSET_LIBRARY'),
    canViewProductionTasks: hasMenu('PRODUCTION_TASKS'),
    canViewTeamManagement:
      Boolean(currentUser) &&
      (!initialState?.selectedTenant || hasMenu('TEAM_MANAGEMENT')),
    canUseFreeCreation: hasMenu('FREE_CREATION'),
    canViewStyleLibrary: Boolean(currentUser),
    canUseVideoScriptDecomposition:
      currentUser &&
      hasMenu('VIDEO_DECOMPOSITION'),
    canViewScriptReview: hasMenu('SCRIPT_REVIEW'),
    canViewProjects: hasMenu('PROJECTS'),
    canViewDramas:
      canUseDramaAdministration && hasMenu('DRAMA_LIBRARY'),
    canManageDramas:
      canUseDramaAdministration && hasMenu('DRAMA_LIBRARY'),
    canPublishDramas:
      canUseDramaAdministration && hasMenu('DRAMA_LIBRARY'),
    canManageMiniapps:
      canUseDramaAdministration && hasMenu('DRAMA_LIBRARY'),
    canCreateProject:
      hasActiveTenant && canUseProjectFeatures,
    canViewAiCallLogs:
      currentUser && hasMenu('AI_MANAGEMENT'),
    canViewAiManagement:
      currentUser &&
      (hasMenu('AI_MANAGEMENT') ||
        platformPermissions.includes('PLATFORM_AI_PROVIDER_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_MODEL_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_ACCOUNTING_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_CONCURRENCY_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_WORKFLOW_AGENT_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_WORKFLOW_SKILL_VIEW')),
    canManageBilling:
      currentUser && hasMenu('TEAM_MANAGEMENT'),
    canViewCommercial: hasActiveTenant,
    canViewCommercialPackages:
      currentUser &&
      platformPermissions.includes('PLATFORM_COMMERCIAL_ORDER_VIEW'),
    canViewCommercialOrders:
      currentUser &&
      platformPermissions.includes('PLATFORM_COMMERCIAL_ORDER_VIEW'),
    canEditCommercialPackages:
      currentUser &&
      platformPermissions.includes('PLATFORM_COMMERCIAL_PACKAGE_EDIT'),
    canViewPlatformTenants:
      currentUser && platformPermissions.includes('PLATFORM_TENANT_VIEW'),
    canGrantPlatformTenantPackage:
      !!currentUser && initialState?.bootstrap?.platform.roles.includes('PLATFORM_ADMIN') === true,
    canAssignPlatformTenantTrial:
      !!currentUser && platformPermissions.includes('PLATFORM_TENANT_TRIAL_ASSIGN'),
    canEditPlatformTenantStatus:
      currentUser &&
      platformPermissions.includes('PLATFORM_TENANT_STATUS_EDIT'),
    canViewPlatformAiProviders:
      currentUser && platformPermissions.includes('PLATFORM_AI_PROVIDER_VIEW'),
    canViewPlatformAiConcurrency:
      Boolean(currentUser) &&
      platformPermissions.includes('PLATFORM_AI_CONCURRENCY_VIEW'),
    canEditPlatformAiConcurrency:
      Boolean(currentUser) &&
      platformPermissions.includes('PLATFORM_AI_CONCURRENCY_EDIT'),
    canCreatePlatformAiProviders:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_PROVIDER_CREATE'),
    canEditPlatformAiProviders:
      currentUser && platformPermissions.includes('PLATFORM_AI_PROVIDER_EDIT'),
    canEnablePlatformAiProviders:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_PROVIDER_ENABLE'),
    canTestPlatformAiProviders:
      currentUser && platformPermissions.includes('PLATFORM_AI_PROVIDER_TEST'),
    canViewPlatformAiModels:
      currentUser && platformPermissions.includes('PLATFORM_AI_MODEL_VIEW'),
    canViewModelBilling:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_ACCOUNTING_VIEW'),
    canPublishModelBilling:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_PRICE_PUBLISH') &&
      platformPermissions.includes('PLATFORM_AI_POINT_PRICE_PUBLISH'),
    canCreatePlatformAiModels:
      currentUser && platformPermissions.includes('PLATFORM_AI_MODEL_CREATE'),
    canEditPlatformAiModels:
      currentUser && platformPermissions.includes('PLATFORM_AI_MODEL_EDIT'),
    canEnablePlatformAiModels:
      currentUser && platformPermissions.includes('PLATFORM_AI_MODEL_ENABLE'),
    canViewWorkflowAgents:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_WORKFLOW_AGENT_VIEW'),
    canEditWorkflowAgents:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_WORKFLOW_AGENT_EDIT'),
    canViewWorkflowSkills:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_WORKFLOW_SKILL_VIEW'),
    canEditWorkflowSkills:
      currentUser &&
      platformPermissions.includes('PLATFORM_AI_WORKFLOW_SKILL_EDIT'),
    canViewProjectAiConfig:
      currentUser && canUseProjectFeatures,
    canEditProjectAiConfig:
      currentUser && canUseProjectFeatures,
    canViewAiImageTasks:
      currentUser && canUseProjectFeatures,
    canCreateAiImageTasks:
      currentUser &&
      canUseProjectFeatures,
    canAiGenerateScript:
      currentUser &&
      canUseProjectFeatures,
    canAiRewriteScript:
      currentUser &&
      canUseProjectFeatures,
    canViewElements: currentUser && canUseProjectFeatures,
    canAiExtractElements:
      currentUser &&
      canUseProjectFeatures,
    canEditElements: currentUser && canUseProjectFeatures,
    canViewStoryboards:
      currentUser && canUseProjectFeatures,
    canAiBreakdownStoryboards:
      currentUser &&
      canUseProjectFeatures,
    canEditStoryboards:
      currentUser && canUseProjectFeatures,
    canAiGeneratePrompts:
      currentUser &&
      canUseProjectFeatures,
    canViewAiVideoTasks:
      currentUser && canUseProjectFeatures,
    canCreateAiVideoTasks:
      currentUser &&
      canUseProjectFeatures,
    canCancelAiVideoTasks:
      currentUser && canUseProjectFeatures,
    canDeleteAiVideoTasks:
      currentUser && canUseProjectFeatures,
    canSaveAiVideoResults:
      currentUser && canUseProjectFeatures,
    canBindAiVideoResults:
      currentUser && canUseProjectFeatures,
    canDownloadAiVideoResults:
      currentUser && canUseProjectFeatures,
    canViewEpisodeComposeTasks:
      currentUser && canUseProjectFeatures,
    canCreateEpisodeComposeTasks:
      currentUser && canUseProjectFeatures,
    canCancelEpisodeComposeTasks:
      currentUser && canUseProjectFeatures,
    canDeleteEpisodeComposeTasks:
      currentUser && canUseProjectFeatures,
    canViewEpisodeVersions:
      currentUser && canUseProjectFeatures,
    canSetCurrentEpisodeVersion:
      currentUser && canUseProjectFeatures,
    canDownloadEpisodeVersions:
      currentUser && canUseProjectFeatures,
    canDeleteEpisodeVersions:
      currentUser && canUseProjectFeatures,
    canSaveEpisodeVersions:
      currentUser &&
      canUseProjectFeatures,
  };
}
