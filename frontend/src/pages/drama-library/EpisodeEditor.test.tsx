import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  preview: vi.fn(),
  inspect: vi.fn(),
  update: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.mock('./service', () => ({
  getEpisodePreview: mocks.preview,
  inspectEpisode: mocks.inspect,
  updateEpisode: mocks.update,
}));

import EpisodeEditor from './EpisodeEditor';

it('使用签名地址预览，服务端分析分辨率和时长，保存标题封面', async () => {
  const episode = {
    id: 11,
    dramaId: 7,
    episodeNo: 1,
    title: '原标题',
    sourceType: 'LOCAL_UPLOAD',
    createdAt: '',
  };
  mocks.preview.mockResolvedValue({
    dramaId: 7,
    episodeId: 11,
    url: 'https://private.example/signed',
    expiresAt: '2026-11-12T12:00:00Z',
  });
  mocks.inspect.mockResolvedValue({
    ...episode,
    durationSeconds: 33.5,
    width: 720,
    height: 1280,
  });
  mocks.update.mockResolvedValue(episode);
  const saved = vi.fn().mockResolvedValue(undefined);
  render(
    <App>
      <EpisodeEditor
        dramaId={7}
        episode={episode}
        canEdit
        onClose={vi.fn()}
        onSaved={saved}
      />
    </App>,
  );
  await waitFor(() =>
    expect(document.querySelector('video')).toHaveAttribute(
      'src',
      'https://private.example/signed',
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: '分析媒体信息' }));
  await screen.findByText('720 × 1280');
  expect(screen.getByText('33.5 秒')).toBeInTheDocument();
  await screen.findByRole('button', { name: /^保\s*存$/ });
  fireEvent.change(screen.getByLabelText('单集标题'), {
    target: { value: '新标题' },
  });
  fireEvent.change(screen.getByLabelText('单集封面 HTTPS 地址'), {
    target: { value: 'https://cdn.example/poster.jpg' },
  });
  fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }));
  await waitFor(() =>
    expect(mocks.update).toHaveBeenCalledWith(7, 11, {
      title: '新标题',
      posterUrl: 'https://cdn.example/poster.jpg',
    }),
  );
});
