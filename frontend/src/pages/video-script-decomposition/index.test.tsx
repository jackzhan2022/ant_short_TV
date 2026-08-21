import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import VideoScriptDecompositionPage from './index';

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(async ({ onOk }) => onOk?.()),
  success: vi.fn(),
  historyPush: vi.fn(),
  queryVideoDecompositionBatches: vi.fn(),
  queryVideoDecompositionEpisode: vi.fn(),
  retryVideoDecompositionEpisode: vi.fn(),
  updateVideoDecompositionDraft: vi.fn(),
  confirmVideoDecompositionDraft: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { push: mocks.historyPush },
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
      modal: { confirm: mocks.confirm },
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
  ProTable: ({ columns = [], dataSource = [], headerTitle, toolBarRender }: any) => (
    <section>
      <h2>{headerTitle}</h2>
      <div>{toolBarRender?.()}</div>
      {dataSource.map((record: any) => (
        <div key={record.id ?? record.uid}>
          {columns.map((column: any, index: number) => {
            const value = column.dataIndex ? record[column.dataIndex] : undefined;
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
  queryVideoDecompositionEpisode: mocks.queryVideoDecompositionEpisode,
  retryVideoDecompositionEpisode: mocks.retryVideoDecompositionEpisode,
  updateVideoDecompositionDraft: mocks.updateVideoDecompositionDraft,
  confirmVideoDecompositionDraft: mocks.confirmVideoDecompositionDraft,
  uploadEpisodeVideo: vi.fn(),
}));

describe('VideoScriptDecompositionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryVideoDecompositionBatches.mockResolvedValue({
      data: [
        {
          id: 9,
          projectId: 101,
          name: '第一季拆剧',
          status: 'PENDING_REVIEW',
          totalEpisodes: 1,
          completedEpisodes: 1,
          failedEpisodes: 0,
          episodes: [
            {
              id: 88,
              batchId: 9,
              projectId: 101,
              episodeNo: 1,
              sourceFileName: 'episode-1.mp4',
              storagePath: '/materials/1/101/episode-1.mp4',
              fileSize: 2048,
              status: 'PENDING_REVIEW',
              draftStatus: 'PENDING_REVIEW',
              draftVersion: 2,
            },
          ],
        },
      ],
    });
    mocks.queryVideoDecompositionEpisode.mockResolvedValue({
      data: {
        episode: {
          id: 88,
          batchId: 9,
          projectId: 101,
          episodeNo: 1,
          sourceFileName: 'episode-1.mp4',
          storagePath: '/materials/1/101/episode-1.mp4',
          fileSize: 2048,
          status: 'PENDING_REVIEW',
          analysisVersion: 1,
          draftVersion: 2,
        },
        draftContent: '原始草稿',
        currentScriptVersionId: 12,
        rawResponse: '{"characters":[]}',
        normalizedJson:
          '{"characters":[],"scenes":[],"props":[],"timeline":[],"dialogue":[],"actions":[],"emotions":[]}',
        attempts: [],
      },
    });
    mocks.updateVideoDecompositionDraft.mockResolvedValue({ data: {} });
    mocks.confirmVideoDecompositionDraft.mockResolvedValue({ data: {} });
  });

  it('opens episode detail, saves draft, and confirms import explicitly', async () => {
    render(<VideoScriptDecompositionPage />);

    fireEvent.click(await screen.findByRole('button', { name: /第 1 集 · PENDING_REVIEW/ }));

    expect(await screen.findByText('第 1 集拆剧详情')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('等待草稿生成后可在此审核和编辑'), {
      target: { value: '审核后草稿' },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    await waitFor(() => {
      expect(mocks.updateVideoDecompositionDraft).toHaveBeenCalledWith(88, '审核后草稿', 2);
    });

    fireEvent.change(screen.getByPlaceholderText('等待草稿生成后可在此审核和编辑'), {
      target: { value: '审核后草稿' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认导入' }));

    await waitFor(() => {
      expect(mocks.confirmVideoDecompositionDraft).toHaveBeenCalledWith(
        88,
        '审核后草稿',
        2,
        12,
      );
    });
    expect(mocks.confirm).toHaveBeenCalled();
  });
});
