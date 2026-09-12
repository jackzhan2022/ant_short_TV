import { render, screen, waitFor, within } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InvitationDetail from './detail';
import TeamInvitations from './index';

const mocks = vi.hoisted(() => ({
  acceptInvitation: vi.fn(),
  cancelInvitation: vi.fn(),
  getCurrentTenantId: vi.fn(),
  memberType: 'OWNER',
  queryMyInvitations: vi.fn(),
  queryInvitation: vi.fn(),
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
  PageContainer: ({ children, extra }: any) => (
    <main>
      {extra}
      {children}
    </main>
  ),
  ProDescriptions: ({ dataSource, columns }: any) => (
    <div>
      {columns.map((column: any) => (
        <div key={column.dataIndex}>
          {column.render?.(dataSource[column.dataIndex], dataSource) ??
            dataSource[column.dataIndex]}
        </div>
      ))}
    </div>
  ),
  ProTable: ({ headerTitle, request, columns }: any) => {
    const [rows, setRows] = useState<any[]>([]);
    useEffect(() => {
      let active = true;
      request({}, {}, {}).then((response: any) => {
        if (active) setRows(response.data);
      });
      return () => {
        active = false;
      };
    }, [request]);
    return (
      <section aria-label={headerTitle}>
        <h1>{headerTitle}</h1>
        {rows.map((record) => (
          <div key={record.id}>
            {columns.map((column: any) => (
              <div key={column.dataIndex || column.title}>
                {column.render
                  ? column.render(record[column.dataIndex], record)
                  : record[column.dataIndex]}
              </div>
            ))}
          </div>
        ))}
      </section>
    );
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ token: 'test-invitation' }),
  useModel: () => ({
    initialState: {
      selectedTenant: { membership: { memberType: mocks.memberType } },
    },
  }),
}));

vi.mock('@/services/account-team/invitation', () => ({
  queryInvitation: mocks.queryInvitation,
  acceptInvitation: mocks.acceptInvitation,
  rejectInvitation: mocks.rejectInvitation,
}));

vi.mock('./service', () => ({
  acceptInvitation: mocks.acceptInvitation,
  cancelInvitation: mocks.cancelInvitation,
  queryMyInvitations: mocks.queryMyInvitations,
  queryTenantInvitations: mocks.queryTenantInvitations,
  rejectInvitation: mocks.rejectInvitation,
}));

describe('TeamInvitations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.memberType = 'OWNER';
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

  it('shows expired invitations without actions in both received and sent lists', async () => {
    const record = {
      id: 1,
      status: 'EXPIRED',
      expiredAt: '2026-08-26T15:22:23',
    };
    mocks.queryMyInvitations.mockResolvedValue({
      success: true,
      data: [record],
    });
    mocks.queryTenantInvitations.mockResolvedValue({
      success: true,
      data: [record],
    });
    render(<TeamInvitations />);

    for (const name of ['收到的团队邀请', '团队已发邀请']) {
      const region = within(screen.getByRole('region', { name }));
      expect(await region.findByText('已过期')).toBeInTheDocument();
      expect(region.queryByText('待处理')).not.toBeInTheDocument();
      expect(region.queryByRole('button')).not.toBeInTheDocument();
    }
  });

  it('preserves actions for pending invitations', async () => {
    const record = { id: 1, status: 'PENDING', token: 'test-invitation' };
    mocks.queryMyInvitations.mockResolvedValue({
      success: true,
      data: [record],
    });
    mocks.queryTenantInvitations.mockResolvedValue({
      success: true,
      data: [record],
    });
    render(<TeamInvitations />);

    expect(await screen.findByRole('button', { name: '接受' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '拒绝' })).toBeEnabled();
    expect(await screen.findByRole('button', { name: '取消' })).toBeEnabled();
  });

  it('hides accept and reject actions in expired invitation details', async () => {
    mocks.queryInvitation.mockResolvedValue({
      success: true,
      data: { id: 1, status: 'EXPIRED', expiredAt: '2026-08-26T15:22:23' },
    });
    render(<InvitationDetail />);

    expect(await screen.findByText('已过期')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '接受邀请' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '拒绝邀请' }),
    ).not.toBeInTheDocument();
  });
});
