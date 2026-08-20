import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench from './index';

const mocks = vi.hoisted(() => ({
  historyPush: vi.fn(),
  queryProject: vi.fn(),
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

vi.mock('../detail/service', () => ({
  queryProject: mocks.queryProject,
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
      },
    });
  });

  it('renders the shared header and navigates to child routes', async () => {
    render(<ProductionWorkbench />);

    expect(await screen.findByText('最危险的捉迷藏')).toBeInTheDocument();
    expect(screen.getByTestId('outlet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '设定' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '分镜' }));

    expect(mocks.historyPush).toHaveBeenCalledWith(
      '/projects/1/production-workbench/storyboard',
    );
  });
});
