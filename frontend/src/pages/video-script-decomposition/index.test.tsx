import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
  retry: vi.fn(),
  writeText: vi.fn(),
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
  App: {
    useApp: () => ({
      message: {
        success: mocks.success,
        error: mocks.messageError,
        warning: vi.fn(),
      },
    }),
  },
  Button: ({ children, disabled, onClick }: any) => (
    <button disabled={disabled} type="button" onClick={onClick}>
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
  Drawer: ({ children, extra, open, title }: any) =>
    open ? (
      <aside>
        <h2>{title}</h2>
        {extra}
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
    Dragger: ({ children }: any) => <div>{children}</div>,
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
  createVideoDecompositionBatch: vi.fn(),
  queryVideoDecompositionBatches: mocks.queryBatches,
  queryVideoDecompositionBatchScreenplays: mocks.queryScreenplays,
  queryVideoUnderstandingModels: mocks.queryModels,
  retryVideoDecompositionEpisode: mocks.retry,
  uploadEpisodeVideo: vi.fn(),
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
    vi.clearAllMocks();
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
    mocks.retry.mockResolvedValue({ data: {} });
    mocks.writeText.mockResolvedValue(undefined);
  });

  it('removes the former top workflow and opens ordered read-only screenplays', async () => {
    render(<VideoScriptDecompositionPage />);
    expect(screen.queryByText(/审核草稿/)).not.toBeInTheDocument();
    expect(screen.queryByText(/确认导入/)).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    expect(await screen.findByText('# 第1集：真相')).toBeInTheDocument();
    expect(screen.getByText('模型输出截断')).toBeInTheDocument();
    expect(screen.getByText(/总进度 100%/)).toBeInTheDocument();
  });

  it('copies successful scripts and retries only the retryable failed episode', async () => {
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    fireEvent.click(
      await screen.findByRole('button', { name: '复制全部剧本' }),
    );
    await waitFor(() =>
      expect(mocks.writeText).toHaveBeenCalledWith('# 第1集：真相'),
    );

    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(mocks.retry).toHaveBeenCalledWith(89));
  });

  it('reports clipboard permission failures', async () => {
    mocks.writeText.mockRejectedValueOnce(new Error('denied'));
    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    fireEvent.click(
      await screen.findByRole('button', { name: '复制全部剧本' }),
    );
    await waitFor(() =>
      expect(mocks.messageError).toHaveBeenCalledWith(
        '复制失败，请检查浏览器剪贴板权限',
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

  it('keeps a running drawer snapshot while the batch list continues polling', async () => {
    vi.useFakeTimers();
    const runningBatch = {
      ...screenplayBatch,
      status: 'RUNNING',
      percentage: 20,
      failedEpisodes: 0,
      processingEpisodes: 1,
    };
    mocks.queryBatches
      .mockResolvedValueOnce({ data: [{ ...runningBatch, id: 9, episodes: [] }] })
      .mockResolvedValue({
        data: [{ ...screenplayBatch, id: 9, status: 'SUCCEEDED', episodes: [] }],
      });
    mocks.queryScreenplays
      .mockResolvedValueOnce({ data: runningBatch })
      .mockResolvedValue({ data: { ...screenplayBatch, status: 'SUCCEEDED' } });

    const view = render(<VideoScriptDecompositionPage />);
    await act(async () => Promise.resolve());
    fireEvent.click(screen.getByRole('button', { name: '查看全部剧本' }));
    await act(async () => Promise.resolve());
    expect(mocks.queryBatches).toHaveBeenCalledTimes(1);
    expect(mocks.queryScreenplays).toHaveBeenCalledTimes(1);

    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.queryBatches).toHaveBeenCalledTimes(2);
    expect(mocks.queryScreenplays).toHaveBeenCalledTimes(1);

    await act(async () => vi.advanceTimersByTimeAsync(6000));
    expect(mocks.queryBatches).toHaveBeenCalledTimes(2);
    expect(mocks.queryScreenplays).toHaveBeenCalledTimes(1);
    view.unmount();
    vi.useRealTimers();
  });

  it('manually refreshes both the batch list and the open drawer', async () => {
    const refreshedBatch = {
      ...screenplayBatch,
      status: 'RUNNING',
      percentage: 80,
      failedEpisodes: 0,
      processingEpisodes: 1,
    };
    mocks.queryBatches
      .mockResolvedValueOnce({ data: [{ ...screenplayBatch, id: 9, episodes: [] }] })
      .mockResolvedValueOnce({
        data: [{ ...refreshedBatch, id: 9, episodes: [] }],
      });
    mocks.queryScreenplays
      .mockResolvedValueOnce({ data: screenplayBatch })
      .mockResolvedValueOnce({ data: refreshedBatch });

    render(<VideoScriptDecompositionPage />);
    fireEvent.click(
      await screen.findByRole('button', { name: '查看全部剧本' }),
    );
    await screen.findByText('# 第1集：真相');

    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => {
      expect(mocks.queryBatches).toHaveBeenCalledTimes(2);
      expect(mocks.queryScreenplays).toHaveBeenCalledTimes(2);
    });
  });

  it('refreshes the open drawer once after an accepted retry without polling', async () => {
    vi.useFakeTimers();
    const running = {
      ...screenplayBatch,
      status: 'RUNNING',
      percentage: 55,
      failedEpisodes: 0,
      processingEpisodes: 1,
    };
    mocks.queryScreenplays
      .mockResolvedValueOnce({ data: screenplayBatch })
      .mockResolvedValueOnce({ data: running })
      .mockResolvedValue({ data: { ...screenplayBatch, status: 'SUCCEEDED' } });
    mocks.queryBatches
      .mockResolvedValueOnce({ data: [{ ...screenplayBatch, id: 9, episodes: [] }] })
      .mockResolvedValueOnce({ data: [{ ...running, id: 9, episodes: [] }] })
      .mockResolvedValue({
        data: [{ ...screenplayBatch, id: 9, status: 'SUCCEEDED', episodes: [] }],
      });

    const view = render(<VideoScriptDecompositionPage />);
    await act(async () => Promise.resolve());
    fireEvent.click(screen.getByRole('button', { name: '查看全部剧本' }));
    await act(async () => Promise.resolve());
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await act(async () => Promise.resolve());
    expect(mocks.queryScreenplays).toHaveBeenCalledTimes(2);

    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(mocks.queryScreenplays).toHaveBeenCalledTimes(2);
    view.unmount();
    vi.useRealTimers();
  });
});
