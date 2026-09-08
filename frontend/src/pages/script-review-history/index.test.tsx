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

vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra }: any) => <div>{extra}{children}</div>,
}));
vi.mock('../script-review/service', () => ({
  queryReviewProjectHistory: mocks.queryReviewProjectHistory,
  createReviewTask: mocks.createReviewTask,
  cancelReviewTask: vi.fn(),
  retryReviewTask: vi.fn(),
}));

it('offers all thirteen dimensions and submits an additionally selected dimension', async () => {
  window.history.replaceState({}, '', '/script-review/projects/1/reviews');
  mocks.queryReviewProjectHistory.mockResolvedValue({ data: {
    project: { id: 1, name: '测试剧本' },
    versions: [{ id: 2, versionNo: 1 }],
    items: [], page: 1, pageSize: 20, total: 0,
  } });
  mocks.createReviewTask.mockResolvedValue({ data: { businessId: 20 } });
  render(<App><ScriptReviewHistoryPage /></App>);
  await waitFor(() => expect(mocks.queryReviewProjectHistory).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '发起审核' }));
  expect(await screen.findAllByRole('checkbox')).toHaveLength(13);
  expect(screen.getAllByRole('checkbox').filter((input) => (input as HTMLInputElement).checked)).toHaveLength(3);
  fireEvent.click(screen.getByRole('checkbox', { name: '伏笔回收' }));
  fireEvent.click(screen.getByRole('button', { name: /OK|确\s*定/ }));
  await waitFor(() => expect(mocks.createReviewTask).toHaveBeenCalledWith(1, {
    versionId: 2, reviewMode: 'QUICK', reviewScopeType: 'ALL', reviewScope: {},
    selectedDimensions: ['台词合理性', '人物关系一致性', '人物认知一致性', '伏笔回收'],
  }));
});
