import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ScriptReviewPage from './index';

const mocks = vi.hoisted(() => ({
  message: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
  confirm: vi.fn(async ({ onOk }) => onOk?.()),
  queryReviewProjects: vi.fn(),
  queryReviewProject: vi.fn(),
  queryReviewTask: vi.fn(),
  queryReviewVersionHistory: vi.fn(),
  importReviewProject: vi.fn(),
  saveReviewVersion: vi.fn(),
  createReviewTask: vi.fn(),
  cancelReviewTask: vi.fn(),
  retryReviewTask: vi.fn(),
  resolveReviewIssue: vi.fn(),
  batchRepairReview: vi.fn(),
  rollbackReviewVersion: vi.fn(),
  exportReviewReport: vi.fn(),
  pollExecution: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  AuditOutlined: () => <span />,
  CheckCircleOutlined: () => <span />,
  CopyOutlined: () => <span />,
  CloudUploadOutlined: () => <span />,
  DownloadOutlined: () => <span />,
  FileTextOutlined: () => <span />,
  LockOutlined: () => <span />,
  ReloadOutlined: () => <span />,
  SaveOutlined: () => <span />,
  SwapOutlined: () => <span />,
}));

vi.mock('@umijs/max', () => ({
  request: vi.fn(),
}));

vi.mock('antd', () => {
  const React = require('react');
  const ListItem = Object.assign(
    ({ children, actions = [], extra }: any) => (
      <div>
        <div>{children}</div>
        <div>
          {actions.map((action: any) => (
            <span key={String(action)}>{action}</span>
          ))}
        </div>
        <div>{extra}</div>
      </div>
    ),
    {
      Meta: ({ title, description }: any) => (
        <div>
          <div>{title}</div>
          <div>{description}</div>
        </div>
      ),
    },
  );
  return {
    App: {
      useApp: () => ({
        message: mocks.message,
        modal: { confirm: mocks.confirm },
      }),
    },
    Button: ({ children, disabled, icon, loading, onClick, type }: any) => (
      <button disabled={disabled} onClick={onClick} type={type ?? 'button'}>
        {icon}
        {children}
        {loading ? 'loading' : null}
      </button>
    ),
    Card: ({ children, extra, title, onClick }: any) => (
      <section onClick={onClick}>
        <header>
          {title}
          {extra}
        </header>
        {children}
      </section>
    ),
    Checkbox: {
      Group: ({ value = [], options = [], onChange, disabled }: any) => (
        <div>
          {options.map((option: any) => {
            const optionValue = typeof option === 'string' ? option : option.value;
            const optionLabel = typeof option === 'string' ? option : option.label;
            const checked = value.includes(optionValue);
            return (
              <label key={optionValue}>
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={disabled}
                  onChange={() => {
                    const next = checked
                      ? value.filter((item: any) => item !== optionValue)
                      : [...value, optionValue];
                    onChange?.(next);
                  }}
                />
                <span>{optionLabel}</span>
              </label>
            );
          })}
        </div>
      ),
    },
    Col: ({ children }: any) => <div>{children}</div>,
    Empty: ({ description }: any) => <div>{description}</div>,
    Input: Object.assign(
      React.forwardRef(
        ({ value, onChange, placeholder, disabled }: any, ref: any) => (
          <input
            ref={ref}
            value={value}
            placeholder={placeholder}
            disabled={disabled}
            onChange={onChange}
          />
        ),
      ),
      {
        TextArea: React.forwardRef(
          ({ value, onChange, placeholder, disabled }: any, ref: any) => (
            <textarea
              ref={ref}
              value={value}
              placeholder={placeholder}
              disabled={disabled}
              onChange={onChange}
            />
          ),
        ),
      },
    ),
    List: Object.assign(
      ({ dataSource = [], renderItem, locale }: any) => (
        <div>
          {dataSource.length === 0
            ? locale?.emptyText
            : dataSource.map((item: any, index: number) => (
                <div key={item.id ?? index}>{renderItem(item, index)}</div>
              ))}
        </div>
      ),
      {
        Item: ListItem,
      },
    ),
    Modal: ({ children, open, title, onCancel, onOk }: any) =>
      open ? (
        <section>
          <h2>{title}</h2>
          {children}
          <button type="button" onClick={onCancel}>取消</button>
          <button type="button" onClick={onOk}>开始审核</button>
        </section>
      ) : null,
    Progress: ({ percent }: any) => <div>{percent}%</div>,
    Radio: {
      Group: ({ value, options = [], onChange, disabled }: any) => (
        <div>
          {options.map((option: any) => (
            <button
              key={option.value}
              disabled={disabled}
              type="button"
              onClick={() => onChange?.({ target: { value: option.value } })}
            >
              {option.label}
              {value === option.value ? '*' : ''}
            </button>
          ))}
        </div>
      ),
    },
    Row: ({ children }: any) => <div>{children}</div>,
    Select: ({ value, options = [], onChange, disabled, style }: any) => (
      <select
        disabled={disabled}
        style={style}
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
      >
        {options.map((option: any) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    ),
    Space: ({ children }: any) => <div>{children}</div>,
    Tag: ({ children }: any) => <span>{children}</span>,
    Typography: {
      Paragraph: ({ children }: any) => <p>{children}</p>,
      Text: ({ children }: any) => <span>{children}</span>,
    },
    Upload: {
      Dragger: ({ children }: any) => <div>{children}</div>,
    },
  };
});

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra, title }: any) => (
    <main>
      <h1>{title}</h1>
      <div>{extra}</div>
      {children}
    </main>
  ),
}));

