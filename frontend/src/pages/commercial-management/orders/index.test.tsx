import { fireEvent, render, screen, within } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canViewCommercialOrders: true },
  listOrders: vi.fn(),
  getOrder: vi.fn(),
}));

vi.mock('@umijs/max', async () => {
  const actual = await vi.importActual<typeof import('@umijs/max')>('@umijs/max');
  return { ...actual, useAccess: () => mocks.access };
});

vi.mock('./service', () => ({
  getPlatformCommercialOrder: mocks.getOrder,
  queryPlatformCommercialOrders: mocks.listOrders,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ columns = [], request }: any) => {
    const [data, setData] = useState<any[]>([]);
    useEffect(() => { void request({ current: 1, pageSize: 20 }).then((response: any) => setData(response.data)); }, [request]);
    return <div>{columns.filter((column: any) => column.hideInTable).map((column: any) => <span key={column.dataIndex}>{column.title}</span>)}{data.map((record: any) => <div key={record.id}>{columns.filter((column: any) => !column.hideInTable).map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>;
  },
}));

vi.mock('antd', () => ({
  Descriptions: ({ items }: any) => <div>{items.map((item: any) => <span key={item.key}>{item.label}：{item.children}</span>)}</div>,
  Drawer: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
  App: { useApp: () => ({ message: { error: vi.fn() } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
}));

import PlatformCommercialOrderPage from './index';

describe('PlatformCommercialOrderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listOrders.mockResolvedValue({ data: {
      records: [{ id: 1, merchantOrderNo: 'COM202609040001', tenantId: 2, tenantName: '新禾文创', tenantCode: 'TENANT01', packageName: '专业版月卡', packageVersionNo: 2, packageType: 'SUBSCRIPTION', amount: 99, currency: 'CNY', status: 'COMPLETED', createdAt: '2026-09-04T10:00:00', paidAt: '2026-09-04T10:01:00', payment: { provider: 'WECHAT_NATIVE', providerTradeNo: 'WX-1001', status: 'SUCCESS', paidAt: '2026-09-04T10:01:00' } }], total: 1, current: 1, pageSize: 20,
    } });
    mocks.getOrder.mockResolvedValue({ data: { id: 1, merchantOrderNo: 'COM202609040001', tenantId: 2, tenantName: '新禾文创', tenantCode: 'TENANT01', packageName: '专业版月卡', packageVersionNo: 2, packageType: 'SUBSCRIPTION', amount: 99, currency: 'CNY', status: 'COMPLETED', expiresAt: '2026-09-04T10:30:00', createdAt: '2026-09-04T10:00:00', payment: { provider: 'WECHAT_NATIVE', providerTradeNo: 'WX-1001', status: 'SUCCESS', paidAt: '2026-09-04T10:01:00' } } });
  });

  it('uses tenant-style filters and opens order payment details', async () => {
    render(<PlatformCommercialOrderPage />);

    expect(await screen.findByText('订单号或租户名称')).toBeInTheDocument();
    expect(screen.getByText('订单状态')).toBeInTheDocument();
    expect(screen.getByText('套餐类型')).toBeInTheDocument();
    expect(screen.getByText('COM202609040001')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));

    expect(await screen.findByRole('region', { name: '订单详情' })).toBeInTheDocument();
    expect(screen.getByText('微信支付单号：WX-1001')).toBeInTheDocument();
  });

  it('highlights the amount and order status in the read-only detail summary', async () => {
    render(<PlatformCommercialOrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));

    const summary = await screen.findByRole('region', { name: '订单摘要' });
    expect(within(summary).getByText('实付金额')).toBeInTheDocument();
    expect(summary).toHaveTextContent('99CNY');
    expect(within(summary).getByText('已完成')).toBeInTheDocument();
  });

  it('renders an absent payment time as a dash', async () => {
    mocks.listOrders.mockResolvedValue({ data: {
      records: [{ id: 2, merchantOrderNo: 'COM-CLOSED', tenantId: 2, tenantName: '新禾文创', tenantCode: 'TENANT01', packageName: '积分包', packageVersionNo: 1, packageType: 'POINT_PACKAGE', amount: 99, currency: 'CNY', status: 'CLOSED', createdAt: '2026-09-04T10:00:00', paidAt: '-' }], total: 1, current: 1, pageSize: 20,
    } });

    render(<PlatformCommercialOrderPage />);

    expect(await screen.findByText('COM-CLOSED')).toBeInTheDocument();
    expect(screen.queryByText('Invalid Date')).not.toBeInTheDocument();
  });
});
