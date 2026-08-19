import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProjectList from './index';

const mocks = vi.hoisted(() => ({
  historyPush: vi.fn(),
  queryOrganizations: vi.fn(),
  queryProjects: vi.fn(),
  queryTenantMembers: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () => 9,
}));

vi.mock('./service', () => ({
  createProject: vi.fn(),
  queryOrganizations: mocks.queryOrganizations,
  queryProjects: mocks.queryProjects,
  queryTenantMembers: mocks.queryTenantMembers,
  updateProject: vi.fn(),
  updateProjectStatus: vi.fn(),
}));

vi.mock('@ant-design/icons', () => ({
  EditOutlined: () => <span data-testid="edit-icon" />,
  FolderOpenOutlined: () => <span data-testid="folder-icon" />,
  PlusOutlined: () => <span data-testid="plus-icon" />,
  ProfileOutlined: () => <span data-testid="progress-icon" />,
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
    mocks.queryOrganizations.mockResolvedValue({ data: [] });
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
        },
      ],
    });
  });

  it('opens the independent production workbench from project progress', async () => {
    render(<ProjectList />);

    await screen.findByText('测试短剧');
    fireEvent.click(screen.getByRole('button', { name: /进度/ }));

    await waitFor(() => {
      expect(mocks.historyPush).toHaveBeenCalledWith(
        '/projects/1/production-workbench',
      );
    });
  });
});
