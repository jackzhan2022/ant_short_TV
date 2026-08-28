import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SidebarAccount from './index';

vi.mock('../RightContent/AvatarDropdown', () => ({
  AvatarDropdown: ({ placement, triggerClassName }: any) => (
    <button className={triggerClassName} data-placement={placement} type="button">
      账户菜单
    </button>
  ),
}));

describe('SidebarAccount', () => {
  it('renders team points above the current account', () => {
    const { container } = render(
      <SidebarAccount currentUser={{ name: 'Test User', avatar: '/avatar.png' }} />,
    );

    const points = screen.getByText('团队积分');
    const account = screen.getByRole('button', { name: '账户菜单' });

    expect(points.compareDocumentPosition(account)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(container.querySelector('.ant-short-sidebar-account')).toBeInTheDocument();
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
});
