import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiCallLogsPage from './index';

const mocks = vi.hoisted(() => ({
  getCurrentTenantId: vi.fn(),
  lastProTableProps: undefined as any,
  queryAiCallLogs: vi.fn(),
}));

vi.mock('antd', () => ({
  Empty: ({ description }: any) => <div>{description}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ columns, headerTitle, request, scroll }: any) => {
    mocks.lastProTableProps = { columns, scroll };
    request?.(
      {
        current: 1,
        pageSize: 20,
        serviceType: 'TEXT',
        status: 'SUCCESS',
        businessScene: 'chatbot',
      },
      {},
      {},
    );
    return (
      <section>
        <h1>{headerTitle}</h1>
        <div>
          {columns.map((column: any) => (
            <span key={column.dataIndex || column.title}>{column.title}</span>
          ))}
        </div>
      </section>
    );
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('./service', () => ({
  queryAiCallLogs: mocks.queryAiCallLogs,
}));

describe('AiCallLogsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.lastProTableProps = undefined;
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryAiCallLogs.mockResolvedValue({
      success: true,
      data: {
        records: [],
        total: 0,
        current: 1,
        pageSize: 20,
      },
    });
  });

  it('renders AI call log table and requests tenant logs', async () => {
    render(<AiCallLogsPage />);

    expect(screen.getByText('AI调用日志')).toBeInTheDocument();
    expect(screen.getByText('业务场景')).toBeInTheDocument();
    expect(screen.getByText('服务类型')).toBeInTheDocument();
    expect(screen.getByText('调用状态')).toBeInTheDocument();
    expect(screen.getByText('输入 Token')).toBeInTheDocument();
    expect(screen.getByText('缓存 Token')).toBeInTheDocument();
    expect(screen.getByText('缓存写入')).toBeInTheDocument();
    expect(screen.getByText('输出 Token')).toBeInTheDocument();
    expect(screen.getByText('缓存命中率')).toBeInTheDocument();
    expect(screen.getByText('耗时')).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryAiCallLogs).toHaveBeenCalledWith(10, {
        current: 1,
        pageSize: 20,
        serviceType: 'TEXT',
        status: 'SUCCESS',
        businessScene: 'chatbot',
      });
    });
    expect(mocks.lastProTableProps.scroll).toEqual({ x: 2050 });

    const cacheRateColumn = mocks.lastProTableProps.columns.find(
      (column: any) => column.title === '缓存命中率',
    );
    expect(
      cacheRateColumn.renderText(undefined, {
        promptTokens: 9631,
        cachedInputTokens: 8960,
      }),
    ).toBe('93.0%');
    expect(cacheRateColumn.renderText(undefined, { promptTokens: 9631 })).toBe(
      '-',
    );

    const ordinaryInputColumn = mocks.lastProTableProps.columns.find(
      (column: any) => column.title === '输入 Token',
    );
    expect(
      ordinaryInputColumn.renderText(undefined, {
        promptTokens: 9631,
        cachedInputTokens: 8960,
        cacheWriteTokens: 512,
      }),
    ).toBe('159');

    for (const title of ['缓存 Token', '缓存写入', '输出 Token']) {
      const column = mocks.lastProTableProps.columns.find(
        (candidate: any) => candidate.title === title,
      );
      expect(column.renderText(undefined)).toBe('-');
    }
  });

  it('prompts users to select a team when there is no current tenant', () => {
    mocks.getCurrentTenantId.mockReturnValue(undefined);

    render(<AiCallLogsPage />);

    expect(
      screen.getByText('请先在我的团队中选择当前创作团队'),
    ).toBeInTheDocument();
  });
});
