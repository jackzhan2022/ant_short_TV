import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  tenantId: 10,
  canManageBilling: true,
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageInfo: vi.fn(),
  catalog: vi.fn(),
  current: vi.fn(),
  queued: vi.fn(),
  grants: vi.fn(),
  orders: vi.fn(),
  points: vi.fn(),
  pointTransactions: vi.fn(),
  create: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock('@/services/account-team/auth', () => ({ getCurrentTenantId: () => mocks.tenantId }));
vi.mock('@/services/account-team/points', () => ({
  queryTeamPointAccount: mocks.points,
  queryTeamPointTransactions: mocks.pointTransactions,
}));
vi.mock('./service', () => ({
  queryCommercialCatalog: mocks.catalog,
  queryCurrentSubscription: mocks.current,
  queryQueuedSubscriptions: mocks.queued,
  queryCommercialGrants: mocks.grants,
  queryActiveCommercialOrders: mocks.orders,
  createCommercialOrder: mocks.create,
  refreshCommercialOrder: mocks.refresh,
}));
vi.mock('@umijs/max', () => ({
  history: { back: vi.fn(), push: vi.fn() },
  useAccess: () => ({ canManageBilling: mocks.canManageBilling }),
  useModel: () => ({ initialState: { tenants: [
    { id: 10, name: '新禾文创' }, { id: 20, name: '远山工作室' },
  ] } }),
}));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { error: mocks.messageError, success: mocks.messageSuccess, info: mocks.messageInfo } }) },
  Button: ({ children, onClick, disabled, loading, ...props }: any) => <button type="button" onClick={onClick} disabled={disabled || loading} {...props}>{children}</button>,
  Card: ({ children, title }: any) => <section>{title && <h2>{title}</h2>}{children}</section>,
  Empty: ({ description }: any) => <div>{description}</div>,
  List: Object.assign(({ dataSource = [], renderItem, children }: any) => <div>{children}{dataSource.map((item: any, index: number) => <div key={item.id ?? index}>{renderItem(item)}</div>)}</div>, {
    Item: Object.assign(({ children }: any) => <div>{children}</div>, { Meta: ({ title, description }: any) => <div>{title}{description}</div> }),
  }),
  Modal: ({ children, open, title }: any) => open ? <section role="dialog" aria-label={title}>{children}</section> : null,
  QRCode: ({ value }: any) => <div data-testid="qr-code">{value}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Spin: ({ children }: any) => <div>{children}</div>,
  Statistic: ({ value, suffix }: any) => <div>{value} {suffix}</div>,
  Table: ({ dataSource = [], columns = [] }: any) => <div>{dataSource.map((record: any) => <div key={record.id}>{columns.map((column: any, index: number) => <span key={column.dataIndex ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Tabs: ({ items, onChange, activeKey }: any) => <div role="tablist">{items.map((item: any) => <button role="tab" aria-selected={activeKey === item.key} type="button" key={item.key} onClick={() => onChange(item.key)}>{item.label}</button>)}</div>,
  Radio: { Group: ({ options, value, onChange }: any) => <fieldset aria-label="会员周期">{options.map((item: any) => <button type="button" aria-pressed={value === item.value} key={item.value} onClick={() => onChange({ target: { value: item.value } })}>{item.label}</button>)}</fieldset> },
  Typography: { Text: ({ children }: any) => <span>{children}</span>, Title: ({ children }: any) => <h1>{children}</h1>, Paragraph: ({ children }: any) => <p>{children}</p> },
}));
vi.mock('@ant-design/icons', () => ({ CheckOutlined: () => null, LeftOutlined: () => null, LockOutlined: () => null, WalletOutlined: () => null, CreditCardOutlined: () => null, SafetyCertificateOutlined: () => null }));

import CommercialPage from './index';

describe('CommercialPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mocks.tenantId = 10;
    mocks.canManageBilling = true;
    mocks.catalog.mockResolvedValue({ data: [{ packageId: 1, packageVersionId: 11, packageType: 'SUBSCRIPTION', billingPeriod: 'MONTH', periodMonths: 1, name: '专业版月卡', price: 99, currency: 'CNY', entitlements: [{ type: 'PERIODIC_POINTS', value: 3000 }] }] });
    mocks.current.mockResolvedValue({ data: { id: 21, status: 'ACTIVE', startsAt: '2026-08-01T00:00:00', endsAt: '2026-09-01T00:00:00', snapshotJson: '{"name":"专业版月卡"}' } });
    mocks.queued.mockResolvedValue({ data: [{ id: 22, status: 'QUEUED', startsAt: '2026-09-01T00:00:00', endsAt: '2026-12-01T00:00:00', snapshotJson: '{"name":"专业版季卡"}' }] });
    mocks.grants.mockResolvedValue({ data: [{ id: 31, entitlementType: 'PERIODIC_POINTS', amount: 3000, status: 'GRANTED', grantedAt: '2026-08-01T00:00:00' }] });
    mocks.orders.mockResolvedValue({ data: [] });
    mocks.points.mockResolvedValue({ data: { balance: 2140 } });
    mocks.pointTransactions.mockResolvedValue({
      data: {
        records: [
          { id: 41, transactionType: 'ENTITLEMENT_GRANT', changeAmount: 3000, balanceAfter: 4000, description: '会员周期积分发放', createdAt: '2026-08-01T00:00:00' },
          { id: 42, transactionType: 'ADJUST_GRANT', changeAmount: 25, balanceAfter: 1000, description: '历史手工增加', createdAt: '2026-07-01T00:00:00' },
          { id: 43, transactionType: 'RESERVE', changeAmount: -100, balanceAfter: 2900, description: 'AI 积分预扣', createdAt: '2026-08-02T00:00:00' },
          { id: 48, transactionType: 'INCREMENTAL_RESERVE', changeAmount: -20, balanceAfter: 2880, description: 'AI 追加预扣', createdAt: '2026-08-02T00:01:00' },
          { id: 44, transactionType: 'SETTLE', changeAmount: -80, balanceAfter: 2900, description: 'AI 积分结算', createdAt: '2026-08-03T00:00:00' },
          { id: 45, transactionType: 'RELEASE', changeAmount: 20, balanceAfter: 2920, description: 'AI 预扣释放', createdAt: '2026-08-03T00:01:00' },
          { id: 46, transactionType: 'REFUND', changeAmount: 80, balanceAfter: 3000, description: 'AI 积分退回', createdAt: '2026-08-04T00:00:00' },
          { id: 47, transactionType: 'LOYALTY_BONUS', changeAmount: 10, balanceAfter: 3010, description: '活动奖励', createdAt: '2026-08-05T00:00:00' },
        ],
        total: 8,
        current: 1,
        pageSize: 20,
      },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads unified point history without querying commercial grants', async () => {
    render(<CommercialPage />);

    await waitFor(() => expect(mocks.catalog).toHaveBeenCalledWith(10));
    expect(mocks.pointTransactions).toHaveBeenCalledWith(10);
    expect(mocks.grants).not.toHaveBeenCalled();
    expect(await screen.findAllByText('专业版月卡')).not.toHaveLength(0);
    expect(screen.getByText('专业版季卡', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('2,140')).toBeInTheDocument();
    expect(screen.getByText('积分明细')).toBeInTheDocument();
    expect(screen.getByText('权益发放')).toBeInTheDocument();
    expect(screen.getByText('手动增加')).toBeInTheDocument();
    expect(screen.getByText('积分预扣')).toBeInTheDocument();
    expect(screen.getByText('追加预扣')).toBeInTheDocument();
    expect(screen.getByText('积分消耗')).toBeInTheDocument();
    expect(screen.getByText('预扣释放')).toBeInTheDocument();
    expect(screen.getByText('积分退回')).toBeInTheDocument();
    expect(screen.getByText('LOYALTY_BONUS')).toBeInTheDocument();
    expect(screen.getByText('4,000')).toBeInTheDocument();
    expect(screen.getByText('会员周期积分发放')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '积分明细' })).not.toBeInTheDocument();
  });

  it('shows point packages inline without creating an order on selection', async () => {
    mocks.catalog.mockResolvedValue({ data: [{ packageId: 2, packageVersionId: 12, packageType: 'POINT_PACKAGE', name: '积分增强包', price: 59, currency: 'CNY', entitlements: [{ type: 'ONE_TIME_POINTS', value: 2000 }] }] });

    render(<CommercialPage />);

    fireEvent.click(await screen.findByRole('tab', { name: '积分包' }));
    expect(screen.getByRole('button', { name: '选择积分增强包' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '选择套餐' })).not.toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('keeps selection local and displays the selected team', async () => {
    mocks.catalog.mockResolvedValue({ data: [
      { packageId: 1, packageVersionId: 11, packageType: 'SUBSCRIPTION', periodMonths: 1, name: '标准月卡', price: 99, currency: 'CNY', entitlements: [] },
      { packageId: 2, packageVersionId: 12, packageType: 'SUBSCRIPTION', periodMonths: 1, name: '专业月卡', price: 199, currency: 'CNY', entitlements: [] },
    ] });
    render(<CommercialPage />);
    fireEvent.click(await screen.findByRole('button', { name: '选择专业月卡' }));
    expect(document.querySelector('[aria-live="polite"]')).toHaveTextContent('新禾文创');
    expect(screen.getByRole('button', { name: '确认付款' })).toBeEnabled();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('does not submit when the viewer lacks billing permission', async () => {
    mocks.canManageBilling = false;
    mocks.orders.mockResolvedValue({ data: [{
      id: 49, merchantOrderNo: 'COM-49', status: 'PENDING_PAYMENT',
      amount: 59, currency: 'CNY', codeUrl: 'weixin://code-49',
      expiresAt: '2099-01-01T00:00:00',
    }] });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '继续支付' })).not.toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('keeps checkout disabled on a catalog error', async () => {
    mocks.catalog.mockRejectedValue(new Error('目录服务不可用'));
    render(<CommercialPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('目录服务不可用');
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('shows a pending order continuation without automatically displaying its QR code', async () => {
    mocks.orders.mockResolvedValue({ data: [{
      id: 42,
      merchantOrderNo: 'COM-42',
      status: 'PENDING_PAYMENT',
      amount: 59,
      currency: 'CNY',
      codeUrl: 'weixin://wxpay/code-42',
      expiresAt: '2099-08-26T22:00:00',
    }] });

    render(<CommercialPage />);

    const continuePayment = await screen.findByRole('button', { name: '继续支付' });
    expect(screen.queryByTestId('qr-code')).toBeNull();

    fireEvent.click(continuePayment);

    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-42');
    const dialog = screen.getByRole('dialog', { name: '扫码支付' });
    expect(dialog).toHaveTextContent('套餐订单');
    expect(dialog).toHaveTextContent('COM-42');
    expect(dialog).toHaveTextContent('¥59.00');
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('does not offer continuation for an expired pending order', async () => {
    mocks.orders.mockResolvedValue({ data: [{
      id: 42, merchantOrderNo: 'COM-42', status: 'PENDING_PAYMENT',
      amount: 59, currency: 'CNY', codeUrl: 'weixin://expired',
      expiresAt: '2020-01-01T00:00:00',
    }] });
    render(<CommercialPage />);
    await screen.findByText('COM-42');
    expect(screen.queryByRole('button', { name: '继续支付' })).not.toBeInTheDocument();
  });

  it('rechecks a pending order expiry when continuation is clicked', async () => {
    const expiresAt = new Date(Date.now() + 1000).toISOString();
    mocks.orders.mockResolvedValue({ data: [{
      id: 42, merchantOrderNo: 'COM-42', status: 'PENDING_PAYMENT',
      amount: 59, currency: 'CNY', codeUrl: 'weixin://expired', expiresAt,
    }] });
    render(<CommercialPage />);
    const continuePayment = await screen.findByRole('button', { name: '继续支付' });
    vi.useFakeTimers();
    vi.advanceTimersByTime(2000);
    await act(async () => {
      fireEvent.click(continuePayment);
      await Promise.resolve();
    });
    expect(screen.queryByTestId('qr-code')).not.toBeInTheDocument();
    expect(mocks.messageError).toHaveBeenCalledWith(expect.stringContaining('已过期'));
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('submits once on rapid repeated confirmation', async () => {
    let finishOrder: ((value: unknown) => void) | undefined;
    mocks.create.mockImplementation(() => new Promise((resolve) => { finishOrder = resolve; }));
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    const confirm = screen.getByRole('button', { name: '确认付款' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith(10, 11);
    await act(async () => finishOrder?.({ data: {
      id: 71, merchantOrderNo: 'COM-71', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', codeUrl: 'weixin://code-71', expiresAt: '2099-01-01T00:00:00',
    } }));
  });

  it('shows a missing payment code and never retries a rejected order automatically', async () => {
    mocks.create.mockResolvedValueOnce({ data: {
      id: 72, merchantOrderNo: 'COM-72', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', expiresAt: '2099-01-01T00:00:00',
    } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await waitFor(() => expect(mocks.messageError).toHaveBeenCalledWith(expect.stringContaining('微信支付')));
    expect(screen.queryByTestId('qr-code')).not.toBeInTheDocument();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it('shows the server rejection and reloads unavailable offers without another order', async () => {
    mocks.create.mockRejectedValueOnce(new Error('套餐版本已下架'));
    mocks.catalog.mockResolvedValueOnce({ data: [{ packageId: 1, packageVersionId: 11, packageType: 'SUBSCRIPTION', periodMonths: 1, name: '专业版月卡', price: 99, currency: 'CNY', entitlements: [] }] });
    mocks.catalog.mockResolvedValueOnce({ data: [] });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await waitFor(() => expect(mocks.messageError).toHaveBeenCalledWith('套餐版本已下架'));
    await screen.findByText('当前周期暂无可售会员套餐');
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it('reads pending orders after an uncertain network failure without resubmitting', async () => {
    mocks.create.mockRejectedValueOnce(new Error('网络连接中断'));
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await waitFor(() => expect(mocks.messageError).toHaveBeenCalledWith('网络连接中断'));
    await waitFor(() => expect(mocks.orders).toHaveBeenCalledTimes(2));
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it('renders the payment QR code and stops polling after completion', async () => {
    const pendingOrder = {
      id: 41,
      merchantOrderNo: 'COM-41',
      status: 'PENDING_PAYMENT',
      amount: 99,
      currency: 'CNY',
      codeUrl: 'weixin://wxpay/code-41',
      expiresAt: '2099-08-26T22:00:00',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValue({ data: { ...pendingOrder, status: 'COMPLETED' } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-41');
    expect(within(screen.getByRole('dialog', { name: '扫码支付' })).getByText('专业版月卡')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(mocks.refresh).toHaveBeenCalledTimes(1);
    expect(mocks.refresh).toHaveBeenCalledWith(10, 41);
    expect(mocks.messageSuccess).toHaveBeenCalledWith(expect.stringContaining('支付成功'));
    await act(async () => Promise.resolve());
    expect(mocks.points).toHaveBeenCalledTimes(2);
    expect(mocks.pointTransactions).toHaveBeenCalledTimes(2);
    expect(mocks.current).toHaveBeenCalledTimes(2);
    expect(mocks.queued).toHaveBeenCalledTimes(2);
    expect(mocks.orders).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(mocks.refresh).toHaveBeenCalledTimes(1);
  });

  it('checks the server at QR expiry before reporting payment outcome', async () => {
    const pendingOrder = {
      id: 53, merchantOrderNo: 'COM-53', status: 'PENDING_PAYMENT', amount: 99,
      currency: 'CNY', codeUrl: 'weixin://code-53', expiresAt: '2026-09-03T00:00:02',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValue({ data: { ...pendingOrder, status: 'COMPLETED' } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00'));
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.refresh).toHaveBeenCalledWith(10, 53);
    expect(mocks.messageSuccess).toHaveBeenCalledWith(expect.stringContaining('支付成功'));
    expect(mocks.messageError).not.toHaveBeenCalled();
  });

  it('tracks fulfillment-pending orders until the server confirms completion', async () => {
    const pendingOrder = {
      id: 54, merchantOrderNo: 'COM-54', status: 'PENDING_PAYMENT', amount: 99,
      currency: 'CNY', codeUrl: 'weixin://code-54', expiresAt: '2099-01-01T00:00:00',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValueOnce({ data: { ...pendingOrder, status: 'ENTITLEMENT_PENDING' } });
    mocks.refresh.mockResolvedValueOnce({ data: { ...pendingOrder, status: 'COMPLETED' } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(screen.getByText('权益发放中')).toBeInTheDocument();
    expect(mocks.messageInfo).toHaveBeenCalledWith(expect.stringContaining('权益发放'));
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.refresh).toHaveBeenCalledTimes(2);
    expect(mocks.messageSuccess).toHaveBeenCalledWith(expect.stringContaining('支付成功'));
    expect(mocks.points).toHaveBeenCalledTimes(2);
  });

  it('hides an expired QR but waits for the server to close the order', async () => {
    const pendingOrder = {
      id: 55, merchantOrderNo: 'COM-55', status: 'PENDING_PAYMENT', amount: 99,
      currency: 'CNY', codeUrl: 'weixin://code-55', expiresAt: '2026-09-03T00:00:02',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValueOnce({ data: pendingOrder });
    mocks.refresh.mockResolvedValueOnce({ data: { ...pendingOrder, status: 'CLOSED' } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00'));
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(mocks.messageInfo).toHaveBeenCalledWith(expect.stringContaining('核对'));
    expect(mocks.messageError).not.toHaveBeenCalled();
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.refresh).toHaveBeenCalledTimes(2);
    expect(mocks.messageError).toHaveBeenCalledWith('订单已关闭');
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
  });

  it('keeps the selected package name while a refreshed order remains pending', async () => {
    const pendingOrder = {
      id: 44,
      merchantOrderNo: 'COM-44',
      status: 'PENDING_PAYMENT',
      amount: 99,
      currency: 'CNY',
      codeUrl: 'weixin://wxpay/code-44',
      expiresAt: '2099-08-26T22:00:00',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValue({ data: pendingOrder });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(within(screen.getByRole('dialog', { name: '扫码支付' })).getByText('专业版月卡')).toBeInTheDocument();
  });

  it('returns to package selection after a refreshed order expires', async () => {
    const pendingOrder = {
      id: 43,
      merchantOrderNo: 'COM-43',
      status: 'PENDING_PAYMENT',
      amount: 99,
      currency: 'CNY',
      codeUrl: 'weixin://wxpay/code-43',
      expiresAt: '2099-08-26T22:00:00',
    };
    mocks.create.mockResolvedValue({ data: pendingOrder });
    mocks.refresh.mockResolvedValue({ data: { ...pendingOrder, status: 'EXPIRED' } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-43');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(screen.getByRole('tab', { name: '会员套餐' })).toBeInTheDocument();
  });

  it('stops polling when payment is closed', async () => {
    mocks.create.mockResolvedValue({ data: {
      id: 50, merchantOrderNo: 'COM-50', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', codeUrl: 'weixin://code-50', expiresAt: '2099-01-01T00:00:00',
    } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    fireEvent.click(screen.getByRole('button', { name: '返回选购' }));
    await act(async () => vi.advanceTimersByTimeAsync(9000));
    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(mocks.refresh).not.toHaveBeenCalled();
  });

  it('retries a temporary status lookup failure without creating another order', async () => {
    mocks.create.mockResolvedValue({ data: {
      id: 51, merchantOrderNo: 'COM-51', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', codeUrl: 'weixin://code-51', expiresAt: '2099-01-01T00:00:00',
    } });
    mocks.refresh.mockRejectedValueOnce(new Error('查询超时'));
    mocks.refresh.mockResolvedValueOnce({ data: {
      id: 51, merchantOrderNo: 'COM-51', amount: 99, currency: 'CNY',
      status: 'COMPLETED', expiresAt: '2099-01-01T00:00:00',
    } });
    render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    await act(async () => vi.advanceTimersByTimeAsync(6000));
    expect(mocks.refresh).toHaveBeenCalledTimes(2);
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.messageSuccess).toHaveBeenCalledWith(expect.stringContaining('支付成功'));
    expect(screen.queryByTestId('qr-code')).toBeNull();
  });

  it('ignores a late catalog response from the previous team', async () => {
    let finishOldCatalog: ((value: unknown) => void) | undefined;
    mocks.catalog.mockImplementationOnce(() => new Promise((resolve) => { finishOldCatalog = resolve; }));
    mocks.catalog.mockResolvedValueOnce({ data: [{ packageId: 2, packageVersionId: 22, packageType: 'POINT_PACKAGE', name: '团队B积分包', price: 59, currency: 'CNY', entitlements: [] }] });
    const view = render(<CommercialPage />);
    await waitFor(() => expect(mocks.catalog).toHaveBeenCalledWith(10));
    mocks.tenantId = 20;
    view.rerender(<CommercialPage />);
    fireEvent.click(await screen.findByRole('tab', { name: '积分包' }));
    expect(await screen.findByRole('button', { name: '选择团队B积分包' })).toBeInTheDocument();
    await act(async () => finishOldCatalog?.({ data: [{ packageId: 1, packageVersionId: 11, packageType: 'SUBSCRIPTION', periodMonths: 1, name: '团队A月卡', price: 99, currency: 'CNY', entitlements: [] }] }));
    expect(screen.queryByText('团队A月卡')).not.toBeInTheDocument();
    expect(document.querySelector('[aria-live="polite"]')).toHaveTextContent('远山工作室');
  });

  it('ignores an old team order response after switching teams', async () => {
    let finishOldOrder: ((value: unknown) => void) | undefined;
    mocks.create.mockImplementationOnce(() => new Promise((resolve) => { finishOldOrder = resolve; }));
    const view = render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    expect(mocks.create).toHaveBeenCalledWith(10, 11);
    mocks.tenantId = 20;
    view.rerender(<CommercialPage />);
    await waitFor(() => expect(mocks.catalog).toHaveBeenCalledWith(20));
    await act(async () => finishOldOrder?.({ data: {
      id: 61, merchantOrderNo: 'COM-61', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', codeUrl: 'weixin://old-team', expiresAt: '2099-01-01T00:00:00',
    } }));
    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(document.querySelector('[aria-live="polite"]')).toHaveTextContent('远山工作室');
    expect(screen.getByRole('button', { name: '确认付款' })).toBeEnabled();
  });

  it('ignores a late payment status response after switching teams', async () => {
    let finishOldStatus: ((value: unknown) => void) | undefined;
    mocks.create.mockResolvedValue({ data: {
      id: 62, merchantOrderNo: 'COM-62', amount: 99, currency: 'CNY',
      status: 'PENDING_PAYMENT', codeUrl: 'weixin://old-team', expiresAt: '2099-01-01T00:00:00',
    } });
    mocks.refresh.mockImplementationOnce(() => new Promise((resolve) => { finishOldStatus = resolve; }));
    const view = render(<CommercialPage />);
    await screen.findByRole('button', { name: '选择专业版月卡' });
    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    await act(async () => Promise.resolve());
    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.refresh).toHaveBeenCalledWith(10, 62);
    mocks.tenantId = 20;
    view.rerender(<CommercialPage />);
    await act(async () => finishOldStatus?.({ data: {
      id: 62, merchantOrderNo: 'COM-62', amount: 99, currency: 'CNY',
      status: 'COMPLETED', expiresAt: '2099-01-01T00:00:00',
    } }));
    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
    expect(mocks.points).not.toHaveBeenCalledTimes(3);
  });
});
