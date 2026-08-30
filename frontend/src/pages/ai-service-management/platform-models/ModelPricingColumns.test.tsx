import { render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  columns: [] as any[],
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
  }),
}));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ columns, request }: any) => {
    mocks.columns = columns;
    void request();
    return null;
  },
}));
vi.mock('antd', () => ({ App: { useApp: () => ({ message: {} }) } }));
vi.mock('../platform-service', () => ({
  queryPlatformModels: mocks.queryPlatformModels,
  queryPlatformProviders: mocks.queryPlatformProviders,
  serviceTypeText: {},
}));
vi.mock('@/services/ant-design-pro/platformAiAccountingController', () => ({
  billingHistory: vi.fn(),
}));

import PlatformModelsPage from './index';

describe('PlatformModelsPage pricing columns', () => {
  it('adds read-only real-time price columns and a model-pricing action', async () => {
    mocks.queryPlatformModels.mockResolvedValue({ success: true, data: [] });
    mocks.queryPlatformProviders.mockResolvedValue({ success: true, data: [] });

    render(<PlatformModelsPage />);

    await waitFor(() => expect(mocks.columns.map((column) => column.title)).toEqual(
      expect.arrayContaining(['当前成本价', '当前积分价', '操作']),
    ));
    expect(mocks.columns.find((column) => column.title === '当前成本价').renderText).toBeDefined();
    expect(mocks.columns.find((column) => column.title === '当前积分价').renderText).toBeDefined();
  });
});
