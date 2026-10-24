import '@root/tests/mockPointEstimate';
import { focusManager } from '@tanstack/react-query';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import VideoScriptDecompositionPage, {
  buildCopyAllScreenplays,
  buildDefaultVideoDecompositionBatchName,
  canCreateVideoDecompositionBatch,
  shouldPollVideoDecompositionBatch,
} from './index';

const mocks = vi.hoisted(() => ({
  success: vi.fn(),
  messageError: vi.fn(),
  queryBatches: vi.fn(),
  queryModels: vi.fn(),
  queryScreenplays: vi.fn(),
  queryBatch: vi.fn(),
  queryEpisode: vi.fn(),
  create: vi.fn(),
  retry: vi.fn(),
  writeText: vi.fn(),
  upload: vi.fn(),
  uploadProps: undefined as any,
}));

vi.mock('@ant-design/icons', () => ({
  ArrowDownOutlined: () => null,
  ArrowUpOutlined: () => null,
  CloudUploadOutlined: () => null,
  CopyOutlined: () => null,
  DeleteOutlined: () => null,
  EyeOutlined: () => null,
  PlayCircleOutlined: () => null,
  ReloadOutlined: () => null,
}));
vi.mock('@ant-design/x-markdown', () => ({
  default: ({ children }: any) => <article>{children}</article>,
}));
vi.mock('antd', () => ({
  Tooltip: ({ children }: any) => <>{children}</>,
  App: {
    useApp: () => ({
      message: {
        success: mocks.success,
        error: mocks.messageError,
        warning: vi.fn(),
      },
    }),
  },
  Button: ({ children, disabled, onClick, 'aria-label': label }: any) => (
    <button
      aria-label={label}
      disabled={disabled}
      type="button"
      onClick={onClick}
    >
      {children}
    </button>
  ),
  Col: ({ children }: any) => <div>{children}</div>,
  Collapse: ({ items = [] }: any) => (
    <div>
      {items.map((item: any) => (
        <section key={item.key}>
          <h3>{item.label}</h3>
          {item.extra}
          {item.children}
        </section>
      ))}
    </div>
  ),
  Skeleton: () => <div>加载中</div>,
  Drawer: ({ children, extra, open, title, onClose }: any) =>
    open ? (
      <aside>
        <h2>{title}</h2>
        {extra}
        <button type="button" onClick={onClose}>
          关闭
        </button>
        {children}
      </aside>
    ) : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Form: Object.assign(({ children }: any) => <form>{children}</form>, {
    Item: ({ children, label }: any) => (
      <div>
        {label}
        {children}
      </div>
    ),
  }),
  Input: ({ value, onChange }: any) => (
    <input value={value} onChange={onChange} />
  ),
  Progress: ({ percent }: any) => <span>进度 {percent}%</span>,
  Row: ({ children }: any) => <div>{children}</div>,
  Select: ({ value, onChange, options = [] }: any) => (
    <select
      aria-label="视频理解模型"
      value={value ?? ''}
      onChange={(event) => onChange(Number(event.target.value))}
    >
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Space: ({ children }: any) => <span>{children}</span>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
  },
  Upload: {
    LIST_IGNORE: 'LIST_IGNORE',
    Dragger: (props: any) => {
      mocks.uploadProps = props;
      return <div>{props.children}</div>;
    },
  },
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, title }: any) => (
    <main>
      <h1>{title}</h1>
      {children}
    </main>
  ),
  ProTable: ({
    columns = [],
    dataSource = [],
    headerTitle,
    toolBarRender,
  }: any) => (
    <section>
      <h2>{headerTitle}</h2>
      {toolBarRender?.()}
      {dataSource.map((record: any) => (
        <div key={record.id ?? record.uid}>
          {columns.map((column: any, index: number) => {
            const value = column.dataIndex
              ? record[column.dataIndex]
              : undefined;
            return (
              <div key={column.dataIndex ?? index}>
                {column.render
                  ? column.render(value, record)
                  : column.renderText
                    ? column.renderText(value)
                    : value}
              </div>
            );
          })}
        </div>
      ))}
    </section>
  ),
}));

