import type { AuthBootstrap, LayoutCurrentUser, UserProfile } from './types';
import { queryAuthBootstrap, setCurrentTenantId } from './auth';

const toLayoutCurrentUser = (user: UserProfile): LayoutCurrentUser => ({
  name: user.nickname,
  avatar: user.avatar || undefined,
  userid: String(user.id),
  email: user.email || undefined,
  phone: user.mobile,
  title: user.status === 'ACTIVE' ? '创作团队成员' : '账号已停用',
  group: 'Ant Short TV',
  access: 'user',
});

export const toBootstrapState = (bootstrap: AuthBootstrap) => ({
  currentUser: toLayoutCurrentUser(bootstrap.user),
  currentTenantId: bootstrap.selectedTenant?.tenant.id,
  tenants: bootstrap.tenants,
  selectedTenant: bootstrap.selectedTenant,
  tenantPermissions: bootstrap.selectedTenant?.permissions ?? [],
  platformPermissions: bootstrap.platform.permissions,
  bootstrap,
});

type InitialStateSetter<T> = (
  initialState: T | ((state: T) => T),
) => void | Promise<void>;

export const applyBootstrapSelection = async <T>(
  tenantId: number | undefined,
  setInitialState: InitialStateSetter<T>,
) => {
  const bootstrap = (
    await queryAuthBootstrap(tenantId, { skipErrorHandler: true })
  ).data;
  const selectedTenantId = bootstrap.selectedTenant?.tenant.id;
  if (tenantId && selectedTenantId !== tenantId) {
    throw new Error(
      bootstrap.unavailableSelectionReason || 'Selected tenant is unavailable',
    );
  }
  await setInitialState(
    (state) => ({ ...state, ...toBootstrapState(bootstrap) }) as T,
  );
  if (selectedTenantId) setCurrentTenantId(selectedTenantId);
  return bootstrap;
};
