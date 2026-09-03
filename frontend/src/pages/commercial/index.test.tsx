import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  catalog: vi.fn(),
  current: vi.fn(),
  queued: vi.fn(),
  grants: vi.fn(),
  orders: vi.fn(),
  points: vi.fn(),
  create: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock('@/services/account-team/auth', () => ({ getCurrentTenantId: () => 10 }));
vi.mock('@/services/account-team/points', () => ({ queryTeamPointAccount: mocks.points }));
vi.mock('./service', () => ({
  queryCommercialCatalog: mocks.catalog,
  queryCurrentSubscription: mocks.current,
  queryQueuedSubscriptions: mocks.queued,
  queryCommercialGrants: mocks.grants,
  queryActiveCommercialOrders: mocks.orders,
  createCommercialOrder: mocks.create,
  refreshCommercialOrder: mocks.refresh,
}));
vi.mock('@umijs/max', () => ({ history: { back: vi.fn(), push: vi.fn() }, useAccess: () => ({ canManageBilling: true }) }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }) },
  Button: ({ children, onClick, disabled }: any) => <button type="button" onClick={onClick} disabled={disabled}>{children}</button>,
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
  Tabs: ({ items, onChange }: any) => <div>{items.map((item: any) => <button type="button" key={item.key} onClick={() => onChange(item.key)}>{item.label}</button>)}</div>,
  Typography: { Text: ({ children }: any) => <span>{children}</span>, Title: ({ children }: any) => <h1>{children}</h1>, Paragraph: ({ children }: any) => <p>{children}</p> },
}));
vi.mock('@ant-design/icons', () => ({ CheckOutlined: () => null, LeftOutlined: () => null, LockOutlined: () => null, WalletOutlined: () => null }));

import CommercialPage from './index';

describe('CommercialPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.catalog.mockResolvedValue({ data: [{ packageId: 1, packageVersionId: 11, packageType: 'SUBSCRIPTION', name: '专业版月卡', price: 99, currency: 'CNY', entitlements: [{ type: 'PERIODIC_POINTS', value: 3000 }] }] });
    mocks.current.mockResolvedValue({ data: { id: 21, status: 'ACTIVE', startsAt: '2026-08-01T00:00:00', endsAt: '2026-09-01T00:00:00', snapshotJson: '{"name":"专业版月卡"}' } });
    mocks.queued.mockResolvedValue({ data: [{ id: 22, status: 'QUEUED', startsAt: '2026-09-01T00:00:00', endsAt: '2026-12-01T00:00:00', snapshotJson: '{"name":"专业版季卡"}' }] });
    mocks.grants.mockResolvedValue({ data: [{ id: 31, entitlementType: 'PERIODIC_POINTS', amount: 3000, status: 'GRANTED', grantedAt: '2026-08-01T00:00:00' }] });
    mocks.orders.mockResolvedValue({ data: [] });
    mocks.points.mockResolvedValue({ data: { balance: 2140 } });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads the selected team commercial overview from APIs', async () => {
    render(<CommercialPage />);

    await waitFor(() => expect(mocks.catalog).toHaveBeenCalledWith(10));
    expect(await screen.findAllByText('专业版月卡')).not.toHaveLength(0);
    expect(screen.getByText('专业版季卡', { exact: false })).toBeInTheDocument();
    expect(screen.getAllByText('3,000', { exact: false }).length).toBeGreaterThan(0);
    expect(screen.getByText(/2140/)).toBeInTheDocument();
    expect(mocks.grants).toHaveBeenCalledWith(10);
  });

  it('opens the package selection dialog from a purchase entry', async () => {
    mocks.catalog.mockResolvedValue({ data: [{ packageId: 2, packageVersionId: 12, packageType: 'POINT_PACKAGE', name: '积分增强包', price: 59, currency: 'CNY', entitlements: [{ type: 'ONE_TIME_POINTS', value: 2000 }] }] });

    render(<CommercialPage />);

    fireEvent.click(await screen.findByRole('button', { name: '购买套餐' }));

    expect(screen.getByRole('dialog', { name: '选择套餐' })).toBeInTheDocument();
    expect(screen.getByText('积分增强包')).toBeInTheDocument();
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
    await screen.findAllByText('专业版月卡');

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '购买套餐' }));
    fireEvent.click(screen.getByRole('button', { name: '月度会员' }));
    fireEvent.click(screen.getByRole('button', { name: '订购专业版月卡' }));
    await act(async () => Promise.resolve());
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-41');
    expect(within(screen.getByRole('dialog', { name: '扫码支付' })).getByText('专业版月卡')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(mocks.refresh).toHaveBeenCalledTimes(1);
    expect(mocks.refresh).toHaveBeenCalledWith(10, 41);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
    });
    expect(mocks.refresh).toHaveBeenCalledTimes(1);
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
    await screen.findAllByText('专业版月卡');

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '购买套餐' }));
    fireEvent.click(screen.getByRole('button', { name: '月度会员' }));
    fireEvent.click(screen.getByRole('button', { name: '订购专业版月卡' }));
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
    await screen.findAllByText('专业版月卡');

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '购买套餐' }));
    fireEvent.click(screen.getByRole('button', { name: '月度会员' }));
    fireEvent.click(screen.getByRole('button', { name: '订购专业版月卡' }));
    await act(async () => Promise.resolve());
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-43');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(screen.queryByTestId('qr-code')).toBeNull();
    expect(screen.getByRole('dialog', { name: '选择套餐' })).toBeInTheDocument();
  });
});
