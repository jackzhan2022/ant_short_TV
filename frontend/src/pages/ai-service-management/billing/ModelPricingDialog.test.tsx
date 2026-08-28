import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  billingHistory: vi.fn(),
}));

vi.mock('@umijs/max', () => ({ useAccess: () => ({ canPublishModelBilling: false }) }));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { success: vi.fn() } }) },
  Modal: ({ children, open, title }: any) => open ? <section aria-label={title}>{children}</section> : null,
  Space: ({ children }: any) => <div>{children}</div>,
  Table: () => <div />,
  Tabs: ({ items }: any) => <div>{items[0].children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
}));
vi.mock('@ant-design/pro-components', () => ({
  ModalForm: () => null,
  ProFormDateTimePicker: () => null,
  ProFormDigit: () => null,
  ProFormList: () => null,
  ProFormSelect: () => null,
  ProFormText: () => null,
}));
vi.mock('@/services/ant-design-pro/platformAiAccountingController', () => ({
  billingHistory: mocks.billingHistory,
  publishModelPrice: vi.fn(),
  publishPointPrice: vi.fn(),
  revokeCostPrice: vi.fn(),
  revokePointPrice: vi.fn(),
}));

import ModelPricingDialog from './ModelPricingDialog';

describe('ModelPricingDialog', () => {
  it('loads billing history for the supplied model without rendering a model selector', async () => {
    mocks.billingHistory.mockResolvedValue({ data: { modelId: 9, costPrices: [], pointPrices: [] } });

    render(<ModelPricingDialog model={{ id: 9, name: 'Qwen Max', code: 'QWEN_MAX' }} open onClose={vi.fn()} onChanged={vi.fn()} />);

    expect(screen.getByLabelText('Qwen Max 模型价格')).toBeInTheDocument();
    expect(screen.queryByLabelText('选择已启用模型')).not.toBeInTheDocument();
    await waitFor(() => expect(mocks.billingHistory).toHaveBeenCalledWith({ modelId: 9 }));
  });
});
