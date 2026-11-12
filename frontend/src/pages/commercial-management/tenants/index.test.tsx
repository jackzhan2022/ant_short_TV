import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PlatformTenantDetail, PlatformTenantSummary } from './service';

const mocks = vi.hoisted(() => ({
  access: { canViewPlatformTenants: true, canEditPlatformTenantStatus: true, canAssignPlatformTenantTrial: true, canGrantPlatformTenantPackage: true, canImpersonateTenantMember: true },
  queryTenants: vi.fn(), getTenant: vi.fn(), updateStatus: vi.fn(), assignTrial: vi.fn(), success: vi.fn(),
}));
vi.mock('@umijs/max', async () => ({ ...await vi.importActual<typeof import('@umijs/max')>('@umijs/max'), useAccess: () => mocks.access }));
vi.mock('@ant-design/pro-components', () => ({ PageContainer: ({ children }: any) => <ConfigProvider theme={{ token: { motion: false } }}><main>{children}</main></ConfigProvider> }));
vi.mock('./service', () => ({ queryPlatformTenants: mocks.queryTenants, getPlatformTenant: mocks.getTenant, updatePlatformTenantStatus: mocks.updateStatus, assignPlatformTenantTrial: mocks.assignTrial }));
vi.mock('./GrantPackageModal', () => ({ default: ({ tenant, onGranted }: any) => <div><span>发放至：{tenant.name}</span><button type="button" onClick={onGranted}>完成发放</button></div> }));
vi.mock('./ImpersonationModal', () => ({ default: ({ member }: any) => <div>切换至：{member.nickname}</div> }));
vi.mock('antd', async () => ({
  ...await vi.importActual<typeof import('antd')>('antd'),
  App: { useApp: () => ({ message: { success: mocks.success } }) },
  Select: ({ value, onChange, options, 'aria-label': label }: any) => <select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)}>{options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>,
}));
import PlatformTenantManagementPage from './index';

const tenant: PlatformTenantSummary = {
  id: 9, code: 'TENANT-9', name: 'Alpha Studio', type: 'STUDIO', status: 'ACTIVE',
  owner: { memberId: 1, userId: 2, nickname: 'Alice', mobile: '13800000000' },
  activeMemberCount: 3, pointBalance: 0, currentPackage: null, createdAt: '2026-09-01T08:00:00',
};
const detail: PlatformTenantDetail = { ...tenant, description: '运营租户', updatedAt: tenant.createdAt, trialPointBalance: 0, members: [
  { memberId: 11, userId: 11, nickname: '可用成员', mobile: '13800000001', memberType: 'MEMBER', status: 'ACTIVE', userStatus: 'ACTIVE', joinedAt: tenant.createdAt, canImpersonate: true },
  { memberId: 12, userId: 12, nickname: '已移除成员', mobile: null, memberType: 'MEMBER', status: 'REMOVED', userStatus: 'ACTIVE', joinedAt: tenant.createdAt, canImpersonate: false },
  { memberId: 13, userId: 13, nickname: '停用成员', mobile: null, memberType: 'MEMBER', status: 'ACTIVE', userStatus: 'DISABLED', joinedAt: tenant.createdAt, canImpersonate: false },
  { memberId: 14, userId: 14, nickname: null, mobile: null, memberType: 'MEMBER', status: 'REMOVED', userStatus: 'DELETED', joinedAt: tenant.createdAt, canImpersonate: false },
], queuedPackages: [{ subscriptionId: 11, packageId: 4, packageVersionId: 7, name: '专业版', packageType: 'SUBSCRIPTION', subscriptionStatus: 'QUEUED', startsAt: '2026-10-01T00:00:00', endsAt: '2026-11-01T00:00:00' }] };
const page = (records = [tenant], total = records.length) => ({ success: true, data: { records, total, current: 1, pageSize: 20 } });
const openMore = async () => { fireEvent.click(await screen.findByRole('button', { name: 'Alpha Studio的更多操作' })); return screen.findByRole('menu'); };
const openDetail = async () => { fireEvent.click(await screen.findByRole('button', { name: '查看详情' })); return screen.findByRole('dialog'); };

