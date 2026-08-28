import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  ModalForm: ({ trigger }: any) => <div>{trigger}</div>,
  ProFormDateTimePicker: () => null,
  ProFormDigit: () => null,
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: () => null,
  ProFormText: () => null,
}));

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: vi.fn() } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Drawer: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children, onConfirm }: any) => <span onClick={onConfirm}>{children}</span>,
  Space: ({ children }: any) => <div>{children}</div>,
  Table: ({ columns, dataSource }: any) => <div>{dataSource.map((record: any) => <div key={record.id ?? record.versionId}>{columns.map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));

import CommercialPackageManagementPage from './index';

describe('CommercialPackageManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canEditCommercialPackages = true;
    mocks.listPackages.mockResolvedValue({ data: [{ id: 1, code: 'PRO_MONTH', packageType: 'SUBSCRIPTION', status: 'ACTIVE' }] });
    mocks.listPackageVersions.mockResolvedValue({ data: [{ packageId: 1, versionId: 11, versionNo: 1, name: '专业版月卡', status: 'DRAFT', price: 99, currency: 'CNY', effectiveFrom: '2026-09-01T00:00:00', entitlements: [{ type: 'GLOBAL_DISCOUNT', value: 0.9 }] }] });
  });

  it('loads packages and displays versioned entitlements', async () => {
    render(<CommercialPackageManagementPage />);
    expect(await screen.findByText('PRO_MONTH')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '版本历史' }));
    await waitFor(() => expect(mocks.listPackageVersions).toHaveBeenCalledWith(1));
    expect(await screen.findByText('专业版月卡')).toBeInTheDocument();
    expect(screen.getByText('全局折扣：0.9')).toBeInTheDocument();
  });

  it('shows lifecycle controls only with package edit permission', async () => {
    const first = render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');
    fireEvent.click(screen.getByRole('button', { name: '版本历史' }));
    expect(await screen.findByRole('button', { name: '发布' })).toBeInTheDocument();

    first.unmount();
    mocks.access.canEditCommercialPackages = false;
    render(<CommercialPackageManagementPage />);
    await screen.findByText('PRO_MONTH');
    fireEvent.click(screen.getByRole('button', { name: '版本历史' }));
    await screen.findByText('专业版月卡');
    expect(screen.queryByRole('button', { name: '发布' })).not.toBeInTheDocument();
  });
});
