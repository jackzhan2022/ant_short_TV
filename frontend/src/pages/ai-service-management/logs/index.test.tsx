import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiCallLogsPage from './index';

const mocks = vi.hoisted(() => ({
  getCurrentTenantId: vi.fn(),
  lastProTableProps: undefined as any,
  queryAiCallLogs: vi.fn(),
}));
vi.mock('@ant-design/pro-components', () => ({
  ProTable: ({ columns, request, scroll }: any) => {
    mocks.lastProTableProps = { columns, scroll };
    void request({
      current: 1,
      pageSize: 20,
      serviceType: 'TEXT',
      status: 'SUCCESS',
      businessScene: 'chatbot',
    });
    const action = columns.find((column: any) => column.title === '操作');
    return (
      <section>
        {columns.map((column: any) => (
          <span key={column.title}>{column.title}</span>
        ))}
        {action.render(undefined, {
          id: 42,
          tenantId: 10,
          userId: 3,
          businessScene: 'chatbot',
          serviceType: 'TEXT',
          status: 'SUCCESS',
          model: '真实模型',
          provider: '真实服务商',
          requestSummary: '完整请求摘要',
          responseSummary: '完整响应摘要',
          errorMessage: '完整错误信息',
          promptTokens: 9631,
          cachedInputTokens: 8960,
          cacheWriteTokens: 512,
          completionTokens: 100,
          totalTokens: 9731,
          promptCacheKey: 'cache-key-42',
          durationMs: 120,
          createdAt: '2026-11-11 12:00:00',
        })}
      </section>
    );
  },
}));
vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));
vi.mock('./service', () => ({ queryAiCallLogs: mocks.queryAiCallLogs }));

describe('AiCallLogsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryAiCallLogs.mockResolvedValue({
      success: true,
      data: { records: [], total: 0, current: 1, pageSize: 20 },
    });
  });

  it('keeps tenant filters and opens complete log and cache details from the compact table', async () => {
    render(<AiCallLogsPage />);
    expect(
      screen.getByRole('heading', { name: '调用日志' }),
    ).toBeInTheDocument();
    expect(mocks.lastProTableProps.columns).toHaveLength(8);
    await waitFor(() =>
      expect(mocks.queryAiCallLogs).toHaveBeenCalledWith(10, {
        current: 1,
        pageSize: 20,
        serviceType: 'TEXT',
        status: 'SUCCESS',
        businessScene: 'chatbot',
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: '详情' }));
    const detail = within(await screen.findByRole('dialog'));
    for (const value of [
      '完整请求摘要',
      '完整响应摘要',
      '完整错误信息',
      '159',
      '8,960',
      '512',
      '100',
      '9,731',
      '93.0%',
      'cache-key-42',
      '真实模型',
      '真实服务商',
    ])
      expect(detail.getByText(value)).toBeInTheDocument();
    for (const label of [
      '输入 Token',
      '缓存 Token',
      '缓存写入',
      '输出 Token',
      '缓存命中率',
    ])
      expect(detail.getByText(label)).toBeInTheDocument();
  });

  it('prompts users to select a team when there is no current tenant', () => {
    mocks.getCurrentTenantId.mockReturnValue(undefined);
    render(<AiCallLogsPage />);
    expect(
      screen.getByText('请先在我的团队中选择当前创作团队'),
    ).toBeInTheDocument();
    expect(mocks.queryAiCallLogs).not.toHaveBeenCalled();
  });
});
