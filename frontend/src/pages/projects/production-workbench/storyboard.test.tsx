import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbench from './storyboard';

const mocks = vi.hoisted(() => ({
  queryProject: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  queryAiImageTasks: vi.fn(),
  queryAiImageTask: vi.fn(),
  createAiImageTask: vi.fn(),
  regenerateAiImageTask: vi.fn(),
  cancelAiImageTask: vi.fn(),
  queryAiVideoTasks: vi.fn(),
  createAiVideoTask: vi.fn(),
  cancelAiVideoTask: vi.fn(),
  regenerateAiVideoTask: vi.fn(),
  createStoryboard: vi.fn(),
  updateStoryboard: vi.fn(),
  deleteStoryboard: vi.fn(),
  pollExecution: vi.fn(),
  cancelExecution: vi.fn(),
  retryExecution: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: vi.fn(),
  },
  useParams: () => ({ id: '1' }),
}));

vi.mock('@/services/account-team/project', () => ({
  queryProject: mocks.queryProject,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: {
    poll: mocks.pollExecution,
    cancel: mocks.cancelExecution,
    retry: mocks.retryExecution,
  },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task, onCancel, onRetry }: any) => (
    <span>
      execution:{task.status}
      {onCancel ? (
        <button
          type="button"
          aria-label={`execution-cancel-${task.id}`}
          onClick={onCancel}
        />
      ) : null}
      {onRetry ? (
        <button
          type="button"
          aria-label={`execution-retry-${task.id}`}
          onClick={onRetry}
        />
      ) : null}
    </span>
  ),
}));

vi.mock('./service', () => ({
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  queryAiImageTasks: mocks.queryAiImageTasks,
  queryAiImageTask: mocks.queryAiImageTask,
  createAiImageTask: mocks.createAiImageTask,
  regenerateAiImageTask: mocks.regenerateAiImageTask,
  cancelAiImageTask: mocks.cancelAiImageTask,
  queryAiVideoTasks: mocks.queryAiVideoTasks,
  createAiVideoTask: mocks.createAiVideoTask,
  cancelAiVideoTask: mocks.cancelAiVideoTask,
  regenerateAiVideoTask: mocks.regenerateAiVideoTask,
  createStoryboard: mocks.createStoryboard,
  updateStoryboard: mocks.updateStoryboard,
  deleteStoryboard: mocks.deleteStoryboard,
}));

vi.mock('./ShotProductionWorkspace', () => ({
  default: () => null,
}));

