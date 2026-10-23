import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { cloneElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TeamSettings from './index';

const mocks = vi.hoisted(() => ({
  canManage: true,
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
  setInitialState: vi.fn(),
  formValues: {} as any,
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('@umijs/max', () => ({
  useModel: () => ({ setInitialState: mocks.setInitialState, initialState: { tenantPermissions: mocks.canManage ? ['MENU:TEAM_MANAGEMENT'] : [] } }),
}));
vi.mock('@/components/TeamIconPicker', () => ({
  default: ({ value, onChange, disabled }: any) =>
    disabled ? (
      <span>只读团队图标</span>
    ) : (
      <button
        type="button"
        aria-label="选择团队图标"
        data-value={value || ''}
        onClick={() => onChange?.('icon:camera')}
      >
        选择相机
      </button>
    ),
}));

vi.mock('antd', () => ({
  Form: {
    Item: ({ children, label, name }: any) => (
      <div>
        {label}
        {cloneElement(children, {
          value: mocks.formValues[name],
          onChange: (value: unknown) => {
            mocks.formValues[name] = value;
          },
        })}
      </div>
    ),
  },
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
  ProForm: ({ children, initialValues, onFinish, submitter }: any) => {
    mocks.formValues = {
      name: initialValues.name,
      type: initialValues.type,
      logo: initialValues.logo,
      description: initialValues.description,
    };
    return (
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void onFinish(mocks.formValues);
        }}
      >
        {children}
        {submitter !== false && <button type="submit">保存团队</button>}
      </form>
    );
  },
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
    mocks.canManage = true;
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

  it('saves a picked icon and refreshes the global team summary for immediate sidebar display', async () => {
    const updated = {
      id: 10,
      code: 'TEAM',
      name: '创作团队',
      type: 'STUDIO',
      status: 'ACTIVE',
      memberType: 'OWNER',
      memberId: 100,
      logo: 'icon:camera',
    };
    mocks.updateTenant.mockResolvedValue({ success: true, data: updated });
    render(<TeamSettings />);
    fireEvent.click(
      await screen.findByRole('button', { name: '选择团队图标' }),
    );
    fireEvent.click(screen.getByRole('button', { name: '保存团队' }));
    await waitFor(() =>
      expect(mocks.updateTenant).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ logo: 'icon:camera' }),
      ),
    );
    await waitFor(() => expect(mocks.setInitialState).toHaveBeenCalled());
    const update = mocks.setInitialState.mock.calls[0][0];
    const state = update({
      tenants: [
        { ...updated, logo: undefined },
        { ...updated, id: 11 },
      ],
      selectedTenant: {
        tenant: { ...updated, logo: undefined },
        permissions: ['PROJECT:CREATE'],
      },
      tenantPermissions: ['PROJECT:CREATE'],
    });
    expect(state.tenants[0].logo).toBe('icon:camera');
    expect(state.selectedTenant.tenant.logo).toBe('icon:camera');
    expect(state.tenantPermissions).toEqual(['PROJECT:CREATE']);
  });

  it('keeps the icon read-only without the management menu', async () => {
    mocks.canManage = false;
    const response = await mocks.queryTenant();
    mocks.queryTenant.mockResolvedValue({
      data: { ...response.data, memberType: 'MEMBER', logo: 'icon:star' },
    });
    render(<TeamSettings />);
    expect(await screen.findByText('只读团队图标')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '选择团队图标' }),
    ).not.toBeInTheDocument();
  });

  it('loads team settings without loading or rendering point information', async () => {
    render(<TeamSettings />);

    await waitFor(() => {
      expect(mocks.queryTenant).toHaveBeenCalledWith(10);
    });
    expect(mocks.queryTenantMembers).toHaveBeenCalledWith(10);
    expect(mocks.queryTeamPointAccount).not.toHaveBeenCalled();
    expect(mocks.queryTeamPointTransactions).not.toHaveBeenCalled();
    expect(screen.getByText('团队名称')).toBeInTheDocument();
    expect(screen.queryByText('团队积分')).not.toBeInTheDocument();
    expect(screen.queryByText('可用积分')).not.toBeInTheDocument();
    expect(screen.queryByText('累计获得')).not.toBeInTheDocument();
    expect(screen.queryByText('累计消耗')).not.toBeInTheDocument();
  });
});
