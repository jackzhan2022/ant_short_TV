import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import ScriptReviewLibraryPage from '.';

const mocks = vi.hoisted(() => ({
  queryReviewProjectSummaries: vi.fn(),
  queryReviewProjectMetrics: vi.fn(),
  queryReviewProject: vi.fn(),
  importReviewProject: vi.fn(),
  push: vi.fn(),
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

vi.mock('mammoth', () => ({
  default: {
    extractRawText: vi.fn(async () => ({
      value: '第一集\n从 Word 提取的正文。',
    })),
  },
}));

vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra, title }: any) => (
    <main>
      <h1>{title}</h1>
      {extra}
      {children}
    </main>
  ),
}));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: mocks.message }) },
  Badge: ({ count }: any) => <span>{count}</span>,
  Button: ({ children, disabled, onClick }: any) => (
    <button disabled={disabled} type="button" onClick={onClick}>
      {children}
    </button>
  ),
  Card: ({ children, title }: any) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Input: Object.assign(
    ({ value, onChange, placeholder }: any) => (
      <input value={value} onChange={onChange} placeholder={placeholder} />
    ),
    {
      Search: ({ value, onChange, placeholder }: any) => (
        <input value={value} onChange={onChange} placeholder={placeholder} />
      ),
      TextArea: ({ value, onChange, placeholder }: any) => (
        <textarea value={value} onChange={onChange} placeholder={placeholder} />
      ),
    },
  ),
  Spin: ({ description }: any) => <div>{description}</div>,
  List: Object.assign(
    ({ dataSource = [], locale, renderItem }: any) => (
      <div>{dataSource.length ? dataSource.map(renderItem) : locale?.emptyText}</div>
    ),
    {
      Item: Object.assign(
        ({ children, actions }: any) => (
          <div>
            {children}
            {actions}
          </div>
        ),
        {
          Meta: ({ title, description }: any) => (
            <div>
              {title}
              {description}
            </div>
          ),
        },
      ),
    },
  ),
  Modal: ({ open, title, children, onCancel, onOk }: any) =>
    open ? (
      <section>
        <h2>{title}</h2>
        {children}
        <button type="button" onClick={onCancel}>
          取消
        </button>
        <button type="button" onClick={onOk}>
          导入剧本
        </button>
      </section>
    ) : null,
  Select: ({ value, options, onChange }: any) => (
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
  Upload: {
    Dragger: ({ beforeUpload, children, onChange }: any) => (
      <div>
        {children}
        <button
          type="button"
          aria-label="选择测试剧本文件"
          onClick={async () => {
            const file = {
              name: '雨夜来信.md',
              type: 'text/markdown',
              text: async () => '第一集\n雨夜，林晚收到一封信。',
            } as File;
            await beforeUpload(file);
            onChange({
              fileList: [{ uid: '1', name: file.name, originFileObj: file }],
            });
          }}
        >
          选择文件
        </button>
        <button
          type="button"
          aria-label="选择测试Word剧本"
          onClick={async () => {
            const file = {
              name: '雨夜来信.docx',
              type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
              arrayBuffer: async () => new ArrayBuffer(8),
            } as File;
            await beforeUpload(file);
            onChange({
              fileList: [{ uid: '2', name: file.name, originFileObj: file }],
            });
          }}
        >
          选择 Word 文件
        </button>
      </div>
    ),
  },
}));
vi.mock('@ant-design/icons', () => ({
  CloudUploadOutlined: () => null,
  FileTextOutlined: () => null,
  PlusOutlined: () => null,
}));
vi.mock('../script-review/service', () => ({
  queryReviewProjectSummaries: mocks.queryReviewProjectSummaries,
  queryReviewProjectMetrics: mocks.queryReviewProjectMetrics,
  queryReviewProject: mocks.queryReviewProject,
  importReviewProject: mocks.importReviewProject,
}));

