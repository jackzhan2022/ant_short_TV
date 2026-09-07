import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiOperationsPage from './index';

const mocks = vi.hoisted(() => ({
  queryAiOperationsOverview: vi.fn(),
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, title }: any) => (
    <main>
      <h1>{title}</h1>
      {children}
    </main>
  ),
}));

vi.mock('antd', () => ({
  Statistic: ({ title, value }: any) => (
    <div>
      <span>{title}</span>
      <span>{value}</span>
    </div>
  ),
  Table: () => <div>服务商状态</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('./service', () => ({
  queryAiOperationsOverview: mocks.queryAiOperationsOverview,
}));

describe('AiOperationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryAiOperationsOverview.mockResolvedValue({
      success: true,
      data: {
        expiredClaims: 0,
        retryExhausted: 0,
        unpricedUsage: 3,
        incompleteUsage: 2,
        settlementReview: 0,
        totalProviderCost: 1.25,
        totalSettledPoints: 10,
        providerFailureRates: [],
      },
    });
  });

  it('distinguishes missing price components from incomplete usage data', async () => {
    render(<AiOperationsPage />);

    await waitFor(() => {
      expect(screen.getByText('价格组件缺失')).toBeInTheDocument();
      expect(screen.getByText('用量数据不完整')).toBeInTheDocument();
    });
  });
});