vi.mock('./service', () => ({
  createVideoDecompositionBatch: mocks.create,
  queryVideoDecompositionBatch: mocks.queryBatch,
  queryVideoDecompositionScreenplay: mocks.queryEpisode,
  queryVideoDecompositionBatches: async (...args: unknown[]) => {
    const response = await mocks.queryBatches(...args);
    return {
      ...response,
      data: Array.isArray(response.data)
        ? {
            data: response.data,
            current: 1,
            pageSize: 20,
            total: response.data.length,
          }
        : response.data,
    };
  },
  queryVideoDecompositionBatchScreenplays: mocks.queryScreenplays,
  queryVideoUnderstandingModels: mocks.queryModels,
  retryVideoDecompositionEpisode: mocks.retry,
  uploadEpisodeVideo: mocks.upload,
}));

const screenplayBatch = {
  batchId: 9,
  batchName: '第一季拆剧',
  status: 'PARTIAL_FAILED',
  percentage: 100,
  totalEpisodes: 2,
  succeededEpisodes: 1,
  failedEpisodes: 1,
  processingEpisodes: 0,
  pendingEpisodes: 0,
  episodes: [
    {
      episode: {
        id: 88,
        batchId: 9,
        episodeNo: 1,
        sourceFileName: '1.mp4',
        storagePath: '/1.mp4',
        fileSize: 1,
        status: 'SUCCEEDED',
        percentage: 100,
      },
      screenplayContent: '# 第1集：真相',
      formatVersion: 'markdown-screenplay-v1',
    },
    {
      episode: {
        id: 89,
        batchId: 9,
        episodeNo: 2,
        sourceFileName: '2.mp4',
        storagePath: '/2.mp4',
        fileSize: 1,
        status: 'FAILED',
        percentage: 100,
        retryable: true,
        errorMessage: '模型输出截断',
      },
    },
  ],
};

