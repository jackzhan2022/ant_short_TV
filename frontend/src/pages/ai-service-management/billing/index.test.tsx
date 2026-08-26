import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canPublishModelBilling: true },
  queryPlatformModels: vi.fn(),
  billingHistory: vi.fn(),
  publishModelPrice: vi.fn(),
  publishPointPrice: vi.fn(),
  revokeCostPrice: vi.fn(),
  revokePointPrice: vi.fn(),
}));

vi.mock('@umijs/max', async () => {
  const actual = await vi.importActual<typeof import('@umijs/max')>('@umijs/max');
  return { ...actual, useAccess: () => mocks.access };
});

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ModalForm: ({ children, trigger }: any) => (
    <section>
      {trigger}
      <div>{children}</div>
    </section>
  ),
  ProFormDateTimePicker: ({ label, name, rules }: any) => (
    <label>{label}<input aria-label={label} data-name={name} required={rules?.some((rule: any) => rule.required)} /></label>
  ),
  ProFormDigit: ({ label, min, name, rules }: any) => (
    <label>{label}<input aria-label={label} data-name={name} min={min} required={rules?.some((rule: any) => rule.required)} /></label>
  ),
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: ({ label, name, rules }: any) => (
    <label>{label}<select aria-label={label} data-name={name} required={rules?.some((rule: any) => rule.required)} /></label>
  ),
  ProFormText: ({ label, name, rules }: any) => (
    <label>{label}<input aria-label={label} data-name={name} required={rules?.some((rule: any) => rule.required)} /></label>
  ),
}));

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: vi.fn() } }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children, onConfirm }: any) => <span onClick={onConfirm}>{children}</span>,
  Select: ({ options, onChange }: any) => (
    <select aria-label="选择已启用模型" onChange={(event) => onChange(Number(event.target.value))}>
      <option value="">请选择</option>
      {options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Table: ({ columns, dataSource }: any) => (
    <div>{dataSource.map((record: any) => (
      <div key={record.id}>{columns.map((column: any, index: number) => (
        <span key={column.dataIndex ?? column.title ?? index}>
          {column.render ? column.render(record[column.dataIndex], record) : record[column.dataIndex]}
        </span>
      ))}</div>
    ))}</div>
  ),
  Tabs: ({ items }: any) => <div>{items.map((item: any) => <section key={item.key}>{item.children}</section>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));

vi.mock('../platform-service', () => ({ queryPlatformModels: mocks.queryPlatformModels }));
vi.mock('@/services/ant-design-pro/platformAiAccountingController', () => ({
  billingHistory: mocks.billingHistory,
  publishModelPrice: mocks.publishModelPrice,
  publishPointPrice: mocks.publishPointPrice,
  revokeCostPrice: mocks.revokeCostPrice,
  revokePointPrice: mocks.revokePointPrice,
}));

import ModelBillingPage from './index';

describe('ModelBillingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canPublishModelBilling = true;
    mocks.queryPlatformModels.mockResolvedValue({
      data: [{ id: 9, name: 'Qwen Max', code: 'QWEN_MAX', providerName: 'Aliyun', status: 'ENABLED' }],
    });
    mocks.billingHistory.mockResolvedValue({
      data: {
        modelId: 9,
        costPrices: [{ id: 11, versionNo: 1, status: 'PUBLISHED', effectiveFrom: '2099-01-01T00:00:00', components: [] }],
        pointPrices: [],
      },
    });
  });

  it('selects an enabled model and shows future revoke action', async () => {
    render(<ModelBillingPage />);
    expect(await screen.findByRole('option', { name: 'Qwen Max (QWEN_MAX) - Aliyun' })).toBeInTheDocument();
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });

    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalledWith({ modelId: 9 }));
    expect(await screen.findByText('v1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '撤销' })).toBeInTheDocument();
  });

  it('opens a publish form without a version number input', async () => {
    render(<ModelBillingPage />);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });
    fireEvent.click(await screen.findByRole('button', { name: '发布成本价' }));

    expect(screen.getAllByLabelText('生效时间').length).toBeGreaterThan(0);
    expect(screen.queryByLabelText('版本号')).not.toBeInTheDocument();
  });

  it('declares required fields and numeric limits for publish validation', async () => {
    render(<ModelBillingPage />);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });

    expect(screen.getAllByLabelText('生效时间')[0]).toBeRequired();
    expect(screen.getAllByLabelText('指标')[0]).toBeRequired();
    expect(screen.getAllByLabelText('计费单位')[0]).toHaveAttribute('min', '1e-8');
    expect(screen.getByLabelText('成本单价')).toHaveAttribute('min', '0');
    expect(screen.getByLabelText('币种')).toBeRequired();
    expect(screen.getByLabelText('积分单价')).toHaveAttribute('min', '0');
  });

  it('hides publishing and revocation controls without billing permission', async () => {
    mocks.access.canPublishModelBilling = false;
    render(<ModelBillingPage />);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });

    await screen.findByText('v1');
    expect(screen.queryByRole('button', { name: '发布成本价' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '发布积分价' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '撤销' })).not.toBeInTheDocument();
  });

  it('renders lifecycle states and only allows a future version to be revoked', async () => {
    mocks.billingHistory.mockResolvedValue({
      data: {
        modelId: 9,
        costPrices: [
          { id: 11, versionNo: 1, status: 'PUBLISHED', effectiveFrom: '2099-01-01T00:00:00', components: [] },
          { id: 12, versionNo: 2, status: 'PUBLISHED', effectiveFrom: '2020-01-01T00:00:00', components: [] },
          { id: 13, versionNo: 3, status: 'PUBLISHED', effectiveFrom: '2020-01-01T00:00:00', effectiveTo: '2021-01-01T00:00:00', components: [] },
          { id: 14, versionNo: 4, status: 'REVOKED', effectiveFrom: '2099-02-01T00:00:00', components: [] },
        ],
        pointPrices: [],
      },
    });
    render(<ModelBillingPage />);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });

    expect(await screen.findByText('待生效')).toBeInTheDocument();
    expect(screen.getByText('生效中')).toBeInTheDocument();
    expect(screen.getByText('已过期')).toBeInTheDocument();
    expect(screen.getByText('已撤销')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '撤销' })).toHaveLength(1);
  });

  it('revokes the selected future cost version and refreshes history', async () => {
    mocks.revokeCostPrice.mockResolvedValue({ success: true });
    render(<ModelBillingPage />);
    fireEvent.change(await screen.findByRole('combobox'), { target: { value: '9' } });
    fireEvent.click(await screen.findByRole('button', { name: '撤销' }));

    await waitFor(() => expect(mocks.revokeCostPrice).toHaveBeenCalledWith({ modelId: 9, versionId: 11 }));
    expect(mocks.billingHistory).toHaveBeenCalledTimes(2);
  });
});
