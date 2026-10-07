import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CommercialOrder } from './service';
import PaymentModal from './PaymentModal';

vi.mock('antd', () => ({
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Modal: ({ children, open, title }: any) => open ? <section role="dialog" aria-label={title}>{children}</section> : null,
  QRCode: ({ value }: any) => <div data-testid="qr-code">{value}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
    Paragraph: ({ children }: any) => <p>{children}</p>,
  },
}));

const order: CommercialOrder = {
  id: 42,
  merchantOrderNo: 'COM-42',
  amount: 59,
  currency: 'CNY',
  status: 'PENDING_PAYMENT',
  expiresAt: '2099-08-26T22:00:00',
  codeUrl: 'weixin://wxpay/code-42',
};

describe('PaymentModal', () => {
  afterEach(() => vi.useRealTimers());

  it('shows the created order amount, expiry and real QR value', () => {
    render(<PaymentModal payment={{ ...order, packageName: '积分增强包' }} onClose={vi.fn()} />);
    const dialog = screen.getByRole('dialog', { name: '扫码支付' });
    expect(dialog).toHaveTextContent('积分增强包');
    expect(dialog).toHaveTextContent('COM-42');
    expect(dialog).toHaveTextContent('¥59.00');
    expect(dialog).toHaveTextContent('2099');
    expect(screen.getByTestId('qr-code')).toHaveTextContent('weixin://wxpay/code-42');
  });

  it('uses a neutral name for an order resumed without a package snapshot', () => {
    const onClose = vi.fn();
    render(<PaymentModal payment={order} onClose={onClose} />);
    expect(screen.getByRole('dialog', { name: '扫码支付' })).toHaveTextContent('套餐订单');
    fireEvent.click(screen.getByRole('button', { name: '返回选购' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('updates remaining time and clears the payment view on close', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00'));
    const view = render(<PaymentModal payment={{ ...order, expiresAt: '2026-09-03T00:05:40' }} onClose={vi.fn()} />);
    expect(screen.getByText(/剩余 5 分 40 秒/)).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(screen.getByText(/剩余 5 分 39 秒/)).toBeInTheDocument();
    view.rerender(<PaymentModal payment={undefined} onClose={vi.fn()} />);
    expect(screen.queryByTestId('qr-code')).not.toBeInTheDocument();
  });

  it('hides the QR when its local expiry passes even if status lookup is unavailable', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T00:00:00'));
    render(<PaymentModal payment={{ ...order, expiresAt: '2026-09-03T00:00:02' }} onClose={vi.fn()} />);
    expect(screen.getByTestId('qr-code')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(2000));
    expect(screen.queryByTestId('qr-code')).not.toBeInTheDocument();
  });
});
