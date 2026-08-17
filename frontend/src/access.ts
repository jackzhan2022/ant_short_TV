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
    canViewProjects: currentUser && permissions.includes('PROJECT:VIEW'),
    canViewAiServices: currentUser && permissions.includes('AI_SERVICE:VIEW'),
    canCreateAiServices: currentUser && permissions.includes('AI_SERVICE:CREATE'),
    canEditAiServices: currentUser && permissions.includes('AI_SERVICE:EDIT'),
    canDeleteAiServices: currentUser && permissions.includes('AI_SERVICE:DELETE'),
    canTestAiServices: currentUser && permissions.includes('AI_SERVICE:TEST'),
    canViewAiVideoTasks: currentUser && permissions.includes('AI_VIDEO_TASK:VIEW'),
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
  };
}
