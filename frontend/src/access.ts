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
  };
}
