import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MyTeams from './index';

const mocks = vi.hoisted(() => ({
  queryMyTenants: vi.fn(),
  createTenant: vi.fn(),
  switchTenant: vi.fn(),
  success: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  PlusOutlined: () => <span data-testid="plus-icon" />,
}));

vi.mock('@umijs/max', () => ({
  history: { push: vi.fn() },
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
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, trigger }: any) => (
    <div>
      {trigger}
      {children}
    </div>
  ),
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ headerTitle, request, toolBarRender }: any) => {
    request?.({}, {}, {});
    return (
      <section>
        <h1>{headerTitle}</h1>
        <div>{toolBarRender?.()}</div>
      </section>
    );
  },
}));

vi.mock('./service', () => ({
  createTenant: mocks.createTenant,
  queryMyTenants: mocks.queryMyTenants,
  switchTenant: mocks.switchTenant,
}));

describe('MyTeams', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryMyTenants.mockResolvedValue({ success: true, data: [] });
  });

  it('renders create action and requests my team list', async () => {
    render(<MyTeams />);

    expect(screen.getByText('我的创作团队')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '创建创作团队' }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryMyTenants).toHaveBeenCalled();
    });
  });
});
