import { render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  columns: [] as any[],
  rows: [] as any[],
  billingHistory: vi.fn(),
  queryPlatformModels: vi.fn(),
  queryPlatformProviders: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useAccess: () => ({
    canViewPlatformAiModels: true,
    canViewPlatformAiProviders: true,
    canCreatePlatformAiModels: false,
    canEditPlatformAiModels: false,
    canEnablePlatformAiModels: false,
    canPublishModelBilling: false,
    canViewModelBilling: true,
  }),
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ columns, request }: any) => {
    mocks.columns = columns;
    void request().then((response: any) => {
      mocks.rows = response.data;
    });
    return null;
  },
}));
vi.mock('antd', async (importOriginal) => ({
  ...(await importOriginal<typeof import('antd')>()),
  App: { useApp: () => ({ message: {} }) },
}));
vi.mock('../platform-service', () => ({
  queryPlatformModels: mocks.queryPlatformModels,
  queryPlatformProviders: mocks.queryPlatformProviders,
  serviceTypeText: {},
}));
vi.mock('@/services/ant-design-pro/platformAiAccountingController', () => ({
  billingHistory: mocks.billingHistory,
}));

import PlatformModelsPage from './index';

describe('PlatformModelsPage pricing columns', () => {
  it('adds read-only real-time price columns and a model-pricing action', async () => {
    mocks.queryPlatformModels.mockResolvedValue({ success: true, data: [] });
    mocks.queryPlatformProviders.mockResolvedValue({ success: true, data: [] });

    render(<PlatformModelsPage />);

    await waitFor(() =>
      expect(mocks.columns.map((column) => column.title)).toEqual(
        expect.arrayContaining(['当前成本价', '当前积分价', '操作']),
      ),
    );
    expect(
      mocks.columns.find((column) => column.title === '当前成本价').renderText,
    ).toBeDefined();
    expect(
      mocks.columns.find((column) => column.title === '当前积分价').renderText,
    ).toBeDefined();
    expect(mocks.columns.find((column) => column.title === '操作').fixed).toBe(
      'right',
    );
  });
  it('retains real units, currency and dimension conditions in price data', async () => {
    mocks.queryPlatformProviders.mockResolvedValue({ success: true, data: [] });
    mocks.queryPlatformModels.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          name: '真实模型',
          code: 'real',
          providerId: 2,
          serviceType: 'TEXT',
          status: 'ENABLED',
        },
      ],
    });
    mocks.billingHistory.mockResolvedValue({
      data: {
        costPrices: [
          {
            status: 'PUBLISHED',
            components: [
              {
                metric: 'INPUT_TOKEN',
                unitSize: 1_000_000,
                unitPrice: 0.2,
                currency: 'USD',
                dimensions: { context: '128k' },
              },
            ],
          },
        ],
        pointPrices: [
          {
            status: 'PUBLISHED',
            components: [
              {
                metric: 'INPUT_TOKEN',
                unitSize: 1_000_000,
                pointRate: 12,
                dimensions: { context: '128k' },
              },
            ],
          },
        ],
      },
    });
    render(<PlatformModelsPage />);
    await waitFor(() =>
      expect(mocks.rows[0]?.currentCostPrice).toContain(
        '0.2 USD / 1 百万 Token',
      ),
    );
    expect(mocks.rows[0].currentCostPrice).toContain('"context": "128k"');
    expect(mocks.rows[0].currentPointPrice).toContain('12 积分 / 1 百万 Token');
    expect(mocks.rows[0].currentPointPrice).toContain('"context": "128k"');
    expect(mocks.columns).toHaveLength(6);
  });
});
