import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import DownloadCenter from './index';

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  retry: vi.fn(),
  receive: vi.fn(),
  tenant: 1,
}));
vi.mock('@umijs/max', () => ({
  useModel: () => ({ initialState: { currentTenantId: mocks.tenant } }),
}));
vi.mock('./service', async () => ({
  ...(await vi.importActual('./service')),
  listDownloadTasks: mocks.list,
  retryDownloadTask: mocks.retry,
  receiveDownload: mocks.receive,
}));
const base = {
  name: '创作文件',
  sourceType: 'FREE_CREATION',
  fileCount: 2,
  createdAt: '2026-10-28T01:00:00Z',
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.tenant = 1;
  mocks.list.mockResolvedValue({
    items: [
      {
        ...base,
        id: 'ready',
        status: 'READY',
        expiresAt: '2099-10-29T02:00:00Z',
        fileSize: 2048,
      },
      {
        ...base,
        id: 'expired',
        status: 'READY',
        expiresAt: '2020-10-29T02:00:00Z',
      },
      { ...base, id: 'running', status: 'RUNNING' },
      { ...base, id: 'failed', status: 'FAILED', errorMessage: '文件不存在' },
    ],
    total: 4,
    page: 1,
    pageSize: 10,
  });
  mocks.receive.mockResolvedValue(undefined);
  mocks.retry.mockResolvedValue({ id: 'new-task' });
});
afterEach(cleanup);
it('shows completion-based expiry and only enables ready archives', async () => {
  render(
    <App>
      <DownloadCenter />
    </App>,
  );
  await screen.findByText('可下载');
  expect(screen.getByText('已过期')).toBeInTheDocument();
  expect(screen.getByText('正在打包')).toBeInTheDocument();
  expect(
    screen.getByText('下载文件自准备成功起保留 24 小时'),
  ).toBeInTheDocument();
  expect(screen.getAllByRole('button', { name: '下载文件' })).toHaveLength(1);
  expect(screen.getAllByRole('button', { name: '重新生成' })).toHaveLength(2);
  fireEvent.click(screen.getByRole('button', { name: '下载文件' }));
  await waitFor(() =>
    expect(mocks.receive).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ id: 'ready' }),
    ),
  );
});
it('regenerates expired tasks as new jobs', async () => {
  render(
    <App>
      <DownloadCenter />
    </App>,
  );
  await screen.findByText('已过期');
  fireEvent.click(screen.getAllByRole('button', { name: '重新生成' })[0]);
  await waitFor(() => expect(mocks.retry).toHaveBeenCalledWith(1, 'expired'));
});
it('clears previous-team tasks and ignores late responses when switching teams', async () => {
  let resolveOld!: (value: unknown) => void;
  mocks.list.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveOld = resolve;
      }),
  );
  const view = render(
    <App>
      <DownloadCenter />
    </App>,
  );
  mocks.tenant = 2;
  mocks.list.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 10 });
  view.rerender(
    <App>
      <DownloadCenter />
    </App>,
  );
  await waitFor(() =>
    expect(mocks.list).toHaveBeenCalledWith(
      2,
      expect.anything(),
      expect.any(AbortSignal),
    ),
  );
  await act(async () =>
    resolveOld({
      items: [{ ...base, id: 'old', name: '旧团队文件', status: 'READY' }],
      total: 1,
    }),
  );
  expect(screen.queryByText('旧团队文件.zip')).not.toBeInTheDocument();
});
