import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench from './index';

const mocks = vi.hoisted(() => ({
  confirmScriptElement: vi.fn(),
  createAiImageTask: vi.fn(),
  deleteScriptElement: vi.fn(),
  extractScriptElements: vi.fn(),
  generateWorkflowPrompts: vi.fn(),
  historyPush: vi.fn(),
  messageWarning: vi.fn(),
  queryProject: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryAiImageTasks: vi.fn(),
  updateScriptElement: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
  useParams: () => ({ id: '1' }),
}));

vi.mock('../detail/service', () => ({
  queryProject: mocks.queryProject,
}));

vi.mock('../detail/components/service', () => ({
  confirmScriptElement: mocks.confirmScriptElement,
  createAiImageTask: mocks.createAiImageTask,
  deleteScriptElement: mocks.deleteScriptElement,
  extractScriptElements: mocks.extractScriptElements,
  generateWorkflowPrompts: mocks.generateWorkflowPrompts,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  queryAiImageTasks: mocks.queryAiImageTasks,
  updateScriptElement: mocks.updateScriptElement,
}));

vi.mock('../detail/components/ScriptCreationWorkspace', () => ({
  default: ({ initialTabKey }: { initialTabKey?: string }) => (
    <div>剧本容器-{initialTabKey || 'script'}</div>
  ),
}));

vi.mock('../detail/components/ShotProductionWorkspace', () => ({
  default: () => <div>视频容器</div>,
}));

vi.mock('@ant-design/icons', () => ({
  ArrowLeftOutlined: () => <span>back</span>,
  AudioOutlined: () => <span>audio</span>,
  BarsOutlined: () => <span>bars</span>,
  BulbOutlined: () => <span>bulb</span>,
  CheckCircleOutlined: () => <span>check</span>,
  CloseOutlined: () => <span>close</span>,
  CopyOutlined: () => <span>copy</span>,
  DeleteOutlined: () => <span>delete</span>,
  EditOutlined: () => <span>edit</span>,
  FileTextOutlined: () => <span>file</span>,
  BookOutlined: () => <span>book</span>,
  MoreOutlined: () => <span>more</span>,
  PictureOutlined: () => <span>image</span>,
  PlusOutlined: () => <span>plus</span>,
  ReloadOutlined: () => <span>reload</span>,
  SettingOutlined: () => <span>setting</span>,
  SoundOutlined: () => <span>sound</span>,
  SplitCellsOutlined: () => <span>split</span>,
  PlayCircleOutlined: () => <span>play</span>,
  UploadOutlined: () => <span>upload</span>,
  VideoCameraOutlined: () => <span>video</span>,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({
      message: { error: vi.fn(), success: vi.fn(), warning: mocks.messageWarning },
      modal: { confirm: vi.fn() },
    }),
  },
  Button: ({ children, icon, onClick, ...props }: any) => (
    <button type="button" onClick={onClick} aria-label={props['aria-label']}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Image: ({ alt, src }: any) => <img alt={alt} src={src} />,
  Spin: ({ children }: any) => <div>{children}</div>,
  Tooltip: ({ children }: any) => <>{children}</>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Tabs: ({ items = [] }: any) => (
    <div>
      {items.map((item: any) => (
        <section key={item.key}>{item.children}</section>
      ))}
    </div>
  ),
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
  },
}));