describe('ScriptReviewLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryReviewProjectSummaries.mockResolvedValue({
      data: [
        {
          id: 1,
          name: '待处理剧本',
          sourceType: 'TEXT',
          status: 'ACTIVE',
        },
        {
          id: 2,
          name: '已完成剧本',
          sourceType: 'TEXT',
          status: 'ACTIVE',
        },
      ],
    });
    mocks.queryReviewProjectMetrics.mockResolvedValue({
      data: [
        {
          projectId: 1,
          versionCount: 2,
          latestRoundNo: 1,
          reviewState: 'ACTION_REQUIRED',
          outstandingIssueCount: 1,
          actionLabel: '处理问题',
        },
        {
          projectId: 2,
          versionCount: 1,
          latestRoundNo: 1,
          reviewState: 'COMPLETED',
          outstandingIssueCount: 0,
          actionLabel: '查看报告',
        },
      ],
    });
  });

  it('renders the library from lightweight summaries and navigates to project history', async () => {
    render(<ScriptReviewLibraryPage />);
    expect(await screen.findByText('待处理剧本')).toBeInTheDocument();
    expect(screen.getAllByText('待处理')).not.toHaveLength(0);
    expect(mocks.queryReviewProject).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    expect(screen.getByText('新建独立剧本')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '处理问题' }));
    expect(mocks.push).toHaveBeenCalledWith(
      '/script-review/projects/1/reviews',
    );
  });

  it('filters projects from the left review-status navigation', async () => {
    render(<ScriptReviewLibraryPage />);
    expect(await screen.findByText('审核状态')).toBeInTheDocument();
    expect(screen.getByText('已完成剧本')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: /审核完成/ })[0]);

    expect(screen.getByText('已完成剧本')).toBeInTheDocument();
    expect(screen.queryByText('待处理剧本')).not.toBeInTheDocument();
  });

  it('fills the script name and content after choosing a text script', async () => {
    render(<ScriptReviewLibraryPage />);
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    fireEvent.click(screen.getByRole('button', { name: '选择测试剧本文件' }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('剧本名称')).toHaveValue('雨夜来信');
      expect(screen.getByPlaceholderText('或直接粘贴剧本内容')).toHaveValue(
        '第一集\n雨夜，林晚收到一封信。',
      );
    });
  });

  it('fills the script content after choosing a docx script', async () => {
    render(<ScriptReviewLibraryPage />);
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    fireEvent.click(screen.getByRole('button', { name: '选择测试Word剧本' }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('剧本名称')).toHaveValue('雨夜来信');
      expect(screen.getByPlaceholderText('或直接粘贴剧本内容')).toHaveValue(
        '第一集\n从 Word 提取的正文。',
      );
    });
  });
  it('shows centered loading feedback until the project list is ready', async () => {
    let resolveProjects: (value: unknown) => void = () => undefined;
    mocks.queryReviewProjectSummaries.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveProjects = resolve;
      }),
    );

    render(<ScriptReviewLibraryPage />);

    expect(screen.getByText('正在加载剧本…')).toBeInTheDocument();

    resolveProjects({ data: [] });

    expect(await screen.findByText('暂无独立剧本')).toBeInTheDocument();
    expect(screen.queryByText('正在加载剧本…')).not.toBeInTheDocument();
  });

  it('renders summary rows before review metrics resolve', async () => {
    let resolveMetrics: (value: unknown) => void = () => undefined;
    mocks.queryReviewProjectMetrics.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveMetrics = resolve;
      }),
    );

    render(<ScriptReviewLibraryPage />);

    expect(await screen.findByText('待处理剧本')).toBeInTheDocument();
    expect(screen.getAllByText('指标加载中').length).toBeGreaterThan(0);
    expect(mocks.queryReviewProjectMetrics).toHaveBeenCalledTimes(1);
    fireEvent.change(screen.getByPlaceholderText('搜索剧本名称'), {
      target: { value: '待处理' },
    });
    expect(screen.queryByText('已完成剧本')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '进入审核' }));
    expect(mocks.push).toHaveBeenCalledWith('/script-review/projects/1/reviews');
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    expect(screen.getByText('新建独立剧本')).toBeInTheDocument();

    resolveMetrics({ data: [] });
    await waitFor(() => {
      expect(screen.queryByText('审核指标加载中…')).not.toBeInTheDocument();
    });
  });

  it('keeps summary rows and retries only failed metrics', async () => {
    mocks.queryReviewProjectMetrics
      .mockRejectedValueOnce(new Error('metrics unavailable'))
      .mockResolvedValueOnce({
        data: [
          {
            projectId: 1,
            versionCount: 2,
            latestRoundNo: 1,
            reviewState: 'ACTION_REQUIRED',
            outstandingIssueCount: 1,
            actionLabel: '处理问题',
          },
        ],
      });
    render(<ScriptReviewLibraryPage />);

    expect(await screen.findByText('待处理剧本')).toBeInTheDocument();
    expect(await screen.findByText('重试审核指标')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试审核指标' }));

    expect(await screen.findByRole('button', { name: '处理问题' })).toBeInTheDocument();
    expect(mocks.queryReviewProjectSummaries).toHaveBeenCalledTimes(1);
    expect(mocks.queryReviewProjectMetrics).toHaveBeenCalledTimes(2);
  });

  it('merges a completed metrics response into the rendered summary row', async () => {
    render(<ScriptReviewLibraryPage />);

    expect(await screen.findByRole('button', { name: '处理问题' })).toBeInTheDocument();
    expect(screen.getByText('待处理 1 项')).toBeInTheDocument();
    expect(mocks.queryReviewProjectSummaries).toHaveBeenCalledTimes(1);
    expect(mocks.queryReviewProjectMetrics).toHaveBeenCalledTimes(1);
  });

  it('refreshes summaries and starts a new metrics request after importing a project', async () => {
    mocks.importReviewProject.mockResolvedValue({ data: { project: { id: 3 } } });
    mocks.queryReviewProjectSummaries
      .mockResolvedValueOnce({ data: [{ id: 1, name: '旧剧本', sourceType: 'TEXT', status: 'ACTIVE' }] })
      .mockResolvedValueOnce({ data: [{ id: 3, name: '新剧本', sourceType: 'TEXT', status: 'ACTIVE' }] });

    render(<ScriptReviewLibraryPage />);
    expect(await screen.findByText('旧剧本')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    fireEvent.change(screen.getByPlaceholderText('剧本名称'), { target: { value: '新剧本' } });
    fireEvent.change(screen.getByPlaceholderText('或直接粘贴剧本内容'), { target: { value: '第一集\\n新内容' } });
    fireEvent.click(screen.getByRole('button', { name: '导入剧本' }));

    expect(await screen.findByText('新剧本')).toBeInTheDocument();
    expect(mocks.queryReviewProjectSummaries).toHaveBeenCalledTimes(2);
    expect(mocks.queryReviewProjectMetrics).toHaveBeenCalledTimes(2);
    expect(mocks.push).toHaveBeenCalledWith('/script-review/projects/3/reviews');
  });

  it('ignores metrics that resolve after a newer summary refresh', async () => {
    let resolveFirstMetrics: (value: unknown) => void = () => undefined;
    let resolveSecondMetrics: (value: unknown) => void = () => undefined;
    mocks.importReviewProject.mockResolvedValue({ data: { project: { id: 3 } } });
    mocks.queryReviewProjectSummaries
      .mockResolvedValueOnce({ data: [{ id: 1, name: '旧剧本', sourceType: 'TEXT', status: 'ACTIVE' }] })
      .mockResolvedValueOnce({ data: [{ id: 3, name: '新剧本', sourceType: 'TEXT', status: 'ACTIVE' }] });
    mocks.queryReviewProjectMetrics
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirstMetrics = resolve; }))
      .mockReturnValueOnce(new Promise((resolve) => { resolveSecondMetrics = resolve; }));

    render(<ScriptReviewLibraryPage />);
    expect(await screen.findByText('旧剧本')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    fireEvent.change(screen.getByPlaceholderText('剧本名称'), { target: { value: '新剧本' } });
    fireEvent.change(screen.getByPlaceholderText('或直接粘贴剧本内容'), { target: { value: '第一集\\n新内容' } });
    fireEvent.click(screen.getByRole('button', { name: '导入剧本' }));
    expect(await screen.findByText('新剧本')).toBeInTheDocument();

    resolveFirstMetrics({
      data: [{ projectId: 1, versionCount: 99, latestRoundNo: 9, reviewState: 'COMPLETED', outstandingIssueCount: 0, actionLabel: '旧响应' }],
    });
    await waitFor(() => expect(screen.queryByRole('button', { name: '旧响应' })).not.toBeInTheDocument());

    resolveSecondMetrics({
      data: [{ projectId: 3, versionCount: 1, latestRoundNo: 1, reviewState: 'COMPLETED', outstandingIssueCount: 0, actionLabel: '查看报告' }],
    });
    expect(await screen.findByRole('button', { name: '查看报告' })).toBeInTheDocument();
  });
});
