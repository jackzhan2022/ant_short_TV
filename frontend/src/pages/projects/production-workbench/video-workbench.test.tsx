import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import VideoWorkbench from './video-workbench';

const mocks = vi.hoisted(() => ({
  queryStoryboardWorkspace: vi.fn(),
  queryStoryboardMedia: vi.fn(),
  queryEpisodeVideoVersions: vi.fn(),
  queryEpisodeComposeTasks: vi.fn(),
  createEpisodeComposeTask: vi.fn(),
  downloadEpisodeVideoVersion: vi.fn(),
  downloadStoryboardVideo: vi.fn(),
  downloadEpisodeStoryboardVideos: vi.fn(),
  push: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}));
vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('./service', () => mocks);
vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () => 10,
}));
vi.mock('./ShotProductionWorkspace', () => ({
  default: () => <div>原有语音字幕与单镜头任务</div>,
}));
vi.mock('./StableImage', () => ({
  default: ({ src, alt }: any) => <img src={src} alt={alt} />,
}));
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    App: {
      useApp: () => ({
        message: { success: mocks.success, error: mocks.error },
      }),
    },
  };
});
const shots = [
  {
    id: 31,
    episodeNo: 1,
    shotNo: 1,
    durationSeconds: 8,
    currentShotVideoUrl: '/composed.mp4',
    currentVideoUrl: '/raw.mp4',
    firstFrameUrl: '/1.jpg',
  },
  {
    id: 32,
    episodeNo: 1,
    shotNo: 2,
    durationSeconds: 6,
    firstFrameUrl: '/2.jpg',
  },
];
const page = (episodeNo = 1, storyboards = shots) => ({
  data: {
    projectId: 44,
    episodes: [
      { episodeNo: 1, title: '初见' },
      { episodeNo: 2, title: '重逢' },
    ],
    episodeNo,
    current: 1,
    pageSize: 100,
    total: storyboards.length,
    storyboards,
  },
});
const empty = { data: { data: [], total: 0, current: 1, pageSize: 100 } };

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  mocks.queryStoryboardWorkspace.mockImplementation((_id, params) =>
    Promise.resolve(
      params?.episodeNo === 2
        ? page(2, [{ ...shots[0], id: 41, episodeNo: 2 }])
        : page(),
    ),
  );
  mocks.queryStoryboardMedia.mockResolvedValue({
    data: { videoTasks: [], imageTasks: [], voiceTasks: [] },
  });
  mocks.queryEpisodeVideoVersions.mockResolvedValue(empty);
  mocks.queryEpisodeComposeTasks.mockResolvedValue(empty);
  Object.defineProperty(HTMLMediaElement.prototype, 'pause', {
    configurable: true,
    value: vi.fn(),
  });
  Object.defineProperty(HTMLMediaElement.prototype, 'load', {
    configurable: true,
    value: vi.fn(),
  });
  Object.defineProperty(HTMLMediaElement.prototype, 'play', {
    configurable: true,
    value: vi.fn().mockResolvedValue(undefined),
  });
});

