import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench from './index';

const mocks = vi.hoisted(() => ({
  historyPush: vi.fn(),
  queryProject: vi.fn(),
  updateProject: vi.fn(),
  queryTeamPointAccount: vi.fn(),
  queryScriptWorkspace: vi.fn(),
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
  hasProjectPermission: () => true,
  queryProject: mocks.queryProject,
  updateProject: mocks.updateProject,
}));

vi.mock('./ai-config/service', () => ({
  queryProjectAiConfig: vi.fn().mockResolvedValue({ data: { projectId: 1 } }),
  queryProjectAiModels: vi.fn().mockResolvedValue({ data: { textModels: [], imageModels: [], videoModels: [], audioModels: [] } }),
  saveProjectAiConfig: vi.fn(),
}));

vi.mock('./service', () => ({
  queryScriptWorkspace: mocks.queryScriptWorkspace,
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
  Input: ({ value, onChange, onPressEnter: _onPressEnter, ...props }: any) => (
    <input value={value} onChange={onChange} {...props} />
  ),
  Modal: ({ title, open, children, onOk, onCancel, okText = '确定' }: any) =>
    open ? (
      <section role="dialog" aria-label={title}>
        {children}
        {onOk ? <button type="button" onClick={onOk}>{okText}</button> : null}
        {onCancel ? <button type="button" onClick={onCancel}>取消</button> : null}
      </section>
    ) : null,
  Empty: ({ description }: any) => <div>{description}</div>,
  Spin: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Result: ({ status, title }: any) => <div>{status} {title}</div>,
  Typography: {
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
    Paragraph: ({ children }: any) => <p>{children}</p>,
  },
}));

vi.mock('@ant-design/icons', () => ({
  AudioOutlined: () => <span>audio</span>,
  ArrowLeftOutlined: () => <span>back</span>,
  ArrowRightOutlined: () => <span>next</span>,
  BookOutlined: () => <span>book</span>,
  CheckCircleFilled: () => <span>checked</span>,
  EditOutlined: () => <span>edit</span>,
  PictureOutlined: () => <span>picture</span>,
  RobotOutlined: () => <span>robot</span>,
  SettingOutlined: () => <span>setting</span>,
  SoundOutlined: () => <span>sound</span>,
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
        effectivePermissions: ['PROJECT_AI_CONFIG_VIEW'],
        capabilities: { canView: true, canEdit: true },
      },
    });
    mocks.queryTeamPointAccount.mockResolvedValue({
      data: { balance: 88 },
    });
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: {
          id: 2,
          projectId: 1,
          title: '第一稿',
          sourceType: 'TEXT',
          content: '这是完整原文。\n第二段也需要展示。',
          status: 'READY',
        },
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
      },
    });
    mocks.updateProject.mockResolvedValue({
      data: {
        id: 1,
        name: '新项目名',
        aspectRatio: '9:16',
        visualStyle: '写实都市',
        effectivePermissions: ['PROJECT_AI_CONFIG_VIEW'],
        capabilities: { canView: true, canEdit: true },
      },
    });
  });

  it('renders the shared header and navigates to child routes', async () => {
    render(<ProductionWorkbench />);

    expect(await screen.findByText('最危险的捉迷藏')).toBeInTheDocument();
    expect(screen.getByTestId('outlet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /进入下一步/ })).toBeInTheDocument();
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

    expect(screen.getByRole('dialog', { name: '项目 AI 模型' })).toBeInTheDocument();
  });

  it('shows the full original script in a dialog', async () => {
    render(<ProductionWorkbench />);

    await screen.findByText('最危险的捉迷藏');
    fireEvent.click(screen.getByRole('button', { name: /查看原文/ }));

    expect(await screen.findByRole('dialog', { name: '剧本原文' })).toHaveTextContent(
      /这是完整原文。\s*第二段也需要展示。/,
    );
  });

  it('prefills and saves a renamed project in a dialog', async () => {
    render(<ProductionWorkbench />);

    await screen.findByText('最危险的捉迷藏');
    fireEvent.click(screen.getByRole('button', { name: '编辑项目名' }));
    const dialog = screen.getByRole('dialog', { name: '编辑项目名称' });
    const input = screen.getByRole('textbox', { name: '项目名称' });

    expect(input).toHaveValue('最危险的捉迷藏');
    fireEvent.change(input, { target: { value: ' 新项目名 ' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(mocks.updateProject).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: '新项目名' }),
    ));
    expect(dialog).not.toBeInTheDocument();
  });

  it('navigates from settings to storyboard with the next-step action', async () => {
    render(<ProductionWorkbench />);

    fireEvent.click(screen.getByRole('button', { name: /进入下一步/ }));

    expect(mocks.historyPush).toHaveBeenCalledWith(
      '/projects/1/production-workbench/storyboard',
    );
  });

  it('renders the backend authorization failure for a forbidden dynamic route', async () => {
    mocks.queryProject.mockRejectedValue({ response: { status: 403 } });

    render(<ProductionWorkbench />);

    expect(await screen.findByText(/无权访问该项目/)).toBeInTheDocument();
    expect(screen.queryByTestId('outlet')).not.toBeInTheDocument();
  });
});
