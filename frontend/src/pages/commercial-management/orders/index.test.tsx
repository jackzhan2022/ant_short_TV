import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PlatformCommercialOrderDetail } from './service';

const mocks = vi.hoisted(() => ({ access: { canViewCommercialOrders: true }, listOrders: vi.fn(), getOrder: vi.fn() }));
vi.mock('@umijs/max', async () => ({ ...await vi.importActual<typeof import('@umijs/max')>('@umijs/max'), useAccess: () => mocks.access }));
vi.mock('./service', () => ({ getPlatformCommercialOrder: mocks.getOrder, queryPlatformCommercialOrders: mocks.listOrders }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
}));
vi.mock('antd', async () => ({
  ...await vi.importActual<typeof import('antd')>('antd'),
  Select: ({ value, onChange, options, 'aria-label': label }: any) => <select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)}>{options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>,
}));
import PlatformCommercialOrderPage from './index';

const order: PlatformCommercialOrderDetail = {
  id: 1, merchantOrderNo: 'COM202609040001', tenantId: 2, tenantName: '新禾文创', tenantCode: 'TENANT01',
  packageVersionId: 10, packageName: '专业版月卡', packageVersionNo: 2, packageType: 'SUBSCRIPTION', amount: 99, currency: 'CNY', status: 'COMPLETED',
  createdAt: '2026-09-04T10:00:00', paidAt: '2026-09-04T10:01:00', completedAt: '2026-09-04T10:01:03', expiresAt: '2026-09-04T10:30:00',
  payment: { provider: 'WECHAT_NATIVE', providerTradeNo: 'WX-1001', status: 'SUCCESS', paidAt: '2026-09-04T10:01:00' },
};
const page = (records = [order], total = records.length) => ({ success: true, data: { records, total, current: 1, pageSize: 20 } });
describe('PlatformCommercialOrderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks(); mocks.access.canViewCommercialOrders = true;
    mocks.listOrders.mockResolvedValue(page()); mocks.getOrder.mockResolvedValue({ success: true, data: order });
  });
  it('opens live payment details with a formatted amount and a close action', async () => {
    render(<PlatformCommercialOrderPage />);
    expect(await screen.findByText(order.merchantOrderNo)).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '搜索订单号或租户名称' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    await waitFor(() => expect(mocks.getOrder).toHaveBeenCalled());
    // antd assigns the same test-id to pagination and drawer labels in test mode.
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('订单详情')).toBeInTheDocument();
    expect(await within(dialog).findByText('WX-1001')).toBeInTheDocument();
    const summary = within(dialog).getByRole('region', { name: '订单摘要' });
    expect(summary).toHaveTextContent('99.00'); expect(within(summary).getByText('已完成')).toBeInTheDocument();
    expect(within(dialog).getByText('微信扫码支付')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: /^关\s*闭$/ })).toBeInTheDocument();
    expect(mocks.getOrder).toHaveBeenCalledWith(1);
  });
  it('sends keyword, status and package type to the server and resets pagination', async () => {
    mocks.listOrders.mockResolvedValue(page([order], 42)); render(<PlatformCommercialOrderPage />);
    await screen.findByText(order.merchantOrderNo);
    fireEvent.click(within(screen.getByTitle('Next Page')).getByRole('button'));
    await waitFor(() => expect(mocks.listOrders).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2, pageSize: 20 })));
    fireEvent.click(within(screen.getByRole('group', { name: '订单状态' })).getByRole('button', { name: '权益发放中' }));
    await waitFor(() => expect(mocks.listOrders).toHaveBeenLastCalledWith(expect.objectContaining({ current: 1, status: 'ENTITLEMENT_PENDING' })));
    fireEvent.change(screen.getByRole('combobox', { name: '套餐类型' }), { target: { value: 'SUBSCRIPTION' } });
    fireEvent.change(screen.getByRole('textbox', { name: '搜索订单号或租户名称' }), { target: { value: ' 新禾 ' } });
    await waitFor(() => expect(mocks.listOrders).toHaveBeenLastCalledWith(expect.objectContaining({ current: 1, keyword: '新禾', status: 'ENTITLEMENT_PENDING', packageType: 'SUBSCRIPTION' })));
    fireEvent.click(screen.getByRole('button', { name: '清除筛选' }));
    await waitFor(() => expect(mocks.listOrders).toHaveBeenLastCalledWith({ current: 1, pageSize: 20 }));
    expect(screen.getByRole('textbox', { name: '搜索订单号或租户名称' })).toHaveValue('');
  });
  it('keeps all six order states separate', async () => {
    render(<PlatformCommercialOrderPage />);
    const filters = screen.getByRole('group', { name: '订单状态' });
    expect(within(filters).getAllByRole('button')).toHaveLength(7);
    for (const name of ['待支付', '已支付', '权益发放中', '已完成', '已关闭', '支付异常']) expect(within(filters).getByRole('button', { name })).toBeInTheDocument();
    await screen.findByText(order.merchantOrderNo);
  });
  it('renders absent dates without invalid values or fictional events', async () => {
    const closed = { ...order, id: 2, merchantOrderNo: 'COM-CLOSED', status: 'CLOSED' as const, paidAt: '-', completedAt: undefined, payment: undefined };
    mocks.listOrders.mockResolvedValue(page([closed])); mocks.getOrder.mockResolvedValue({ success: true, data: closed });
    render(<PlatformCommercialOrderPage />); fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));
    const dialog = await screen.findByRole('dialog'); await within(dialog).findByText('COM-CLOSED');
    expect(screen.queryByText('Invalid Date')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('订单完成')).not.toBeInTheDocument(); expect(within(dialog).queryByText('支付成功')).not.toBeInTheDocument();
  });
  it('allows retry after a list failure', async () => {
    mocks.listOrders.mockRejectedValueOnce(new Error('offline')); render(<PlatformCommercialOrderPage />);
    expect(await screen.findByText('订单加载失败')).toBeInTheDocument(); fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
    expect(await screen.findByText(order.merchantOrderNo)).toBeInTheDocument();
  });
  it('keeps the detail drawer open with a retry action when loading fails', async () => {
    mocks.getOrder.mockRejectedValueOnce(new Error('offline')); render(<PlatformCommercialOrderPage />);
    fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('订单详情加载失败')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: /^重\s*试$/ })); expect(await within(dialog).findByText('WX-1001')).toBeInTheDocument();
  });
  it('does not reopen a closed drawer after a delayed detail response', async () => {
    let resolve!: (value: unknown) => void;
    mocks.getOrder.mockImplementationOnce(() => new Promise((done) => { resolve = done; })); render(<PlatformCommercialOrderPage />);
    fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: /^关\s*闭$/ }));
    await act(async () => resolve({ success: true, data: order }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
  it('does not query orders without view permission', () => {
    mocks.access.canViewCommercialOrders = false; render(<PlatformCommercialOrderPage />); expect(mocks.listOrders).not.toHaveBeenCalled();
  });
});
