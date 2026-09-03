import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProjectList from './index';

const mocks = vi.hoisted(() => ({
  historyPush: vi.fn(),
  canCreateProject: true,
  queryProjects: vi.fn(),
  queryTenantMembers: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
  useAccess: () => ({ canCreateProject: mocks.canCreateProject }),
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () => 9,
}));

vi.mock('./service', () => ({
  createProject: vi.fn(),
  queryProjects: mocks.queryProjects,
  queryTenantMembers: mocks.queryTenantMembers,
  updateProject: vi.fn(),
  updateProjectStatus: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  EditOutlined: () => <span data-testid="edit-icon" />,
  MoreOutlined: () => <span data-testid="more-icon" />,
  PlusOutlined: () => <span data-testid="plus-icon" />,
  TeamOutlined: () => <span data-testid="team-icon" />,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, type, disabled }: any) => (
    <button
      type="button"
      data-button-type={type}
      disabled={disabled}
      onClick={onClick}
    >
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, title, trigger }: any) => (
    <div>
      {trigger}
      <form aria-label={title}>{children}</form>
    </div>
  ),
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProFormDatePicker: ({ label }: any) => <span>{label}</span>,
  ProFormSelect: ({ label }: any) => <span>{label}</span>,
  ProFormText: ({ label }: any) => <span>{label}</span>,
  ProFormTextArea: ({ label }: any) => <span>{label}</span>,
  ProTable: ({ columns = [], request, toolBarRender }: any) => {
    const [rows, setRows] = require('react').useState([]);
    require('react').useEffect(() => {
      request?.().then((response: any) => setRows(response.data || []));
    }, [request]);

    return (
      <section>
        <div>{toolBarRender?.()}</div>
        {rows.map((record: any) => (
          <div key={record.id}>
            <span>{record.name}</span>
            {columns
              .find((column: any) => column.valueType === 'option')
              ?.render?.(null, record)}
          </div>
        ))}
      </section>
    );
  },
}));

describe('ProjectList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.canCreateProject = true;
    mocks.queryTenantMembers.mockResolvedValue({ data: [] });
    mocks.queryProjects.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          name: '测试短剧',
          code: 'TEST_DRAMA',
          status: 'IN_PROGRESS',
          memberCount: 3,
          capabilities: {
            canView: true,
            canEdit: true,
            canDelete: false,
            canManageMembers: false,
            canManageRoles: false,
          },
        },
      ],
    });
  });

  it('opens the production workbench script page by clicking the project card', async () => {
    render(<ProjectList />);

    await screen.findByText('测试短剧');
    fireEvent.click(screen.getByRole('button', { name: '进入测试短剧' }));

    expect(mocks.historyPush).toHaveBeenCalledWith(
      '/projects/1/production-workbench/script',
    );
  });

  it('loads tenant members only when opening the project editor', async () => {
    render(<ProjectList />);

    await screen.findByText('测试短剧');
    expect(mocks.queryTenantMembers).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    await waitFor(() => {
      expect(mocks.queryTenantMembers).toHaveBeenCalledWith(9);
    });
  });

  it('renders project cards with cover, status, owner and creation metadata', async () => {
    mocks.queryProjects.mockResolvedValue({
      success: true,
      data: [
        {
          id: 3,
          name: '卡片项目',
          code: 'CARD_PROJECT',
          coverUrl: 'https://example.com/card.jpg',
          ownerName: '张编剧',
          status: 'COMPLETED',
          memberCount: 4,
          createdAt: '2026-08-25T12:29:45Z',
          capabilities: {
            canView: true,
            canEdit: false,
            canDelete: false,
            canManageMembers: false,
            canManageRoles: false,
          },
        },
      ],
    });

    render(<ProjectList />);

    expect(await screen.findByText('卡片项目')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '卡片项目封面' })).toHaveAttribute(
      'src',
      'https://example.com/card.jpg',
    );
    expect(screen.getByText('已完成')).toBeInTheDocument();
    expect(screen.getByText('张编剧')).toBeInTheDocument();
    expect(screen.getByText(/2026-08-25/)).toBeInTheDocument();
    expect(screen.getByLabelText('4 位成员')).toBeInTheDocument();
    expect(screen.queryByText('短剧项目')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '进入' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '进度' }),
    ).not.toBeInTheDocument();
  });

  it('opens the independent short drama creation page from toolbar', async () => {
    render(<ProjectList />);

    await screen.findByText('测试短剧');
    fireEvent.click(screen.getByRole('button', { name: /创建项目/ }));

    expect(mocks.historyPush).toHaveBeenCalledWith('/short-drama-creation');
  });

  it('hides edit controls when the project capability denies editing', async () => {
    mocks.queryProjects.mockResolvedValue({
      success: true,
      data: [
        {
          id: 2,
          name: '只读项目',
          code: 'READ_ONLY',
          status: 'IN_PROGRESS',
          memberCount: 1,
          capabilities: {
            canView: true,
            canEdit: false,
            canDelete: false,
            canManageMembers: false,
            canManageRoles: false,
          },
        },
      ],
    });

    render(<ProjectList />);

    await screen.findByText('只读项目');
    expect(
      screen.queryByRole('button', { name: /编辑/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /归档/ }),
    ).not.toBeInTheDocument();
  });

  it('hides project creation without tenant PROJECT:CREATE permission', async () => {
    mocks.canCreateProject = false;
    render(<ProjectList />);
    await screen.findByText('测试短剧');
    expect(
      screen.queryByRole('button', { name: /创建项目/ }),
    ).not.toBeInTheDocument();
  });
});
