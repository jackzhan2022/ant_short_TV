import type access from '@/access';

const destinations: [keyof ReturnType<typeof access>, string][] = [
  ['canViewProjects', '/projects/list'],
  ['canUseFreeCreation', '/free-creation'],
  ['canUseShortDramaCreation', '/short-drama-creation'],
  ['canViewAssetLibrary', '/asset-library'],
  ['canViewProductionTasks', '/tasks'],
  ['canViewScriptReview', '/script-review-library'],
  ['canUseVideoScriptDecomposition', '/video-script-decomposition'],
  ['canViewDramas', '/drama-library'],
  ['canViewTeamManagement', '/team/my'],
  ['canViewAiManagement', '/ai-service-management/model-management'],
  ['canViewCommercialPackages', '/commercial-management/packages'],
  ['canViewCommercialOrders', '/commercial-management/orders'],
  ['canViewPlatformTenants', '/commercial-management/tenants'],
];

export const workspaceLandingPath = (permissions: ReturnType<typeof access>) =>
  destinations.find(([key]) => permissions[key])?.[1] ?? '/exception/403';
