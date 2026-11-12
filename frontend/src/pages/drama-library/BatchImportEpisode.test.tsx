import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ start: vi.fn(), import: vi.fn() }));
vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
vi.mock('./service', () => ({ importEpisode: mocks.import }));

import BatchImportEpisode from './BatchImportEpisode';

it('逐文件导入，重试失败项沿用已校验会话且跳过已成功文件', async () => {
  vi.clearAllMocks();
  mocks.start.mockImplementation(async (file: File) => ({
    attempt: Promise.resolve({ sessionToken: file.name }),
    cancel: vi.fn(),
  }));
  mocks.import
    .mockResolvedValueOnce({ id: 11 })
    .mockRejectedValueOnce(new Error('临时失败'))
    .mockResolvedValueOnce({ id: 12 });
  const imported = vi.fn().mockResolvedValue(undefined);
  const { container } = render(
    <App>
      <BatchImportEpisode
        dramaId={7}
        nextNo={3}
        onClose={vi.fn()}
        onImported={imported}
      />
    </App>,
  );
  const input = container.ownerDocument.querySelector('input[type="file"]');
  fireEvent.change(input as HTMLInputElement, {
    target: {
      files: [
        new File(['1'], 'a.mp4', { type: 'video/mp4' }),
        new File(['2'], 'b.mp4', { type: 'video/mp4' }),
      ],
    },
  });
  await screen.findByDisplayValue('a');
  fireEvent.click(screen.getByRole('button', { name: '开始批量导入' }));
  await screen.findByText('临时失败');
  expect(mocks.import).toHaveBeenCalledWith(7, {
    episodeNo: 3,
    title: 'a',
    uploadSessionToken: 'a.mp4',
  });
  fireEvent.click(screen.getByRole('button', { name: '重试失败项' }));
  await waitFor(() => expect(mocks.import).toHaveBeenCalledTimes(3));
  expect(mocks.start).toHaveBeenCalledTimes(2);
  expect(mocks.import.mock.calls[2]).toEqual([
    7,
    { episodeNo: 4, title: 'b', uploadSessionToken: 'b.mp4' },
  ]);
  await waitFor(() => expect(screen.getAllByText('已导入')).toHaveLength(2));
});
it('上传失败只重试原上传句柄，重复选择同一文件只保留一次', async () => {
  vi.clearAllMocks();
  const retry = vi
    .fn()
    .mockResolvedValue({ sessionToken: 'recovered-session' });
  mocks.start.mockImplementation(async () => ({
    attempt: Promise.reject(new Error('上传中断')),
    retry,
    cancel: vi.fn(),
  }));
  mocks.import.mockResolvedValue({ id: 11 });
  const { container } = render(
    <App>
      <BatchImportEpisode
        dramaId={7}
        nextNo={1}
        onClose={vi.fn()}
        onImported={vi.fn().mockResolvedValue(undefined)}
      />
    </App>,
  );
  const input = container.ownerDocument.querySelector(
    'input[type="file"]',
  ) as HTMLInputElement;
  const file = new File(['video'], 'first.mp4', {
    type: 'video/mp4',
    lastModified: 123,
  });
  fireEvent.change(input, { target: { files: [file, file] } });
  expect(await screen.findAllByDisplayValue('first')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: '开始批量导入' }));
  await screen.findByText('上传中断');
  expect(mocks.import).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '重试失败项' }));
  await screen.findByText('已导入');
  expect(mocks.start).toHaveBeenCalledTimes(1);
  expect(retry).toHaveBeenCalledTimes(1);
  expect(mocks.import).toHaveBeenCalledWith(7, {
    episodeNo: 1,
    title: 'first',
    uploadSessionToken: 'recovered-session',
  });
});
