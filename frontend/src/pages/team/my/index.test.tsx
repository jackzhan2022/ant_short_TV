import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MyTeams from './index';

const mocks = vi.hoisted(() => ({
  queryMyTenants: vi.fn(),
  createTenant: vi.fn(),
  switchTenant: vi.fn(),
  success: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  PlusOutlined: () => <span data-testid="plus-icon" />,
}));

vi.mock('@umijs/max', () => ({
  history: { push: vi.fn() },
  useAccess: () => ({ canManageRoles: true }),
  useModel: () => ({ setInitialState: vi.fn() }),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.success } }),
  },
  Button: ({ children, icon, ...props }: any) => (
    <button type="button" {...props}>
      {icon}
      {children}
    </button>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Tabs: ({ items }: any) => (
    <div>
      {items?.map((item: any) => <button type="button" key={item.key} role="tab">{item.label}</button>)}
      {items?.[0]?.children}
    </div>
  ),
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, trigger }: any) => (
    <div>
      {trigger}
      {children}
    </div>
  ),
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ headerTitle, request, toolBarRender, columns }: any) => {
    request?.({}, {}, {});
    const sampleTenant = {
      id: 1,
      code: 'T0000001',
      name: '测试团队',
      type: 'STUDIO',
      status: 'ACTIVE',
      memberType: 'OWNER',
      memberId: 100,
    };
    return (
      <section>
        <h1>{headerTitle}</h1>
        <div>{toolBarRender?.()}</div>
        <div>
          {columns?.map((column: any) => (
            <div key={column.title}>
              {column.render ? column.render(undefined, sampleTenant) : column.title}
            </div>
          ))}
        </div>
      </section>
    );
  },
}));

vi.mock('./service', () => ({
  createTenant: mocks.createTenant,
  queryMyTenants: mocks.queryMyTenants,
  switchTenant: mocks.switchTenant,
}));

describe('MyTeams', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryMyTenants.mockResolvedValue({ success: true, data: [] });
  });

  it('renders create action and requests my team list', async () => {
    render(<MyTeams />);

    expect(screen.getByText('团队管理')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '团队列表' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '成员管理' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '角色管理' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '权限树' })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '创建创作团队' }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryMyTenants).toHaveBeenCalled();
    });
  });

  it('shows member management and settings actions in the team list', () => {
    render(<MyTeams />);

    expect(
      screen.getByRole('button', { name: '成员管理' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '团队设置' }),
    ).toBeInTheDocument();
  });
});
