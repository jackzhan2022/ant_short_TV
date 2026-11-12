import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { StrictMode } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  preflight: vi.fn(),
  publish: vi.fn(),
  releases: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.mock('./service', () => ({
  preflightDrama: mocks.preflight,
  publishDrama: mocks.publish,
  listReleases: mocks.releases,
}));

import PublishDrama from './PublishDrama';

const props = {
  dramaId: 7,
  app: {
    id: 2,
    name: '目标',
    appid: 'wx1',
    enabled: true,
    bindingType: 'MANUAL',
    createdAt: '',
  },
  episodeIds: [11, 13],
  onClose: vi.fn(),
  onPublished: vi.fn(),
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.publish.mockResolvedValue({ id: 23 });
  mocks.releases.mockResolvedValue([]);
  props.onPublished.mockResolvedValue(undefined);
});
it('补发新集沿用当前在架版本排序及推荐，不使用旧版本或其他剧配置', async () => {
  mocks.releases.mockResolvedValue([
    { id: 18, dramaId: 7, published: false, sortOrder: 0, recommended: false },
    { id: 19, dramaId: 8, published: true, sortOrder: 2, recommended: false },
    { id: 20, dramaId: 7, published: true, sortOrder: -12, recommended: true },
  ]);
  mocks.preflight.mockResolvedValue({
    valid: true,
    errors: [],
    warnings: [],
    added: [3],
    removed: [],
    changed: [],
    metadataChanged: false,
    previousReleaseId: 20,
  });
  render(
    <App>
      <PublishDrama {...props} episodeIds={[11, 13, 15]} />
    </App>,
  );
  expect(await screen.findByDisplayValue('-12')).toBeInTheDocument();
  expect(screen.getByRole('switch')).toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: '执行预检' }));
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '确认发布' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '确认发布' }));
  await waitFor(() =>
    expect(mocks.publish).toHaveBeenCalledWith(2, 7, [11, 13, 15], {
      sortOrder: -12,
      recommended: true,
    }),
  );
});
it('必须预检通过并展示差异，修改排序后需重新预检再发布', async () => {
  mocks.preflight.mockResolvedValue({
    valid: true,
    errors: [],
    warnings: ['缺少封面'],
    added: [3],
    removed: [2],
    changed: [1],
    metadataChanged: true,
    previousReleaseId: 20,
  });
  render(
    <App>
      <PublishDrama {...props} />
    </App>,
  );
  expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '执行预检' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '执行预检' }));
  await screen.findByText('缺少封面');
  expect(screen.getByText('新增集号：3')).toBeInTheDocument();
  expect(screen.getByText('上一内容版本：#20')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('目录排序'), {
    target: { value: '9' },
  });
  expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '执行预检' }));
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '确认发布' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '确认发布' }));
  await waitFor(() =>
    expect(mocks.publish).toHaveBeenCalledWith(2, 7, [11, 13], {
      sortOrder: 9,
      recommended: false,
    }),
  );
  expect(props.onPublished).toHaveBeenCalled();
});
it('服务端校验失败不允许发布', async () => {
  mocks.preflight.mockResolvedValue({
    valid: false,
    errors: ['媒体不可用'],
    warnings: [],
    added: [],
    removed: [],
    changed: [],
    metadataChanged: false,
    previousReleaseId: null,
  });
  render(
    <App>
      <PublishDrama {...props} />
    </App>,
  );
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '执行预检' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '执行预检' }));
  await screen.findByText('媒体不可用');
  expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  expect(mocks.publish).not.toHaveBeenCalled();
});
it('严格模式重复挂载仍接收预检结果', async () => {
  mocks.preflight.mockResolvedValue({
    valid: true,
    errors: [],
    warnings: [],
    added: [1],
    removed: [],
    changed: [],
    metadataChanged: false,
    previousReleaseId: null,
  });
  render(
    <StrictMode>
      <App>
        <PublishDrama {...props} />
      </App>
    </StrictMode>,
  );
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '执行预检' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: '执行预检' }));
  expect(await screen.findByText('新增集号：1')).toBeInTheDocument();
});
it('当前发布设置加载失败时阻止预检，重试成功后才允许操作', async () => {
  mocks.releases
    .mockRejectedValueOnce(new Error('网络失败'))
    .mockResolvedValueOnce([]);
  render(
    <App>
      <PublishDrama {...props} />
    </App>,
  );
  await screen.findByText('当前发布设置加载失败，请重试');
  expect(screen.getByRole('button', { name: '执行预检' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '重试加载设置' }));
  await waitFor(() =>
    expect(screen.getByRole('button', { name: '执行预检' })).toBeEnabled(),
  );
  expect(mocks.releases).toHaveBeenCalledTimes(2);
});
