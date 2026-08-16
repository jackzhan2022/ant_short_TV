import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamMembers from './index';

const mocks = vi.hoisted(() => ({
  createInvitation: vi.fn(),
  getCurrentTenantId: vi.fn(),
  queryCurrentTenant: vi.fn(),
  queryTenantMembers: vi.fn(),
  removeTenantMember: vi.fn(),
  success: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  PlusOutlined: () => <span data-testid="plus-icon" />,
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
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children }: any) => <div>{children}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, trigger }: any) => (
    <div>
      {trigger}
      {children}
    </div>
  ),
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ headerTitle, request, toolBarRender }: any) => {
    request?.({}, {}, {});
    return (
      <section>
        <h1>{headerTitle}</h1>
        <div>{toolBarRender?.()}</div>
      </section>
    );
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('./service', () => ({
  createInvitation: mocks.createInvitation,
  queryCurrentTenant: mocks.queryCurrentTenant,
  queryTenantMembers: mocks.queryTenantMembers,
  removeTenantMember: mocks.removeTenantMember,
}));

describe('TeamMembers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryCurrentTenant.mockResolvedValue({
      success: true,
      data: {
        userId: 1,
        tenantId: 10,
        memberId: 100,
        memberType: 'OWNER',
      },
    });
    mocks.queryTenantMembers.mockResolvedValue({ success: true, data: [] });
  });

  it('renders invite action for the current team', async () => {
    render(<TeamMembers />);

    expect(screen.getByText('团队成员')).toBeInTheDocument();
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: '邀请成员' }),
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(mocks.queryTenantMembers).toHaveBeenCalledWith(10);
    });
  });

  it('prompts users to select a team when there is no current tenant', () => {
    mocks.getCurrentTenantId.mockReturnValue(undefined);

    render(<TeamMembers />);

    expect(screen.getByText('请先在我的团队中选择当前创作团队')).toBeInTheDocument();
  });

  it('hides owner-only invite action for normal members', async () => {
    mocks.queryCurrentTenant.mockResolvedValue({
      success: true,
      data: {
        userId: 1,
        tenantId: 10,
        memberId: 100,
        memberType: 'MEMBER',
      },
    });

    render(<TeamMembers />);

    await waitFor(() => {
      expect(mocks.queryCurrentTenant).toHaveBeenCalled();
    });
    expect(
      screen.queryByRole('button', { name: '邀请成员' }),
    ).not.toBeInTheDocument();
  });
});
