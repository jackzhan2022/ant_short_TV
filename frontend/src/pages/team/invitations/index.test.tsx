import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamInvitations from './index';

const mocks = vi.hoisted(() => ({
  acceptInvitation: vi.fn(),
  cancelInvitation: vi.fn(),
  getCurrentTenantId: vi.fn(),
  queryCurrentTenant: vi.fn(),
  queryMyInvitations: vi.fn(),
  queryTenantInvitations: vi.fn(),
  rejectInvitation: vi.fn(),
  success: vi.fn(),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.success } }),
  },
  Button: ({ children, ...props }: any) => (
    <button type="button" {...props}>
      {children}
    </button>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ headerTitle, request }: any) => {
    request?.({}, {}, {});
    return (
      <section>
        <h1>{headerTitle}</h1>
      </section>
    );
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('./service', () => ({
  acceptInvitation: mocks.acceptInvitation,
  cancelInvitation: mocks.cancelInvitation,
  queryCurrentTenant: mocks.queryCurrentTenant,
  queryMyInvitations: mocks.queryMyInvitations,
  queryTenantInvitations: mocks.queryTenantInvitations,
  rejectInvitation: mocks.rejectInvitation,
}));

describe('TeamInvitations', () => {
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
    mocks.queryMyInvitations.mockResolvedValue({ success: true, data: [] });
    mocks.queryTenantInvitations.mockResolvedValue({ success: true, data: [] });
  });

  it('loads sent invitations for the current owner team', async () => {
    render(<TeamInvitations />);

    expect(screen.getByText('收到的团队邀请')).toBeInTheDocument();
    await waitFor(() => {
      expect(mocks.queryTenantInvitations).toHaveBeenCalledWith(10);
    });
    expect(screen.getByText('团队已发邀请')).toBeInTheDocument();
  });
});
