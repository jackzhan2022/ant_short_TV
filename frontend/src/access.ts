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
  };
}
