import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench from './index';

const mocks = vi.hoisted(() => ({
  historyPush: vi.fn(),
  queryProject: vi.fn(),
  queryTeamPointAccount: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
  useLocation: () => ({
    pathname: '/projects/1/production-workbench/settings',
  }),
  useParams: () => ({ id: '1' }),
  Outlet: () => <div data-testid="outlet" />,
}));

vi.mock('@/services/account-team/project', () => ({
  queryProject: mocks.queryProject,
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: () => 10,
}));

vi.mock('@/services/account-team/points', () => ({
  queryTeamPointAccount: mocks.queryTeamPointAccount,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, ...props }: any) => (
    <button type="button" onClick={onClick} {...props}>
      {icon}
      {children}
    </button>
  ),
  Flex: ({ children }: any) => <div>{children}</div>,
  Typography: {
    Text: ({ children }: any) => <span>{children}</span>,
  },
}));

vi.mock('@ant-design/icons', () => ({
  ArrowLeftOutlined: () => <span>back</span>,
  BookOutlined: () => <span>book</span>,
  EditOutlined: () => <span>edit</span>,
  RobotOutlined: () => <span>robot</span>,
  SettingOutlined: () => <span>setting</span>,
  SplitCellsOutlined: () => <span>split</span>,
  VideoCameraOutlined: () => <span>video</span>,
}));

describe('ProductionWorkbench shell', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryProject.mockResolvedValue({
      data: {
        id: 1,
        name: '最危险的捉迷藏',
        aspectRatio: '9:16',
        fileFormat: 'SCRIPT',
        scriptType: 'PREMIUM_DRAMA',
        breakdownStrength: 'MEDIUM',
        visualStyle: '写实都市',
      },
    });
    mocks.queryTeamPointAccount.mockResolvedValue({
      data: { balance: 88 },
    });
  });

  it('renders the shared header and navigates to child routes', async () => {
    render(<ProductionWorkbench />);

    expect(await screen.findByText('最危险的捉迷藏')).toBeInTheDocument();
    expect(screen.getByTestId('outlet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '设定' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 模型' })).toBeInTheDocument();
    expect(await screen.findByText((_, element) => element?.textContent === '✦ 88')).toBeInTheDocument();
    expect(screen.getAllByText('9:16').length).toBeGreaterThan(0);
    expect(screen.getByText('写实都市')).toBeInTheDocument();
    expect(screen.queryByText('绘梦工坊')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '初始设定' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '角色资产' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '返回' }));
    expect(mocks.historyPush).toHaveBeenCalledWith('/projects/list');

    fireEvent.click(screen.getByRole('button', { name: '分镜' }));

    expect(mocks.historyPush).toHaveBeenCalledWith(
      '/projects/1/production-workbench/storyboard',
    );

    fireEvent.click(screen.getByRole('button', { name: 'AI 模型' }));

    expect(mocks.historyPush).toHaveBeenCalledWith(
      '/projects/1/production-workbench/ai-config',
    );
  });
});
