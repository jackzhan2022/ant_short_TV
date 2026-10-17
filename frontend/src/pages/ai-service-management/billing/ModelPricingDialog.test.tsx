import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
  const costForm = {
    getFieldsValue: vi.fn(),
    resetFields: vi.fn(),
    setFieldsValue: vi.fn(),
  };
  const pointForm = {
    getFieldsValue: vi.fn(),
    resetFields: vi.fn(),
    setFieldsValue: vi.fn(),
  };
  return {
    billingHistory: vi.fn(),
    publishModelPrice: vi.fn(),
    publishPointPrice: vi.fn(),
    textAreas: [] as any[],
    canPublishModelBilling: true,
    message: { success: vi.fn(), warning: vi.fn() },
    modalForms: [] as any[],
    selects: [] as any[],
    digits: [] as any[],
    costForm,
    pointForm,
    useForm: vi.fn(),
  };
});

vi.mock('@umijs/max', () => ({ useAccess: () => ({ canPublishModelBilling: mocks.canPublishModelBilling }) }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: mocks.message }) },
  Button: ({ children, onClick, ...props }: any) => <button {...props} onClick={onClick}>{children}</button>,
  Form: { useForm: mocks.useForm },
  Modal: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Popconfirm: ({ children }: any) => children,
  Space: ({ children }: any) => <div>{children}</div>,
  Table: ({ columns, dataSource }: any) => <div>{dataSource.flatMap((record: any) =>
    columns.map((column: any) => column.render?.(record[column.dataIndex], record) ?? record[column.dataIndex]),
  )}</div>,
  Tabs: ({ items }: any) => <div>{items.map((item: any) => <section key={item.key}>{item.children}</section>)}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));
vi.mock('@ant-design/pro-components', () => ({
  ModalForm: (props: any) => {
    mocks.modalForms.push(props);
    return <section>{props.trigger}{props.children}</section>;
  },
  ProFormDateTimePicker: (props: any) => <span data-field={props.name} />,
  ProFormDigit: (props: any) => {
    mocks.digits.push(props);
    return <span data-field={props.name} />;
  },
  ProFormList: ({ children }: any) => <div>{children}</div>,
  ProFormSelect: (props: any) => {
    mocks.selects.push(props);
    return <span data-field={props.name} />;
  },
  ProFormText: () => null,
  ProFormTextArea: (props: any) => {
    mocks.textAreas.push(props);
    return <textarea aria-label={props.label} data-field={props.name} />;
  },
}));
vi.mock('@/services/ant-design-pro/platformAiAccountingController', () => ({
  billingHistory: mocks.billingHistory,
  publishModelPrice: mocks.publishModelPrice,
  publishPointPrice: mocks.publishPointPrice,
  revokeCostPrice: vi.fn(),
  revokePointPrice: vi.fn(),
}));

import ModelPricingDialog from './ModelPricingDialog';

const renderDialog = () => render(
  <ModelPricingDialog model={{ id: 9, name: 'Qwen Max', code: 'QWEN_MAX' }} open onClose={vi.fn()} onChanged={vi.fn()} />,
);

