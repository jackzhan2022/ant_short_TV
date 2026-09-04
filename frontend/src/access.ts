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
        selectedTenant?: { membership?: { status?: string } } | null;
      }
    | undefined,
) {
  const { currentUser } = initialState ?? {};
  const tenantPermissions = initialState?.tenantPermissions ?? [];
  const platformPermissions = initialState?.platformPermissions ?? [];
  const hasActiveTenant =
    Boolean(currentUser) &&
    initialState?.selectedTenant?.membership?.status === 'ACTIVE';
  return {
    canAdmin: currentUser && currentUser.access === 'admin',
    canManageRoles: currentUser && tenantPermissions.includes('ROLE:VIEW'),
    canUseProjectCenter: hasActiveTenant,
    canViewStyleLibrary: Boolean(currentUser),
    canUseVideoScriptDecomposition:
      currentUser &&
      hasActiveTenant &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canViewScriptReview: hasActiveTenant,
    canViewProjects: hasActiveTenant,
    canCreateProject:
      hasActiveTenant && tenantPermissions.includes('PROJECT:CREATE'),
    canViewAiCallLogs:
      currentUser && tenantPermissions.includes('AI_CALL_LOG:VIEW'),
    canViewAiManagement:
      currentUser &&
      (tenantPermissions.includes('AI_CALL_LOG:VIEW') ||
        platformPermissions.includes('PLATFORM_AI_PROVIDER_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_MODEL_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_ACCOUNTING_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_AGENT_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_WORKFLOW_AGENT_VIEW') ||
        platformPermissions.includes('PLATFORM_AI_WORKFLOW_SKILL_VIEW')),
    canManageBilling:
      currentUser && tenantPermissions.includes('BILLING:MANAGE'),
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
    canEditPlatformTenantStatus:
      currentUser &&
      platformPermissions.includes('PLATFORM_TENANT_STATUS_EDIT'),
    canViewPlatformAiProviders:
      currentUser && platformPermissions.includes('PLATFORM_AI_PROVIDER_VIEW'),
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
    canViewBuiltInAiAgents:
      currentUser && platformPermissions.includes('PLATFORM_AI_AGENT_VIEW'),
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
      currentUser && tenantPermissions.includes('PROJECT_AI_CONFIG_VIEW'),
    canEditProjectAiConfig:
      currentUser && tenantPermissions.includes('PROJECT_AI_CONFIG_EDIT'),
    canViewAiImageTasks:
      currentUser && tenantPermissions.includes('AI_IMAGE_TASK:VIEW'),
    canCreateAiImageTasks:
      currentUser &&
      tenantPermissions.includes('AI_IMAGE_TASK:CREATE') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canAiGenerateScript:
      currentUser &&
      tenantPermissions.includes('SCRIPT:AI_GENERATE') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canAiRewriteScript:
      currentUser &&
      tenantPermissions.includes('SCRIPT:AI_REWRITE') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canViewElements: currentUser && tenantPermissions.includes('ELEMENT:VIEW'),
    canAiExtractElements:
      currentUser &&
      tenantPermissions.includes('ELEMENT:AI_EXTRACT') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canEditElements: currentUser && tenantPermissions.includes('ELEMENT:EDIT'),
    canViewStoryboards:
      currentUser && tenantPermissions.includes('STORYBOARD:VIEW'),
    canAiBreakdownStoryboards:
      currentUser &&
      tenantPermissions.includes('STORYBOARD:AI_BREAKDOWN') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canEditStoryboards:
      currentUser && tenantPermissions.includes('STORYBOARD:EDIT'),
    canAiGeneratePrompts:
      currentUser &&
      tenantPermissions.includes('PROMPT:AI_GENERATE') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canViewAiVideoTasks:
      currentUser && tenantPermissions.includes('AI_VIDEO_TASK:VIEW'),
    canCreateAiVideoTasks:
      currentUser &&
      tenantPermissions.includes('AI_VIDEO_TASK:CREATE') &&
      tenantPermissions.includes('AI_SERVICE:USE'),
    canCancelAiVideoTasks:
      currentUser && tenantPermissions.includes('AI_VIDEO_TASK:CANCEL'),
    canDeleteAiVideoTasks:
      currentUser && tenantPermissions.includes('AI_VIDEO_TASK:DELETE'),
    canSaveAiVideoResults:
      currentUser && tenantPermissions.includes('AI_VIDEO_RESULT:SAVE'),
    canBindAiVideoResults:
      currentUser && tenantPermissions.includes('AI_VIDEO_RESULT:BIND'),
    canDownloadAiVideoResults:
      currentUser && tenantPermissions.includes('AI_VIDEO_RESULT:DOWNLOAD'),
    canViewEpisodeComposeTasks:
      currentUser && tenantPermissions.includes('EPISODE_COMPOSE:VIEW'),
    canCreateEpisodeComposeTasks:
      currentUser && tenantPermissions.includes('EPISODE_COMPOSE:CREATE'),
    canCancelEpisodeComposeTasks:
      currentUser && tenantPermissions.includes('EPISODE_COMPOSE:CANCEL'),
    canDeleteEpisodeComposeTasks:
      currentUser && tenantPermissions.includes('EPISODE_COMPOSE:DELETE'),
    canViewEpisodeVersions:
      currentUser && tenantPermissions.includes('EPISODE_VERSION:VIEW'),
    canSetCurrentEpisodeVersion:
      currentUser && tenantPermissions.includes('EPISODE_VERSION:SET_CURRENT'),
    canDownloadEpisodeVersions:
      currentUser && tenantPermissions.includes('EPISODE_VERSION:DOWNLOAD'),
    canDeleteEpisodeVersions:
      currentUser && tenantPermissions.includes('EPISODE_VERSION:DELETE'),
    canSaveEpisodeVersions:
      currentUser &&
      tenantPermissions.includes('EPISODE_VERSION:SAVE_MATERIAL'),
  };
}
