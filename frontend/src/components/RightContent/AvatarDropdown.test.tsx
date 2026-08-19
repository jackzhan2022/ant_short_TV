import { render, screen } from '@testing-library/react';
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
  default: ({ children }: any) => <div>{children}</div>,
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
  });
});