vi.mock('@ant-design/icons', () => ({
  ArrowLeftOutlined: () => <span>back</span>,
  AudioOutlined: () => <span>audio</span>,
  BarsOutlined: () => <span>bars</span>,
  BulbOutlined: () => <span>bulb</span>,
  CheckCircleOutlined: () => <span>check</span>,
  CloseOutlined: () => <span>close</span>,
  CopyOutlined: () => <span>copy</span>,
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
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, ...rest }: any) => (
    <button type="button" onClick={onClick} {...rest}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Image: ({ alt, src }: any) => <img alt={alt} src={src} />,
  Input: {
    TextArea: ({ 'aria-label': ariaLabel, onBlur, onChange, value }: any) => (
      <textarea
        aria-label={ariaLabel}
        onBlur={onBlur}
        onChange={onChange}
        value={value}
      />
    ),
  },
  Select: ({
    'aria-label': ariaLabel,
    options = [],
    value,
    onChange,
    ...rest
  }: any) => (
    <select
      aria-label={ariaLabel}
      onChange={(event) => onChange?.(event.target.value)}
      value={value}
      {...rest}
    >
      {options.map((option: any) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
  Spin: ({ children }: any) => <div>{children}</div>,
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
    storyboards: any[];
    imageTasks: any[];
    videoTasks: any[];
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
    name:
      index === 0
        ? '停车场'
        : index === 1
          ? '小区广场'
          : `${['停车场', '后备箱内部', '小区广场', '新闻播音室'][index % 4]}${index + 1}`,
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
      storyboards: overrides?.storyboards ?? [
        {
          id: 101,
          shotNo: 1,
          episodeNo: 1,
          shotType: '远景',
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
          characters: '李慧',
          scene: '停车场',
          props: '灰色轿车后备箱',
          dialogue:
            '李慧VO: “停车场，灰色轿车的车主卸下货物，忘记关上后备箱。”',
          durationSeconds: 5,
          imagePrompt: '停车场首帧提示词',
          videoPrompt: '画风：写实都市。镜头1 1s 远景摇镜停车场内灰色轿车。',
          firstFrameUrl: 'https://example.com/shot-101-first.png',
          currentVideoUrl: 'https://example.com/shot-101.mp4',
        },
        {
          id: 102,
          shotNo: 2,
          episodeNo: 1,
          shotType: '中景',
          visualDescription: '斌斌在小区寻找可以躲藏的地方。',
          characters: '斌斌',
          scene: '小区广场',
          props: '',
          dialogue: '斌斌VO: “这时，我儿子在找捉迷藏可以躲藏的地方。”',
          durationSeconds: 3.5,
          imagePrompt: '小区首帧提示词',
          videoPrompt: '镜头2 3.5s 中景固定镜头斌斌跑过。',
          firstFrameUrl: 'https://example.com/shot-102-first.png',
          currentVideoUrl: null,
        },
        {
          id: 201,
          shotNo: 1,
          episodeNo: 2,
          shotType: '特写',
          visualDescription: '夜色中楼道灯忽明忽暗。',
          characters: '冯建业',
          scene: '楼道',
          props: '手机',
          dialogue: '冯建业: “别出声。”',
          durationSeconds: 4,
          imagePrompt: '楼道首帧提示词',
          videoPrompt: '镜头1 4s 夜色楼道压迫感。',
          firstFrameUrl: 'https://example.com/shot-201-first.png',
          currentVideoUrl: null,
        },
      ],
    },
  });
  mocks.queryAiImageTasks.mockResolvedValue({
    data: overrides?.imageTasks ?? [
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
        ],
      },
    ],
  });
  mocks.queryAiVideoTasks.mockResolvedValue({
    data: overrides?.videoTasks ?? [
      {
        id: 9001,
        projectId: 1,
        storyboardId: 101,
        serviceConfigId: 1,
        providerCode: 'doubao',
        model: 'Doubao-Seedance-2.5',
        prompt: '画风：写实都市。',
        firstFrameUrl: 'https://example.com/shot-101-first.png',
        durationSeconds: 5,
        aspectRatio: '9:16',
        resolution: '720p',
        status: 'SUCCEEDED',
        results: [
          {
            id: 9101,
            taskId: 9001,
            storyboardId: 101,
            videoUrl: 'https://example.com/shot-101.mp4',
            storagePath: 'shot-101.mp4',
            coverUrl: 'https://example.com/shot-101-cover.png',
            isSelected: true,
            status: 'SUCCEEDED',
          },
        ],
      },
    ],
  });
  mocks.createAiVideoTask.mockResolvedValue({
    data: { id: 9002, storyboardId: 101, executionId: 7002, results: [] },
  });
  mocks.createAiImageTask.mockResolvedValue({
    data: {
      id: 1200,
      projectId: 1,
      taskType: 'STORYBOARD_FIRST_FRAME',
      targetType: 'STORYBOARD',
      targetId: 101,
      prompt: '首帧提示词',
      aspectRatio: '9:16',
      imageCount: 1,
      status: 'PENDING',
      results: [],
    },
  });
  mocks.createStoryboard.mockImplementation((_projectId, values) =>
    Promise.resolve({
      data: {
        projectId: 1,
        script: null,
        versions: [],
        characters,
        scenes,
        props,
        storyboards: [
          ...((overrides?.storyboards ?? []) as any[]),
          {
            id: 999,
            shotNo: values.shotNo,
            episodeNo: values.episodeNo,
            shotType: values.shotType,
            visualDescription: values.visualDescription,
            characters: values.characters || '',
            scene: values.scene || '',
            props: values.props || '',
            dialogue: values.dialogue || '',
            durationSeconds: values.durationSeconds || 5,
            imagePrompt: values.imagePrompt || '',
            videoPrompt: values.videoPrompt || '',
            firstFrameUrl: null,
            currentVideoUrl: null,
          },
        ],
      },
    }),
  );
  mocks.updateStoryboard.mockImplementation(
    (_projectId, _storyboardId, values) =>
      Promise.resolve({
        data: {
          projectId: 1,
          script: null,
          versions: [],
          characters,
          scenes,
          props,
          storyboards: overrides?.storyboards ?? [],
          ...values,
        },
      }),
  );
  mocks.deleteStoryboard.mockResolvedValue({ data: {} });
};

