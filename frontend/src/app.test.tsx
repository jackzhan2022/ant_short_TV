import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  replace: vi.fn(), queryAuthBootstrap: vi.fn(), getCurrentTenantId: vi.fn(), setCurrentTenantId: vi.fn(),
}));
const mockHistory = { location: { pathname: '/team/my', search: '', hash: '' }, replace: mocks.replace };

vi.mock('@umijs/max', () => ({ history: mockHistory, Link: ({ children }: any) => children }));
vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
  queryAuthBootstrap: mocks.queryAuthBootstrap,
  setCurrentTenantId: mocks.setCurrentTenantId,
}));
vi.mock('@/components', () => ({
  AvatarDropdown: ({ children }: any) => <div>{children}</div>,
  ErrorBoundary: ({ children }: any) => children,
  Footer: () => null,
  OfflineBanner: () => null,
  SidebarAccount: () => <div data-testid="sidebar-account" />,
  TeamSwitcher: ({ currentTenantId, onChange }: any) => (
    <button type="button" onClick={() => { void onChange?.(22).catch(() => undefined); }}>
      当前团队 {currentTenantId}
    </button>
  ),
}));
vi.mock('@ant-design/pro-components', () => ({ SettingDrawer: () => <div data-testid="setting-drawer" /> }));
vi.mock('antd', () => ({ App: Object.assign(
  ({ children }: any) => <div data-testid="antd-app-provider">{children}</div>,
  { useApp: () => ({ modal: { confirm: vi.fn() } }) },
) }));
vi.mock('./requestErrorConfig', () => ({ errorConfig: {} }));
vi.mock('../config/defaultSettings', () => ({ default: { navTheme: 'light' } }));

const tenant = {
  id: 10, code: 'T0000010', name: '测试团队', type: 'STUDIO', status: 'ACTIVE', memberType: 'MEMBER', memberId: 100,
};
const bootstrap = (selected = true): any => ({
  user: { id: 1, mobile: '13800000000', nickname: 'Test User', status: 'ACTIVE' },
  session: { sessionId: 'session-1', expiresAt: '2026-09-01T00:00:00' },
  platform: { roles: ['PLATFORM_OPERATOR'], permissions: ['PLATFORM_AI_PROVIDER_VIEW'] },
  tenants: [tenant],
  selectedTenant: selected ? {
    tenant,
    membership: { id: 100, memberType: 'MEMBER', status: 'ACTIVE' },
    roles: ['MEMBER'], permissions: ['PROJECT:CREATE'],
  } : null,
  unavailableSelectionReason: null,
  nextAction: selected ? 'ENTER_WORKSPACE' : 'SELECT_TENANT',
});