const setupWorkspaceResponse = (
  overrides?: Partial<{
    characters: any[];
    scenes: any[];
    props: any[];
    imageTasks: any[];
  }>,
) => {
  const characters = Array.from({ length: 13 }, (_, index) => {
    const names = [
      '斌斌',
      '冯建业',
      '李慧',
      '刘凤英',
      '物业经理',
      '司机',
      '保安',
      '邻居阿姨',
      '护士',
      '保姆',
      '同学',
      '老师',
      '警察',
    ];
    const name = names[index];
    return {
      id: index + 1,
      name,
      roleType: index === 0 ? 'LEAD' : 'SUPPORTING',
      gender: index % 2 === 0 ? '男' : '女',
      ageRange: '常规',
      identity: `${name}身份`,
      personality: ['活泼', '好奇'],
      appearance: `${name}外观`,
      prompt: `${name}提示词`,
    };
  });
  const scenes = Array.from({ length: 21 }, (_, index) => ({
    id: index + 1,
    name: `${['停车场', '后备箱内部', '小区广场', '新闻播音室'][index % 4]}${index + 1}`,
    sceneType: index % 2 === 0 ? '室外' : '室内',
    atmosphere: '日间',
    description: '场景描述',
    visualStyle: '写实',
    prompt: '场景提示词',
  }));
  const props = [
    {
      id: 1,
      name: '棒棒糖',
      propType: '食品',
      appearance: '棒棒糖',
      plotFunction: '道具',
      prompt: '棒棒糖提示词',
    },
    {
      id: 2,
      name: '灰色轿车后备箱',
      propType: '交通工具',
      appearance: '灰色轿车后备箱',
      plotFunction: '剧情道具',
      prompt: '后备箱提示词',
    },
    {
      id: 3,
      name: '拼图盒',
      propType: '玩具',
      appearance: '拼图盒',
      plotFunction: '玩具道具',
      prompt: '拼图盒提示词',
    },
    {
      id: 4,
      name: '玩具汽车',
      propType: '玩具',
      appearance: '玩具汽车',
      plotFunction: '玩具道具',
      prompt: '玩具汽车提示词',
    },
  ];
  mocks.queryProject.mockResolvedValue({
    data: {
      id: 1,
      name: '最危险的捉迷藏',
      code: 'DANGEROUS_HIDE_AND_SEEK',
      status: 'IN_PROGRESS',
      coverUrl: '/cover.png',
    },
  });
  mocks.queryScriptWorkspace.mockResolvedValue({
    data: {
      script: {
        id: 11,
        projectId: 1,
        title: '最危险的捉迷藏',
        sourceType: 'AI_GENERATE',
        content:
          '剧本正文\n\n1. 第一集\n2. 第二集\n人物：斌斌、冯建业、李慧、刘凤英、物业经理、司机',
        status: 'CONFIRMED',
      },
      characters: overrides?.characters ?? characters,
      scenes: overrides?.scenes ?? scenes,
      props: overrides?.props ?? props,
      storyboards: [],
    },
  });
  mocks.queryAiImageTasks.mockResolvedValue({
    data:
      overrides?.imageTasks ?? [
        {
          id: 101,
          projectId: 1,
          taskType: 'CHARACTER',
          targetType: 'CHARACTER',
          targetId: 1,
          serviceConfigId: 1,
          providerCode: 'mock',
          model: 'mock',
          prompt: 'child',
          referenceImages: [],
          aspectRatio: '3:4',
          imageCount: 4,
          status: 'SUCCESS',
          createdBy: 1,
          results: [
            {
              id: 11,
              taskId: 101,
              targetType: 'CHARACTER',
              targetId: 1,
              imageUrl: 'https://example.com/character-1.png',
              thumbnailUrl: 'https://example.com/character-1-thumb.png',
              selected: true,
              status: 'SUCCESS',
            },
            {
              id: 12,
              taskId: 101,
              targetType: 'CHARACTER',
              targetId: 1,
              imageUrl: 'https://example.com/character-2.png',
              thumbnailUrl: 'https://example.com/character-2-thumb.png',
              selected: false,
              status: 'SUCCESS',
            },
            {
              id: 13,
              taskId: 101,
              targetType: 'CHARACTER',
              targetId: 1,
              imageUrl: 'https://example.com/character-3.png',
              thumbnailUrl: 'https://example.com/character-3-thumb.png',
              selected: false,
              status: 'SUCCESS',
            },
            {
              id: 14,
              taskId: 101,
              targetType: 'CHARACTER',
              targetId: 1,
              imageUrl: 'https://example.com/character-4.png',
              thumbnailUrl: 'https://example.com/character-4-thumb.png',
              selected: false,
              status: 'SUCCESS',
            },
          ],
        },
      ],
  });
};