describe('video preview workbench', () => {
  it('shows a preview and shot strip, not task tables or an inline editor', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    expect(
      await screen.findByRole('button', { name: '分镜1，已就绪' }),
    ).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('当前视频')).toHaveAttribute(
      'src',
      '/composed.mp4',
    );
    expect(
      screen.queryByText('原有语音字幕与单镜头任务'),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '分镜2，待生成' }));
    expect(screen.getByText('该分镜尚未生成视频')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '编辑分镜' }));
    expect(mocks.push).toHaveBeenCalledWith(
      '/projects/44/production-workbench/storyboard?episodeNo=1&current=1&storyboardId=32',
    );
    expect(screen.queryByText('合成素材')).not.toBeInTheDocument();
  });
  it('switches episodes and restores the selected shot after returning', async () => {
    const first = render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    fireEvent.click(screen.getByRole('button', { name: '第2集 重逢' }));
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '第2集 重逢' }),
      ).toHaveAttribute('aria-pressed', 'true'),
    );
    await waitFor(() =>
      expect(mocks.queryStoryboardMedia).toHaveBeenCalledWith(44, [41]),
    );
    first.unmount();
    render(<VideoWorkbench projectId={44} canEdit />);
    await waitFor(() =>
      expect(mocks.queryStoryboardWorkspace).toHaveBeenLastCalledWith(44, {
        episodeNo: 2,
        current: 1,
        pageSize: 100,
      }),
    );
    expect(
      await screen.findByRole('button', { name: '第2集 重逢' }),
    ).toHaveAttribute('aria-pressed', 'true');
  });
  it('blocks incomplete episode composition and exposes existing tasks on demand', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    expect(screen.getByRole('button', { name: '合成整集' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    expect(
      await screen.findByRole('menuitem', { name: '下载合并视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
    expect(
      screen.getByRole('menuitem', { name: '下载分镜视频' }),
    ).not.toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(screen.getByRole('button', { name: '任务记录' }));
    expect(
      await screen.findByText('原有语音字幕与单镜头任务'),
    ).toBeInTheDocument();
  });
  it('previews and downloads persisted episode output, not individual shots', async () => {
    mocks.queryEpisodeVideoVersions.mockResolvedValue({
      data: {
        ...empty.data,
        data: [
          {
            id: 9,
            current: true,
            versionName: '成片 v1',
            videoUrl: '/episode.mp4',
          },
        ],
      },
    });
    render(<VideoWorkbench projectId={44} canEdit />);
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '整集成片' }),
      ).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: '整集成片' }));
    expect(screen.getByLabelText('当前视频')).toHaveAttribute(
      'src',
      '/episode.mp4',
    );
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    fireEvent.click(
      await screen.findByRole('menuitem', { name: '下载合并视频' }),
    );
    await waitFor(() =>
      expect(mocks.downloadEpisodeVideoVersion).toHaveBeenCalledWith(44, 9),
    );
  });
  it('does not show success when the server rejects composition validation', async () => {
    mocks.queryStoryboardWorkspace.mockResolvedValue(page(1, [shots[0]]));
    mocks.createEpisodeComposeTask.mockResolvedValue({
      data: {
        id: 7,
        status: 'VALIDATION_FAILED',
        errorMessage: '视频比例不一致',
      },
    });
    render(<VideoWorkbench projectId={44} canEdit />);
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '合成整集' }),
      ).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: '合成整集' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认合成' }));
    await waitFor(() =>
      expect(mocks.error).toHaveBeenCalledWith('视频比例不一致'),
    );
    expect(mocks.success).not.toHaveBeenCalled();
  });
  it('does not allow read-only users to compose', async () => {
    mocks.queryStoryboardWorkspace.mockResolvedValue(page(1, [shots[0]]));
    render(<VideoWorkbench projectId={44} canEdit={false} />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    expect(screen.getByRole('button', { name: '合成整集' })).toBeDisabled();
  });
  it('ignores a slow response from an episode the user has left', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    let finish: (value: any) => void = () => {};
    mocks.queryStoryboardWorkspace.mockImplementation((_id, params) =>
      params?.episodeNo === 2
        ? new Promise((resolve) => {
            finish = resolve;
          })
        : Promise.resolve(page()),
    );
    fireEvent.click(screen.getByRole('button', { name: '第2集 重逢' }));
    fireEvent.click(screen.getByRole('button', { name: '第1集 初见' }));
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    await act(async () =>
      finish(page(2, [{ ...shots[0], id: 41, episodeNo: 2 }])),
    );
    expect(screen.getByRole('button', { name: '第1集 初见' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    fireEvent.click(screen.getByRole('button', { name: '编辑分镜' }));
    expect(mocks.push).toHaveBeenLastCalledWith(
      '/projects/44/production-workbench/storyboard?episodeNo=1&current=1&storyboardId=31',
    );
  });
  it('shows a recoverable load error instead of an empty episode', async () => {
    mocks.queryStoryboardWorkspace.mockRejectedValue(new Error('offline'));
    render(<VideoWorkbench projectId={44} canEdit />);
    expect(await screen.findByRole('alert')).toHaveTextContent('分镜加载失败');
    mocks.queryStoryboardWorkspace.mockResolvedValue(page());
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(
      await screen.findByRole('button', { name: '分镜1，已就绪' }),
    ).toBeInTheDocument();
  });
  it('downloads the selected shot without merging or creating a task', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    fireEvent.click(
      await screen.findByRole('menuitem', { name: '下载分镜视频' }),
    );
    await waitFor(() =>
      expect(mocks.downloadStoryboardVideo).toHaveBeenCalledWith(44, 31),
    );
    expect(mocks.createEpisodeComposeTask).not.toHaveBeenCalled();
  });
  it('downloads all videos only for the current episode', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    fireEvent.click(screen.getByRole('button', { name: '第2集 重逢' }));
    await waitFor(() =>
      expect(mocks.queryStoryboardMedia).toHaveBeenCalledWith(44, [41]),
    );
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    fireEvent.click(
      await screen.findByRole('menuitem', { name: '下载所有视频' }),
    );
    await waitFor(() =>
      expect(mocks.downloadEpisodeStoryboardVideos).toHaveBeenCalledWith(44, 2),
    );
    expect(mocks.downloadEpisodeStoryboardVideos).not.toHaveBeenCalledWith(
      44,
      1,
    );
  });
  it('disables only the unavailable shot and merged options, keeping ready shots downloadable', async () => {
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    fireEvent.click(screen.getByRole('button', { name: '分镜2，待生成' }));
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    expect(
      await screen.findByRole('menuitem', { name: '下载分镜视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
    expect(
      screen.getByRole('menuitem', { name: '下载合并视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
    expect(
      screen.getByRole('menuitem', { name: '下载所有视频' }),
    ).not.toHaveAttribute('aria-disabled', 'true');
  });
  it('reports a failed archive download and re-enables the menu', async () => {
    mocks.downloadEpisodeStoryboardVideos.mockRejectedValue(
      new Error('offline'),
    );
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜1，已就绪' });
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    fireEvent.click(
      await screen.findByRole('menuitem', { name: '下载所有视频' }),
    );
    await waitFor(() =>
      expect(mocks.error).toHaveBeenCalledWith('批量下载失败，请重试。'),
    );
    expect(screen.getByRole('button', { name: '下载' })).not.toBeDisabled();
  });
  it('keeps the dropdown visible and disables all options when this episode has no video', async () => {
    mocks.queryStoryboardWorkspace.mockResolvedValue(page(1, [shots[1]]));
    render(<VideoWorkbench projectId={44} canEdit />);
    await screen.findByRole('button', { name: '分镜2，待生成' });
    fireEvent.click(screen.getByRole('button', { name: '下载' }));
    expect(
      await screen.findByRole('menuitem', { name: '下载合并视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
    expect(
      screen.getByRole('menuitem', { name: '下载分镜视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
    expect(
      screen.getByRole('menuitem', { name: '下载所有视频' }),
    ).toHaveAttribute('aria-disabled', 'true');
  });
});
