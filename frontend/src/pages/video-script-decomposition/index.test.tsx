import '@root/tests/mockPointEstimate';
import { focusManager } from '@tanstack/react-query';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useEffect } from 'react';
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
  uploadUnmount: vi.fn(),
  uploadProps: undefined as any,
}));

vi.mock('@ant-design/icons', () => ({
  ArrowDownOutlined: () => null,
  ArrowUpOutlined: () => null,
  CloudUploadOutlined: () => null,
  CopyOutlined: () => null,
  CheckOutlined: () => null,
  DeleteOutlined: () => null,
  EyeOutlined: () => null,
  FileTextOutlined: () => null,
  HistoryOutlined: () => null,
  PlayCircleOutlined: () => null,
  PlusOutlined: () => null,
  ReloadOutlined: () => null,
  RightOutlined: () => null,
  VideoCameraOutlined: () => null,
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
  Input: ({ value, onChange, 'aria-label': label }: any) => (
    <input aria-label={label} value={value} onChange={onChange} />
  ),
  Progress: ({ percent, format }: any) => (
    <span role="progressbar" aria-valuenow={percent}>
      {format ? format(percent) : `进度 ${percent}%`}
    </span>
  ),
  Pagination: ({ current, onChange }: any) => (
    <button type="button" onClick={() => onChange(current + 1, 20)}>
      下一页记录
    </button>
  ),
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
      useEffect(() => () => mocks.uploadUnmount(), []);
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
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:episode-preview');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
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
    vi.restoreAllMocks();
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

  it('starts with video intake and keeps history in a separate view', async () => {
    render(<VideoScriptDecompositionPage />);
    expect(
      screen.getByRole('heading', { name: '先选视频，再开始拆剧' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('第一季拆剧')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '生成剧本' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    expect(await screen.findByText('第一季拆剧')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: '生成记录' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /新建拆剧/ }));
    expect(
      screen.getByRole('heading', { name: '先选视频，再开始拆剧' }),
    ).toBeInTheDocument();
  });

  it('keeps user episode order across upload progress and submits the selected model', async () => {
    mocks.create.mockResolvedValue({
      data: { ...screenplayBatch, id: 9, name: '新批次', episodes: [] },
    });
    render(<VideoScriptDecompositionPage />);
    await screen.findByRole('option', { name: /千问视频理解/ });
    const fileList = [
      {
        uid: 'a',
        name: 'a.mp4',
        size: 1,
        status: 'done',
        response: { storagePath: '/a', uploadSessionToken: 'session-a' },
      },
      { uid: 'b', name: 'b.mp4', size: 2, status: 'uploading', percent: 20 },
    ];
    act(() => mocks.uploadProps.onChange({ fileList }));
    expect(
      screen.getByRole('region', { name: '视频预览' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下移第 1 集' }));
    act(() =>
      mocks.uploadProps.onChange({
        fileList: [
          fileList[0],
          {
            ...fileList[1],
            status: 'done',
            percent: 100,
            response: { storagePath: '/b', uploadSessionToken: 'session-b' },
          },
        ],
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: /生成 2 集剧本/ }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    expect(mocks.create.mock.calls[0][0]).toMatchObject({
      modelId: 42,
      videos: [
        { fileName: 'b.mp4', uploadSessionToken: 'session-b' },
        { fileName: 'a.mp4', uploadSessionToken: 'session-a' },
      ],
    });
    expect(
      await screen.findByRole('region', { name: '剧本工作区' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('retains a draft queue when opening history and returning to new decomposition', async () => {
    render(<VideoScriptDecompositionPage />);
    act(() =>
      mocks.uploadProps.onChange({
        fileList: [
          {
            uid: 'a',
            name: 'retained.mp4',
            size: 1,
            status: 'uploading',
            percent: 40,
          },
        ],
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    await screen.findByText('第一季拆剧');
    fireEvent.click(screen.getByRole('button', { name: /继续整理/ }));
    expect(
      screen.getByRole('region', { name: '视频预览' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /第 1 集.*retained.mp4/ }),
    ).toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('keeps the same uploader mounted when intake becomes an episode workspace', async () => {
    const view = render(<VideoScriptDecompositionPage />);
    await screen.findByRole('option', { name: /千问视频理解/ });
    act(() =>
      mocks.uploadProps.onChange({
        fileList: [
          {
            uid: 'live',
            name: 'uploading.mp4',
            size: 1,
            status: 'uploading',
            percent: 10,
          },
        ],
      }),
    );
    expect(
      screen.getByRole('region', { name: '视频预览' }),
    ).toBeInTheDocument();
    expect(mocks.uploadUnmount).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(screen.getByRole('button', { name: /继续整理/ }));
    expect(mocks.uploadUnmount).not.toHaveBeenCalled();
    view.unmount();
    expect(mocks.uploadUnmount).toHaveBeenCalledTimes(1);
  });

  it('releases local preview URLs when selecting another file and unmounting', async () => {
    vi.mocked(URL.createObjectURL)
      .mockReturnValueOnce('blob:first')
      .mockReturnValueOnce('blob:second');
    const view = render(<VideoScriptDecompositionPage />);
    await screen.findByRole('option', { name: /千问视频理解/ });
    const fileList = ['first', 'second'].map((name) => ({
      uid: name,
      name: `${name}.mp4`,
      status: 'uploading',
      originFileObj: new File(['video'], `${name}.mp4`, { type: 'video/mp4' }),
    }));
    act(() => mocks.uploadProps.onChange({ fileList }));
    expect(await screen.findByLabelText('first.mp4')).toHaveAttribute(
      'src',
      'blob:first',
    );
    fireEvent.click(screen.getByRole('button', { name: '第 2 集 second.mp4' }));
    expect(await screen.findByLabelText('second.mp4')).toHaveAttribute(
      'src',
      'blob:second',
    );
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:first');
    view.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:second');
  });

  it('does not restore a removed file when a late upload event arrives', async () => {
    render(<VideoScriptDecompositionPage />);
    await screen.findByRole('option', { name: /千问视频理解/ });
    const file = {
      uid: 'removed',
      name: 'removed.mp4',
      size: 1,
      status: 'uploading',
      percent: 20,
    };
    act(() => mocks.uploadProps.onChange({ fileList: [file] }));
    fireEvent.click(screen.getByRole('button', { name: '移除第 1 集' }));
    act(() =>
      mocks.uploadProps.onChange({
        file: { ...file, percent: 90 },
        fileList: [{ ...file, percent: 90 }],
      }),
    );
    expect(
      screen.getByRole('heading', { name: '先选视频，再开始拆剧' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: '视频预览' }),
    ).not.toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('copies the current screenplay without fetching all batch screenplays', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
    );
    await screen.findByText('# 第1集：真相');
    fireEvent.click(screen.getByRole('button', { name: '复制本集' }));
    await waitFor(() =>
      expect(mocks.writeText).toHaveBeenCalledWith('# 第1集：真相'),
    );
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
  });

  it('uses API percentages in circles and retains reader scroll across episode switches', async () => {
    mocks.queryBatch.mockResolvedValue({
      data: {
        ...screenplayBatch,
        id: 9,
        name: '生成中',
        status: 'RUNNING',
        episodes: [
          screenplayBatch.episodes[0].episode,
          {
            ...screenplayBatch.episodes[1].episode,
            status: 'ANALYZING',
            percentage: 37,
          },
        ],
      },
    });
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
    );
    const article = await screen.findByRole('article', {
      name: '第 1 集剧本正文',
    });
    const reader = article.parentElement as HTMLElement;
    reader.scrollTop = 240;
    fireEvent.scroll(reader);
    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    expect(
      screen.getByRole('region', { name: '第 2 集生成进度' }),
    ).toBeInTheDocument();
    expect(
      screen
        .getAllByRole('progressbar')
        .filter((el) => el.getAttribute('aria-valuenow') === '37'),
    ).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '第 1 集' }));
    expect(
      screen.getByRole('article', { name: '第 1 集剧本正文' }).parentElement
        ?.scrollTop,
    ).toBe(240);
    expect(mocks.queryEpisode).toHaveBeenCalledTimes(1);
  });

  it('removes the former top workflow and opens ordered read-only screenplays', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    expect(screen.queryByText(/审核草稿/)).not.toBeInTheDocument();
    expect(screen.queryByText(/确认导入/)).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
    );
    expect(await screen.findByText('# 第1集：真相')).toBeInTheDocument();
    expect(screen.queryByText('模型输出截断')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '第 2 集' }));
    expect(screen.getByText('模型输出截断')).toBeInTheDocument();
    expect(mocks.queryScreenplays).not.toHaveBeenCalled();
    expect(screen.getByText(/1 \/ 2 集已完成/)).toBeInTheDocument();
  });

  it('copies successful scripts and retries only the retryable failed episode', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
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
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
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
    expect(shouldPollVideoDecompositionBatch('ANALYSIS_SUCCEEDED')).toBe(false);
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
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    fireEvent.click(
      await screen.findByRole('button', { name: /查看剧本|查看并处理/ }),
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
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    const buttons = await screen.findAllByRole('button', {
      name: /查看剧本|查看并处理/,
    });
    fireEvent.click(buttons[0]);
    const signal = mocks.queryBatch.mock.calls[0][1] as AbortSignal;
    fireEvent.click(screen.getByRole('button', { name: /返回生成记录/ }));
    expect(signal.aborted).toBe(true);
    fireEvent.click(
      (
        await screen.findAllByRole('button', { name: /查看剧本|查看并处理/ })
      )[1],
    );
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
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    fireEvent.click(
      screen.getByRole('button', { name: /查看剧本|查看并处理/ }),
    );
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
    fireEvent.click(screen.getByRole('button', { name: /生成记录/ }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    fireEvent.click(
      screen.getByRole('button', { name: /查看剧本|查看并处理/ }),
    );
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
    await screen.findByRole('option', { name: /千问视频理解/ });
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
    fireEvent.click(screen.getByRole('button', { name: /生成 2 集剧本/ }));
    await screen.findByRole('button', { name: /返回生成记录/ });
    expect(
      mocks.create.mock.calls[0][0].videos.map((v: any) => v.fileName),
    ).toEqual(['b.mp4', 'a.mp4']);
  });
});