vi.mock('@ant-design/x-markdown', () => ({
  default: ({ children }: any) => <article>{children}</article>,
}));

vi.mock('./service', () => ({
  batchRepairReview: mocks.batchRepairReview,
  cancelReviewTask: mocks.cancelReviewTask,
  createReviewTask: mocks.createReviewTask,
  exportReviewReport: mocks.exportReviewReport,
  importReviewProject: mocks.importReviewProject,
  queryReviewProject: mocks.queryReviewProject,
  queryReviewTask: mocks.queryReviewTask,
  queryReviewProjects: mocks.queryReviewProjects,
  queryReviewVersionHistory: mocks.queryReviewVersionHistory,
  resolveReviewIssue: mocks.resolveReviewIssue,
  retryReviewTask: mocks.retryReviewTask,
  rollbackReviewVersion: mocks.rollbackReviewVersion,
  saveReviewVersion: mocks.saveReviewVersion,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: { poll: mocks.pollExecution },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task }: any) => (
    <div>
      execution-{task.id}-{task.status}
    </div>
  ),
}));

describe('ScriptReviewPage', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/script-review');
    vi.clearAllMocks();
    mocks.queryReviewTask.mockResolvedValue({ data: undefined });
    mocks.queryReviewProjects.mockResolvedValue({
      data: [
        {
          id: 1,
          name: '审稿样例',
          sourceType: 'TEXT',
          status: 'ACTIVE',
          versionCount: 2,
          latestRoundNo: 1,
        },
      ],
    });
    mocks.queryReviewProject.mockResolvedValue({
      data: {
        project: {
          id: 1,
          name: '审稿样例',
          sourceType: 'TEXT',
          currentVersionId: 2,
          lastTaskId: 7,
          status: 'ACTIVE',
          versionCount: 2,
          latestRoundNo: 1,
        },
        versions: [
          {
            id: 1,
            projectId: 1,
            versionNo: 1,
            sourceType: 'IMPORT',
            content: '第1集\n林晚说：别走。',
          },
          {
            id: 2,
            projectId: 1,
            versionNo: 2,
            sourceType: 'MANUAL_EDIT',
            fileName: 'v2.md',
            content: '第1集\n林晚说：别走。\n周野说：我会回来。',
          },
        ],
        tasks: [
          {
            id: 7,
            projectId: 1,
            scriptVersionId: 2,
            roundNo: 1,
            reviewMode: 'QUICK',
            selectedDimensions: ['台词合理性', '人物关系一致性'],
            reviewScopeType: 'ALL',
            reviewScope: {},
            status: 'COMPLETED',
            currentStage: null,
            overallProgress: 100,
            currentAction: '审核已完成',
            summary: {
              overallConclusion: 'PASS',
              overallScore: 88,
              summary: '整体良好',
            },
            issues: [
              {
                id: 11,
                taskId: 7,
                scriptVersionId: 2,
                roundNo: 1,
                issueNo: 'R1-01',
                dimension: '台词合理性',
                severity: 'P1',
                title: '人名混乱',
                position: { episode: 1, scene: '1' },
                excerpt: '林晚说：别走。',
                problem: '同一句台词里称呼不一致',
                evidence: ['林晚和周野称呼混乱'],
                suggestion: '统一称呼',
                status: 'persists',
                manuallyResolved: false,
                hits: [
                  {
                    id: 101,
                    hitNo: 1,
                    anchorLabel: '台词',
                    excerpt: '林晚说：别走。',
                    selected: true,
                    replacementText: '林晚说：别走。',
                  },
                  {
                    id: 102,
                    hitNo: 2,
                    anchorLabel: '台词',
                    excerpt: '周野说：我会回来。',
                    selected: true,
                    replacementText: '周野说：我会回来。',
                  },
                ],
              },
              {
                id: 12,
                taskId: 7,
                scriptVersionId: 2,
                roundNo: 1,
                issueNo: 'R1-02',
                dimension: '人物关系一致性',
                severity: 'P2',
                title: '已处理问题',
                position: { episode: 1, scene: '2' },
                excerpt: '已处理片段',
                problem: '已处理',
                evidence: [],
                suggestion: '',
                status: 'processed',
                manuallyResolved: true,
                manuallyResolvedAt: '2026-08-23T20:00:00',
                hits: [],
              },
            ],
          },
          {
            id: 8,
            projectId: 1,
            scriptVersionId: 2,
            roundNo: 2,
            reviewMode: 'QUICK',
            selectedDimensions: ['剧情逻辑与因果'],
            reviewScopeType: 'ALL',
            reviewScope: {},
            status: 'COMPLETED',
            overallProgress: 100,
            currentAction: '复审已完成',
            issues: [
              {
                id: 21,
                taskId: 8,
                scriptVersionId: 2,
                roundNo: 2,
                issueNo: 'R2-01',
                dimension: '剧情逻辑与因果',
                severity: 'P1',
                title: '因果链缺失',
                position: {},
                excerpt: '周野说：我会回来。',
                problem: '人物离开的原因未交代',
                evidence: [],
                suggestion: '补充离开动机',
                status: 'persists',
                manuallyResolved: false,
                hits: [],
              },
            ],
          },
        ],
      },
    });
    mocks.queryReviewVersionHistory.mockResolvedValue({
      data: {
        project: {
          id: 1,
          name: '审稿样例',
          sourceType: 'TEXT',
          currentVersionId: 2,
          lastTaskId: 7,
          status: 'ACTIVE',
          versionCount: 2,
          latestRoundNo: 1,
        },
        selectedVersion: {
          id: 2,
          projectId: 1,
          versionNo: 2,
          sourceType: 'MANUAL_EDIT',
          fileName: 'v2.md',
          content: '第1集\n林晚说：别走。\n周野说：我会回来。',
        },
        versions: [
          {
            id: 1,
            projectId: 1,
            versionNo: 1,
            sourceType: 'IMPORT',
            content: '第1集\n林晚说：别走。',
          },
          {
            id: 2,
            projectId: 1,
            versionNo: 2,
            sourceType: 'MANUAL_EDIT',
            fileName: 'v2.md',
            content: '第1集\n林晚说：别走。\n周野说：我会回来。',
          },
        ],
        diffLines: [
          {
            fromVersionId: 1,
            toVersionId: 2,
            addedLines: 1,
            removedLines: 0,
            lines: [
              {
                type: 'UNCHANGED',
                lineNo: 1,
                beforeText: '第1集',
                afterText: '第1集',
              },
              {
                type: 'UNCHANGED',
                lineNo: 2,
                beforeText: '林晚说：别走。',
                afterText: '林晚说：别走。',
              },
              {
                type: 'ADDED',
                lineNo: 3,
                beforeText: null,
                afterText: '周野说：我会回来。',
              },
            ],
          },
        ],
        roundHistory: [
          {
            taskId: 7,
            roundNo: 1,
            status: 'COMPLETED',
            reviewMode: 'QUICK',
            issueCount: 2,
            processedIssueCount: 1,
            summary: {
              overallConclusion: 'PASS',
              overallScore: 88,
              summary: '整体良好',
            },
            completedAt: '2026-08-23T20:00:00',
          },
        ],
        issueMappings: [
          {
            issueId: 11,
            issueNo: 'R1-01',
            roundNo: 1,
            status: 'persists',
            relatedIssueNo: 'R0-01',
            dimension: '台词合理性',
            title: '人名混乱',
            hitCount: 2,
            hitIds: [101, 102],
          },
        ],
      },
    });
    mocks.batchRepairReview.mockResolvedValue({ data: {} });
    mocks.resolveReviewIssue.mockResolvedValue({ data: {} });
    localStorage.setItem('currentTenantId', '10');
    mocks.createReviewTask.mockResolvedValue({
      data: { id: 701, businessId: 8, status: 'PENDING', progress: 0 },
    });
    mocks.pollExecution.mockResolvedValue({
      id: 701,
      businessId: 8,
      status: 'SUCCEEDED',
      progress: 100,
    });
  });

  it('shows Markdown reports with copy and md download while hiding issue actions', async () => {
    const markdownTask = {
      id: 12,
      projectId: 1,
      scriptVersionId: 2,
      roundNo: 3,
      reviewMode: 'QUICK',
      selectedDimensions: ['台词合理性'],
      reviewScopeType: 'ALL',
      reviewScope: {},
      resultFormat: 'MARKDOWN',
      reportMarkdown: '# 自定义审核\n\n|位置|建议|\n|---|---|\n|第1集|保留|',
      status: 'COMPLETED',
      overallProgress: 100,
      issues: [],
    };
    mocks.queryReviewProject.mockResolvedValueOnce({
      data: {
        project: { id: 1, name: '审稿样例', sourceType: 'TEXT', currentVersionId: 2, lastTaskId: 12, status: 'ACTIVE', versionCount: 1, latestRoundNo: 3 },
        versions: [{ id: 2, projectId: 1, versionNo: 1, sourceType: 'IMPORT', content: '第1集\n正文' }],
        tasks: [markdownTask],
      },
    });
    mocks.queryReviewTask.mockResolvedValueOnce({ data: markdownTask });
    mocks.exportReviewReport.mockResolvedValue({ data: { fileName: 'report.md' } });
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:markdown-report');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let downloadedFile: { download: string; href: string } | undefined;
    const downloadClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function captureDownload(this: HTMLAnchorElement) {
        downloadedFile = { download: this.download, href: this.href };
      });

    render(<ScriptReviewPage />);

    expect(await screen.findByTestId('markdown-report-reader')).toHaveTextContent('自定义审核');
    expect(screen.getByTestId('markdown-report-reader')).toHaveTextContent('第1集|保留');
    expect(screen.queryByRole('button', { name: /未处理/ })).not.toBeInTheDocument();
    expect(screen.queryByText('问题详情')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '复制报告' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(markdownTask.reportMarkdown));
    fireEvent.click(screen.getByRole('button', { name: '下载 .md' }));
    await waitFor(() => expect(mocks.exportReviewReport).toHaveBeenCalledWith(1, 2, 'MARKDOWN', 12));
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(downloadedFile).toEqual({ download: 'report.md', href: 'blob:markdown-report' });
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:markdown-report');
    downloadClick.mockRestore();
  });

  it('shows a completed task report as right-side findings when structured issues are empty', async () => {
    window.history.replaceState({}, '', '/script-review/tasks/30');
    mocks.queryReviewTask.mockResolvedValue({ data: {
      id: 30, projectId: 9, scriptVersionId: 9, roundNo: 1, reviewMode: 'DEEP', selectedDimensions: ['人物动机', '台词合理性'], reviewScopeType: 'ALL', reviewScope: {}, status: 'COMPLETED', overallProgress: 100, issues: [],
      resultFormat: 'MARKDOWN',
      reportMarkdown: '## 二、合并后的主要问题\n### 1. 角色转折缺少铺垫\n建议补充转折动机\n### 2. 字幕名称不一致\n建议统一译名\n## 三、各维度审核结论\n审核总结',
      boundVersion: { id: 9, projectId: 9, versionNo: 1, sourceType: 'IMPORT', content: '剧本原文仍然可读' },
    } });
    render(<ScriptReviewPage />);
    expect(await screen.findByRole('list', { name: '审核问题列表' })).toBeVisible();
    expect(screen.getByRole('heading', { name: '1. 角色转折缺少铺垫' })).toBeVisible();
    expect(screen.getByText('共 2 项问题')).toBeVisible();
    expect(screen.getByText('剧本原文仍然可读')).toBeVisible();
    expect(screen.getByRole('region', { name: '审核维度标签' })).toHaveTextContent('人物动机');
    expect(screen.getByRole('region', { name: '审核维度标签' })).toHaveTextContent('台词合理性');
    expect(screen.getByText('执行详情').closest('details')).not.toHaveAttribute('open');
    expect(mocks.createReviewTask).not.toHaveBeenCalled();
  });

  it('loads only the selected task on the dedicated task-detail route', async () => {
    window.history.replaceState({}, '', '/script-review/tasks/7');
    mocks.queryReviewTask.mockResolvedValue({ data: {
      id: 7, projectId: 1, scriptVersionId: 2, roundNo: 1, reviewMode: 'QUICK', selectedDimensions: [], reviewScopeType: 'ALL', reviewScope: {}, status: 'COMPLETED', overallProgress: 100, issues: [],
      boundVersion: { id: 2, projectId: 1, versionNo: 1, sourceType: 'IMPORT', content: '正文' },
    } });
    render(<ScriptReviewPage />);
    await waitFor(() => expect(mocks.queryReviewTask).toHaveBeenCalledWith(7));
    expect(mocks.queryReviewProjects).not.toHaveBeenCalled();
    expect(mocks.queryReviewProject).not.toHaveBeenCalled();
  });

  it('loads issue details for a summarized selected task', async () => {
    mocks.queryReviewProject.mockResolvedValueOnce({
      data: {
        project: { id: 1, name: '审稿样例', sourceType: 'TEXT', currentVersionId: 2, lastTaskId: 7, status: 'ACTIVE', versionCount: 1, latestRoundNo: 1 },
        versions: [{ id: 2, projectId: 1, versionNo: 1, sourceType: 'IMPORT', content: '第1集\n正文' }],
        tasks: [{ id: 7, projectId: 1, scriptVersionId: 2, roundNo: 1, reviewMode: 'QUICK', selectedDimensions: ['台词合理性'], reviewScopeType: 'ALL', reviewScope: {}, status: 'COMPLETED', overallProgress: 100, issues: [] }],
      },
    });
    mocks.queryReviewTask.mockResolvedValueOnce({
      data: {
        id: 7, projectId: 1, scriptVersionId: 2, roundNo: 1, reviewMode: 'QUICK', selectedDimensions: ['台词合理性'], reviewScopeType: 'ALL', reviewScope: {}, status: 'COMPLETED', overallProgress: 100,
        issues: [{ id: 21, taskId: 7, scriptVersionId: 2, roundNo: 1, issueNo: 'R1-01', dimension: '台词合理性', severity: 'MEDIUM', title: '真实问题', position: {}, excerpt: '正文', problem: '存在问题', evidence: ['正文'], suggestion: '调整', status: 'new', manuallyResolved: false, hits: [] }],
      },
    });

    render(<ScriptReviewPage />);

    await waitFor(() => expect(mocks.queryReviewTask).toHaveBeenCalledWith(7));
    expect((await screen.findAllByText('真实问题')).length).toBeGreaterThan(0);
  });

  it('highlights issue hits in the editor and shows version history', async () => {
    render(<ScriptReviewPage />);

    expect(await screen.findByText('版本历史')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '定位命中 1' }));

    const editor = screen
      .getAllByRole('textbox')
      .filter((node) => node.tagName === 'TEXTAREA')[0] as HTMLTextAreaElement;
    await waitFor(() => {
      expect(editor.value).toContain('林晚说：别走。');
      expect(editor.selectionStart).toBeGreaterThanOrEqual(0);
      expect(editor.selectionEnd).toBeGreaterThan(editor.selectionStart);
    });
    expect(screen.getByText('当前版本 V2')).toBeInTheDocument();
    expect(screen.getByText('第 1 轮 · 已完成')).toBeInTheDocument();
    expect(screen.getByText('问题 2 · 已处理 1')).toBeInTheDocument();
    expect(screen.getByText('整体良好')).toBeInTheDocument();
    expect(screen.getByText('PASS')).toBeInTheDocument();
  });

  it('loads history for the version selected in the editor', async () => {
    render(<ScriptReviewPage />);

    await screen.findByText('版本历史');
    fireEvent.change(screen.getAllByRole('combobox')[0], {
      target: { value: '1' },
    });

    await waitFor(() => {
      expect(mocks.queryReviewVersionHistory).toHaveBeenCalledWith(1, 1);
    });
  });

  it('selects the first issue when switching review rounds', async () => {
    render(<ScriptReviewPage />);

    fireEvent.click(await screen.findByText('第 2 轮 · QUICK'));
    expect((await screen.findAllByText('因果链缺失')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('人物离开的原因未交代')).not.toHaveLength(0);
  });

  it('opens review configuration in a modal before creating a task', async () => {
    render(<ScriptReviewPage />);

    expect(await screen.findByRole('button', { name: '发起审核' })).toBeInTheDocument();
    expect(screen.queryByText('新建审核任务')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '发起审核' }));
    expect(screen.getByText('新建审核任务')).toBeInTheDocument();
  });

  it('shows the selected issue in the issue detail panel', async () => {
    render(<ScriptReviewPage />);

    fireEvent.click((await screen.findAllByText('人名混乱'))[0]);
    expect(screen.getByText('问题详情')).toBeInTheDocument();
    expect(screen.getAllByText('同一句台词里称呼不一致')).not.toHaveLength(0);
  });

  it('filters the problem queue between pending and processed issues', async () => {
    render(<ScriptReviewPage />);

    expect(await screen.findByRole('button', { name: '未处理 (1)' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '已处理 (1)' }));
    expect(screen.getAllByText('已处理问题')).not.toHaveLength(0);
    expect(screen.queryAllByText('人名混乱')).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: '未处理 (1)' }));
    expect(screen.getAllByText('人名混乱')).not.toHaveLength(0);
  });

  it('uses selected hit fragments for batch repair and keeps processed issues folded away', async () => {
    render(<ScriptReviewPage />);

    expect(await screen.findByText('审核问题')).toBeInTheDocument();
    const firstHit = screen.getByLabelText('1. 台词：林晚说：别走。');
    fireEvent.click(firstHit);
    await waitFor(() => expect(firstHit).not.toBeChecked());
    fireEvent.click(screen.getByRole('button', { name: '批量修复' }));

    await waitFor(() => {
      expect(mocks.batchRepairReview).toHaveBeenCalledWith(7, {
        actionType: 'GLOBAL_REPLACE',
        replacementFrom: '林晚说：别走。',
        replacementTo: '林晚说：别走。',
        selectedHitIds: [102],
      });
    });
    expect(screen.getByRole('button', { name: '已处理 (1)' })).toBeInTheDocument();
  });

  it('follows the shared review execution and selects its domain task', async () => {
    render(<ScriptReviewPage />);

    await screen.findByText('审核问题');
    fireEvent.click(await screen.findByRole('button', { name: '发起审核' }));
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));

    await waitFor(() => {
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        10,
        701,
        expect.any(Function),
      );
      expect(mocks.queryReviewProject.mock.calls.length).toBeGreaterThanOrEqual(
        2,
      );
    });
    expect(screen.getByText('execution-701-SUCCEEDED')).toBeInTheDocument();
  });

  it('submits a single QUICK dimension with an explicit trusted scene scope', async () => {
    render(<ScriptReviewPage />);

    fireEvent.click(await screen.findByRole('button', { name: '发起审核' }));
    fireEvent.click(screen.getByLabelText('人物关系一致性'));
    fireEvent.click(screen.getByLabelText('人物认知一致性'));
    const scopeSelect = screen.getAllByRole('combobox').find((element) =>
      Array.from((element as HTMLSelectElement).options).some(
        (option) => option.value === 'SCENES',
      ),
    );
    expect(scopeSelect).toBeDefined();
    if (!scopeSelect) throw new Error('Scene scope select is missing');
    fireEvent.change(scopeSelect, { target: { value: 'SCENES' } });
    fireEvent.change(await screen.findByPlaceholderText('输入场次编号，用逗号分隔，如 1-2, 2-1'), {
      target: { value: '1-2, 2-1' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));

    await waitFor(() => {
      expect(mocks.createReviewTask).toHaveBeenCalledWith(1, {
        versionId: 2,
        reviewMode: 'QUICK',
        selectedDimensions: ['台词合理性'],
        reviewScopeType: 'SCENES',
        reviewScope: { sceneKeys: ['1-2', '2-1'] },
      });
    });
  });

  it('submits the default ordered multi-dimension QUICK review', async () => {
    render(<ScriptReviewPage />);

    await screen.findByText('审核问题');
    fireEvent.click(await screen.findByRole('button', { name: '发起审核' }));
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));

    await waitFor(() => {
      expect(mocks.createReviewTask).toHaveBeenCalledWith(1, {
        versionId: 2,
        reviewMode: 'QUICK',
        selectedDimensions: ['台词合理性', '人物关系一致性', '人物认知一致性'],
        reviewScopeType: 'ALL',
        reviewScope: {},
      });
    });
  });

  it('restores DEEP progress, stale state, and targeted retry actions', async () => {
    mocks.queryReviewProject.mockResolvedValueOnce({
      data: {
        project: {
          id: 1,
          name: '审稿样例',
          sourceType: 'TEXT',
          currentVersionId: 2,
          lastTaskId: 9,
          status: 'ACTIVE',
          versionCount: 2,
          latestRoundNo: 2,
        },
        versions: [
          {
            id: 2,
            projectId: 1,
            versionNo: 2,
            sourceType: 'MANUAL_EDIT',
            content: '无分集标题剧本正文',
          },
        ],
        tasks: [
          {
            id: 9,
            projectId: 1,
            scriptVersionId: 2,
            roundNo: 2,
            reviewMode: 'DEEP',
            selectedDimensions: ['台词合理性', '人物关系一致性'],
            reviewScopeType: 'ALL',
            reviewScope: {},
            status: 'FAILED',
            currentStage: 'DEEP_UNITS',
            overallProgress: 60,
            currentAction: '逐维审核 2/4',
            workflowAgentCode: 'script-review',
            workflowPhase: 'DEEP_CHILD',
            retryKind: 'FAILED_UNITS',
            stale: true,
            issues: [],
            fanout: {
              snapshotId: 90,
              status: 'PARTIAL_FAILED',
              totalUnits: 4,
              completedUnits: 2,
              failedUnits: 1,
              currentUnitId: 903,
              aggregationStatus: 'PENDING',
              units: [
                {
                  id: 903,
                  unitNo: 3,
                  unitKey: 'dimension-dialogue',
                  stageType: 'DIMENSION_DISCOVERY',
                  dimension: '台词合理性',
                  attemptNo: 2,
                  status: 'FAILED',
                  candidateSaved: false,
                  errorMessage: '模型调用失败',
                },
              ],
            },
            observability: {
              quality: {
                status: 'SUCCEEDED',
                runId: 904,
                attemptNo: 1,
                candidateCount: 4,
                decisionCount: 4,
                anomalyRequired: true,
                anomalyPassed: true,
              },
              decisions: {
                confirmed: 2,
                needsHumanReview: 1,
                rejected: 1,
                insufficientEvidence: 0,
              },
              humanReviewFindings: [
                {
                  candidateId: 905,
                  unitId: 903,
                  dimension: '台词合理性',
                  confidence: 0.62,
                  rationale: '存在合理替代解释',
                  severityDecision: 'LOW',
                  evidenceRefs: ['scene:1'],
                  candidate: { title: '告别突兀' },
                },
              ],
              cacheUsage: {
                promptTokens: 9631,
                ordinaryInputTokens: 159,
                cachedInputTokens: 8960,
                cacheWriteTokens: 512,
                outputTokens: 5,
                latencyMs: 1200,
                cacheHitRatio: 0.9303,
                cacheObservable: true,
              },
            },
          },
        ],
      },
    });
    mocks.retryReviewTask.mockResolvedValue({
      data: { id: 702, businessId: 9, status: 'PENDING', progress: 0 },
    });

    render(<ScriptReviewPage />);

    expect(await screen.findByText('审核维度 2/4 · 失败 1 · 聚合 PENDING · 当前 台词合理性')).toBeInTheDocument();
    expect(screen.getByText('台词合理性 · 失败 · 第 2 次')).toBeInTheDocument();
    expect(screen.getByText('输入已变化')).toBeInTheDocument();
    expect(screen.getByText('Skill：台词合理性')).toBeInTheDocument();
    expect(screen.getByText('语义质检：已完成 · 候选 4 / 裁决 4')).toBeInTheDocument();
    expect(screen.getByText('已确认 2')).toBeInTheDocument();
    expect(screen.getByText('待人工 1')).toBeInTheDocument();
    expect(screen.getByText('异常复核通过')).toBeInTheDocument();
    expect(screen.getByText('缓存 8960 / 9631 tokens · 命中率 93.03% · 耗时 1.2s')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试失败维度' }));
    await waitFor(() => expect(mocks.retryReviewTask).toHaveBeenCalledWith(9));
  });

  it('retries only DEEP aggregation when child candidates are complete', async () => {
    mocks.queryReviewProject.mockResolvedValueOnce({
      data: {
        project: {
          id: 1,
          name: '审稿样例',
          sourceType: 'TEXT',
          currentVersionId: 2,
          lastTaskId: 10,
          status: 'ACTIVE',
          versionCount: 2,
          latestRoundNo: 3,
        },
        versions: [
          {
            id: 2,
            projectId: 1,
            versionNo: 2,
            sourceType: 'MANUAL_EDIT',
            content: '无分集标题剧本正文',
          },
        ],
        tasks: [
          {
            id: 10,
            projectId: 1,
            scriptVersionId: 2,
            roundNo: 3,
            reviewMode: 'DEEP',
            selectedDimensions: ['剧情逻辑与因果'],
            reviewScopeType: 'ALL',
            reviewScope: {},
            status: 'FAILED',
            currentStage: 'DEEP_AGGREGATION',
            overallProgress: 90,
            currentAction: '聚合失败，可仅重试聚合',
            workflowAgentCode: 'script-review',
            workflowPhase: 'DEEP_AGGREGATION',
            retryKind: 'AGGREGATION_ONLY',
            stale: false,
            issues: [],
            fanout: {
              snapshotId: 91,
              status: 'FAILED',
              totalUnits: 4,
              completedUnits: 4,
              failedUnits: 0,
              aggregationStatus: 'FAILED',
              units: [],
            },
            observability: {
              quality: {
                status: 'SUCCEEDED',
                candidateCount: 1,
                decisionCount: 1,
                anomalyRequired: false,
              },
              decisions: {
                confirmed: 0,
                needsHumanReview: 1,
                rejected: 0,
                insufficientEvidence: 0,
              },
              humanReviewFindings: [
                {
                  candidateId: 1001,
                  unitId: 1002,
                  dimension: '剧情逻辑与因果',
                  confidence: 0.58,
                  rationale: '可能存在未明说的人物动机',
                  evidenceRefs: ['scene:2'],
                  candidate: { title: '人物决定缺少动机' },
                },
              ],
              cacheUsage: {
                promptTokens: 2100,
                outputTokens: 120,
                latencyMs: 860,
                cacheObservable: false,
              },
            },
          },
        ],
      },
    });
    mocks.retryReviewTask.mockResolvedValue({
      data: { id: 703, businessId: 10, status: 'PENDING', progress: 0 },
    });

    render(<ScriptReviewPage />);

    expect(await screen.findByText('深度单元 4/4 · 聚合 FAILED')).toBeInTheDocument();
    expect(screen.getByText('缓存明细不可观测 · 输入 2100 tokens · 输出 120 · 耗时 0.9s')).toBeInTheDocument();
    expect(screen.getByText('待人工复核 (1)')).toBeInTheDocument();
    expect(screen.getByText('人物决定缺少动机')).toBeInTheDocument();
    expect(screen.getByText('可能存在未明说的人物动机')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '仅重试聚合' }));
    await waitFor(() => expect(mocks.retryReviewTask).toHaveBeenCalledWith(10));
  });
});
