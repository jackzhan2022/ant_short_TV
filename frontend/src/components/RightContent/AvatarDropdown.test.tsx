import { fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { describe, expect, it, vi } from 'vitest';
import { AvatarDropdown } from './AvatarDropdown';

const mockUseModel = vi.hoisted(() => vi.fn());

vi.mock('@umijs/max', () => ({
  history: {
    push: vi.fn(),
    replace: vi.fn(),
  },
  useModel: mockUseModel,
}));

vi.mock('antd', () => ({
  Avatar: ({ alt, src }: any) => <img alt={alt} src={src} />,
  Spin: () => <span>loading</span>,
}));

vi.mock('../HeaderDropdown', () => ({
  default: ({ children, menu }: any) => <div>{children}{menu.items.filter((item: any) => item.key).map((item: any) => (
    <button key={item.key} type="button" onClick={() => menu.onClick({ key: item.key })}>{item.label}</button>
  ))}</div>,
}));

vi.mock('@/services/account-team/auth', () => ({
  logout: vi.fn(),
}));

describe('AvatarDropdown', () => {
  it('shows avatar and name in the account trigger', () => {
    mockUseModel.mockReturnValue({
      initialState: {
        currentUser: {
          name: 'yonghu67387',
          avatar: 'https://example.com/avatar.png',
        },
      },
      setInitialState: vi.fn(),
    });

    render(<AvatarDropdown>Trigger</AvatarDropdown>);

    expect(screen.getByAltText('yonghu67387')).toHaveAttribute(
      'src',
      'https://example.com/avatar.png',
    );
    expect(screen.getByText('yonghu67387')).toBeInTheDocument();
    expect(screen.queryByText('主题设置')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '退出登录' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '个人设置' }));
    expect(history.push).toHaveBeenCalledWith('/team/settings');
  });
});
