import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// Mock all heavy dependencies before importing app
const mockReplace = vi.fn();
const mockHistory = {
  location: {
    pathname: '/team/my',
    search: '',
    hash: '',
  },
  replace: mockReplace,
};

const mockQueryCurrentUser = vi.fn();
const mockGetCurrentTenantId = vi.fn();
const mockQueryCurrentPermissions = vi.fn();

vi.mock('@umijs/max', () => ({
  history: mockHistory,
  Link: ({ children }: any) => children,
}));

vi.mock('@/services/account-team/auth', () => ({
  currentUser: mockQueryCurrentUser,
  getCurrentTenantId: mockGetCurrentTenantId,
}));

vi.mock('@/services/account-team/rbac', () => ({
  queryCurrentPermissions: mockQueryCurrentPermissions,
}));

vi.mock('@/components', () => ({
  AvatarDropdown: ({ children }: any) => <div>{children}</div>,
  DocLink: () => null,
  ErrorBoundary: ({ children }: any) => children,
  Footer: () => null,
  LangDropdown: () => null,
  OfflineBanner: () => null,
  TeamSwitcher: ({ currentTenantId, onChange }: any) => (
    <button type="button" onClick={() => onChange?.(22)}>
      当前团队 {currentTenantId}
    </button>
  ),
  VersionDropdown: () => null,
}));

vi.mock('@ant-design/pro-components', () => ({
  SettingDrawer: () => null,
}));

vi.mock('@ant-design/icons', () => ({
  LinkOutlined: () => null,
}));

vi.mock('./requestErrorConfig', () => ({
  errorConfig: {},
}));

vi.mock('../config/defaultSettings', () => ({
  default: { navTheme: 'light' },
}));

describe('app getInitialState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHistory.location = {
      pathname: '/team/my',
      search: '',
      hash: '',
    };
    mockGetCurrentTenantId.mockReturnValue(undefined);
    mockQueryCurrentPermissions.mockResolvedValue({
      data: { menus: [], permissions: [] },
    });
  });

  it('should fetch currentUser when not on login page', async () => {
    const { getInitialState } = await import('./app');
    mockQueryCurrentUser.mockResolvedValue({
      data: {
        id: 1,
        mobile: '13800000000',
        nickname: 'Test User',
        status: 'ACTIVE',
      },
    });

    const state = await getInitialState();

    expect(mockQueryCurrentUser).toHaveBeenCalled();
    expect(state.currentUser).toEqual({
      name: 'Test User',
      avatar: undefined,
      userid: '1',
      email: undefined,
      phone: '13800000000',
      title: '创作团队成员',
      group: 'Ant Short TV',
      access: 'user',
    });
    expect(state.settingDrawerOpen).toBe(false);
    expect(state.fetchUserInfo).toBeDefined();
  });

  it('should fetch current permissions when a current tenant is selected', async () => {
    const { getInitialState } = await import('./app');
    mockGetCurrentTenantId.mockReturnValue(10);
    mockQueryCurrentUser.mockResolvedValue({
      data: {
        id: 1,
        mobile: '13800000000',
        nickname: 'Test User',
        status: 'ACTIVE',
      },
    });
    mockQueryCurrentPermissions.mockResolvedValue({
      data: { menus: ['ROLE'], permissions: ['ROLE:VIEW'] },
    });

    const state = await getInitialState();

    expect(mockQueryCurrentPermissions).toHaveBeenCalledWith({
      skipErrorHandler: true,
    });
    expect(state.currentTenantId).toBe(10);
    expect(state.permissions).toEqual(['ROLE:VIEW']);
  });

  it('should redirect to login when currentUser fetch fails (401)', async () => {
    const { getInitialState } = await import('./app');
    mockQueryCurrentUser.mockRejectedValue(new Error('401 Unauthorized'));

    const state = await getInitialState();

    expect(mockReplace).toHaveBeenCalledWith(
      expect.stringContaining('/user/login?redirect='),
    );
    expect(state.currentUser).toBeUndefined();
  });

  it('should not fetch currentUser on login page', async () => {
    const { getInitialState } = await import('./app');
    mockHistory.location = {
      pathname: '/user/login',
      search: '',
      hash: '',
    };

    const state = await getInitialState();

    expect(mockQueryCurrentUser).not.toHaveBeenCalled();
    expect(state.currentUser).toBeUndefined();
    expect(state.fetchUserInfo).toBeDefined();
  });

  it('should encode redirect path correctly on 401', async () => {
    const { getInitialState } = await import('./app');
    mockHistory.location = {
      pathname: '/admin/users',
      search: '?page=2',
      hash: '#section',
    };
    mockQueryCurrentUser.mockRejectedValue(new Error('401'));

    await getInitialState();

    expect(mockReplace).toHaveBeenCalledWith(
      `/user/login?redirect=${encodeURIComponent('/admin/users?page=2#section')}`,
    );
  });

  it('should include default settings in initial state', async () => {
    const { getInitialState } = await import('./app');
    mockQueryCurrentUser.mockResolvedValue({
      data: {
        id: 1,
        mobile: '13800000000',
        nickname: 'User',
        status: 'ACTIVE',
      },
    });

    const state = await getInitialState();

    expect(state.settings).toEqual({ navTheme: 'light' });
  });

  it('fetchUserInfo should return user data on success', async () => {
    const { getInitialState } = await import('./app');
    mockQueryCurrentUser.mockResolvedValue({
      data: {
        id: 2,
        mobile: '13800000001',
        nickname: 'Fetched User',
        status: 'DISABLED',
      },
    });

    const state = await getInitialState();

    const user = await state.fetchUserInfo?.();
    expect(user).toEqual({
      name: 'Fetched User',
      avatar: undefined,
      userid: '2',
      email: undefined,
      phone: '13800000001',
      title: '账号已停用',
      group: 'Ant Short TV',
      access: 'user',
    });
  });

  it('does not send production account APIs to the Ant Design demo worker by default', async () => {
    const { request } = await import('./app');

    expect(request.baseURL).toBe('');
  });

  it('renders the team switcher in the sidebar and refreshes permissions after switching', async () => {
    const { layout } = await import('./app');
    const setInitialState = vi.fn();
    mockQueryCurrentPermissions.mockResolvedValue({
      data: { menus: ['PROJECT'], permissions: ['PROJECT:VIEW'] },
    });

    const layoutConfig = layout({
      initialState: {
        currentUser: { name: 'Test User' },
        currentTenantId: 10,
      },
      setInitialState,
    } as any);

    const menuExtraRender = layoutConfig.menuExtraRender;
    if (typeof menuExtraRender !== 'function') {
      throw new Error('menuExtraRender should be available');
    }
    render(menuExtraRender({ collapsed: false } as any) as React.ReactElement);
    fireEvent.click(screen.getByRole('button', { name: '当前团队 10' }));

    await waitFor(() => {
      expect(mockQueryCurrentPermissions).toHaveBeenCalledWith({
        skipErrorHandler: true,
      });
      expect(setInitialState).toHaveBeenCalled();
    });

    const updater = setInitialState.mock.calls[0][0];
    expect(updater({ currentTenantId: 10, permissions: [] })).toMatchObject({
      currentTenantId: 22,
      permissions: ['PROJECT:VIEW'],
    });
  });
});
