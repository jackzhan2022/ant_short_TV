import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  start: vi.fn(),
  importEpisode: vi.fn(),
  projects: vi.fn(),
  versions: vi.fn(),
}));
vi.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
}));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
vi.mock('@/services/account-team/project', () => ({
  queryProjects: mocks.projects,
}));
vi.mock('../projects/production-workbench/service', () => ({
  queryEpisodeVideoVersions: mocks.versions,
}));
vi.mock('./service', () => ({ importEpisode: mocks.importEpisode }));

import ImportEpisode from './ImportEpisode';

describe('本地单集上传', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.importEpisode.mockResolvedValue({ id: 1 });
  });
  it('必须完成真实上传会话校验，再使用 sessionToken 导入，且不附带项目绑定', async () => {
    let complete: (value: { sessionToken: string }) => void = () => undefined;
    const attempt = new Promise<{ sessionToken: string }>((resolve) => {
      complete = resolve;
    });
    const cancel = vi.fn();
    mocks.start.mockResolvedValue({ attempt, cancel });
    const close = vi.fn();
    const imported = vi.fn().mockResolvedValue(undefined);
    const { container } = render(
      <App>
        <ImportEpisode
          dramaId={7}
          nextNo={2}
          onClose={close}
          onImported={imported}
        />
      </App>,
    );
    fireEvent.change(screen.getByLabelText('单集标题'), {
      target: { value: '第二集' },
    });
    const file = new File(['video-content'], 'episode.mp4', {
      type: 'video/mp4',
    });
    const input = container.ownerDocument.querySelector('input[type="file"]');
    expect(input).not.toBeNull();
    fireEvent.change(input as HTMLInputElement, { target: { files: [file] } });
    await screen.findByText('episode.mp4');
    fireEvent.click(screen.getByText('上传并校验'));
    await waitFor(() =>
      expect(mocks.start).toHaveBeenCalledWith(
        file,
        expect.objectContaining({ onProgress: expect.any(Function) }),
      ),
    );
    expect(mocks.start.mock.calls[0][1].projectId).toBeUndefined();
    expect(mocks.importEpisode).not.toHaveBeenCalled();
    complete({ sessionToken: 'verified-token' });
    await waitFor(() =>
      expect(screen.getByText('上传并校验').closest('button')).toBeDisabled(),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'OK' })).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(mocks.importEpisode).toHaveBeenCalledWith(7, {
        episodeNo: 2,
        title: '第二集',
        uploadSessionToken: 'verified-token',
      }),
    );
    expect(imported).toHaveBeenCalled();
    expect(close).toHaveBeenCalled();
  });
});
