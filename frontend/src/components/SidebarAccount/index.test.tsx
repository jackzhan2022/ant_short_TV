import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SidebarAccount from './index';

const mocks = vi.hoisted(() => ({
  getCurrentTenantId: vi.fn(),
  push: vi.fn(),
  queryTeamPointAccount: vi.fn(),
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
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders team points above the current account', () => {
    const { container } = render(
      <SidebarAccount
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
    render(<SidebarAccount currentUser={{ name: 'Test User' }} />);

    expect(screen.getByRole('button', { name: '账户菜单' })).toHaveAttribute(
      'data-placement',
      'topLeft',
    );
  });

  it('loads the current team balance and opens recharge when clicked', async () => {
    mocks.getCurrentTenantId.mockReturnValue(7);
    mocks.queryTeamPointAccount.mockResolvedValue({ data: { balance: 1280 } });

    render(<SidebarAccount currentUser={{ name: 'Test User' }} />);

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

    render(<SidebarAccount currentUser={{ name: 'Test User' }} />);
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
