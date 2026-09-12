import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { vi } from 'vitest';
import ScriptReviewHistoryPage from '.';

const mocks = vi.hoisted(() => ({
  queryReviewProjectHistory: vi.fn(),
  createReviewTask: vi.fn(),
  push: vi.fn(),
}));

it.each([
  [
    Array.from(
      { length: 20 },
      (_, index) => `### ${index + 1}. 问题\n证据与建议`,
    ).join('\n'),
    true,
  ],
  ['自定义报告内容', false],
  ['', false],
])('derives history counts from report findings without defaulting to zero (%#)', async (body, hasFindings) => {
  window.history.replaceState({}, '', '/script-review/projects/1/reviews');
  mocks.queryReviewProjectHistory.mockResolvedValue({
    data: {
      project: { id: 1, name: '测试剧本' },
      versions: [],
      page: 1,
      pageSize: 20,
      total: 1,
      items: [
        {
          id: 30,
          scriptVersionId: 2,
          roundNo: 1,
          reviewMode: 'DEEP',
          selectedDimensions: [],
          reviewScopeType: 'ALL',
          status: 'COMPLETED',
          overallProgress: 100,
          issueCount: 0,
          pendingIssueCount: 0,
          reportMarkdown: body
            ? `## 主要问题\n${body}\n## 各维度审核结论\n### 1. 总结`
            : null,
        },
      ],
    },
  });
  render(
    <App>
      <ScriptReviewHistoryPage />
    </App>,
  );
  await screen.findByText('第 1 轮审核');
  if (hasFindings)
    expect(screen.getByLabelText('报告问题总数')).toHaveTextContent(
      '20 项问题',
    );
  else expect(screen.queryByLabelText('报告问题总数')).not.toBeInTheDocument();
  expect(
    screen.queryByText(/问题\s*0|待处理\s*0|共\s*0\s*项问题/),
  ).not.toBeInTheDocument();
});

vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra }: any) => (
    <div>
      {extra}
      {children}
    </div>
  ),
}));
vi.mock('../script-review/service', () => ({
  queryReviewProjectHistory: mocks.queryReviewProjectHistory,
  createReviewTask: mocks.createReviewTask,
  cancelReviewTask: vi.fn(),
  retryReviewTask: vi.fn(),
}));

it('offers all thirteen dimensions and submits an additionally selected dimension', async () => {
  window.history.replaceState({}, '', '/script-review/projects/1/reviews');
  mocks.queryReviewProjectHistory.mockResolvedValue({
    data: {
      project: { id: 1, name: '测试剧本' },
      versions: [{ id: 2, versionNo: 1 }],
      items: [],
      page: 1,
      pageSize: 20,
      total: 0,
    },
  });
  mocks.createReviewTask.mockResolvedValue({ data: { businessId: 20 } });
  render(
    <App>
      <ScriptReviewHistoryPage />
    </App>,
  );
  await waitFor(() =>
    expect(mocks.queryReviewProjectHistory).toHaveBeenCalled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '发起审核' }));
  expect(await screen.findAllByRole('checkbox')).toHaveLength(13);
  expect(
    screen
      .getAllByRole('checkbox')
      .filter((input) => (input as HTMLInputElement).checked),
  ).toHaveLength(3);
  fireEvent.click(screen.getByRole('checkbox', { name: '伏笔回收' }));
  fireEvent.click(screen.getByRole('button', { name: /OK|确\s*定/ }));
  await waitFor(() =>
    expect(mocks.createReviewTask).toHaveBeenCalledWith(1, {
      versionId: 2,
      reviewMode: 'QUICK',
      reviewScopeType: 'ALL',
      reviewScope: {},
      selectedDimensions: [
        '台词合理性',
        '人物关系一致性',
        '人物认知一致性',
        '伏笔回收',
      ],
    }),
  );
});
