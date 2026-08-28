import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamSettings from './index';

const mocks = vi.hoisted(() => ({
  getCurrentTenantId: vi.fn(),
  queryTenant: vi.fn(),
  queryTenantMembers: vi.fn(),
  queryTeamPointAccount: vi.fn(),
  queryTeamPointTransactions: vi.fn(),
  updateTenant: vi.fn(),
  updateTenantStatus: vi.fn(),
  leaveTenant: vi.fn(),
  transferOwner: vi.fn(),
  success: vi.fn(),
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: mocks.success } }),
  },
  Button: ({ children, icon, ...props }: any) => (
    <button type="button" {...props}>
      {icon}
      {children}
    </button>
  ),
  Card: ({ children, extra, title }: any) => (
    <section>
      <h2>{title}</h2>
      {extra}
      {children}
    </section>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Popconfirm: ({ children }: any) => <div>{children}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Statistic: ({ title, value, suffix }: any) => (
    <div>
      <span>{title}</span>
      <strong>
        {value}
        {suffix}
      </strong>
    </div>
  ),
  Table: ({ dataSource }: any) => (
    <table>
      <tbody>
        {dataSource?.map((record: any) => (
          <tr key={record.id}>
            <td>{record.description}</td>
            <td>{record.balanceAfter}</td>
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, trigger }: any) => (
    <div>
      {trigger}
      {children}
    </div>
  ),
  PageContainer: ({ children, extra }: any) => (
    <main>
      {extra}
      {children}
    </main>
  ),
  ProForm: ({ children }: any) => <form>{children}</form>,
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
}));

vi.mock('./service', () => ({
  leaveTenant: mocks.leaveTenant,
  queryTeamPointAccount: mocks.queryTeamPointAccount,
  queryTeamPointTransactions: mocks.queryTeamPointTransactions,
  queryTenant: mocks.queryTenant,
  queryTenantMembers: mocks.queryTenantMembers,
  transferOwner: mocks.transferOwner,
  updateTenant: mocks.updateTenant,
  updateTenantStatus: mocks.updateTenantStatus,
}));

describe('TeamSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getCurrentTenantId.mockReturnValue(10);
    mocks.queryTenant.mockResolvedValue({
      success: true,
      data: {
        id: 10,
        code: 'TEAM',
        name: '创作团队',
        type: 'STUDIO',
        status: 'ACTIVE',
        memberType: 'OWNER',
        memberId: 100,
      },
    });
    mocks.queryTenantMembers.mockResolvedValue({ success: true, data: [] });
    mocks.queryTeamPointAccount.mockResolvedValue({
      success: true,
      data: {
        tenantId: 10,
        balance: 88,
        totalGranted: 100,
        totalConsumed: 12,
      },
    });
    mocks.queryTeamPointTransactions.mockResolvedValue({
      success: true,
      data: {
        records: [
          {
            id: 1,
            description: 'AI 调用消耗积分',
            balanceAfter: 88,
          },
          {
            id: 2,
            transactionType: 'ADJUST_GRANT',
            description: '历史手工增加',
            balanceAfter: 100,
          },
        ],
        total: 2,
        current: 1,
        pageSize: 20,
      },
    });
  });

  it('renders team point balance and loads point transactions', async () => {
    render(<TeamSettings />);

    await waitFor(() => {
      expect(mocks.queryTeamPointAccount).toHaveBeenCalledWith(10);
    });
    expect(screen.getByText('团队积分')).toBeInTheDocument();
    expect(screen.getByText('可用积分')).toBeInTheDocument();
    expect(screen.getByText('88点')).toBeInTheDocument();
    expect(screen.getByText('AI 调用消耗积分')).toBeInTheDocument();
    expect(screen.getByText('历史手工增加')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '调整积分' })).not.toBeInTheDocument();
  });
});
