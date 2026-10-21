import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SidebarAccount from './index';

const mocks = vi.hoisted(() => ({
  getCurrentTenantId: vi.fn(),
  push: vi.fn(),
  queryTeamPointAccount: vi.fn(),
  messageSummary: vi.fn(async () => ({ unread: 0, categories: {}, canSend: false })),
}));

vi.mock('@umijs/max', () => ({
  history: { push: mocks.push },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('@/services/account-team/points', () => ({
  queryTeamPointAccount: mocks.queryTeamPointAccount,
}));

vi.mock('@/pages/messages/service', () => ({ messageSummary: mocks.messageSummary }));

vi.mock('../RightContent/AvatarDropdown', () => ({
  AvatarDropdown: ({ placement, triggerClassName }: any) => (
    <button
      className={triggerClassName}
      data-placement={placement}
      type="button"
    >
      账户菜单
    </button>
  ),
}));

describe('SidebarAccount', () => {
  it('refreshes the unread badge after message changes', async () => {
    mocks.messageSummary.mockResolvedValueOnce({ unread: 120, categories: {}, canSend: false });
    render(<SidebarAccount currentUser={{ name: 'Test User' }} />);
    expect(await screen.findByRole('button', { name: '消息中心，120 条未读' })).toHaveTextContent('99+');
    fireEvent(window, new Event('message-center-change'));
    await screen.findByRole('button', { name: '消息中心' });
    expect(screen.queryByText('99+')).not.toBeInTheDocument();
  });
  it('opens the message center from above the points entry', () => {
    render(<SidebarAccount currentTenantId={7} currentUser={{ name: 'Test User' }} />);
    const entry = screen.getByRole('button', { name: /消息中心/ });
    expect(entry.compareDocumentPosition(screen.getByText('团队积分'))).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    fireEvent.click(entry);
    expect(mocks.push).toHaveBeenCalledWith('/messages');
  });
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('keeps the account menu but hides points without a selected team', () => {
    render(<SidebarAccount currentUser={{ name: 'Test User' }} />);
    expect(screen.getByRole('button', { name: '账户菜单' })).toBeInTheDocument();
    expect(screen.queryByText('团队积分')).not.toBeInTheDocument();
    expect(mocks.queryTeamPointAccount).not.toHaveBeenCalled();
  });

  it('refreshes immediately on team changes without displaying the previous balance', async () => {
    mocks.queryTeamPointAccount.mockResolvedValueOnce({ data: { balance: 1280 } }).mockReturnValueOnce(new Promise(() => {}));
    const user = { name: 'Test User' };
    const { rerender } = render(<SidebarAccount currentUser={user} currentTenantId={7} />);
    await screen.findByRole('button', { name: '团队积分 1,280，前往充值中心' });
    rerender(<SidebarAccount currentUser={user} currentTenantId={8} />);
    expect(screen.queryByRole('button', { name: '团队积分 1,280，前往充值中心' })).not.toBeInTheDocument();
    await waitFor(() => expect(mocks.queryTeamPointAccount).toHaveBeenCalledWith(8));
    rerender(<SidebarAccount currentUser={user} />);
    expect(screen.queryByText('团队积分')).not.toBeInTheDocument();
  });

  it('renders team points above the current account', () => {
    const { container } = render(
      <SidebarAccount
        currentTenantId={7}
        currentUser={{ name: 'Test User', avatar: '/avatar.png' }}
      />,
    );

    const points = screen.getByText('团队积分');
    const account = screen.getByRole('button', { name: '账户菜单' });

    expect(points.compareDocumentPosition(account)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
    expect(
      container.querySelector('.ant-short-sidebar-account'),
    ).toBeInTheDocument();
  });

  it('does not render without a current user', () => {
    const { container } = render(<SidebarAccount />);

    expect(container).toBeEmptyDOMElement();
  });

  it('uses the shared account dropdown as the footer trigger', () => {
    render(<SidebarAccount currentTenantId={7} currentUser={{ name: 'Test User' }} />);

    expect(screen.getByRole('button', { name: '账户菜单' })).toHaveAttribute(
      'data-placement',
      'topLeft',
    );
  });

  it('loads the current team balance and opens recharge when clicked', async () => {
    mocks.getCurrentTenantId.mockReturnValue(7);
    mocks.queryTeamPointAccount.mockResolvedValue({ data: { balance: 1280 } });

    render(<SidebarAccount currentTenantId={7} currentUser={{ name: 'Test User' }} />);

    const entry = await screen.findByRole('button', {
      name: '团队积分 1,280，前往充值中心',
    });
    expect(mocks.queryTeamPointAccount).toHaveBeenCalledWith(7);

    fireEvent.click(entry);

    expect(mocks.push).toHaveBeenCalledWith('/recharge');
  });

  it('refreshes the team balance when the window regains focus', async () => {
    mocks.getCurrentTenantId.mockReturnValue(7);
    mocks.queryTeamPointAccount
      .mockResolvedValueOnce({ data: { balance: 1280 } })
      .mockResolvedValueOnce({ data: { balance: 2560 } });

    render(<SidebarAccount currentTenantId={7} currentUser={{ name: 'Test User' }} />);
    await screen.findByRole('button', { name: '团队积分 1,280，前往充值中心' });

    fireEvent.focus(window);

    await waitFor(() => {
      expect(mocks.queryTeamPointAccount).toHaveBeenCalledTimes(2);
    });
    expect(
      screen.getByRole('button', { name: '团队积分 2,560，前往充值中心' }),
    ).toBeInTheDocument();
  });
});
