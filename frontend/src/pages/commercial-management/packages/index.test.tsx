import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canEditCommercialPackages: true },
  listPackages: vi.fn(),
  listPackageVersions: vi.fn(),
  publishPackageVersion: vi.fn(),
  unpublishPackageVersion: vi.fn(),
  listEntitlements: vi.fn(),
  createEntitlement: vi.fn(),
  updateEntitlement: vi.fn(),
  enableEntitlement: vi.fn(),
  disableEntitlement: vi.fn(),
  tableReload: vi.fn(),
  messageSuccess: vi.fn(),
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
  listCommercialEntitlements: mocks.listEntitlements,
  createDisplayEntitlement: mocks.createEntitlement,
  updateDisplayEntitlement: mocks.updateEntitlement,
  enableDisplayEntitlement: mocks.enableEntitlement,
  disableDisplayEntitlement: mocks.disableEntitlement,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ModalForm: ({ children, initialValues, onFinish, open, title, trigger }: any) => <div>{trigger}{open && <section aria-label={title}><output data-testid="draft-package-code">{initialValues?.code}</output><output data-testid="entitlement-name">{initialValues?.name}</output>{children}<button type="button" onClick={() => void onFinish?.({ name: '测试展示权益', description: '展示说明', sortOrder: 50 })}>提交{title}</button></section>}</div>,
  ProFormDateTimePicker: () => null,
  ProFormDependency: ({ children }: any) => children({ type: 'ONE_TIME_POINTS' }),
  ProFormDigit: () => null,
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: () => null,
  ProFormText: ({ fieldProps, label, rules }: any) => label ? <label>{label}<input aria-label={label} maxLength={fieldProps?.maxLength} required={rules?.some((rule: any) => rule.required)} /></label> : null,
  ProTable: ({ actionRef, columns = [], dataSource, request, toolBarRender }: any) => {
    const [data, setData] = useState<any[]>(dataSource ?? []);
    if (actionRef) actionRef.current = { reload: mocks.tableReload };
    useEffect(() => {
      if (dataSource !== undefined) setData(dataSource);
      else void request({ current: 1, pageSize: 20 }).then((response: any) => setData(response.data));
    }, [dataSource, request]);
    return <div>{columns.map((column: any) => <span key={`header-${column.hideInTable ? 'query' : 'table'}-${column.dataIndex ?? column.title}`}>{column.title}</span>)}{typeof toolBarRender === 'function' ? toolBarRender() : null}{data.map((record: any) => <div key={record.id}>{columns.filter((column: any) => !column.hideInTable).map((column: any) => <span key={`cell-${column.dataIndex ?? column.title}`}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>;
  },
}));

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: mocks.messageSuccess } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Drawer: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children, onConfirm }: any) => <span onClick={onConfirm}>{children}</span>,
  Space: ({ children }: any) => <div>{children}</div>,
  Statistic: ({ title, value }: any) => <span>{title}：{value}</span>,
  Input: ({ placeholder, value, onChange }: any) => <input placeholder={placeholder} value={value} onChange={onChange} />,
  Select: () => <select />,
  Table: ({ columns, dataSource, title }: any) => <div>{title?.()} {dataSource.map((record: any) => <div key={record.id ?? record.versionId}>{columns.map((column: any, index: number) => <span key={column.dataIndex ?? column.title ?? index}>{column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}</span>)}</div>)}</div>,
  Tabs: ({ activeKey, items, onChange }: any) => <div>{items.map((item: any) => <button key={item.key} type="button" aria-pressed={item.key === activeKey} onClick={() => onChange(item.key)}>{item.label}</button>)}{items.find((item: any) => item.key === activeKey)?.children}</div>,
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
    mocks.listEntitlements.mockResolvedValue({ data: [
      { id: 1, code: 'ONE_TIME_POINTS', name: '一次性积分', category: 'SYSTEM', status: 'ACTIVE', sortOrder: 10 },
      { id: 4, code: 'DISPLAY_ALL_MODELS', name: '使用全部模型', description: '展示文案', category: 'DISPLAY', status: 'ACTIVE', sortOrder: 40 },
      { id: 5, code: 'DISPLAY_PRIORITY', name: '优先体验', category: 'DISPLAY', status: 'INACTIVE', sortOrder: 50 },
    ] });
    mocks.createEntitlement.mockResolvedValue({ data: {} });
    mocks.updateEntitlement.mockResolvedValue({ data: {} });
    mocks.enableEntitlement.mockResolvedValue({ data: {} });
    mocks.disableEntitlement.mockResolvedValue({ data: {} });
  });

  it('uses package and entitlement management tabs', async () => {
    render(<CommercialPackageManagementPage />);

    expect(screen.getByRole('button', { name: '套餐列表' })).toHaveAttribute('aria-pressed', 'true');
    await screen.findByText('PRO_MONTH');
    fireEvent.click(screen.getByRole('button', { name: '权益管理' }));

    expect(screen.getByRole('button', { name: '权益管理' })).toHaveAttribute('aria-pressed', 'true');
    expect(await screen.findByText('一次性积分')).toBeInTheDocument();
  });

  it('protects system entitlements and exposes display entitlement actions', async () => {
    render(<CommercialPackageManagementPage />);
    fireEvent.click(screen.getByRole('button', { name: '权益管理' }));

    expect(await screen.findByText('系统预置')).toBeInTheDocument();
    expect(screen.getByText('更新时间')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增展示权益' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '编辑' })).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '停用' }));
    await waitFor(() => expect(mocks.disableEntitlement).toHaveBeenCalledWith(4));
    fireEvent.click(screen.getByRole('button', { name: '启用' }));
    await waitFor(() => expect(mocks.enableEntitlement).toHaveBeenCalledWith(5));
  });

  it('opens create and edit forms for display entitlements', async () => {
    render(<CommercialPackageManagementPage />);
    fireEvent.click(screen.getByRole('button', { name: '权益管理' }));
    await screen.findByText('使用全部模型');

    fireEvent.click(screen.getByRole('button', { name: '新增展示权益' }));
    expect(screen.getByRole('region', { name: '新增展示权益' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '权益名称' })).toBeRequired();
    expect(screen.getByRole('textbox', { name: '权益名称' })).toHaveAttribute('maxLength', '128');
    expect(screen.getByRole('textbox', { name: '说明' })).toHaveAttribute('maxLength', '500');
    fireEvent.click(screen.getByRole('button', { name: '提交新增展示权益' }));
    await waitFor(() => expect(mocks.createEntitlement).toHaveBeenCalledWith({ name: '测试展示权益', description: '展示说明', sortOrder: 50 }));
    fireEvent.click(screen.getAllByRole('button', { name: '编辑' })[0]);
    expect(screen.getByRole('region', { name: '编辑展示权益' })).toBeInTheDocument();
    expect(screen.getByTestId('entitlement-name')).toHaveTextContent('使用全部模型');
    fireEvent.click(screen.getByRole('button', { name: '提交编辑展示权益' }));
    await waitFor(() => expect(mocks.updateEntitlement).toHaveBeenCalledWith(4, { name: '测试展示权益', description: '展示说明', sortOrder: 50 }));
  });

  it('refreshes the package-form catalog after an entitlement mutation', async () => {
    render(<CommercialPackageManagementPage />);
    await waitFor(() => expect(mocks.listEntitlements).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '权益管理' }));
    await screen.findByText('使用全部模型');

    fireEvent.click(screen.getByRole('button', { name: '停用' }));

    await waitFor(() => expect(mocks.messageSuccess).toHaveBeenCalledWith('展示权益已停用'));
    expect(mocks.listEntitlements).toHaveBeenCalledTimes(2);
  });

  it('hides entitlement write actions without package edit permission', async () => {
    mocks.access.canEditCommercialPackages = false;
    render(<CommercialPackageManagementPage />);
    fireEvent.click(screen.getByRole('button', { name: '权益管理' }));
    await screen.findByText('使用全部模型');

    expect(screen.queryByRole('button', { name: '新增展示权益' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停用' })).not.toBeInTheDocument();
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
    expect(screen.getAllByText('销售状态')).toHaveLength(2);
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
