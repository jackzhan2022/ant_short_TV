import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ImpersonationBanner from './index';
import {
  markSessionChanged,
  setExpectedSessionId,
} from '@/services/account-team/sessionIdentity';

const mocks = vi.hoisted(() => ({
  stop: vi.fn(),
  display: undefined as { tenantName: string; userName: string } | undefined,
}));
vi.mock('@/services/account-team/impersonation', () => ({
  getImpersonationDisplay: () => mocks.display,
  stopImpersonation: mocks.stop,
}));

describe('global impersonation recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setExpectedSessionId(undefined);
    mocks.display = { tenantName: '目标团队', userName: '目标成员' };
    mocks.stop.mockResolvedValue(undefined);
  });
  it('allows return without bootstrap or layout context (including login recovery)', async () => {
    render(<ImpersonationBanner />);
    expect(screen.getByText(/目标团队.*目标成员/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '返回管理员' }));
    await waitFor(() => expect(mocks.stop).toHaveBeenCalledOnce());
  });
  it('keeps the return action available after a failed return', async () => {
    mocks.stop.mockRejectedValue(new Error('网络中断'));
    render(<ImpersonationBanner />);
    fireEvent.click(screen.getByRole('button', { name: '返回管理员' }));
    expect(await screen.findByText('网络中断')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /返回管理员/ }),
      ).not.toHaveClass('ant-btn-loading'),
    );
    fireEvent.click(screen.getByRole('button', { name: /返回管理员/ }));
    await waitFor(() => expect(mocks.stop).toHaveBeenCalledTimes(2));
  });
  it('shows refresh instead of operating a stale impersonation session', async () => {
    render(<ImpersonationBanner />);
    await act(async () => markSessionChanged());
    expect(
      screen.getByRole('button', { name: '刷新页面' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '返回管理员' }),
    ).not.toBeInTheDocument();
  });
});
