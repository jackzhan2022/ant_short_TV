import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canEditCommercialPackages: true },
  listPackages: vi.fn(),
  listPackageVersions: vi.fn(),
  publishPackageVersion: vi.fn(),
  unpublishPackageVersion: vi.fn(),
}));

vi.mock('@umijs/max', async () => {
  const actual = await vi.importActual<typeof import('@umijs/max')>('@umijs/max');
  return { ...actual, useAccess: () => mocks.access };
});

vi.mock('./service', () => ({
  createCommercialPackageDraft: vi.fn(),
  listCommercialPackages: mocks.listPackages,
  listCommercialPackageVersions: mocks.listPackageVersions,
  publishCommercialPackageVersion: mocks.publishPackageVersion,
  unpublishCommercialPackageVersion: mocks.unpublishPackageVersion,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ModalForm: ({ children, initialValues, open, title, trigger }: any) => <div>{trigger}{open && <section aria-label={title}><output data-testid="draft-package-code">{initialValues?.code}</output>{children}</section>}</div>,
  ProFormDateTimePicker: () => null,
  ProFormDigit: () => null,
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: () => null,
  ProFormText: () => null,
  ProTable: ({ columns = [], request, toolBarRender }: any) => {
    const [data, setData] = useState<any[]>([]);
    useEffect(() => { void request({ current: 1, pageSize: 20 }).then((response: any) => setData(response.data)); }, [request]);
    return <div>{columns.filter((column: any) => column.hideInTable).map((column: any) => <span key={column.dataIndex}>{column.title}</span>)}{toolBarRender?.()}{data.map((record: any) => <div key={record.id}>{columns.filter((column: any) => !column.hideInTable).map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>;
  },
}));

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: vi.fn() } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Drawer: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children, onConfirm }: any) => <span onClick={onConfirm}>{children}</span>,
  Space: ({ children }: any) => <div>{children}</div>,
  Statistic: ({ title, value }: any) => <span>{title}：{value}</span>,
  Input: ({ placeholder, value, onChange }: any) => <input placeholder={placeholder} value={value} onChange={onChange} />,
  Select: () => <select />,
  Table: ({ columns, dataSource, title }: any) => <div>{title?.()} {dataSource.map((record: any) => <div key={record.id ?? record.versionId}>{columns.map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));

import CommercialPackageManagementPage from './index';

describe('CommercialPackageManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canEditCommercialPackages = true;
    mocks.listPackages.mockResolvedValue({ data: [{ id: 1, code: 'PRO_MONTH', packageType: 'SUBSCRIPTION', status: 'ACTIVE', latestVersionNo: 1, latestName: '专业版月卡', latestPrice: 99, latestCurrency: 'CNY', latestStatus: 'DRAFT', latestEntitlements: [{ type: 'GLOBAL_DISCOUNT', value: 0.9 }] }] });
    mocks.listPackageVersions.mockResolvedValue({ data: [{ packageId: 1, versionId: 11, versionNo: 1, name: '专业版月卡', status: 'DRAFT', price: 99, currency: 'CNY', effectiveFrom: '2026-09-01T00:00:00', entitlements: [{ type: 'GLOBAL_DISCOUNT', value: 0.9 }] }] });
  });

  it('loads packages and displays versioned entitlements', async () => {
    render(<CommercialPackageManagementPage />);
    expect(await screen.findByText('PRO_MONTH')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看版本' }));
    await waitFor(() => expect(mocks.listPackageVersions).toHaveBeenCalledWith(1));
    expect((await screen.findAllByText('专业版月卡')).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('全局折扣：0.9')).toBeInTheDocument();
  });

  it('shows the latest commercial information in the package list', async () => {
    render(<CommercialPackageManagementPage />);

    expect(await screen.findByText('专业版月卡')).toBeInTheDocument();
    expect(screen.getByText('99 CNY')).toBeInTheDocument();
    expect(screen.getByText('V1')).toBeInTheDocument();
  });

  it('keeps the package list free of top-level summary statistics', async () => {
    render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');

    expect(screen.queryByText(/套餐总数/)).not.toBeInTheDocument();
  });

  it('uses the tenant-style labelled query form', async () => {
    render(<CommercialPackageManagementPage />);

    expect(await screen.findByText('套餐名称或编码')).toBeInTheDocument();
    expect(screen.getByText('套餐类型')).toBeInTheDocument();
    expect(screen.getByText('销售状态')).toBeInTheDocument();
  });

  it('opens a version draft with the selected package code', async () => {
    render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');

    fireEvent.click(screen.getByRole('button', { name: '新增版本' }));

    expect(await screen.findByRole('region', { name: '新增版本草稿' })).toBeInTheDocument();
    expect(screen.getByTestId('draft-package-code')).toHaveTextContent('PRO_MONTH');
  });

  it('shows lifecycle controls only with package edit permission', async () => {
    const first = render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');
    fireEvent.click(screen.getByRole('button', { name: '查看版本' }));
    expect(await screen.findByRole('button', { name: '发布' })).toBeInTheDocument();

    first.unmount();
    mocks.access.canEditCommercialPackages = false;
    render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');
    fireEvent.click(screen.getByRole('button', { name: '查看版本' }));
    await screen.findByText('专业版月卡');
    expect(screen.queryByRole('button', { name: '发布' })).not.toBeInTheDocument();
  });
});
