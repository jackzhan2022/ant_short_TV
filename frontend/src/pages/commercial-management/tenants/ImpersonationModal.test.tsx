import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ImpersonationModal from './ImpersonationModal';
import type { PlatformTenantMember, PlatformTenantSummary } from './service';

const mocks = vi.hoisted(() => ({ status: vi.fn(), start: vi.fn() }));
vi.mock('./grantService', () => ({
  getAdminAuthenticator: mocks.status,
  grantErrorMessage: (error: Error) => error.message,
}));
vi.mock('./AuthenticatorBinding', () => ({
  default: ({ onBound }: { onBound: () => void }) => (
    <button type="button" onClick={onBound}>
      绑定验证器
    </button>
  ),
}));
vi.mock('@/services/account-team/impersonation', () => ({
  startTenantMemberImpersonation: mocks.start,
}));

const tenant = {
  id: 9,
  code: 'T9',
  name: '测试团队',
  status: 'ACTIVE',
} as PlatformTenantSummary;
const member = {
  memberId: 11,
  nickname: '成员甲',
  mobile: '13800000000',
  canImpersonate: true,
} as PlatformTenantMember;

describe('切换登录二次验证', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.status.mockResolvedValue({ data: { bound: true } });
    mocks.start.mockResolvedValue(undefined);
  });
  it('requires a nonblank reason and six digit code before starting target session', async () => {
    render(
      <ImpersonationModal tenant={tenant} member={member} onClose={vi.fn()} />,
    );
    await screen.findByLabelText('切换原因');
    expect(screen.getByText(/修改数据、创建任务/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('切换原因'), {
      target: { value: '   ' },
    });
    fireEvent.change(screen.getByLabelText('验证器验证码'), {
      target: { value: '123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认切换登录' }));
    await screen.findByText('请输入 6 位验证码');
    expect(mocks.start).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('切换原因'), {
      target: { value: ' 排查任务 ' },
    });
    fireEvent.change(screen.getByLabelText('验证器验证码'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认切换登录' }));
    await waitFor(() =>
      expect(mocks.start).toHaveBeenCalledWith(
        9,
        11,
        { reason: '排查任务', code: '123456' },
        { tenantName: '测试团队', userName: '成员甲' },
      ),
    );
  });
  it('requires authenticator binding and waits for a fresh verification code', async () => {
    mocks.status.mockResolvedValue({ data: { bound: false } });
    render(
      <ImpersonationModal tenant={tenant} member={member} onClose={vi.fn()} />,
    );
    fireEvent.click(await screen.findByRole('button', { name: '绑定验证器' }));
    expect(
      await screen.findByText(/等待验证器生成新的验证码/),
    ).toBeInTheDocument();
    expect(mocks.start).not.toHaveBeenCalled();
  });
  it('retains the modal and clears the code after a failed switch', async () => {
    mocks.start.mockRejectedValue(new Error('验证码已使用'));
    render(
      <ImpersonationModal tenant={tenant} member={member} onClose={vi.fn()} />,
    );
    await screen.findByLabelText('切换原因');
    fireEvent.change(screen.getByLabelText('切换原因'), {
      target: { value: '排查' },
    });
    fireEvent.change(screen.getByLabelText('验证器验证码'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认切换登录' }));
    await screen.findByText('验证码已使用');
    expect(screen.getByLabelText('验证器验证码')).toHaveValue('');
    expect(screen.getByLabelText('切换原因')).toHaveValue('排查');
  });
});
