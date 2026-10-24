import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Page from './index';
import { reviewPollingDelay } from './queries';

const mocks = vi.hoisted(() => ({
  submit: vi.fn(),
  progress: vi.fn(),
  report: vi.fn(),
  content: vi.fn(),
  history: vi.fn(),
  retry: vi.fn(),
  cancel: vi.fn(),
  replace: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  location: { pathname: '/script-review-library', search: '' },
}));
vi.mock('@umijs/max', () => ({
  history: { replace: mocks.replace },
  useLocation: () => mocks.location,
}));
vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () =>
    Number(localStorage.getItem('currentTenantId')) || undefined,
}));
vi.mock('@/services/point-estimate', () => ({
  isPointEstimateCancelled: (error: Error) =>
    error.name === 'PointEstimateCancelledError',
}));
vi.mock('../script-review/simpleService', () => ({
  submitReview: mocks.submit,
  getReviewProgress: mocks.progress,
  getReviewReport: mocks.report,
  getReviewContent: mocks.content,
  getReviewHistory: mocks.history,
  retrySimpleReview: mocks.retry,
  cancelSimpleReview: mocks.cancel,
}));
vi.mock('../script-review/ReportIssueList', () => ({
  default: ({ markdown }: { markdown: string }) => (
    <article>{markdown}</article>
  ),
}));
vi.mock('../short-drama-creation/ScriptContentImport', () => ({
  default: ({ onImport, disabled }: any) => (
    <button
      disabled={disabled}
      onClick={() => onImport('第一集\n导入的剧本')}
      type="button"
    >
      导入剧本文件
    </button>
  ),
}));
vi.mock('antd', () => ({
  App: {
    useApp: () => ({
      message: { error: mocks.error, success: mocks.success },
      modal: { confirm: ({ onOk }: any) => onOk?.() },
    }),
  },
  Button: ({ children, disabled, loading, onClick, ...props }: any) => (
    <button
      type="button"
      disabled={disabled || loading}
      onClick={onClick}
      aria-expanded={props['aria-expanded']}
    >
      {children}
    </button>
  ),
  Checkbox: ({ children, checked, disabled, onChange }: any) => (
    <label>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={onChange}
      />
      {children}
    </label>
  ),
  Input: {
    TextArea: ({ id, value, onChange, disabled, placeholder }: any) => (
      <textarea
        id={id}
        aria-label="剧本内容"
        value={value}
        onChange={onChange}
        disabled={disabled}
        placeholder={placeholder}
      />
    ),
  },
  Space: ({ children }: any) => <div>{children}</div>,
  Alert: ({ title, action }: any) => (
    <div role="alert">
      {title}
      {action}
    </div>
  ),
  Progress: ({ percent }: any) => (
    <div role="progressbar" aria-valuenow={percent} />
  ),
  Drawer: ({ open, title, children, onClose }: any) =>
    open ? (
      <section aria-label={title}>
        <button onClick={onClose} type="button">
          关闭历史
        </button>
        {children}
      </section>
    ) : null,
  Pagination: ({ current, total, onChange }: any) =>
    total > 20 ? (
      <button type="button" onClick={() => onChange(current + 1)}>
        下一页
      </button>
    ) : null,
}));

const task = {
  id: 31,
  projectId: 4,
  scriptVersionId: 8,
  name: '雨夜来信',
  status: 'COMPLETED',
  selectedDimensions: ['台词合理性'],
  createdAt: '2026-10-24T10:00:00',
};
beforeEach(() => {
  vi.clearAllMocks();
  localStorage.setItem('currentTenantId', '7');
  mocks.location = { pathname: '/script-review-library', search: '' };
  mocks.submit.mockResolvedValue({ data: { businessId: 31 } });
  mocks.progress.mockResolvedValue({
    id: 31,
    status: 'COMPLETED',
    overallProgress: 100,
  });
  mocks.report.mockResolvedValue({
    task,
    reportMarkdown: '# 审核结论：需要补充人物动机',
  });
  mocks.content.mockResolvedValue({ id: 8, content: '提交时的原始剧本' });
  mocks.history.mockResolvedValue({
    items: [task],
    page: 1,
    pageSize: 20,
    total: 1,
  });
});
afterEach(cleanup);

