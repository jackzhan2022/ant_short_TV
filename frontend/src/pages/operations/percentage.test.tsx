import { render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import OperationsPage from './index';

vi.mock('@umijs/max', () => ({
  useAccess: () => ({ canViewOperations: true }),
  request: vi.fn(),
}));
vi.mock('./service', async (original) => ({
  ...(await original<typeof import('./service')>()),
  operationsApi: {
    settings: async () => ({ refreshSeconds: 300 }),
    snapshot: async () => ({
      overview: { requestCount: 100, technicalErrors: 1, errorRate: 1 },
      ai: { calls: 100, successRate: 100 },
      trend: [],
      resources: [],
      queue: {},
      pipeline: {},
      activeAlerts: 0,
      coverage: { granularity: 'MINUTE', reasons: [] },
      health: { status: 'NORMAL', reasons: [] },
    }),
  },
}));

it('renders percentages already expressed in percent without multiplying them again', async () => {
  const { container } = render(<OperationsPage />);
  await waitFor(() => expect(screen.getByText('技术失败率')).toBeTruthy());
  await waitFor(() => {
    const values = [
      ...container.querySelectorAll('.ant-statistic-content'),
    ].map((node) => node.textContent);
    expect(values).toContain('1%');
    expect(values).not.toContain('10000%');
  });
});
