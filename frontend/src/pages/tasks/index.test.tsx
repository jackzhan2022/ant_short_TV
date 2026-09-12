import '@testing-library/jest-dom/vitest';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Tasks from '.';

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  summary: vi.fn(),
  detail: vi.fn(),
  children: vi.fn(),
  control: vi.fn(),
  tenantId: 1,
}));
vi.mock('@umijs/max', () => ({
  useModel: () => ({ initialState: { currentTenantId: mocks.tenantId } }),
  history: { push: vi.fn() },
}));
vi.mock('./service', () => ({
  listTasks: mocks.list,
  taskSummary: mocks.summary,
  taskDetail: mocks.detail,
  taskChildren: mocks.children,
  controlTask: mocks.control,
}));
beforeEach(() => {
  vi.clearAllMocks();
  mocks.tenantId = 1;
  window.history.replaceState({}, '', '/tasks');
  mocks.list.mockResolvedValue({
    items: [
      {
        taskKey: 'REVIEW:1',
        type: 'REVIEW',
        title: '审核任务一',
        statusGroup: 'SUCCEEDED',
        createdAt: '2026-09-12',
        childCounts: { total: 0 },
        allowedActions: [],
      },
    ],
    total: 1,
    page: 1,
    pageSize: 20,
    canViewTeamTasks: false,
  });
  mocks.summary.mockResolvedValue({ total: 1, counts: { SUCCEEDED: 1 } });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
});
it('loads mine without fetching every row detail and hides team scope for members', async () => {
  render(<Tasks />);
  expect(await screen.findByText('审核任务一')).toBeInTheDocument();
  expect(
    screen.queryByRole('tab', { name: '团队任务' }),
  ).not.toBeInTheDocument();
  expect(mocks.detail).not.toHaveBeenCalled();
  expect(mocks.list.mock.calls[0][1].scope).toBe('mine');
});
it('loads just the selected detail and persists selection', async () => {
  mocks.detail.mockResolvedValue({
    taskKey: 'REVIEW:1',
    title: '任务详情内容',
    childCounts: { total: 0 },
    allowedActions: [],
    statusGroup: 'SUCCEEDED',
  });
  render(<Tasks />);
  fireEvent.click(await screen.findByText('审核任务一'));
  await waitFor(() =>
    expect(mocks.detail).toHaveBeenCalledWith(1, 'REVIEW:1', expect.anything()),
  );
  expect(mocks.children).not.toHaveBeenCalled();
  expect(window.location.search).toContain('task=REVIEW');
});
it('rejects team URL errors without falling back to a wider or different query', async () => {
  window.history.replaceState({}, '', '/tasks?scope=team');
  mocks.list.mockRejectedValue(new Error('无权访问团队任务'));
  render(<Tasks />);
  expect(await screen.findByText('无权访问团队任务')).toBeInTheDocument();
  expect(mocks.list.mock.calls.every((call) => call[1].scope === 'team')).toBe(
    true,
  );
});

it('restores deep links and page size, and exposes the server-authorized team tab', async () => {
  window.history.replaceState(
    {},
    '',
    '/tasks?scope=team&page=2&pageSize=50&task=REVIEW:1',
  );
  mocks.list.mockResolvedValue({ items: [], total: 0, canViewTeamTasks: true });
  mocks.detail.mockResolvedValue({
    taskKey: 'REVIEW:1',
    title: '深链接任务',
    statusGroup: 'SUCCEEDED',
    childCounts: { total: 0 },
    allowedActions: ['OPEN'],
  });
  render(<Tasks />);
  expect(await screen.findByText('深链接任务')).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '团队任务' })).toBeInTheDocument();
  expect(mocks.list.mock.calls[0][1]).toMatchObject({
    scope: 'team',
    page: 2,
    pageSize: 50,
  });
  expect(
    screen.queryByRole('button', { name: '取消任务' }),
  ).not.toBeInTheDocument();
});

it('discards old team responses and selection even when the transport ignores abort', async () => {
  let resolveOld: (value: unknown) => void = () => {};
  mocks.list.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveOld = resolve;
      }),
  );
  const view = render(<Tasks />);
  const oldSignal = mocks.list.mock.calls[0][2];
  mocks.tenantId = 2;
  mocks.list.mockResolvedValue({
    items: [],
    total: 0,
    canViewTeamTasks: false,
  });
  view.rerender(<Tasks />);
  await waitFor(() =>
    expect(mocks.list).toHaveBeenCalledWith(
      2,
      expect.objectContaining({ scope: 'mine' }),
      expect.anything(),
    ),
  );
  await act(async () =>
    resolveOld({
      items: [{ taskKey: 'REVIEW:9', title: '旧团队机密' }],
      total: 1,
    }),
  );
  expect(oldSignal.aborted).toBe(true);
  expect(screen.queryByText('旧团队机密')).not.toBeInTheDocument();
});

it('polls active results, stops after terminal state, and clears requests on exit', async () => {
  vi.useFakeTimers();
  const page = (state: string) => ({
    items: [{ taskKey: 'REVIEW:1', title: '轮询任务', statusGroup: state }],
    total: 1,
  });
  mocks.list
    .mockResolvedValueOnce(page('RUNNING'))
    .mockResolvedValue(page('SUCCEEDED'));
  const view = render(<Tasks />);
  await act(async () => {});
  expect(mocks.list).toHaveBeenCalledTimes(1);
  const visibility = vi
    .spyOn(document, 'visibilityState', 'get')
    .mockReturnValue('hidden');
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5000);
  });
  expect(mocks.list).toHaveBeenCalledTimes(1);
  visibility.mockReturnValue('visible');
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5000);
  });
  expect(mocks.list).toHaveBeenCalledTimes(2);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(15000);
  });
  expect(mocks.list).toHaveBeenCalledTimes(2);
  view.unmount();
  expect(mocks.list.mock.calls[1][2].aborted).toBe(true);
});

it('reuses regeneration idempotency after a lost response', async () => {
  window.history.replaceState({}, '', '/tasks?task=IMAGE:1');
  mocks.detail.mockResolvedValue({
    taskKey: 'IMAGE:1',
    title: '图片任务',
    statusGroup: 'SUCCEEDED',
    childCounts: { total: 0 },
    allowedActions: ['REGENERATE'],
  });
  mocks.control
    .mockRejectedValueOnce(new Error('连接中断'))
    .mockResolvedValue({ taskKey: 'IMAGE:2' });
  render(<Tasks />);
  fireEvent.click(await screen.findByRole('button', { name: '重新生成' }));
  await screen.findByText('连接中断');
  fireEvent.click(await screen.findByRole('button', { name: /重新生成/ }));
  await waitFor(() => expect(mocks.control).toHaveBeenCalledTimes(2));
  expect(mocks.control.mock.calls[0][3]).toBe(mocks.control.mock.calls[1][3]);
});
