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
    Card: ({ children, extra, title }: any) => (
      <section>
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
            const checked = value.includes(option.value);
            return (
              <label key={option.value}>
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={disabled}
                  onChange={() => {
                    const next = checked
                      ? value.filter((item: any) => item !== option.value)
                      : [...value, option.value];
                    onChange?.(next);
                  }}
                />
                <span>{option.label}</span>
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

vi.mock('./service', () => ({
  batchRepairReview: mocks.batchRepairReview,
  cancelReviewTask: mocks.cancelReviewTask,
  createReviewTask: mocks.createReviewTask,
  exportReviewReport: mocks.exportReviewReport,
  importReviewProject: mocks.importReviewProject,
  queryReviewProject: mocks.queryReviewProject,
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
    vi.clearAllMocks();
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

  it('highlights issue hits in the editor and shows version history', async () => {
    render(<ScriptReviewPage />);

    expect(await screen.findByText('版本历史')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '定位命中 1' }));

    const editor = screen
      .getAllByRole('textbox')
      .filter((node) => node.tagName === 'TEXTAREA')[1] as HTMLTextAreaElement;
    await waitFor(() => {
      expect(editor.value).toContain('林晚说：别走。');
      expect(editor.selectionStart).toBeGreaterThanOrEqual(0);
      expect(editor.selectionEnd).toBeGreaterThan(editor.selectionStart);
    });
    expect(screen.getByText('当前版本 V2')).toBeInTheDocument();
    expect(screen.getByText('第 1 轮 · COMPLETED')).toBeInTheDocument();
    expect(screen.getByText('问题 2 · 已处理 1')).toBeInTheDocument();
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
    expect(screen.getByText('已处理 (1)')).toBeInTheDocument();
    expect(screen.getByText('R1-02')).toBeInTheDocument();
  });

  it('follows the shared review execution and selects its domain task', async () => {
    render(<ScriptReviewPage />);

    fireEvent.click(
      await screen.findByRole('button', { name: '创建审核任务' }),
    );

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
});
