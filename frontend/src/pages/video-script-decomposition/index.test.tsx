import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import VideoScriptDecompositionPage, {
  buildDefaultVideoDecompositionBatchName,
  canCreateVideoDecompositionBatch,
} from './index';

const mocks = vi.hoisted(() => ({
  success: vi.fn(),
  historyPush: vi.fn(),
  queryVideoDecompositionBatches: vi.fn(),
  queryVideoUnderstandingModels: vi.fn(),
  queryVideoDecompositionEpisode: vi.fn(),
  retryVideoDecompositionEpisode: vi.fn(),
  updateVideoDecompositionDraft: vi.fn(),
  confirmVideoDecompositionDraft: vi.fn(),
  pollExecution: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { push: mocks.historyPush },
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: { poll: mocks.pollExecution },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task }: any) => <span>execution:{task.status}</span>,
}));

vi.mock('@ant-design/icons', () => ({
  ArrowDownOutlined: () => <span />,
  ArrowUpOutlined: () => <span />,
  CheckCircleOutlined: () => <span />,
  CloudUploadOutlined: () => <span />,
  DeleteOutlined: () => <span />,
  EyeOutlined: () => <span />,
  FileTextOutlined: () => <span />,
  PlayCircleOutlined: () => <span />,
  ReloadOutlined: () => <span />,
  SaveOutlined: () => <span />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({
      message: { success: mocks.success, error: vi.fn(), warning: vi.fn() },
    }),
  },
  Button: ({ children, disabled, onClick }: any) => (
    <button disabled={disabled} type="button" onClick={onClick}>
      {children}
    </button>
  ),
  Col: ({ children }: any) => <div>{children}</div>,
  Descriptions: ({ items = [] }: any) => (
    <dl>
      {items.map((item: any) => (
        <div key={item.key}>
          <dt>{item.label}</dt>
          <dd>{item.children}</dd>
        </div>
      ))}
    </dl>
  ),
  Drawer: ({ children, extra, open, title }: any) =>
    open ? (
      <section>
        <h2>{title}</h2>
        <div>{extra}</div>
        {children}
      </section>
    ) : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Form: Object.assign(({ children }: any) => <form>{children}</form>, {
    Item: ({ children, label }: any) => (
      <div>
        <span>{label}</span>
        {children}
      </div>
    ),
  }),
  Input: Object.assign(
    ({ value, onChange, placeholder }: any) => (
      <input value={value} placeholder={placeholder} onChange={onChange} />
    ),
    {
      TextArea: ({ value, onChange, placeholder }: any) => (
        <textarea value={value} placeholder={placeholder} onChange={onChange} />
      ),
    },
  ),
  InputNumber: ({ value, onChange, placeholder }: any) => (
    <input
      value={value ?? ''}
      placeholder={placeholder}
      onChange={(event) => onChange?.(Number(event.target.value))}
    />
  ),
  Select: ({ value, onChange, options = [], placeholder }: any) => (
    <select
      value={value ?? ''}
      aria-label="视频理解模型"
      onChange={(event) => onChange?.(Number(event.target.value))}
    >
      <option value="">{placeholder}</option>
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Modal: ({ children, open, title, onCancel, onOk }: any) =>
    open ? (
      <section>
        <h2>{title}</h2>
        {children}
        <button type="button" onClick={onOk}>
          确认导入
        </button>
        <button type="button" onClick={onCancel}>
          取消
        </button>
      </section>
    ) : null,
  Progress: ({ percent }: any) => <div>{percent}%</div>,
  Steps: ({ items = [], current }: any) => (
    <ol>
      {items.map((item: any, index: number) => (
        <li
          key={item.title}
          aria-current={index === current ? 'step' : undefined}
        >
          {item.title}
        </li>
      ))}
    </ol>
  ),
  Row: ({ children }: any) => <div>{children}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h3>{children}</h3>,
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
      <div>{toolBarRender?.()}</div>
      {dataSource.map((record: any) => (
        <div key={record.id ?? record.uid}>
          {columns.map((column: any, index: number) => {
            const value = column.dataIndex
              ? record[column.dataIndex]
              : undefined;
            return (
              <div key={column.key ?? column.dataIndex ?? index}>
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
  queryVideoDecompositionBatches: mocks.queryVideoDecompositionBatches,
  queryVideoUnderstandingModels: mocks.queryVideoUnderstandingModels,
  queryVideoDecompositionEpisode: mocks.queryVideoDecompositionEpisode,
  retryVideoDecompositionEpisode: mocks.retryVideoDecompositionEpisode,
  updateVideoDecompositionDraft: mocks.updateVideoDecompositionDraft,
  confirmVideoDecompositionDraft: mocks.confirmVideoDecompositionDraft,
  uploadEpisodeVideo: vi.fn(),
}));

describe('VideoScriptDecompositionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    let currentDraftContent = '原始草稿';
    mocks.queryVideoDecompositionBatches.mockResolvedValue({
      data: [
        {
          id: 9,
          projectId: null,
          name: '第一季拆剧',
          status: 'PENDING_REVIEW',
          totalEpisodes: 1,
          completedEpisodes: 1,
          failedEpisodes: 0,
          episodes: [
            {
              id: 88,
              batchId: 9,
              projectId: null,
              episodeNo: 1,
              sourceFileName: 'episode-1.mp4',
              storagePath:
                '/materials/1/video-decomposition/20260823/episode-1.mp4',
              fileSize: 2048,
              status: 'PENDING_REVIEW',
              draftStatus: 'PENDING_REVIEW',
              draftVersion: 2,
            },
          ],
        },
      ],
    });
    mocks.queryVideoUnderstandingModels.mockResolvedValue({
      data: [
        {
          id: 42,
          name: 'Qwen 3.7 Plus 多模态拆剧',
          modelCode: 'qwen3.7-plus',
          serviceType: 'VIDEO_UNDERSTANDING',
          status: 'ENABLED',
          isDefault: true,
        },
      ],
    });
    mocks.queryVideoDecompositionEpisode.mockImplementation(async () => ({
      data: {
        episode: {
          id: 88,
          batchId: 9,
          projectId: null,
          episodeNo: 1,
          sourceFileName: 'episode-1.mp4',
          storagePath:
            '/materials/1/video-decomposition/20260823/episode-1.mp4',
          fileSize: 2048,
          status: 'PENDING_REVIEW',
          analysisVersion: 1,
          draftVersion: 2,
        },
        draftContent: currentDraftContent,
        currentScriptVersionId: 12,
        rawResponse: '{"characters":[]}',
        normalizedJson:
          '{"characters":[],"scenes":[],"props":[],"timeline":[],"dialogue":[],"actions":[],"emotions":[]}',
        attempts: [],
      },
    }));
    mocks.updateVideoDecompositionDraft.mockImplementation(async () => {
      currentDraftContent = '审核后草稿';
      return { data: {} };
    });
    mocks.confirmVideoDecompositionDraft.mockResolvedValue({ data: {} });
    localStorage.setItem('currentTenantId', '1');
    mocks.pollExecution.mockImplementation(
      async (
        _tenantId: number,
        _executionId: number,
        onUpdate?: (task: any) => void,
      ) => {
        const task = { id: 901, status: 'SUCCEEDED', progress: 100 };
        onUpdate?.(task);
        return task;
      },
    );
  });

  it('follows the shared execution when an episode detail is opened', async () => {
    mocks.queryVideoDecompositionEpisode.mockResolvedValue({
      data: {
        episode: {
          id: 88,
          batchId: 9,
          episodeNo: 1,
          executionId: 901,
          status: 'ANALYZING',
          draftVersion: 0,
        },
        attempts: [],
      },
    });

    render(<VideoScriptDecompositionPage />);
    fireEvent.click(await screen.findByRole('button', { name: /第 1 集/ }));

    await waitFor(() => {
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        901,
        expect.any(Function),
      );
    });
  });

  it('opens episode detail, saves draft, and confirms import explicitly', async () => {
    render(<VideoScriptDecompositionPage />);

    fireEvent.click(
      await screen.findByRole('button', { name: /第 1 集 · PENDING_REVIEW/ }),
    );

    expect(await screen.findByText('第 1 集拆剧详情')).toBeInTheDocument();
    fireEvent.change(
      screen.getByPlaceholderText('等待草稿生成后可在此审核和编辑'),
      {
        target: { value: '审核后草稿' },
      },
    );
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    await waitFor(() => {
      expect(mocks.updateVideoDecompositionDraft).toHaveBeenCalledWith(
        88,
        '审核后草稿',
        2,
      );
    });

    fireEvent.click(screen.getByRole('button', { name: '确认导入' }));
    const dialog = (await screen.findByText('确认导入第 1 集剧本？')).closest(
      'section',
    );
    expect(dialog).toBeTruthy();
    fireEvent.change(
      within(dialog as HTMLElement).getByPlaceholderText(
        '请输入要导入的项目 ID',
      ),
      { target: { value: '101' } },
    );
    fireEvent.click(
      within(dialog as HTMLElement).getByRole('button', { name: '确认导入' }),
    );

    await waitFor(() => {
      expect(mocks.confirmVideoDecompositionDraft).toHaveBeenCalledWith(
        88,
        '审核后草稿',
        2,
        12,
        101,
      );
    });
  });

  it('uses a generated batch name and allows submission when uploads are ready', () => {
    expect(
      buildDefaultVideoDecompositionBatchName(new Date('2026-08-24T01:23:45')),
    ).toBe('拆剧批次-20260824-012345');
    expect(
      canCreateVideoDecompositionBatch([
        {
          uid: '1',
          episodeNo: 1,
          fileName: 'episode-1.mp4',
          size: 2048,
          status: 'READY',
          storagePath:
            '/materials/1/video-decomposition/20260824/episode-1.mp4',
        },
      ]),
    ).toBe(true);
    expect(canCreateVideoDecompositionBatch([])).toBe(false);
  });

  it('loads the default video understanding model into the model selector', async () => {
    render(<VideoScriptDecompositionPage />);
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: '视频理解模型' })).toHaveValue('42');
    });
    expect(screen.getByRole('option', { name: /qwen3.7-plus/ })).toBeInTheDocument();
  });
});