describe('VideoScriptDecompositionPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    focusManager.setFocused(true);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: mocks.writeText },
    });
    mocks.queryModels.mockResolvedValue({
      data: [
        {
          id: 42,
          name: '千问视频理解',
          modelCode: 'qwen3.7-plus',
          serviceType: 'VIDEO_UNDERSTANDING',
          status: 'ENABLED',
          isDefault: true,
        },
      ],
    });
    mocks.queryBatches.mockResolvedValue({
      data: [
        {
          id: 9,
          name: '第一季拆剧',
          status: 'PARTIAL_FAILED',
          percentage: 100,
          totalEpisodes: 2,
          completedEpisodes: 1,
          succeededEpisodes: 1,
          failedEpisodes: 1,
          processingEpisodes: 0,
          pendingEpisodes: 0,
          episodes: [],
        },
      ],
    });
    mocks.queryScreenplays.mockResolvedValue({ data: screenplayBatch });
    mocks.queryBatch.mockResolvedValue({
      data: {
        ...screenplayBatch,
        id: 9,
        name: screenplayBatch.batchName,
        episodes: screenplayBatch.episodes.map((item) => item.episode),
      },
    });
    mocks.queryEpisode.mockResolvedValue({ data: screenplayBatch.episodes[0] });
    mocks.retry.mockResolvedValue({ data: {} });
    mocks.writeText.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    focusManager.setFocused(undefined);
  });

  it('retries a failed file with its retained upload handle', async () => {
    const file = new File(['video'], 'episode.mp4', { type: 'video/mp4' });
    Object.assign(file, { uid: 'file-1' });
    const handle = { cancel: vi.fn() };
    mocks.upload
      .mockImplementationOnce(async (_file, _progress, recovery) => {
        recovery?.onHandle?.(handle);
        throw new Error('上传已断开');
      })
      .mockResolvedValue({
        data: {
          storagePath: 'materials/video.mp4',
          uploadSessionToken: 'session-1',
        },
      });
    const view = render(<VideoScriptDecompositionPage />);
    await act(async () => {
      await mocks.uploadProps.customRequest({
        file,
        onError: vi.fn(),
        onSuccess: vi.fn(),
      });
      mocks.uploadProps.onChange({
        fileList: [
          {
            uid: 'file-1',
            name: file.name,
            originFileObj: file,
            status: 'error',
          },
        ],
      });
    });
    fireEvent.click(screen.getByRole('button', { name: '重试上传' }));
    await waitFor(() => expect(mocks.upload).toHaveBeenCalledTimes(2));
    expect(mocks.upload.mock.calls[1][2].handle).toBe(handle);
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: '重试上传' }),
      ).not.toBeInTheDocument(),
    );
    view.unmount();
  });

  it('removes the former top workflow and opens ordered read-only screenplays', async () => {
    render(<VideoScriptDecompositionPage />);
    expect(screen.queryByText(/审核草稿/)).not.toBeInTheDocument();
    expect(screen.queryByText(/确认导入/)).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    expect(await screen.findByText('# 第1集：真相')).toBeInTheDocument();
    expect(screen.queryByText('模型输出截断')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    expect(screen.getByText('模型输出截断')).toBeInTheDocument();
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
    expect(screen.getByText(/总进度 100%/)).toBeInTheDocument();
  });

  it('copies successful scripts and retries only the retryable failed episode', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '复制全部剧本' }),
      ).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: '复制全部剧本' }));
    await waitFor(() =>
      expect(mocks.writeText).toHaveBeenCalledWith('# 第1集：真相'),
    );

    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    fireEvent.click(screen.getByRole('button', { name: '重试本集' }));
    await waitFor(() => expect(mocks.retry).toHaveBeenCalledWith(89));
  });

  it('reports clipboard permission failures', async () => {
    mocks.writeText.mockRejectedValueOnce(new Error('denied'));
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: '复制全部剧本' }),
      ).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole('button', { name: '复制全部剧本' }));
    await waitFor(() =>
      expect(mocks.messageError).toHaveBeenCalledWith(
        '复制失败，请检查网络或浏览器剪贴板权限',
      ),
    );
  });

  it('maps polling and copy behavior without a server-side merge', () => {
    expect(shouldPollVideoDecompositionBatch('RUNNING')).toBe(true);
    expect(shouldPollVideoDecompositionBatch('PARTIAL_FAILED')).toBe(false);
    expect(
      buildCopyAllScreenplays({
        ...screenplayBatch,
        episodes: [...screenplayBatch.episodes].reverse(),
      }),
    ).toBe('# 第1集：真相');
    expect(
      buildDefaultVideoDecompositionBatchName(new Date('2026-08-24T01:23:45')),
    ).toBe('拆剧批次-20260824-012345');
    expect(
      canCreateVideoDecompositionBatch([
        {
          uid: '1',
          episodeNo: 1,
          fileName: '1.mp4',
          size: 1,
          status: 'READY',
          storagePath: '/1.mp4',
        },
      ]),
    ).toBe(true);
  });

  it('loads only the selected successful episode and reuses its cache', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    await screen.findByText('# 第1集：真相');
    expect(mocks.queryEpisode).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    expect(screen.getByText('模型输出截断')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '第 1 集' }));
    expect(screen.getByText('# 第1集：真相')).toBeInTheDocument();
    expect(mocks.queryEpisode).toHaveBeenCalledTimes(1);
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
  });

  it('cancels a closed batch and ignores its late response in another batch', async () => {
    mocks.queryBatches.mockResolvedValue({
      data: [
        { id: 9, name: '旧批次', status: 'SUCCEEDED' },
        { id: 10, name: '新批次', status: 'SUCCEEDED' },
      ],
    });
    let resolveOld: (value: unknown) => void = () => {};
    mocks.queryBatch.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
    );
    mocks.queryBatch.mockResolvedValue({
      data: { ...screenplayBatch, id: 10, name: '新批次', episodes: [] },
    });
    render(<VideoScriptDecompositionPage />);
    const buttons = await screen.findAllByRole('button', {
      name: '查看全部剧本',
    });
    fireEvent.click(buttons[0]);
    const signal = mocks.queryBatch.mock.calls[0][1] as AbortSignal;
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(signal.aborted).toBe(true);
    fireEvent.click(buttons[1]);
    await waitFor(() =>
      expect(screen.getByText('暂无剧集')).toBeInTheDocument(),
    );
    await act(async () =>
      resolveOld({
        data: { ...screenplayBatch, id: 9, name: '旧批次结果', episodes: [] },
      }),
    );
    expect(screen.queryByText('旧批次结果')).not.toBeInTheDocument();
  });

  it('refreshes metadata without overlapping slow calls, downloading text again, or polling hidden tabs', async () => {
    vi.useFakeTimers();
    const running = {
      ...screenplayBatch,
      id: 9,
      name: '第一季拆剧',
      status: 'RUNNING',
      episodes: [
        screenplayBatch.episodes[0].episode,
        { ...screenplayBatch.episodes[1].episode, status: 'ANALYZING' },
      ],
    };
    let finishSlow: (value: unknown) => void = () => {};
    mocks.queryBatch
      .mockResolvedValueOnce({ data: running })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishSlow = resolve;
          }),
      )
      .mockResolvedValue({ data: { ...running, status: 'SUCCEEDED' } });
    const view = render(<VideoScriptDecompositionPage />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    fireEvent.click(screen.getByRole('button', { name: '查看全部剧本' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    expect(screen.getByText('# 第1集：真相')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3100);
    });
    expect(mocks.queryBatch).toHaveBeenCalledTimes(2);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(mocks.queryBatch).toHaveBeenCalledTimes(2);
    await act(async () => {
      finishSlow({ data: running });
      await vi.advanceTimersByTimeAsync(20);
    });
    focusManager.setFocused(false);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(mocks.queryBatch).toHaveBeenCalledTimes(2);
    await act(async () => {
      focusManager.setFocused(true);
      await vi.advanceTimersByTimeAsync(20);
    });
    expect(mocks.queryBatch).toHaveBeenCalledTimes(3);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(mocks.queryBatch).toHaveBeenCalledTimes(3);
    expect(mocks.queryEpisode).toHaveBeenCalledTimes(1);
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
    expect(mocks.queryBatches).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('loads a retried episode automatically when its metadata becomes successful', async () => {
    vi.useFakeTimers();
    const episodes = screenplayBatch.episodes.map((item) => item.episode);
    mocks.queryBatch
      .mockResolvedValueOnce({ data: { ...screenplayBatch, id: 9, episodes } })
      .mockResolvedValueOnce({
        data: {
          ...screenplayBatch,
          id: 9,
          status: 'RUNNING',
          episodes: [episodes[0], { ...episodes[1], status: 'ANALYZING' }],
        },
      })
      .mockResolvedValue({
        data: {
          ...screenplayBatch,
          id: 9,
          status: 'SUCCEEDED',
          episodes: [episodes[0], { ...episodes[1], status: 'SUCCEEDED' }],
        },
      });
    mocks.queryEpisode.mockImplementation(async (id: number) => ({
      data: {
        episode: episodes[id === 88 ? 0 : 1],
        screenplayContent: id === 88 ? '# 第1集：真相' : '# 第2集：重试成功',
      },
    }));
    const view = render(<VideoScriptDecompositionPage />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    fireEvent.click(screen.getByRole('button', { name: '查看全部剧本' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    fireEvent.click(screen.getByRole('button', { name: '重试本集' }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    expect(screen.queryByText('模型输出截断')).not.toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3100);
    });
    expect(screen.getByText('# 第2集：重试成功')).toBeInTheDocument();
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
    view.unmount();
  });

  it('opens a newly created batch directly with the submitted file order', async () => {
    mocks.create.mockResolvedValue({
      data: { ...screenplayBatch, id: 9, name: '新批次', episodes: [] },
    });
    render(<VideoScriptDecompositionPage />);
    await screen.findByRole('button', { name: '查看全部剧本' });
    await act(async () =>
      mocks.uploadProps.onChange({
        fileList: [
          {
            uid: 'b',
            name: 'b.mp4',
            size: 1,
            status: 'done',
            response: { storagePath: '/b', uploadSessionToken: 'b' },
          },
          {
            uid: 'a',
            name: 'a.mp4',
            size: 1,
            status: 'done',
            response: { storagePath: '/a', uploadSessionToken: 'a' },
          },
        ],
      }),
    );
    expect(mocks.uploadProps.showUploadList).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: '开始生成剧本' }));
    await screen.findByRole('button', { name: '关闭' });
    expect(
      mocks.create.mock.calls[0][0].videos.map((v: any) => v.fileName),
    ).toEqual(['b.mp4', 'a.mp4']);
  });
});
