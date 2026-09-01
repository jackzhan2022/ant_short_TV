import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: {
    canViewPlatformTenants: true,
    canEditPlatformTenantStatus: true,
  },
  queryTenants: vi.fn(),
  getTenant: vi.fn(),
  updateStatus: vi.fn(),
  success: vi.fn(),
  reload: vi.fn(),
}));

vi.mock('@umijs/max', () => ({ useAccess: () => mocks.access }));
vi.mock('./service', () => ({
  queryPlatformTenants: mocks.queryTenants,
  getPlatformTenant: mocks.getTenant,
  updatePlatformTenantStatus: mocks.updateStatus,
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ actionRef, columns, request }: any) => {
    const [data, setData] = useState<any[]>([]);
    useEffect(() => {
      if (actionRef) actionRef.current = { reload: mocks.reload };
      void request({
        current: 2,
        pageSize: 20,
        keyword: 'alpha',
        status: 'ACTIVE',
        packageType: 'SUBSCRIPTION',
      }).then((result: any) => setData(result.data));
    }, []);
    return <div>{data.map((record) => <div key={record.id}>{columns.map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>;
  },
}));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: mocks.success } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Descriptions: ({ items }: any) => <dl>{items.map((item: any) => <div key={item.key}><dt>{item.label}</dt><dd>{item.children}</dd></div>)}</dl>,
  Divider: ({ children }: any) => <h3>{children}</h3>,
  Drawer: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children, onCancel, onConfirm, title }: any) => <span>{children}<button type="button" onClick={onConfirm}>{title}</button><button type="button" onClick={onCancel}>取消操作</button></span>,
  Space: ({ children }: any) => <div>{children}</div>,
  Statistic: ({ title, value }: any) => <div>{title}：{value}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));

import PlatformTenantManagementPage from './index';

const tenant = {
  id: 9,
  code: 'TENANT-9',
  name: 'Alpha Studio',
  type: 'STUDIO',
  status: 'ACTIVE',
  owner: { memberId: 1, userId: 2, nickname: 'Alice', mobile: '13800000000' },
  activeMemberCount: 3,
  pointBalance: 0,
  currentPackage: null,
  createdAt: '2026-09-01T08:00:00',
};

describe('PlatformTenantManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canViewPlatformTenants = true;
    mocks.access.canEditPlatformTenantStatus = true;
    mocks.queryTenants.mockResolvedValue({ data: { records: [tenant], total: 1, current: 2, pageSize: 20 } });
    mocks.getTenant.mockResolvedValue({ data: { ...tenant, description: '运营租户', queuedPackages: [{ subscriptionId: 11, name: '专业版', packageType: 'SUBSCRIPTION', subscriptionStatus: 'QUEUED', startsAt: '2026-10-01T00:00:00', endsAt: '2026-11-01T00:00:00' }] } });
    mocks.updateStatus.mockResolvedValue({ data: { ...tenant, status: 'DISABLED' } });
  });

  it('loads filters and displays zero balance and missing package', async () => {
    render(<PlatformTenantManagementPage />);

    expect(await screen.findByText('Alpha Studio')).toBeInTheDocument();
    expect(mocks.queryTenants).toHaveBeenCalledWith(expect.objectContaining({
      current: 2,
      pageSize: 20,
      keyword: 'alpha',
      status: 'ACTIVE',
      packageType: 'SUBSCRIPTION',
    }));
    expect(screen.getByText('暂无套餐')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('shows detail and refreshes list and drawer after confirmed disable', async () => {
    render(<PlatformTenantManagementPage />);
    await screen.findByText('Alpha Studio');
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));

    expect(await screen.findByText('运营租户')).toBeInTheDocument();
    expect(screen.getByText('专业版')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /确认停用租户/ }));

    await waitFor(() => expect(mocks.updateStatus).toHaveBeenCalledWith(9, 'DISABLED'));
    expect(mocks.success).toHaveBeenCalledWith('租户已停用');
    expect(mocks.reload).toHaveBeenCalled();
    expect(mocks.getTenant).toHaveBeenCalledTimes(2);
  });

  it('hides status action without edit permission', async () => {
    mocks.access.canEditPlatformTenantStatus = false;
    render(<PlatformTenantManagementPage />);
    await screen.findByText('Alpha Studio');

    expect(screen.queryByRole('button', { name: /确认停用租户/ })).not.toBeInTheDocument();
  });

  it('does not update status when confirmation is cancelled', async () => {
    render(<PlatformTenantManagementPage />);
    await screen.findByText('Alpha Studio');

    fireEvent.click(screen.getByRole('button', { name: '取消操作' }));

    expect(mocks.updateStatus).not.toHaveBeenCalled();
    expect(mocks.reload).not.toHaveBeenCalled();
  });
});
