import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  queryPlatformModels: vi.fn(),
  billingHistory: vi.fn(),
  publishModelPrice: vi.fn(),
  publishPointPrice: vi.fn(),
  revokeCostPrice: vi.fn(),
  revokePointPrice: vi.fn(),
}));

vi.mock('@umijs/max', async () => {
  const actual = await vi.importActual<typeof import('@umijs/max')>('@umijs/max');
  return { ...actual, useAccess: () => ({ canPublishModelBilling: true }) };
});

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ModalForm: ({ children, trigger }: any) => (
    <section>
      {trigger}
      <div>{children}</div>
    </section>
  ),
  ProFormDateTimePicker: ({ label }: any) => <label>{label}<input aria-label={label} /></label>,
  ProFormDigit: ({ label }: any) => <label>{label}<input aria-label={label} /></label>,
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: ({ label }: any) => <label>{label}<select aria-label={label} /></label>,
  ProFormText: ({ label }: any) => <label>{label}<input aria-label={label} /></label>,
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
});