describe('app bootstrap state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHistory.location = { pathname: '/team/my', search: '', hash: '' };
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryAuthBootstrap.mockResolvedValue({ data: bootstrap() });
  });

  it('loads user, tenant and separated permissions with one bootstrap request', async () => {
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(mocks.queryAuthBootstrap).toHaveBeenCalledTimes(1);
    expect(mocks.queryAuthBootstrap).toHaveBeenCalledWith(10, { skipErrorHandler: true });
    expect(state).toMatchObject({
      currentUser: { name: 'Test User', userid: '1' }, currentTenantId: 10,
      tenants: [tenant], tenantPermissions: ['PROJECT:CREATE'],
      platformPermissions: ['PLATFORM_AI_PROVIDER_VIEW'], settingDrawerOpen: false,
    });
  });

  it('keeps multiple-tenant selection empty without leaking old permissions', async () => {
    const response = bootstrap(false);
    response.tenants = [tenant, { ...tenant, id: 11, code: 'T0000011' }];
    mocks.queryAuthBootstrap.mockResolvedValue({ data: response });
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(state.currentTenantId).toBeUndefined();
    expect(state.tenantPermissions).toEqual([]);
    expect(state.platformPermissions).toEqual(['PLATFORM_AI_PROVIDER_VIEW']);
  });

  it('supports a platform-only user with no tenant', async () => {
    const response = bootstrap(false);
    response.tenants = [];
    response.nextAction = 'CREATE_OR_JOIN_TEAM';
    mocks.queryAuthBootstrap.mockResolvedValue({ data: response });
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(state.currentUser?.name).toBe('Test User');
    expect(state.currentTenantId).toBeUndefined();
    expect(state.platformPermissions).toEqual(['PLATFORM_AI_PROVIDER_VIEW']);
  });

  it('redirects to login with the complete return URL when bootstrap fails', async () => {
    mockHistory.location = { pathname: '/projects/1', search: '?tab=script', hash: '#scene' };
    mocks.queryAuthBootstrap.mockRejectedValue(new Error('401'));
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(mocks.replace).toHaveBeenCalledWith(
      `/user/login?redirect=${encodeURIComponent('/projects/1?tab=script#scene')}`,
    );
    expect(state.currentUser).toBeUndefined();
  });

  it('does not bootstrap on authentication pages', async () => {
    mockHistory.location = { pathname: '/user/login', search: '', hash: '' };
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(mocks.queryAuthBootstrap).not.toHaveBeenCalled();
    expect(state.fetchUserInfo).toBeDefined();
  });

  it('does not bootstrap on an authentication page with a trailing slash', async () => {
    mockHistory.location = { pathname: '/user/login/', search: '', hash: '' };
    const { getInitialState } = await import('./app');
    const state = await getInitialState();
    expect(mocks.queryAuthBootstrap).not.toHaveBeenCalled();
    expect(state.fetchUserInfo).toBeDefined();
  });

  it('applies a validated tenant bootstrap before storing the tenant id', async () => {
    const target = bootstrap();
    target.tenants = [tenant, { ...tenant, id: 22, code: 'T0000022', name: '目标团队' }];
    target.selectedTenant = { ...target.selectedTenant, tenant: target.tenants[1], permissions: ['PROJECT:VIEW_ALL'] };
    mocks.queryAuthBootstrap.mockResolvedValue({ data: target });
    const setInitialState = vi.fn().mockResolvedValue(undefined);
    const { layout } = await import('./app');
    const config = layout({ initialState: { currentUser: { name: 'Test User' }, currentTenantId: 10 }, setInitialState } as any);
    if (typeof config.menuExtraRender !== 'function') throw new Error('menuExtraRender missing');
    render(config.menuExtraRender({ collapsed: false } as any) as React.ReactElement);
    fireEvent.click(screen.getByRole('button', { name: '当前团队 10' }));
    await waitFor(() => expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(22));
    expect(mocks.queryAuthBootstrap).toHaveBeenCalledWith(22, { skipErrorHandler: true });
    const updater = setInitialState.mock.calls[0][0];
    expect(updater({ currentTenantId: 10, tenantPermissions: ['OLD'] })).toMatchObject({
      currentTenantId: 22, tenantPermissions: ['PROJECT:VIEW_ALL'],
    });
    expect(setInitialState.mock.invocationCallOrder[0]).toBeLessThan(mocks.setCurrentTenantId.mock.invocationCallOrder[0]);
  });

  it('does not apply or store an unavailable tenant selection', async () => {
    const unavailable = bootstrap(false);
    unavailable.unavailableSelectionReason = 'TENANT_MEMBERSHIP_INACTIVE';
    mocks.queryAuthBootstrap.mockResolvedValue({ data: unavailable });
    const setInitialState = vi.fn();
    const { layout } = await import('./app');
    const config = layout({ initialState: { currentUser: { name: 'Test User' }, currentTenantId: 10 }, setInitialState } as any);
    if (typeof config.menuExtraRender !== 'function') throw new Error('menuExtraRender missing');
    render(config.menuExtraRender({ collapsed: false } as any) as React.ReactElement);
    fireEvent.click(screen.getByRole('button', { name: '当前团队 10' }));
    await waitFor(() => expect(mocks.queryAuthBootstrap).toHaveBeenCalled());
    expect(setInitialState).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).not.toHaveBeenCalled();
  });

  it('仅显示有访问权限的剧库管理入口', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);
    if (typeof config.menuDataRender !== 'function') throw new Error('menuDataRender missing');
    const entry = { path: '/drama-library', name: '剧集管理' };
    expect(config.menuDataRender([entry]).flatMap((group) => group.children || []))
      .toContainEqual(expect.objectContaining(entry));
    for (const flags of [{ unaccessible: true }, { hideInMenu: true }]) {
      expect(config.menuDataRender([{ ...entry, ...flags }]).flatMap((group) => group.children || []))
        .not.toContainEqual(expect.objectContaining(entry));
    }
  });

  it('keeps the route menu data for native sidebar groups', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);

    expect(config.menuDataRender).toEqual(expect.any(Function));
    expect(config.menu).toBeUndefined();
  });

  it('hides the standalone style library from the sidebar and keeps project and task entries', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);
    if (typeof config.menuDataRender !== 'function') throw new Error('menuDataRender missing');
    const menu = config.menuDataRender([
      { path: '/short-drama-creation', name: '短剧创作' },
      { path: '/projects/list', name: '项目列表' },
      { path: '/tasks', name: '任务中心' },
      { path: '/style-library', name: '风格库' },
    ] as any);

    expect(menu.find((group) => group.key === 'mine')?.children).toEqual([
      expect.objectContaining({ path: '/projects/list' }),
      expect.objectContaining({ path: '/tasks' }),
    ]);
    expect(menu.flatMap((group) => group.children || []))
      .not.toContainEqual(expect.objectContaining({ path: '/style-library' }));
  });

  it('adds the script review library entry to the creation menu group', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);
    if (typeof config.menuDataRender !== 'function') throw new Error('menuDataRender missing');

    const menu = config.menuDataRender([
      { path: '/short-drama-creation', name: '短剧创作' },
      { path: '/video-script-decomposition', name: '视频拆剧' },
      { path: '/script-review-library', name: '剧本审核' },
      { path: '/projects/list', name: '项目' },
      { path: '/style-library', name: '风格库' },
    ] as any);

    expect(menu[0]).toMatchObject({
      key: 'creation',
      children: expect.arrayContaining([
        expect.objectContaining({ path: '/script-review-library', name: '剧本审核' }),
      ]),
    });
  });

  it('redirects page navigation when no authenticated bootstrap state exists', async () => {
    mockHistory.location = { pathname: '/projects/1/production-workbench', search: '', hash: '' };
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);
    config.onPageChange?.({} as any);
    expect(mocks.replace).toHaveBeenCalledWith('/user/login?redirect=%2Fprojects%2F1%2Fproduction-workbench');
  });

  it('shows start using for a teamless user while retaining accessible platform management', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: { currentUser: { name: 'New User' }, tenants: [] }, setInitialState: vi.fn() } as any);
    if (typeof config.menuDataRender !== 'function') throw new Error('menuDataRender missing');
    const menu = config.menuDataRender([
      { path: '/team/my', name: '团队管理' },
      { path: '/projects/list', name: '项目', unaccessible: true },
      { path: '/tasks', name: '任务中心', unaccessible: true },
      { path: '/short-drama-creation', name: '短剧创作', unaccessible: true },
      { path: '/commercial-management/orders', name: '订单管理', unaccessible: false },
    ] as any);
    expect(menu.map((item) => item.key)).toEqual(['management', 'commercial']);
    expect(menu[0].children).toEqual([expect.objectContaining({ path: '/team/my', name: '开始使用', locale: false })]);
    expect(menu[1].children).toEqual([expect.objectContaining({ path: '/commercial-management/orders' })]);
  });

  it('drops inaccessible and hidden menu entries before making sidebar groups', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: {}, setInitialState: vi.fn() } as any);
    if (typeof config.menuDataRender !== 'function') throw new Error('menuDataRender missing');
    expect(config.menuDataRender([
      { path: '/projects/list', name: '项目', unaccessible: true },
      { path: '/tasks', name: '任务中心', hideInMenu: true },
      { path: '/team/my', name: '团队管理' },
    ] as any).map((item) => item.key)).toEqual(['management']);
  });

  it('redirects teamless workspace visits to onboarding without blocking invitation links', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: { currentUser: { name: 'New User' }, tenants: [] }, setInitialState: vi.fn() } as any);
    mockHistory.location = { pathname: '/projects/list', search: '', hash: '' };
    config.onPageChange?.({} as any);
    expect(mocks.replace).toHaveBeenCalledWith('/team/my');
    mocks.replace.mockClear();
    for (const pathname of ['/team/my', '/team/invitations/invite-token', '/commercial-management/orders']) {
      mockHistory.location = { pathname, search: '', hash: '' };
      config.onPageChange?.({} as any);
    }
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it('does not expose the floating style settings to production users', async () => {
    const { layout } = await import('./app');
    const config = layout({ initialState: { currentUser: { name: 'New User' }, tenants: [] }, setInitialState: vi.fn() } as any);
    if (typeof config.childrenRender !== 'function') throw new Error('childrenRender missing');
    render(config.childrenRender(<div>onboarding</div>, {} as any) as React.ReactElement);
    expect(screen.queryByTestId('setting-drawer')).not.toBeInTheDocument();
  });

  it('keeps the production API base URL same-origin by default', async () => {
    const { request } = await import('./app');
    expect(request.baseURL).toBe('');
  });

  it('wraps routed pages with the antd App context', async () => {
    const { rootContainer } = await import('./app');
    render(rootContainer(<div>page</div>) as React.ReactElement);
    expect(screen.getByTestId('antd-app-provider')).toBeInTheDocument();
    expect(screen.getByText('page')).toBeInTheDocument();
  });
});
