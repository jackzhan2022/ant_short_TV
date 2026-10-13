import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InvitationDetail from './index';

const mocks = vi.hoisted(() => ({
  queryInvitation: vi.fn(),
  acceptInvitation: vi.fn(),
  rejectInvitation: vi.fn(),
  enterTeam: vi.fn(),
  setInitialState: vi.fn(),
  push: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useParams: () => ({ token: 'invite-token' }),
  history: { push: mocks.push },
  useModel: () => ({ setInitialState: mocks.setInitialState }),
}));
vi.mock('@/services/account-team/invitation', () => ({
  queryInvitation: mocks.queryInvitation,
  acceptInvitation: mocks.acceptInvitation,
  rejectInvitation: mocks.rejectInvitation,
}));
vi.mock('../../useTeamEntry', () => ({ useTeamEntry: () => mocks.enterTeam }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra }: any) => (
    <main>
      {extra}
      {children}
    </main>
  ),
  ProDescriptions: () => <div>邀请详情</div>,
}));
vi.mock('antd', () => ({
  App: {
    useApp: () => ({
      message: { success: mocks.success, warning: mocks.warning },
    }),
  },
  Button: ({ children, onClick }: any) => (
    <button type="button" onClick={onClick}>
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

describe('InvitationDetail onboarding', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryInvitation.mockResolvedValue({
      data: { tenantId: 7, tenantName: '测试团队', status: 'PENDING' },
    });
    mocks.acceptInvitation.mockResolvedValue({
      data: { tenantId: 7, status: 'ACCEPTED' },
    });
    mocks.enterTeam.mockResolvedValue({});
  });

  it('selects the joined team and refreshes permissions before entering projects', async () => {
    render(<InvitationDetail />);
    fireEvent.click(await screen.findByRole('button', { name: '接受邀请' }));
    await waitFor(() => expect(mocks.enterTeam).toHaveBeenCalledWith(7));
    expect(mocks.acceptInvitation).toHaveBeenCalledWith('invite-token');
    expect(mocks.acceptInvitation.mock.invocationCallOrder[0]).toBeLessThan(
      mocks.enterTeam.mock.invocationCallOrder[0],
    );
  });

  it('does not navigate with stale permissions if refreshing a joined team fails', async () => {
    mocks.enterTeam.mockRejectedValue(new Error('offline'));
    mocks.queryInvitation
      .mockResolvedValueOnce({ data: { tenantId: 7, status: 'PENDING' } })
      .mockResolvedValue({ data: { tenantId: 7, status: 'ACCEPTED' } });
    render(<InvitationDetail />);
    fireEvent.click(await screen.findByRole('button', { name: '接受邀请' }));
    await waitFor(() => expect(mocks.warning).toHaveBeenCalled());
    expect(mocks.push).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: '接受邀请' }),
      ).not.toBeInTheDocument(),
    );
  });
  it('retries entry after acceptance without accepting again or reloading the invitation', async () => {
    mocks.enterTeam
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({});
    mocks.queryInvitation
      .mockResolvedValueOnce({ data: { tenantId: 7, status: 'PENDING' } })
      .mockRejectedValue(new Error('still offline'));
    render(<InvitationDetail />);
    fireEvent.click(await screen.findByRole('button', { name: '接受邀请' }));
    const retry = await screen.findByRole('button', { name: '进入团队' });
    expect(
      screen.queryByRole('button', { name: '接受邀请' }),
    ).not.toBeInTheDocument();
    fireEvent.click(retry);
    await waitFor(() => expect(mocks.enterTeam).toHaveBeenCalledTimes(2));
    expect(mocks.acceptInvitation).toHaveBeenCalledTimes(1);
    expect(mocks.queryInvitation).toHaveBeenCalledTimes(1);
  });
});
