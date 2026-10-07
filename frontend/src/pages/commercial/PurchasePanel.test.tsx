import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import PurchasePanel, { initialPurchaseSelection, type PurchaseSelection } from './PurchasePanel';
import type { CommercialCatalogItem } from './service';

vi.mock('antd', () => ({
  Button: ({ children, onClick, disabled, loading, ...props }: any) => (
    <button type="button" onClick={onClick} disabled={disabled || loading} {...props}>{children}</button>
  ),
  Tabs: ({ activeKey, items, onChange }: any) => (
    <div role="tablist" aria-label="商品类型">{items.map((item: any) => (
      <button key={item.key} type="button" role="tab" aria-selected={activeKey === item.key}
        onClick={() => onChange(item.key)}>{item.label}</button>
    ))}</div>
  ),
  Radio: { Group: ({ value, options, onChange }: any) => (
    <fieldset aria-label="会员周期">{options.map((item: any) => (
      <button key={item.value} type="button" aria-pressed={value === item.value}
        onClick={() => onChange({ target: { value: item.value } })}>{item.label}</button>
    ))}</fieldset>
  ) },
}));

const item = (id: number, overrides: Partial<CommercialCatalogItem> = {}): CommercialCatalogItem => ({
  packageId: id,
  packageVersionId: id,
  code: `PKG-${id}`,
  packageType: 'SUBSCRIPTION',
  name: `会员${id}`,
  price: 99,
  currency: 'CNY',
  periodMonths: 1,
  entitlements: [{ type: 'PERIODIC_POINTS', value: 3000 }],
  ...overrides,
});

const renderPanel = (catalog: CommercialCatalogItem[], options: {
  canManageBilling?: boolean;
  loading?: boolean;
  submitting?: boolean;
  error?: string;
  selection?: PurchaseSelection;
  onPurchase?: (offer: CommercialCatalogItem) => void;
} = {}) => {
  const onPurchase = options.onPurchase ?? vi.fn();
  const onReload = vi.fn();
  const Fixture = ({ offers }: { offers: CommercialCatalogItem[] }) => {
    const [selection, setSelection] = useState(options.selection ?? initialPurchaseSelection);
    return <PurchasePanel catalog={offers} selection={selection} onSelectionChange={setSelection}
      canManageBilling={options.canManageBilling ?? true} teamName="新禾文创"
      loading={options.loading ?? false} error={options.error}
      submitting={options.submitting ?? false} onReload={onReload} onPurchase={onPurchase} />;
  };
  const view = render(<Fixture offers={catalog} />);
  return { ...view, rerenderCatalog: (offers: CommercialCatalogItem[]) => view.rerender(<Fixture offers={offers} />), onPurchase, onReload };
};