describe('PlatformTenantManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks(); Object.keys(mocks.access).forEach((key) => { mocks.access[key as keyof typeof mocks.access] = true; });
    mocks.queryTenants.mockResolvedValue(page()); mocks.getTenant.mockResolvedValue({ success: true, data: detail });
    mocks.updateStatus.mockResolvedValue({ success: true, data: { ...tenant, status: 'DISABLED' } }); mocks.assignTrial.mockResolvedValue({ success: true, data: tenant });
  });
  it('sends server filters and resets page while preserving zero balance', async () => {
    mocks.queryTenants.mockResolvedValue(page([tenant], 42)); render(<PlatformTenantManagementPage />); await screen.findByText('Alpha Studio');
    expect(screen.getByRole('textbox', { name: '搜索租户名称或编码' })).toBeInTheDocument(); expect(screen.getByText('暂无套餐')).toBeInTheDocument(); expect(screen.getByText('0')).toBeInTheDocument();
    fireEvent.click(within(screen.getByTitle('Next Page')).getByRole('button'));
    await waitFor(() => expect(mocks.queryTenants).toHaveBeenLastCalledWith({ current: 2, pageSize: 20 }));
    fireEvent.click(within(screen.getByRole('group', { name: '租户状态' })).getByRole('button', { name: '已停用' }));
    fireEvent.change(screen.getByRole('combobox', { name: '当前套餐类型' }), { target: { value: 'SUBSCRIPTION' } });
    fireEvent.change(screen.getByRole('textbox', { name: '搜索租户名称或编码' }), { target: { value: ' alpha ' } });
    await waitFor(() => expect(mocks.queryTenants).toHaveBeenLastCalledWith({ current: 1, pageSize: 20, keyword: 'alpha', status: 'DISABLED', packageType: 'SUBSCRIPTION' }));
    fireEvent.click(screen.getByRole('button', { name: '清除筛选' })); await waitFor(() => expect(mocks.queryTenants).toHaveBeenLastCalledWith({ current: 1, pageSize: 20 }));
  });
  it('hides unauthorized mutation entries and disables grants for disabled tenants', async () => {
    mocks.access.canGrantPlatformTenantPackage = false; mocks.access.canEditPlatformTenantStatus = false; mocks.access.canAssignPlatformTenantTrial = false;
    const view = render(<PlatformTenantManagementPage />); await screen.findByText('Alpha Studio');
    expect(screen.queryByRole('button', { name: 'Alpha Studio的更多操作' })).not.toBeInTheDocument(); view.unmount();
    mocks.access.canGrantPlatformTenantPackage = true; mocks.queryTenants.mockResolvedValue(page([{ ...tenant, status: 'DISABLED' }]));
    render(<PlatformTenantManagementPage />); const menu = await openMore(); expect(within(menu).getByRole('menuitem', { name: /添加套餐/ })).toHaveAttribute('aria-disabled', 'true');
  });
  it('shows all members and preserves switching eligibility and permissions', async () => {
    const view = render(<PlatformTenantManagementPage />); const drawer = await openDetail(); await within(drawer).findByText('运营租户');
    fireEvent.click(within(drawer).getByRole('tab', { name: /成员/ })); const panel = within(drawer).getByRole('tabpanel');
    expect(within(panel).getByText('已移除成员')).toBeInTheDocument(); expect(within(panel).getByText('已删除')).toBeInTheDocument();
    const buttons = within(panel).getAllByRole('button', { name: '切换登录' }); expect(buttons[0]).toBeEnabled(); buttons.slice(1).forEach((b) => { expect(b).toBeDisabled(); });
    fireEvent.click(buttons[0]); expect(screen.getByText('切换至：可用成员')).toBeInTheDocument();
    mocks.access.canImpersonateTenantMember = false; view.rerender(<PlatformTenantManagementPage />); expect(screen.queryByRole('button', { name: '切换登录' })).not.toBeInTheDocument();
  });
  it('disables member switching for disabled tenants', async () => {
    mocks.getTenant.mockResolvedValue({ success: true, data: { ...detail, status: 'DISABLED' } }); render(<PlatformTenantManagementPage />);
    const drawer = await openDetail(); await within(drawer).findByText('运营租户'); fireEvent.click(within(drawer).getByRole('tab', { name: /成员/ }));
    within(drawer).getAllByRole('button', { name: '切换登录' }).forEach((button) => { expect(button).toBeDisabled(); });
  });
  it('keeps queued packages and refreshes the open detail after granting', async () => {
    render(<PlatformTenantManagementPage />); const drawer = await openDetail(); await within(drawer).findByText('运营租户');
    fireEvent.click(within(drawer).getByRole('tab', { name: '套餐' })); expect(within(drawer).getByText('专业版')).toBeInTheDocument();
    fireEvent.click(within(drawer).getByRole('button', { name: '添加套餐/积分包' })); fireEvent.click(screen.getByRole('button', { name: '完成发放' }));
    await waitFor(() => expect(mocks.getTenant).toHaveBeenCalledTimes(2)); expect(mocks.queryTenants).toHaveBeenCalledTimes(2);
  });
  it('cancels without a request and only disables after confirmation, then refreshes list and detail', async () => {
    render(<PlatformTenantManagementPage />); const drawer = await openDetail(); await within(drawer).findByText('运营租户');
    fireEvent.click(within(drawer).getByRole('button', { name: '更多租户操作' })); fireEvent.click(await screen.findByRole('menuitem', { name: '停用租户' }));
    expect(mocks.updateStatus).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole('button', { name: /^取\s*消$/ }));
    await waitFor(() => expect(screen.queryByText('确认停用租户')).not.toBeInTheDocument()); expect(mocks.updateStatus).not.toHaveBeenCalled();
    fireEvent.click(within(drawer).getByRole('button', { name: '更多租户操作' })); fireEvent.click(await screen.findByRole('menuitem', { name: '停用租户' }));
    fireEvent.click(screen.getByRole('button', { name: '确认停用' })); await waitFor(() => expect(mocks.updateStatus).toHaveBeenCalledWith(9, 'DISABLED'));
    await waitFor(() => expect(mocks.getTenant).toHaveBeenCalledTimes(2)); expect(mocks.queryTenants).toHaveBeenCalledTimes(2);
  });
  it('assigns a trial only after confirmation', async () => {
    render(<PlatformTenantManagementPage />); const menu = await openMore(); fireEvent.click(within(menu).getByRole('menuitem', { name: '分配试用套餐' }));
    expect(mocks.assignTrial).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole('button', { name: '确认分配' }));
    await waitFor(() => expect(mocks.assignTrial).toHaveBeenCalledWith(9)); expect(mocks.queryTenants).toHaveBeenCalledTimes(2);
  });
  it.each([{ trialAssigned: true }, { currentPackage: { name: '试用套餐', packageType: 'TRIAL' } }])('hides trial action when already used or packaged: %j', async (overrides) => {
    mocks.queryTenants.mockResolvedValue(page([{ ...tenant, ...overrides } as PlatformTenantSummary])); render(<PlatformTenantManagementPage />);
    const menu = await openMore(); expect(within(menu).queryByRole('menuitem', { name: '分配试用套餐' })).not.toBeInTheDocument();
  });
  it('keeps failed mutations open for retry without reporting success', async () => {
    mocks.updateStatus.mockRejectedValueOnce(new Error('network')); render(<PlatformTenantManagementPage />); const menu = await openMore();
    fireEvent.click(within(menu).getByRole('menuitem', { name: '停用租户' })); fireEvent.click(screen.getByRole('button', { name: '确认停用' }));
    expect(await screen.findByText('操作失败，请重试')).toBeInTheDocument(); expect(mocks.success).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '确认停用' })); await waitFor(() => expect(mocks.success).toHaveBeenCalledWith('租户已停用'));
  });
  it('opens failed details for retry and ignores responses after closing', async () => {
    mocks.getTenant.mockRejectedValueOnce(new Error('offline')); render(<PlatformTenantManagementPage />); const drawer = await openDetail();
    expect(await within(drawer).findByText('租户详情加载失败')).toBeInTheDocument(); let resolve!: (value: unknown) => void;
    mocks.getTenant.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    fireEvent.click(within(drawer).getByRole('button', { name: /^重\s*试$/ })); fireEvent.click(within(drawer).getByRole('button', { name: /^关\s*闭$/ }));
    await act(async () => resolve({ success: true, data: detail })); await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
  it('retries list failures and does not query without permission', async () => {
    mocks.queryTenants.mockRejectedValueOnce(new Error('offline')); const view = render(<PlatformTenantManagementPage />); await screen.findByText('租户加载失败');
    fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ })); await screen.findByText('Alpha Studio'); view.unmount(); mocks.queryTenants.mockClear();
    mocks.access.canViewPlatformTenants = false; render(<PlatformTenantManagementPage />); expect(mocks.queryTenants).not.toHaveBeenCalled();
  });
});
