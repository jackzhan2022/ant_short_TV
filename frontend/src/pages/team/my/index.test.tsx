import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MyTeams from './index';

const mocks = vi.hoisted(() => ({
  queryMyTenants: vi.fn(),
  createTenant: vi.fn(),
  enterTeam: vi.fn(),
  applyBootstrapSelection: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  push: vi.fn(),
  setInitialState: vi.fn(),
}));
const tenant = {
  id: 1,
  code: 'T0000001',
  name: '测试团队',
  type: 'PERSONAL',
  status: 'ACTIVE',
  memberType: 'OWNER',
  memberId: 100,
};

vi.mock('@ant-design/icons', () => ({
  PlusOutlined: () => <span />,
  TeamOutlined: () => <span />,
  CheckOutlined: () => <span />,
}));
vi.mock('@umijs/max', () => ({
  history: { push: mocks.push },
  useAccess: () => ({ canManageRoles: true }),
  useModel: () => ({
    initialState: { currentUser: { name: '测试用户' } },
    setInitialState: mocks.setInitialState,
  }),
}));
vi.mock('../useTeamEntry', () => ({ useTeamEntry: () => mocks.enterTeam }));

vi.mock('@/services/account-team/bootstrap', () => ({
  applyBootstrapSelection: mocks.applyBootstrapSelection,
}));
vi.mock('@/components/TeamIconPicker', () => ({ default: () => <span>团队图标选择器</span> }));
vi.mock('@/components/TeamIconPicker/teamIcons', () => ({ TeamIcon: () => <span /> }));

vi.mock('antd', () => ({
  Form: { Item: ({ label, children }: any) => <div>{label}{children}</div> },
  App: {
    useApp: () => ({
      message: { success: mocks.success, warning: mocks.warning },
    }),
  },
  Button: ({ children, onClick, htmlType }: any) => (
    <button
      type={htmlType === 'submit' ? 'submit' : 'button'}
      onClick={onClick}
    >
      {children}
    </button>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Spin: () => <span />,
  Alert: ({ title, action }: any) => (
    <div role="alert">
      {title}
      {action}
    </div>
  ),
  Tabs: ({ items }: any) => (
    <div>
      {items?.map((item: any) => (
        <button type="button" key={item.key} role="tab">
          {item.label}
        </button>
      ))}
      {items?.[0]?.children}
    </div>
  ),
}));
vi.mock('@ant-design/pro-components', async () => {
  const React = await import('react');
  return {
    ModalForm: ({ children, trigger, onFinish, initialValues }: any) => {
      const [open, setOpen] = React.useState(false);
      return (
        <div>
          {React.cloneElement(trigger, { onClick: () => setOpen(true) })}
          {open && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void onFinish({ name: '我的创作团队', ...initialValues });
              }}
            >
              {children}
              <button type="submit">提交创建</button>
            </form>
          )}
        </div>
      );
    },
    PageContainer: ({ children }: any) => <main>{children}</main>,
    ProFormSelect: ({ label }: any) => <span>{label}</span>,
    ProFormText: ({ label }: any) => <span>{label}</span>,
    ProFormTextArea: ({ label }: any) => <span>{label}</span>,
    ProTable: ({
      headerTitle,
      dataSource = [],
      request,
      toolBarRender,
      columns,
    }: any) => {
      request?.({}, {}, {});
      return (
        <section data-testid="team-table">
          <h1>{headerTitle}</h1>
          {toolBarRender?.()}
          {dataSource.map((record: any) => (
            <div key={record.id}>
              {columns?.map((column: any) => (
                <div key={column.title}>
                  {column.render
                    ? column.render(undefined, record)
                    : record[column.dataIndex]}
                </div>
              ))}
            </div>
          ))}
        </section>
      );
    },
  };
});
vi.mock('./service', () => ({
  createTenant: mocks.createTenant,
  queryMyTenants: mocks.queryMyTenants,
}));

const submitCreation = async () => {
  fireEvent.click(await screen.findByRole('button', { name: '创建创作团队' }));
  fireEvent.click(screen.getByRole('button', { name: '提交创建' }));
};

