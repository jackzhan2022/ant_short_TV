/**
 * @see https://umijs.org/docs/max/access#access
 * */
export default function access(
  initialState:
    | { currentUser?: API.CurrentUser; permissions?: string[] }
    | undefined,
) {
  const { currentUser } = initialState ?? {};
  const permissions = initialState?.permissions ?? [];
  return {
    canAdmin: currentUser && currentUser.access === 'admin',
    canManageRoles: currentUser && permissions.includes('ROLE:VIEW'),
    canManageOrganizations:
      currentUser && permissions.includes('ORGANIZATION:VIEW'),
    canUseProjectCenter: Boolean(currentUser),
    canViewStyleLibrary: Boolean(currentUser),
    canUseVideoScriptDecomposition:
      currentUser &&
      permissions.includes('PROJECT:VIEW') &&
      permissions.includes('AI_SERVICE:USE'),
    canViewScriptReview:
      currentUser &&
      permissions.includes('PROJECT:VIEW') &&
      permissions.includes('SCRIPT:EDIT'),
    canViewProjects: currentUser && permissions.includes('PROJECT:VIEW'),
    canViewAiServices: currentUser && permissions.includes('AI_SERVICE:VIEW'),
    canViewAiManagement:
      currentUser &&
      (permissions.includes('AI_SERVICE:VIEW') ||
        permissions.includes('PLATFORM_AI_PROVIDER_VIEW') ||
        permissions.includes('PLATFORM_AI_MODEL_VIEW') ||
        permissions.includes('PLATFORM_AI_AGENT_VIEW')),
    canCreateAiServices: currentUser && permissions.includes('AI_SERVICE:CREATE'),
    canEditAiServices: currentUser && permissions.includes('AI_SERVICE:EDIT'),
    canDeleteAiServices: currentUser && permissions.includes('AI_SERVICE:DELETE'),
    canTestAiServices: currentUser && permissions.includes('AI_SERVICE:TEST'),
    canViewPlatformAiProviders:
      currentUser && permissions.includes('PLATFORM_AI_PROVIDER_VIEW'),
    canCreatePlatformAiProviders:
      currentUser && permissions.includes('PLATFORM_AI_PROVIDER_CREATE'),
    canEditPlatformAiProviders:
      currentUser && permissions.includes('PLATFORM_AI_PROVIDER_EDIT'),
    canEnablePlatformAiProviders:
      currentUser && permissions.includes('PLATFORM_AI_PROVIDER_ENABLE'),
    canTestPlatformAiProviders:
      currentUser && permissions.includes('PLATFORM_AI_PROVIDER_TEST'),
    canViewPlatformAiModels:
      currentUser && permissions.includes('PLATFORM_AI_MODEL_VIEW'),
    canCreatePlatformAiModels:
      currentUser && permissions.includes('PLATFORM_AI_MODEL_CREATE'),
    canEditPlatformAiModels:
      currentUser && permissions.includes('PLATFORM_AI_MODEL_EDIT'),
    canEnablePlatformAiModels:
      currentUser && permissions.includes('PLATFORM_AI_MODEL_ENABLE'),
    canViewBuiltInAiAgents:
      currentUser && permissions.includes('PLATFORM_AI_AGENT_VIEW'),
    canViewProjectAiConfig:
      currentUser && permissions.includes('PROJECT_AI_CONFIG_VIEW'),
    canEditProjectAiConfig:
      currentUser && permissions.includes('PROJECT_AI_CONFIG_EDIT'),
    canViewAiImageTasks:
      currentUser && permissions.includes('AI_IMAGE_TASK:VIEW'),
    canCreateAiImageTasks:
      currentUser && permissions.includes('AI_IMAGE_TASK:CREATE'),
    canAiGenerateScript:
      currentUser &&
      permissions.includes('SCRIPT:AI_GENERATE') &&
      permissions.includes('AI_SERVICE:USE'),
    canAiRewriteScript:
      currentUser &&
      permissions.includes('SCRIPT:AI_REWRITE') &&
      permissions.includes('AI_SERVICE:USE'),
    canViewElements: currentUser && permissions.includes('ELEMENT:VIEW'),
    canAiExtractElements:
      currentUser &&
      permissions.includes('ELEMENT:AI_EXTRACT') &&
      permissions.includes('AI_SERVICE:USE'),
    canEditElements: currentUser && permissions.includes('ELEMENT:EDIT'),
    canViewStoryboards: currentUser && permissions.includes('STORYBOARD:VIEW'),
    canAiBreakdownStoryboards:
      currentUser &&
      permissions.includes('STORYBOARD:AI_BREAKDOWN') &&
      permissions.includes('AI_SERVICE:USE'),
    canEditStoryboards: currentUser && permissions.includes('STORYBOARD:EDIT'),
    canAiGeneratePrompts:
      currentUser &&
      permissions.includes('PROMPT:AI_GENERATE') &&
      permissions.includes('AI_SERVICE:USE'),
    canViewAiVideoTasks:
      currentUser && permissions.includes('AI_VIDEO_TASK:VIEW'),
    canCreateAiVideoTasks:
      currentUser && permissions.includes('AI_VIDEO_TASK:CREATE'),
    canCancelAiVideoTasks:
      currentUser && permissions.includes('AI_VIDEO_TASK:CANCEL'),
    canDeleteAiVideoTasks:
      currentUser && permissions.includes('AI_VIDEO_TASK:DELETE'),
    canSaveAiVideoResults:
      currentUser && permissions.includes('AI_VIDEO_RESULT:SAVE'),
    canBindAiVideoResults:
      currentUser && permissions.includes('AI_VIDEO_RESULT:BIND'),
    canDownloadAiVideoResults:
      currentUser && permissions.includes('AI_VIDEO_RESULT:DOWNLOAD'),
    canViewEpisodeComposeTasks:
      currentUser && permissions.includes('EPISODE_COMPOSE:VIEW'),
    canCreateEpisodeComposeTasks:
      currentUser && permissions.includes('EPISODE_COMPOSE:CREATE'),
    canCancelEpisodeComposeTasks:
      currentUser && permissions.includes('EPISODE_COMPOSE:CANCEL'),
    canDeleteEpisodeComposeTasks:
      currentUser && permissions.includes('EPISODE_COMPOSE:DELETE'),
    canViewEpisodeVersions:
      currentUser && permissions.includes('EPISODE_VERSION:VIEW'),
    canSetCurrentEpisodeVersion:
      currentUser && permissions.includes('EPISODE_VERSION:SET_CURRENT'),
    canDownloadEpisodeVersions:
      currentUser && permissions.includes('EPISODE_VERSION:DOWNLOAD'),
    canDeleteEpisodeVersions:
      currentUser && permissions.includes('EPISODE_VERSION:DELETE'),
    canSaveEpisodeVersions:
      currentUser && permissions.includes('EPISODE_VERSION:SAVE_MATERIAL'),
  };
}