describe('ProductionWorkbench script page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('currentTenantId', '1');
    mocks.pollExecution.mockImplementation(
      async (
        _tenantId: number,
        executionId: number,
        onUpdate?: (task: any) => void,
      ) => {
        const task = { id: executionId, status: 'SUCCEEDED', progress: 100 };
        onUpdate?.(task);
        return task;
      },
    );
    mocks.cancelAiVideoTask.mockResolvedValue({
      data: { id: 9003, status: 'CANCELED' },
    });
    mocks.regenerateAiVideoTask.mockResolvedValue({
      data: { id: 9004, executionId: 7004 },
    });
    setupWorkspaceResponse();
  });

  it('renders the storyboard section from the screenshot and switches episodes', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
      expect(mocks.queryAiImageTasks).toHaveBeenCalledWith(1, undefined);
      expect(mocks.queryAiVideoTasks).toHaveBeenCalledWith(1, undefined);
    });

    expect(screen.getByText('分镜表')).toBeInTheDocument();
    expect(screen.getByText('批量生成视频')).toBeInTheDocument();
    expect(screen.getByText('第1集 致命捉迷藏')).toBeInTheDocument();
    expect(screen.getByText(/斌斌独自下楼玩耍/)).toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
    expect(screen.getByText('分镜2')).toBeInTheDocument();
    expect(screen.getAllByText('全能参考生视频').length).toBeGreaterThan(0);
    expect(screen.getAllByText('首尾帧生视频').length).toBeGreaterThan(0);
    expect(
      screen.getAllByDisplayValue(/停车场，灰色轿车的车主卸下货物/).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText('李慧 - 李慧').length).toBeGreaterThan(0);
    expect(screen.getAllByText('停车场 - 停车场').length).toBeGreaterThan(0);
    expect(
      screen.getAllByText('灰色轿车后备箱 - 灰色轿车后备箱').length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText('3D导演台').length).toBeGreaterThan(0);
    expect(screen.getAllByDisplayValue(/素材引用/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('当前分镜').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(screen.getByText('第2集 夜色警报')).toBeInTheDocument();
    expect(screen.getByText('分镜1')).toBeInTheDocument();
    expect(screen.getAllByDisplayValue(/别出声/).length).toBeGreaterThan(0);
    expect(screen.queryByText('分镜2')).not.toBeInTheDocument();
  });

  it('creates an AI video task from the edited storyboard prompt', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1视频提示词')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('分镜1视频提示词'), {
      target: { value: '新的分镜视频提示词' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成分镜1视频' }));

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledWith(1, {
        storyboardId: 101,
        prompt: '新的分镜视频提示词',
        firstFrameUrl: 'https://example.com/shot-101-first.png',
        durationSeconds: 5,
        aspectRatio: '9:16',
        resolution: '720p',
      });
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7002,
        expect.any(Function),
      );
    });
  });

  it('recovers shared video execution polling after a page reload', async () => {
    setupWorkspaceResponse({
      videoTasks: [
        {
          id: 9003,
          projectId: 1,
          storyboardId: 101,
          executionId: 7003,
          status: 'GENERATING',
          results: [],
        },
      ],
    });

    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        1,
        7003,
        expect.any(Function),
      );
    });
  });

  it('uses the video domain control to cancel a shared execution', async () => {
    mocks.pollExecution.mockImplementation(
      async (
        _tenantId: number,
        executionId: number,
        onUpdate?: (task: any) => void,
      ) => {
        const task = { id: executionId, status: 'RUNNING', progress: 40 };
        onUpdate?.(task);
        return task;
      },
    );
    setupWorkspaceResponse({
      videoTasks: [
        {
          id: 9003,
          projectId: 1,
          storyboardId: 101,
          executionId: 7003,
          status: 'GENERATING',
          results: [],
        },
      ],
    });

    render(<ProductionWorkbench />);
    fireEvent.click(
      await screen.findByRole('button', { name: 'execution-cancel-7003' }),
    );

    await waitFor(() => {
      expect(mocks.cancelAiVideoTask).toHaveBeenCalledWith(1, 9003);
    });
  });

  it('creates a durable storyboard image task from the first-frame action', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: '生成首帧' }).length,
      ).toBeGreaterThan(0);
    });

    fireEvent.click(screen.getAllByRole('button', { name: '生成首帧' })[0]);

    await waitFor(() => {
      expect(mocks.createAiImageTask).toHaveBeenCalledWith(1, {
        taskType: 'STORYBOARD_FIRST_FRAME',
        targetType: 'STORYBOARD',
        targetId: 101,
        prompt: expect.any(String),
        aspectRatio: '9:16',
        imageCount: 1,
        quality: 'STANDARD',
      });
      expect(mocks.queryAiImageTasks).toHaveBeenCalledWith(1, undefined);
    });
  });

  it('maps short storyboard duration to a supported video generation duration', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜2视频提示词')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '生成分镜2视频' }));

    await waitFor(() => {
      expect(mocks.createAiVideoTask).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          storyboardId: 102,
          durationSeconds: 5,
        }),
      );
    });
  });

  it('saves edited storyboard script fields without persisting display scaffolding', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1剧本原文')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('分镜1剧本原文'), {
      target: {
        value: '▲停车场\n新的画面描述。\n李慧VO: “新的对白。”',
      },
    });
    fireEvent.blur(screen.getByLabelText('分镜1剧本原文'));

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(1, 101, {
        visualDescription: '新的画面描述。',
        scene: '停车场',
        dialogue: '李慧VO: “新的对白。”',
        characters: '李慧',
        props: '灰色轿车后备箱',
        durationSeconds: 5,
        imagePrompt: '停车场首帧提示词',
        videoPrompt: expect.stringContaining('素材引用'),
      });
    });
  });

  it('updates storyboard references through the left panel selectors', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1出镜角色')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('分镜1出镜角色'), {
      target: { value: '斌斌' },
    });

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({
          characters: '斌斌',
          scene: '停车场',
          props: '灰色轿车后备箱',
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
        }),
      );
    });

    fireEvent.change(screen.getByLabelText('分镜1场景'), {
      target: { value: '小区广场' },
    });
    fireEvent.change(screen.getByLabelText('分镜1场景道具'), {
      target: { value: '棒棒糖' },
    });

    await waitFor(() => {
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({ scene: '小区广场' }),
      );
      expect(mocks.updateStoryboard).toHaveBeenCalledWith(
        1,
        101,
        expect.objectContaining({ props: '棒棒糖' }),
      );
    });
  });

  it('adds and copies storyboards using the existing storyboard backend', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByText('分镜1')).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByRole('button', { name: '新增分镜' })[0]);

    await waitFor(() => {
      expect(mocks.createStoryboard).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          episodeNo: 1,
          shotNo: 3,
          visualDescription: '新增镜头画面描述',
          durationSeconds: 5,
        }),
      );
    });

    fireEvent.click(screen.getAllByRole('button', { name: '复制分镜' })[0]);

    await waitFor(() => {
      expect(mocks.createStoryboard).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          episodeNo: 1,
          shotNo: 4,
          visualDescription: '停车场内灰色轿车停在车位内，车主背影走远。',
          characters: '李慧',
          scene: '停车场',
          props: '灰色轿车后备箱',
        }),
      );
    });
  });

  it('renders selected generated storyboard video when available', async () => {
    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(screen.getByLabelText('分镜1成片预览')).toBeInTheDocument();
    });

    expect(screen.getByLabelText('分镜1成片预览')).toHaveAttribute(
      'src',
      'https://example.com/shot-101.mp4',
    );
  });

  it('shows an empty state instead of sample storyboards when backend returns no shots', async () => {
    setupWorkspaceResponse({
      characters: [],
      scenes: [],
      props: [],
      storyboards: [],
      imageTasks: [],
      videoTasks: [],
    });

    render(<ProductionWorkbench />);

    await waitFor(() => {
      expect(
        screen.getByText('暂无分镜，请先完成剧本分镜拆解'),
      ).toBeInTheDocument();
    });

    expect(screen.queryByText('斌斌')).not.toBeInTheDocument();
    expect(screen.queryByText('分镜1')).not.toBeInTheDocument();
  });
});
