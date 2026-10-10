import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  queryProjects: async (...args: unknown[]) => {
    const response = await mocks.queryProjects(...args);
    return { ...response, data: Array.isArray(response.data)
      ? { data: response.data, total: response.data.length, current: 1, pageSize: 20 } : response.data };
  },
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
  Pagination: ({ current, onChange }: any) => <button type="button" aria-label="下一页" onClick={() => onChange(current + 1, 20)}>下一页</button>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children, title, trigger, onOpenChange }: any) => {
    const [open, setOpen] = require('react').useState(false);
    return <div>{require('react').cloneElement(trigger, {
      onClick: () => { trigger.props.onClick?.(); setOpen(true); onOpenChange?.(true); },
    })}{open && <form aria-label={title}>{children}</form>}</div>;
  },
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
  afterEach(() => vi.unstubAllGlobals());
  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', class {
      constructor(private callback: IntersectionObserverCallback) {}
      observe() { this.callback([{ isIntersecting: true }] as IntersectionObserverEntry[], this as unknown as IntersectionObserver); }
      disconnect() {}
    });
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
          coverStatus: 'READY',
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
    await waitFor(() => expect(screen.getByRole('img', { name: '卡片项目封面' })).toHaveAttribute(
      'src', 'https://example.com/card.jpg',
    ));
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

  it('requests the next server page and displays its authorized total', async () => {
    mocks.queryProjects.mockResolvedValue({ success: true,
      data: { data: [], current: 1, pageSize: 20, total: 45 } });
    render(<ProjectList />);
    await screen.findByText('共 45 个项目');
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() => expect(mocks.queryProjects).toHaveBeenCalledWith({ current: 2, pageSize: 20 }, expect.anything()));
  });
  it('keeps only edit and status actions without showing the internal project code', async () => {
    const response = await mocks.queryProjects();
    response.data[0].code = 'SHORT_DRAMA_1790949676308';
    response.data[0].status = 'NOT_STARTED';
    mocks.queryProjects.mockResolvedValue(response);
    render(<ProjectList />);
    await screen.findByText('测试短剧');
    const menu = screen.getByLabelText('测试短剧更多操作').closest('details');
    expect(menu).not.toBeNull();
    expect(within(menu as HTMLElement).getAllByRole('button')).toHaveLength(2);
    expect(
      within(menu as HTMLElement).getByRole('button', { name: '编辑' }),
    ).toBeInTheDocument();
    expect(
      within(menu as HTMLElement).getByRole('button', { name: '启动' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('SHORT_DRAMA_1790949676308'),
    ).not.toBeInTheDocument();
  });

  it('does not show an empty operation menu for read-only projects', async () => {
    const response = await mocks.queryProjects();
    response.data[0].capabilities.canEdit = false;
    mocks.queryProjects.mockResolvedValue(response);
    render(<ProjectList />);
    await screen.findByText('测试短剧');
    expect(screen.queryByLabelText('测试短剧更多操作')).not.toBeInTheDocument();
  });
});