describe('PurchasePanel', () => {
  it('defaults to membership monthly and keeps its entry when only points are for sale', () => {
    const points = item(1, { packageType: 'POINT_PACKAGE', billingPeriod: 'YEAR', name: '积分包', entitlements: [{ type: 'ONE_TIME_POINTS', value: 1000 }] });
    renderPanel([points]);

    expect(screen.getByRole('tab', { name: '会员套餐' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', { name: '月度' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('当前周期暂无可售会员套餐')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    fireEvent.click(screen.getByRole('tab', { name: '积分包' }));
    expect(screen.getByRole('button', { name: '选择积分包' })).toBeInTheDocument();
  });

  it('shows but cannot select a point package with incompatible benefits', () => {
    const invalid = item(9, {
      packageType: 'POINT_PACKAGE',
      name: '异常积分包',
      entitlements: [{ type: 'ONE_TIME_POINTS', value: 100 }, { type: 'GLOBAL_DISCOUNT', value: 0.8 }],
    });
    const { onPurchase } = renderPanel([invalid]);
    fireEvent.click(screen.getByRole('tab', { name: '积分包' }));
    expect(screen.getByRole('button', { name: '选择异常积分包' })).toBeDisabled();
    expect(screen.getByText('权益配置待确认，暂不可购买')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    expect(onPurchase).not.toHaveBeenCalled();
  });

  it('lists every item in a membership period and remembers selection across product types', () => {
    const quarterA = item(2, { name: '季度标准', periodMonths: 3, price: 269 });
    const quarterB = item(3, { name: '季度专业', periodMonths: 3, price: 329 });
    const yearly = item(4, { name: '年度会员', periodMonths: 12, price: 899 });
    const points = item(5, { packageType: 'POINT_PACKAGE', billingPeriod: 'YEAR', name: '积分包', price: 59, entitlements: [{ type: 'ONE_TIME_POINTS', value: 1000 }] });
    const { onPurchase } = renderPanel([quarterA, quarterB, yearly, points]);

    fireEvent.click(screen.getByRole('button', { name: '季度' }));
    expect(screen.getByRole('button', { name: '选择季度标准' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '选择季度专业' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '选择季度专业' }));
    fireEvent.click(screen.getByRole('button', { name: '年度' }));
    expect(screen.queryByRole('button', { name: '选择季度标准' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '积分包' }));
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    expect(onPurchase).toHaveBeenCalledWith(points);
    fireEvent.click(screen.getByRole('tab', { name: '会员套餐' }));
    fireEvent.click(screen.getByRole('button', { name: '季度' }));
    expect(screen.getByRole('button', { name: '选择季度专业' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('invalidates a removed selection and never submits its stale version', () => {
    const old = item(1, { name: '旧会员' });
    const next = item(2, { name: '新会员' });
    const { rerenderCatalog, onPurchase } = renderPanel([old, next]);
    fireEvent.click(screen.getByRole('button', { name: '选择新会员' }));
    rerenderCatalog([old]);

    expect(screen.queryByRole('button', { name: '选择新会员' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '确认付款' }));
    expect(onPurchase).toHaveBeenCalledWith(old);
  });

  it('shows unsupported periods without letting them become a payment choice', () => {
    const invalid = item(9, { name: '特殊周期', periodMonths: 5 });
    const { onPurchase } = renderPanel([invalid]);
    expect(screen.getByText('周期信息待确认：特殊周期')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认付款' })).toBeDisabled();
    expect(onPurchase).not.toHaveBeenCalled();
  });

  it('distinguishes errors, empty offers and billing permissions', () => {
    const { onReload } = renderPanel([], { error: '目录暂时不可用' });
    expect(screen.getByRole('alert')).toHaveTextContent('目录暂时不可用');
    expect(screen.queryByText('当前周期暂无可售会员套餐')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新加载套餐' }));
    expect(onReload).toHaveBeenCalledOnce();

    const viewer = renderPanel([item(2)], { canManageBilling: false });
    expect(screen.getByText('仅团队管理员可购买')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '确认付款' }).at(-1)).toBeDisabled();
    viewer.unmount();
  });

  it('prevents another purchase while an order is being submitted', () => {
    const onPurchase = vi.fn();
    renderPanel([item(2)], { submitting: true, onPurchase });
    const confirm = screen.getByRole('button', { name: '确认付款' });
    expect(confirm).toBeDisabled();
    fireEvent.click(confirm);
    expect(onPurchase).not.toHaveBeenCalled();
  });

  it('shows actual catalog values, a selected state and no unearned recommendation', () => {
    const offer = item(7, {
      name: '真实名称', price: 119, listPrice: 119,
      entitlements: [{ type: 'PERIODIC_POINTS', value: 1200 }],
    });
    renderPanel([offer]);
    expect(screen.getByRole('button', { name: '选择真实名称' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByText('¥119.00')).toHaveLength(2);
    expect(screen.getByText('每月发放 1,200 积分')).toBeInTheDocument();
    expect(screen.queryByText('推荐方案')).not.toBeInTheDocument();
    expect(screen.queryByText('¥99.00')).not.toBeInTheDocument();
  });
});