describe('ProductionWorkbench script page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupWorkspaceResponse();
    mocks.extractScriptElements.mockResolvedValue({ data: {} });
    mocks.generateWorkflowPrompts.mockResolvedValue({ data: {} });
  });

  it('renders collage media and runs the settings actions', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.queryProject).toHaveBeenCalledWith(1);
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryAiImageTasks).toHaveBeenCalledWith(1, undefined);
    });

    expect(screen.getAllByText('最危险的捉迷藏').length).toBeGreaterThan(0);
    expect(screen.getAllByText('剧本').length).toBeGreaterThan(0);
    expect(screen.getAllByText('设定').length).toBeGreaterThan(0);
    expect(screen.getAllByText('分镜').length).toBeGreaterThan(0);
    expect(screen.getAllByText('视频').length).toBeGreaterThan(0);
    expect(screen.getByText(/请确保角色、场景及道具已全部生成/)).toBeInTheDocument();
    expect(screen.getByText('角色')).toBeInTheDocument();
    expect(screen.getByText('场景')).toBeInTheDocument();
    expect(screen.getByText('道具')).toBeInTheDocument();
    expect(screen.getByText('角色总计 13')).toBeInTheDocument();
    expect(screen.getByText('斌斌')).toBeInTheDocument();
    expect(screen.getByText(/斌斌身份/)).toBeInTheDocument();
    expect(screen.getAllByAltText('斌斌参考图').length).toBe(4);
    expect(screen.getAllByText('配置音色').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: '添加角色' }));
    await waitFor(() => {
      expect(mocks.extractScriptElements).toHaveBeenCalledWith(1, {
        elementType: 'CHARACTER',
      });
    });

    fireEvent.click(screen.getByRole('button', { name: '批量生成' }));
    await waitFor(() => {
      expect(mocks.generateWorkflowPrompts).toHaveBeenCalledWith(1, {
        targetType: 'CHARACTER',
      });
    });

    fireEvent.click(screen.getByRole('button', { name: '关闭提示' }));
    expect(screen.queryByText(/请确保角色、场景及道具已全部生成/)).not.toBeInTheDocument();

    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '场景' }));
    });
    expect(screen.getByText('场景总计 21')).toBeInTheDocument();
    expect(screen.getAllByText(/场景描述/).length).toBeGreaterThan(0);

    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '道具' }));
    });
    expect(screen.getByText('道具总计 4')).toBeInTheDocument();
    expect(screen.getAllByText('棒棒糖').length).toBeGreaterThan(0);
    expect(screen.getAllByText('灰色轿车后备箱').length).toBeGreaterThan(0);
  });

  it('shows an empty state instead of sample settings when backend returns no elements', async () => {
    setupWorkspaceResponse({
      characters: [],
      scenes: [],
      props: [],
      imageTasks: [],
    });

    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByText('角色总计 0')).toBeInTheDocument();
    });

    expect(screen.getByText('暂无角色设定')).toBeInTheDocument();
    expect(screen.queryByText('斌斌')).not.toBeInTheDocument();
    expect(screen.queryByText('停车场')).not.toBeInTheDocument();
    expect(screen.queryByText('棒棒糖')).not.toBeInTheDocument();
  });

  it('does not submit unsupported settings actions to missing backend APIs', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });

    fireEvent.click(screen.getAllByRole('button', { name: /配置音色/ })[0]);
    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
    expect(mocks.messageWarning).toHaveBeenCalledWith(
      '角色音色配置请在语音字幕流程中完成',
    );

    fireEvent.click(screen.getByRole('button', { name: '道具' }));
    fireEvent.click(screen.getByRole('button', { name: '生成灰色轿车后备箱图片' }));

    expect(mocks.createAiImageTask).not.toHaveBeenCalled();
    expect(mocks.messageWarning).toHaveBeenCalledWith(
      '当前后端暂不支持道具图片生成任务',
    );
  });

  it('switches top production steps inside the workbench', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByText('角色总计 13')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '剧本' }));
    expect(screen.getByText('剧本容器-script')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '分镜' }));
    expect(screen.getByText('剧本容器-storyboard')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '视频' }));
    expect(screen.getByText('视频容器')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '设定' }));
    expect(screen.getByText('角色总计 13')).toBeInTheDocument();
    expect(mocks.historyPush).not.toHaveBeenCalled();
  });
});