describe('MyTeams', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mocks.queryMyTenants.mockResolvedValue({ success: true, data: [] });
    mocks.createTenant.mockResolvedValue({ success: true, data: tenant });
    mocks.applyBootstrapSelection.mockResolvedValue({});
    mocks.enterTeam.mockResolvedValue(undefined);
  });

  it('shows actionable first-use guidance instead of an empty management table', async () => {
    render(<MyTeams />);
    expect(
      await screen.findByRole('heading', { name: '欢迎使用剧智创' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/一个人也可以创建个人团队/)).toBeInTheDocument();
    expect(screen.getByText(/联系团队管理员获取邀请链接/)).toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByTestId('team-table')).not.toBeInTheDocument();
  });

  it('clears an unavailable team context and cached selection while preserving platform access', async () => {
    localStorage.setItem('currentTenantId', '42');
    localStorage.setItem('accessToken', 'do-not-touch');
    render(<MyTeams />);
    await screen.findByRole('heading', { name: '欢迎使用剧智创' });
    const update = mocks.setInitialState.mock.calls[0][0];
    expect(
      update({
        currentTenantId: 42,
        selectedTenant: { tenant: { id: 42 } },
        tenantPermissions: ['OLD'],
        platformPermissions: ['PLATFORM_TENANT_VIEW'],
      }),
    ).toMatchObject({
      tenants: [],
      currentTenantId: undefined,
      selectedTenant: null,
      tenantPermissions: [],
      platformPermissions: ['PLATFORM_TENANT_VIEW'],
    });
    expect(localStorage.getItem('currentTenantId')).toBeNull();
    expect(localStorage.getItem('accessToken')).toBe('do-not-touch');
  });

  it('does not flash onboarding before the team request completes', () => {
    mocks.queryMyTenants.mockReturnValue(new Promise(() => {}));
    render(<MyTeams />);
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '创建创作团队' }),
    ).not.toBeInTheDocument();
  });

  it('shows a retryable load error instead of treating failure as no teams', async () => {
    mocks.queryMyTenants.mockRejectedValueOnce(new Error('offline'));
    render(<MyTeams />);
    expect(await screen.findByRole('alert')).toHaveTextContent('团队加载失败');
    expect(screen.queryByText('欢迎使用剧智创')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(await screen.findByText('欢迎使用剧智创')).toBeInTheDocument();
  });

  it('preserves management tabs and actions for existing teams', async () => {
    mocks.queryMyTenants.mockResolvedValue({ success: true, data: [tenant] });
    render(<MyTeams />);
    expect(
      await screen.findByRole('tab', { name: '团队列表' }),
    ).toBeInTheDocument();
    for (const name of ['成员管理', '角色管理', '权限树', '邀请管理'])
      expect(screen.getByRole('tab', { name })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '团队设置' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('欢迎使用剧智创')).not.toBeInTheDocument();
  });

  it('offers an icon picker instead of a logo address when creating a team', async () => {
    render(<MyTeams />);
    fireEvent.click(await screen.findByRole('button', { name: '创建创作团队' }));
    expect(screen.getByText('团队图标（选填）')).toBeInTheDocument();
    expect(screen.getByText('团队图标选择器')).toBeInTheDocument();
    expect(screen.queryByText(/Logo 地址/)).not.toBeInTheDocument();
  });

  it('defaults to a personal team and delegates entry to the committed-context flow', async () => {
    render(<MyTeams />);
    await submitCreation();
    await waitFor(() => expect(mocks.enterTeam).toHaveBeenCalledWith(1));
    expect(mocks.createTenant).toHaveBeenCalledWith({
      name: '我的创作团队',
      type: 'PERSONAL',
    });
    expect(mocks.createTenant.mock.invocationCallOrder[0]).toBeLessThan(
      mocks.enterTeam.mock.invocationCallOrder[0],
    );
  });

  it('keeps the created team available when context refresh fails without creating it again', async () => {
    mocks.enterTeam.mockRejectedValue(new Error('offline'));
    mocks.queryMyTenants
      .mockResolvedValueOnce({ success: true, data: [] })
      .mockResolvedValue({ success: true, data: [tenant] });
    render(<MyTeams />);
    await submitCreation();
    await waitFor(() => expect(mocks.warning).toHaveBeenCalled());
    expect(await screen.findByTestId('team-table')).toBeInTheDocument();
    expect(mocks.createTenant).toHaveBeenCalledTimes(1);
    expect(mocks.push).not.toHaveBeenCalled();
  });
});