describe('简化剧本审核', () => {
  it('输入和选择维度时不加载项目、历史、进度或报告', () => {
    render(<Page />);
    expect(screen.getByRole('button', { name: '开始审核' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('剧本内容'), {
      target: { value: '第一集\n林晚走进门。' },
    });
    fireEvent.click(screen.getByLabelText('台词合理性'));
    expect(screen.getByRole('button', { name: '开始审核' })).toBeEnabled();
    for (const fn of [
      mocks.history,
      mocks.progress,
      mocks.report,
      mocks.content,
      mocks.submit,
    ])
      expect(fn).not.toHaveBeenCalled();
  });

  it('提交后只读取状态和报告，原文按需加载，返回保留草稿', async () => {
    render(<Page />);
    fireEvent.click(screen.getByRole('button', { name: '导入剧本文件' }));
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));
    await screen.findByText('# 审核结论：需要补充人物动机');
    expect(mocks.submit).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        content: '第一集\n导入的剧本',
        selectedDimensions: ['台词合理性', '人物关系一致性', '人物认知一致性'],
        requestId: expect.any(String),
      }),
      expect.any(AbortSignal),
    );
    expect(mocks.content).not.toHaveBeenCalled();
    expect(mocks.history).not.toHaveBeenCalled();
    const details = screen
      .getByText('查看本次提交的剧本原文')
      .closest('details');
    if (!details) throw new Error('Missing original text section');
    act(() => {
      details.open = true;
      fireEvent(details, new Event('toggle'));
    });
    await screen.findByText('提交时的原始剧本');
    expect(mocks.content).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '返回输入' }));
    expect(screen.getByLabelText('剧本内容')).toHaveValue('第一集\n导入的剧本');
  });

  it('历史列表只在打开时请求，选中某条后才读取报告且不覆盖草稿', async () => {
    render(<Page />);
    fireEvent.change(screen.getByLabelText('剧本内容'), {
      target: { value: '未提交的草稿' },
    });
    fireEvent.click(screen.getByRole('button', { name: '历史报告' }));
    await screen.findByText('雨夜来信');
    expect(mocks.report).not.toHaveBeenCalled();
    expect(mocks.content).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '查看报告' }));
    await screen.findByText('# 审核结论：需要补充人物动机');
    fireEvent.click(screen.getByRole('button', { name: '返回输入' }));
    expect(screen.getByLabelText('剧本内容')).toHaveValue('未提交的草稿');
  });

  it('网络失败后重试复用提交标识，改变输入后生成新标识', async () => {
    mocks.submit.mockRejectedValue(new Error('网络中断'));
    render(<Page />);
    fireEvent.change(screen.getByLabelText('剧本内容'), {
      target: { value: '剧本 A' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));
    await screen.findByText('网络中断');
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));
    await waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '开始审核' })).toBeEnabled(),
    );
    expect(mocks.submit.mock.calls[0][1].requestId).toBe(
      mocks.submit.mock.calls[1][1].requestId,
    );
    fireEvent.change(screen.getByLabelText('剧本内容'), {
      target: { value: '剧本 B' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));
    await waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(3));
    expect(mocks.submit.mock.calls[2][1].requestId).not.toBe(
      mocks.submit.mock.calls[0][1].requestId,
    );
  });

  it('切换团队清空草稿并阻止旧提交响应打开任务', async () => {
    let resolve!: (value: unknown) => void;
    mocks.submit.mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    render(<Page />);
    fireEvent.change(screen.getByLabelText('剧本内容'), {
      target: { value: '团队 7 的剧本' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始审核' }));
    await waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(1));
    const signal = mocks.submit.mock.calls[0][2];
    act(() => {
      localStorage.setItem('currentTenantId', '8');
      window.dispatchEvent(new Event('current-tenant-changed'));
    });
    expect(screen.getByLabelText('剧本内容')).toHaveValue('');
    expect(signal.aborted).toBe(true);
    await act(async () => resolve({ data: { businessId: 31 } }));
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(mocks.progress).not.toHaveBeenCalled();
  });

  it('完成后停止轮询，失败请求逐步退避', () => {
    expect(reviewPollingDelay('RUNNING')).toBe(3000);
    expect(reviewPollingDelay('RUNNING', 2)).toBe(12000);
    expect(reviewPollingDelay(undefined, 6)).toBe(30000);
    for (const status of ['COMPLETED', 'FAILED', 'CANCELED'])
      expect(reviewPollingDelay(status)).toBe(false);
  });
});
