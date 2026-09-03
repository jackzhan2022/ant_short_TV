import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('antd', () => ({
  Button: ({ children, onClick, disabled }: any) => <button type="button" disabled={disabled} onClick={onClick}>{children}</button>,
  Card: ({ children }: any) => <section>{children}</section>,
  Modal: ({ children, open, title }: any) => open ? <section role="dialog" aria-label={title}>{children}</section> : null,
  QRCode: ({ value }: any) => <div data-testid="qr-code">{value}</div>,
  Tabs: ({ activeKey, items, onChange }: any) => <div>{items.map((item: any) => <button key={item.key} type="button" aria-pressed={item.key === activeKey} onClick={() => onChange(item.key)}>{item.label}</button>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h1>{children}</h1>,
    Paragraph: ({ children }: any) => <p>{children}</p>,
  },
}));

import PurchaseModal from './PurchaseModal';
import type { CommercialCatalogItem, CommercialOrder } from './service';

const pointPackage: CommercialCatalogItem = {
  packageId: 1,
  packageVersionId: 11,
  code: 'POINTS_PLUS',
  packageType: 'POINT_PACKAGE',
  name: '积分增强包',
  description: '适合短期补充团队积分',
  price: 59,
  listPrice: 69,
  currency: 'CNY',
  entitlements: [{ type: 'ONE_TIME_POINTS', value: 2000 }],
};

const halfYearPackage: CommercialCatalogItem = {
  ...pointPackage,
  packageId: 2,
  packageVersionId: 12,
  code: 'HALF_YEAR',
  packageType: 'SUBSCRIPTION',
  name: '专业版半年会员',
  billingPeriod: 'HALF_YEAR',
  periodMonths: 6,
};

const halfYearBillingPackage: CommercialCatalogItem = {
  ...halfYearPackage,
  packageId: 3,
  packageVersionId: 13,
  name: '专业版半年会员（周期标识）',
  periodMonths: undefined,
};

describe('PurchaseModal', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('defaults to the points tab and purchases the selected catalog item', () => {
    const onPurchase = vi.fn();
    render(<PurchaseModal open catalog={[pointPackage]} canManageBilling onClose={vi.fn()} onPurchase={onPurchase} />);

    expect(screen.getByRole('button', { name: '积分包' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '订购积分增强包' }));
    expect(onPurchase).toHaveBeenCalledWith(pointPackage);
  });

  it('shows a payment panel for a pending order with a QR code', () => {
    const payment: CommercialOrder & { packageName: string } = {
      id: 42,
      merchantOrderNo: 'COM-42',
      packageName: '积分增强包',
      amount: 59,
      currency: 'CNY',
      status: 'PENDING_PAYMENT',
      expiresAt: '2099-08-26T22:00:00',
      codeUrl: 'weixin://wxpay/code-42',
    };
    render(<PurchaseModal open catalog={[pointPackage]} payment={payment} canManageBilling onClose={vi.fn()} onPurchase={vi.fn()} />);

    expect(screen.getByText('积分增强包')).toBeInTheDocument();
    expect(screen.getByText(/¥59/)).toBeInTheDocument();
    expect(screen.getByText(/请在.*前完成支付/)).toBeInTheDocument();
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-42');
    expect(screen.getByRole('button', { name: '返回选择套餐' })).toBeInTheDocument();
  });

  it('shows the remaining payment time and updates it every second', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00'));
    const payment: CommercialOrder = {
      id: 43,
      merchantOrderNo: 'COM-43',
      amount: 59,
      currency: 'CNY',
      status: 'PENDING_PAYMENT',
      expiresAt: '2026-09-03T00:05:40',
      codeUrl: 'weixin://wxpay/code-43',
    };
    render(<PurchaseModal open catalog={[pointPackage]} payment={payment} canManageBilling onClose={vi.fn()} onPurchase={vi.fn()} />);

    expect(screen.getByText(/剩余 5 分 40 秒/)).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText(/剩余 5 分 39 秒/)).toBeInTheDocument();
  });

  it('shows six-month subscriptions only in the half-year tab', () => {
    render(<PurchaseModal open catalog={[pointPackage, halfYearPackage]} canManageBilling onClose={vi.fn()} onPurchase={vi.fn()} />);

    expect(screen.queryByText('专业版半年会员')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '半年会员' }));
    expect(screen.getByText('专业版半年会员')).toBeInTheDocument();
  });

  it('uses HALF_YEAR billing period exclusively for the half-year tab', () => {
    render(<PurchaseModal open catalog={[halfYearBillingPackage]} canManageBilling onClose={vi.fn()} onPurchase={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '月度会员' }));
    expect(screen.queryByText('专业版半年会员（周期标识）')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '半年会员' }));
    expect(screen.getByText('专业版半年会员（周期标识）')).toBeInTheDocument();
  });
});
