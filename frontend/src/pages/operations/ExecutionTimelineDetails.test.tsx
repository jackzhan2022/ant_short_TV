import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ExecutionTimelineDetails } from './ExecutionTimelineDetails';
import type { ExecutionTimeline } from './service';

vi.mock('antd', async (original) => ({
  ...(await original<typeof import('antd')>()),
  Table: ({ dataSource }: { dataSource: object[] }) => (
    <div data-testid="bounded-source">{dataSource.length}</div>
  ),
}));

it('caps all three evidence sources at 100 and reports truncation even for an oversized response', () => {
  const rows = Array.from({ length: 101 }, (_, id) => ({ id }));
  render(
    <ExecutionTimelineDetails
      data={
        {
          executionId: 42,
          from: '2026-11-05T00:00:00Z',
          to: '2026-11-05T01:00:00Z',
          coverage: 'PARTIAL',
          versionScope: 'LATEST_RETAINED_CONTEXT',
          truncated: false,
          versions: rows,
          attempts: rows,
          calls: rows,
        } as unknown as ExecutionTimeline
      }
    />,
  );
  expect(
    screen.getAllByTestId('bounded-source').map((node) => node.textContent),
  ).toEqual(['100', '100', '100']);
  expect(
    screen.getByText('结果已截断：每个数据源最多100条'),
  ).toBeInTheDocument();
  expect(screen.getByText('时间线覆盖不完整（PARTIAL）')).toBeInTheDocument();
  expect(
    screen.getByText(/版本展示最新保留上下文；尝试\/调用按所选时间/),
  ).toBeInTheDocument();
});