describe('ModelPricingDialog', () => {
  beforeEach(() => {
    mocks.billingHistory.mockResolvedValue({ data: { modelId: 9, costPrices: [], pointPrices: [] } });
    mocks.canPublishModelBilling = true;
    mocks.message.success.mockReset();
    mocks.message.warning.mockReset();
    mocks.modalForms.length = 0;
    mocks.selects.length = 0;
    mocks.digits.length = 0;
    mocks.textAreas.length = 0;
    mocks.publishModelPrice.mockReset();
    mocks.publishPointPrice.mockReset();
    mocks.costForm.getFieldsValue.mockReset();
    mocks.costForm.resetFields.mockReset();
    mocks.costForm.setFieldsValue.mockReset();
    mocks.pointForm.getFieldsValue.mockReset();
    mocks.pointForm.resetFields.mockReset();
    mocks.pointForm.setFieldsValue.mockReset();
    mocks.useForm.mockReset();
    mocks.useForm.mockImplementation(() => [
      [mocks.costForm, mocks.pointForm][(mocks.useForm.mock.calls.length - 1) % 2],
    ]);
  });

  it('loads billing history for the supplied model without rendering a model selector', async () => {
    renderDialog();

    expect(screen.getByLabelText('Qwen Max 模型价格')).toBeInTheDocument();
    expect(screen.queryByLabelText('选择已启用模型')).not.toBeInTheDocument();
    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalledWith({ modelId: 9 }));
  });

  it('configures cost currency as CNY/USD choices and starts both forms at the current time', () => {
    renderDialog();

    const costForm = mocks.modalForms.find((form) => form.title === '发布成本价');
    const pointForm = mocks.modalForms.find((form) => form.title === '发布积分价');
    const currency = mocks.selects.find((field) => field.name === 'currency');

    expect(costForm.initialValues.effectiveFrom).toBeTruthy();
    expect(costForm.initialValues.components).toEqual([{ unitSize: 1, currency: 'USD' }]);
    expect(pointForm.initialValues.effectiveFrom).toBeTruthy();
    expect(currency.options).toEqual([
      { label: '人民币（CNY）', value: 'CNY' },
      { label: '美元（USD）', value: 'USD' },
    ]);
  });

  it('resets each form effective time to the current time whenever its modal opens', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2030-01-02T03:04:05'));
      renderDialog();
      const costModal = mocks.modalForms.find((form) => form.title === '发布成本价');
      const pointModal = mocks.modalForms.find((form) => form.title === '发布积分价');

      costModal.onOpenChange(true);
      pointModal.onOpenChange(true);

      expect(mocks.costForm.resetFields).toHaveBeenCalledBefore(mocks.costForm.setFieldsValue);
      expect(mocks.pointForm.resetFields).toHaveBeenCalledBefore(mocks.pointForm.setFieldsValue);
      const costDefaults = mocks.costForm.setFieldsValue.mock.calls[0][0];
      const pointDefaults = mocks.pointForm.setFieldsValue.mock.calls[0][0];
      expect(costDefaults).toMatchObject({ components: [{ unitSize: 1, currency: 'USD' }] });
      expect(Object.keys(costDefaults)).toEqual(['effectiveFrom', 'components']);
      expect(pointDefaults).toMatchObject({ components: [{ unitSize: 1, currency: 'USD' }], multiplier: 1 });
      expect(Object.keys(pointDefaults)).toEqual(['effectiveFrom', 'components', 'multiplier']);
      expect(costDefaults.effectiveFrom.valueOf()).toBe(new Date('2030-01-02T03:04:05').valueOf());
      expect(pointDefaults.effectiveFrom.valueOf()).toBe(new Date('2030-01-02T03:04:05').valueOf());

      vi.setSystemTime(new Date('2030-01-02T04:05:06'));
      costModal.onOpenChange(true);
      pointModal.onOpenChange(true);
      expect(mocks.costForm.resetFields).toHaveBeenCalledTimes(2);
      expect(mocks.pointForm.resetFields).toHaveBeenCalledTimes(2);
      expect(mocks.costForm.setFieldsValue.mock.calls[1][0].effectiveFrom.valueOf())
        .toBe(new Date('2030-01-02T04:05:06').valueOf());
      expect(mocks.pointForm.setFieldsValue.mock.calls[1][0].effectiveFrom.valueOf())
        .toBe(new Date('2030-01-02T04:05:06').valueOf());
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows formatted billing units and provides a required non-negative multiplier', async () => {
    mocks.billingHistory.mockResolvedValue({
      data: {
        modelId: 9,
        costPrices: [{
          id: 1, versionNo: 1, status: 'PUBLISHED', effectiveFrom: '2026-01-01T00:00:00',
          components: [{ id: 1, metric: 'INPUT_TOKEN', unitSize: 1_000_000, unitPrice: 2, currency: 'USD' }],
        }],
        pointPrices: [],
      },
    });
    renderDialog();

    expect(await screen.findByText(/1 百万 Token/)).toBeInTheDocument();
    const multiplier = mocks.digits.find((field) => field.name === 'multiplier');
    expect(multiplier).toMatchObject({ label: '倍率', min: 0, initialValue: 1, rules: [{ required: true }] });
    expect(screen.getByRole('button', { name: '按倍率生成积分项' })).toBeInTheDocument();
  });

  it('generates editable point components from the effective published cost version', async () => {
    mocks.billingHistory.mockResolvedValue({
      data: {
        modelId: 9,
        costPrices: [{
          id: 1, versionNo: 1, status: 'PUBLISHED', effectiveFrom: '2026-01-01T00:00:00',
          components: [{ metric: 'INPUT_TOKEN', unitSize: 1_000_000, unitPrice: 2, currency: 'USD' }],
        }],
        pointPrices: [],
      },
    });
    mocks.pointForm.getFieldsValue.mockReturnValue({ effectiveFrom: '2026-02-01 00:00:00', multiplier: 3 });
    renderDialog();
    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: '按倍率生成积分项' }));

    expect(mocks.pointForm.setFieldsValue).toHaveBeenCalledWith({
      components: [{ metric: 'INPUT_TOKEN', unitSize: 1_000_000, pointRate: 6, dimensionsJson: '{}' }],
    });
  });

  it('warns and preserves point components when no published cost version applies', async () => {
    mocks.pointForm.getFieldsValue.mockReturnValue({ effectiveFrom: '2026-02-01 00:00:00', multiplier: 3 });
    renderDialog();
    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: '按倍率生成积分项' }));

    expect(mocks.message.warning).toHaveBeenCalledWith('该生效时间没有可用的已发布成本价，请先发布成本价或调整生效时间');
    expect(mocks.pointForm.setFieldsValue).not.toHaveBeenCalled();
  });
  it.each(['cost', 'point'] as const)('publishes %s conditions parsed from JSON', async (kind) => {
    renderDialog();
    const form = mocks.modalForms.find((item) => item.title === (kind === 'cost' ? '发布成本价' : '发布积分价'));
    await act(async () => { await form.onFinish({
      effectiveFrom: '2026-10-12T12:00:00',
      components: [{ metric: 'OUTPUT_TOKEN', unitSize: 1_000_000, unitPrice: 70, pointRate: 140,
        currency: 'CNY', dimensionsJson: '{"resolution":"720p","referenceVideo":"false"}' }],
    }); });
    const publish = kind === 'cost' ? mocks.publishModelPrice : mocks.publishPointPrice;
    expect(publish).toHaveBeenCalledWith({ modelId: 9 }, expect.objectContaining({
      components: [expect.objectContaining({ dimensions: { resolution: '720p', referenceVideo: 'false' } })],
    }));
  });

  it('provides validated JSON condition textareas for both price forms', async () => {
    renderDialog();
    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalled());
    expect(screen.getAllByRole('textbox', { name: '计费条件（JSON）' })).toHaveLength(2);
    for (const field of mocks.textAreas) {
      expect(field.name).toBe('dimensionsJson');
      const validate = field.rules.find((rule: any) => rule.validator).validator;
      await expect(validate({}, '{"resolution":"720p"}')).resolves.toBeUndefined();
      await expect(validate({}, '')).resolves.toBeUndefined();
      await expect(validate({}, '{')).rejects.toThrow();
      await expect(validate({}, '[]')).rejects.toThrow();
      await expect(validate({}, '{"referenceVideo":false}')).rejects.toThrow();
    }
  });

  it('displays cost and point conditions returned by billing history', async () => {
    const dimensions = { resolution: '720p', referenceVideo: 'false' };
    mocks.billingHistory.mockResolvedValue({ data: {
      modelId: 9,
      costPrices: [{ id: 1, versionNo: 1, components: [{ id: 11, metric: 'OUTPUT_TOKEN',
        unitSize: 1_000_000, unitPrice: 70, currency: 'CNY', dimensions }] }],
      pointPrices: [{ id: 2, versionNo: 1, components: [{ id: 21, metric: 'OUTPUT_TOKEN',
        unitSize: 1_000_000, pointRate: 140, dimensions }] }],
    } });
    renderDialog();
    expect(await screen.findAllByText(/"resolution": "720p"/)).toHaveLength(2);
    expect(screen.getAllByText(/"referenceVideo": "false"/)).toHaveLength(2);
  });

  it('preserves cost conditions when generating point-price form values', async () => {
    const dimensions = { resolution: '1080p', referenceVideo: 'true' };
    mocks.billingHistory.mockResolvedValue({ data: { modelId: 9, costPrices: [{
      id: 1, status: 'PUBLISHED', effectiveFrom: '2026-01-01T00:00:00',
      components: [{ metric: 'OUTPUT_TOKEN', unitSize: 1_000_000, unitPrice: 46, dimensions }],
    }], pointPrices: [] } });
    mocks.pointForm.getFieldsValue.mockReturnValue({ effectiveFrom: '2026-10-12T00:00:00', multiplier: 2 });
    renderDialog();
    await screen.findByText(/1 百万 Token/);
    fireEvent.click(screen.getByRole('button', { name: '按倍率生成积分项' }));
    expect(mocks.pointForm.setFieldsValue).toHaveBeenCalledWith({ components: [{
      metric: 'OUTPUT_TOKEN', unitSize: 1_000_000, pointRate: 92,
      dimensionsJson: JSON.stringify(dimensions, null, 2),
    }] });
  });

});
