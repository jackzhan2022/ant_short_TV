import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({
  status: vi.fn(),
  options: vi.fn(),
  grant: vi.fn(),
  enroll: vi.fn(),
  confirm: vi.fn(),
}));
vi.mock('./grantService', () => ({
  getAdminAuthenticator: mocks.status,
  getTenantGrantOptions: mocks.options,
  grantTenantPackage: mocks.grant,
  enrollAdminAuthenticator: mocks.enroll,
  confirmAdminAuthenticator: mocks.confirm,
  grantErrorMessage: (error: Error) => error.message,
}));
import GrantPackageModal from './GrantPackageModal';
import type { PlatformTenantSummary } from './service';
const tenant: PlatformTenantSummary = {
  id: 9,
  code: 'TENANT-9',
  name: '测试租户',
  type: 'STUDIO',
  status: 'ACTIVE',
  activeMemberCount: 1,
  pointBalance: 0,
  createdAt: '',
};
const options = [
  {
    packageId: 1,
    packageVersionId: 10,
    name: '专业会员',
    packageType: 'SUBSCRIPTION',
    periodMonths: 3,
    entitlements: [{ type: 'PERIODIC_POINTS', value: 1000, name: '周期积分' }],
  },
];

async function fillGrant() {
  const combo = await screen.findByRole('combobox', { name: '发放商品' });
  fireEvent.mouseDown(combo);
  fireEvent.click(await screen.findByText('专业会员（3 个月）'));
  fireEvent.change(screen.getByLabelText('发放原因'), {
    target: { value: '服务补偿' },
  });
  fireEvent.change(screen.getByLabelText('验证器验证码'), {
    target: { value: '123456' },
  });
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '确认发放' })).toBeEnabled(),
  );
}

describe('管理员发放弹窗', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.status.mockResolvedValue({ data: { bound: true } });
    mocks.options.mockResolvedValue({ data: options });
    mocks.grant.mockResolvedValue({ data: { id: 1, status: 'COMPLETED' } });
  });
  it('submits selected tenant, benefits, reason and code and preserves request identity on retry', async () => {
    mocks.grant.mockRejectedValueOnce(new Error('网络中断，请重试'));
    const granted = vi.fn();
    render(
      <GrantPackageModal
        tenant={tenant}
        onClose={vi.fn()}
        onGranted={granted}
      />,
    );
    await fillGrant();
    fireEvent.click(screen.getByRole('button', { name: '确认发放' }));
    await screen.findByText('网络中断，请重试');
    expect(mocks.grant).toHaveBeenCalledWith(
      9,
      expect.objectContaining({
        packageVersionId: 10,
        reason: '服务补偿',
        code: '123456',
      }),
    );
    const id = mocks.grant.mock.calls[0][1].requestId;
    fireEvent.change(screen.getByLabelText('验证器验证码'), {
      target: { value: '654321' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认发放' }));
    await waitFor(() => expect(granted).toHaveBeenCalledOnce());
    expect(mocks.grant.mock.calls[1][1].requestId).toBe(id);
  });
  it('selects a point package and explains that membership is unchanged', async () => {
    mocks.options.mockResolvedValue({ data: [...options, { packageId: 2, packageVersionId: 11, name: '积分补充', packageType: 'POINT_PACKAGE', entitlements: [{ type: 'ONE_TIME_POINTS', value: 500 }] }] });
    render(<GrantPackageModal tenant={tenant} onClose={vi.fn()} onGranted={vi.fn()} />);
    const typeSelect = await screen.findByRole('combobox', { name: '发放类型' });
    fireEvent.mouseDown(typeSelect);
    fireEvent.click(await screen.findByText('积分包', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '发放商品' }));
    fireEvent.click(await screen.findByText('积分补充'));
    await screen.findByText('积分立即到账，不改变当前会员套餐。');
    fireEvent.change(screen.getByLabelText('发放原因'), { target: { value: '运营赠送' } });
    fireEvent.change(screen.getByLabelText('验证器验证码'), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: '确认发放' }));
    await waitFor(() => expect(mocks.grant).toHaveBeenCalledWith(9, expect.objectContaining({ packageVersionId: 11 })));
  });

  it('requires binding and does not offer grant before confirmation', async () => {
    mocks.status.mockResolvedValue({ data: { bound: false } });
    mocks.enroll.mockResolvedValue({
      data: {
        secret: 'ABCDEFGHIJKLMN',
        uri: 'otpauth://totp/test?secret=ABCDEFGHIJKLMN',
        expiresAt: '2030-01-01T00:00:00Z',
      },
    });
    mocks.confirm.mockResolvedValue({ data: true });
    render(
      <GrantPackageModal
        tenant={tenant}
        onClose={vi.fn()}
        onGranted={vi.fn()}
      />,
    );
    await screen.findByLabelText('当前登录密码');
    expect(
      screen.queryByRole('button', { name: '确认发放' }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('当前登录密码'), {
      target: { value: 'Password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '验证密码并绑定' }));
    await screen.findByLabelText('绑定验证码');
    expect(mocks.enroll).toHaveBeenCalledWith('Password123');
    fireEvent.change(screen.getByLabelText('绑定验证码'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    await screen.findByText(/绑定成功，请等待验证器生成新的验证码/);
    expect(screen.getByLabelText('验证器验证码')).toHaveValue('');
    expect(mocks.grant).not.toHaveBeenCalled();
  });
  it('shows a switch warning and prevents submitting an empty reason or invalid code', async () => {
    render(
      <GrantPackageModal
        tenant={{
          ...tenant,
          currentPackage: {
            subscriptionId: 4,
            packageId: 3,
            packageVersionId: 20,
            packageType: 'SUBSCRIPTION',
            name: '旧套餐',
            subscriptionStatus: 'ACTIVE',
            startsAt: '',
            endsAt: '2030-01-01',
          },
        }}
        onClose={vi.fn()}
        onGranted={vi.fn()}
      />,
    );
    await fillGrant();
    await screen.findByText(/立即切换/);
    fireEvent.change(screen.getByLabelText('发放原因'), {
      target: { value: '   ' },
    });
    fireEvent.change(screen.getByLabelText('验证器验证码'), {
      target: { value: '123' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认发放' }));
    await screen.findByText('请输入 6 位验证码');
    expect(mocks.grant).not.toHaveBeenCalled();
  });
});
