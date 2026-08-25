import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchScript from './script';

const mocks = vi.hoisted(() => ({
  queryScriptWorkspace: vi.fn(),
  queryProject: vi.fn(),
  retryScriptAnalysis: vi.fn(),
  reanalyzeScript: vi.fn(),
  pollExecution: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ id: '1' }),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Button: ({ children, ...props }: any) => (
    <button {...props}>{children}</button>
  ),
  Collapse: ({ items }: any) => (
    <div>
      {items?.map((item: any) => (
        <section key={item.key}>
          <span>{item.label}</span>
          {item.children}
        </section>
      ))}
    </div>
  ),
  Flex: ({ children }: any) => <div>{children}</div>,
  Descriptions: ({ items }: any) => (
    <dl>
      {items?.map((item: any) => (
        <div key={item.key}>
          <dt>{item.label}</dt>
          <dd>{item.children}</dd>
        </div>
      ))}
    </dl>
  ),
  Input: Object.assign(
    ({ value, ...props }: any) => <textarea value={value} {...props} />,
    {
      TextArea: ({ value, ...props }: any) => (
        <textarea value={value} {...props} />
      ),
    },
  ),
  Progress: ({ percent }: any) => <div>{percent}%</div>,
  Skeleton: () => <div>loading skeleton</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h3>{children}</h3>,
  },
}));

vi.mock('./service', () => ({
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  retryScriptAnalysis: mocks.retryScriptAnalysis,
  reanalyzeScript: mocks.reanalyzeScript,
}));

vi.mock('@/services/ai-execution/task', () => ({
  aiExecutionTaskService: { poll: mocks.pollExecution },
}));

vi.mock('@/components/AiExecutionStatus', () => ({
  default: ({ task }: any) => (
    <div>
      execution-{task.id}-{task.status}
    </div>
  ),
}));

vi.mock('@/services/account-team/project', () => ({
  queryProject: mocks.queryProject,
}));

describe('ProductionWorkbenchScript', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryProject.mockResolvedValue({
      data: {
        id: 1,
        tenantId: 10,
        name: '最危险的捉迷藏',
        code: 'SCRIPT_TEST',
        ownerId: 1,
        status: 'NOT_STARTED',
        aspectRatio: '9:16',
        fileFormat: 'SCRIPT',
        scriptType: 'PREMIUM_DRAMA',
        visualStyle: '写实都市',
        memberCount: 1,
        createdAt: '',
        updatedAt: '',
      },
    });
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: {
          id: 11,
          projectId: 1,
          title: '最危险的捉迷藏',
          sourceType: 'AI_GENERATE',
          content:
            '第1集 致命捉迷藏\n斌斌独自下楼玩耍。\n第2集 夜色警报\n家人开始寻找失踪的孩子。',
          status: 'DRAFT',
          currentVersionId: 3,
          updatedAt: '2026-08-19 18:21:00',
        },
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [
          {
            id: 101,
            shotNo: 1,
            episodeNo: 1,
            shotType: '远景',
            visualDescription: '斌斌独自下楼玩耍',
            characters: '斌斌',
            scene: '小区楼下',
            dialogue: '今天我一定要赢。',
            durationSeconds: 5,
            imagePrompt: '',
            videoPrompt: '',
          },
          {
            id: 201,
            shotNo: 1,
            episodeNo: 2,
            shotType: '中景',
            visualDescription: '家人开始在楼道里寻找',
            characters: '刘凤英',
            scene: '楼道',
            dialogue: '斌斌你在哪儿？',
            durationSeconds: 5,
            imagePrompt: '',
            videoPrompt: '',
          },
        ],
        episodes: [
          {
            episodeNo: 1,
            title: '第1集 致命捉迷藏',
            content: '斌斌独自下楼玩耍。',
          },
          {
            episodeNo: 2,
            title: '第2集 夜色警报',
            content: '家人开始寻找失踪的孩子。',
          },
        ],
        analysis: null,
      },
    });
    mocks.reanalyzeScript.mockResolvedValue({
      data: { id: 501, businessId: 99, status: 'PENDING', progress: 0 },
    });
    mocks.pollExecution.mockResolvedValue({
      id: 501,
      businessId: 99,
      status: 'SUCCEEDED',
      progress: 100,
    });
  });

  it('renders the restored script page without a character list', async () => {
    render(<ProductionWorkbenchScript />);

    expect(await screen.findByText('线上剧本内容')).toBeInTheDocument();
    expect(screen.getByText('剧本类型')).toBeInTheDocument();
    expect(screen.getByText('大纲')).toBeInTheDocument();
    expect(screen.getByText('分集剧情')).toBeInTheDocument();
    expect(screen.getByText('当前集剧情正文')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '第1集 致命捉迷藏' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '第2集 夜色警报' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('人物列表')).not.toBeInTheDocument();
    expect(screen.queryByText('人物小传')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
  });

  it('falls back to one episode when an older workspace omits episodes', async () => {
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: {
          id: 11,
          projectId: 1,
          title: '旧响应剧本',
          sourceType: 'MANUAL_EDIT',
          content: '一段没有集标题的剧本。',
          status: 'DRAFT',
          currentVersionId: 1,
        },
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
        analysis: null,
      },
    });

    render(<ProductionWorkbenchScript />);

    expect(
      await screen.findByRole('button', { name: '第1集' }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByDisplayValue('一段没有集标题的剧本。').length,
    ).toBeGreaterThan(0);
    expect(
      screen.queryByRole('button', { name: '第2集' }),
    ).not.toBeInTheDocument();
  });

  it('renders four analysis stages with percentages and intermediate result access', async () => {
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: {
          id: 11,
          projectId: 1,
          title: '分析剧本',
          sourceType: 'MANUAL_EDIT',
          content: '一段剧本。',
          status: 'DRAFT',
          currentVersionId: 7,
        },
        versions: [],
        characters: [],
        scenes: [],
        props: [],
        storyboards: [],
        episodes: [{ episodeNo: 1, title: '第1集', content: '一段剧本。' }],
        analysis: {
          id: 99,
          scriptVersionId: 7,
          status: 'RUNNING',
          currentStage: 'EPISODE_SUMMARY',
          overallProgress: 56,
          currentAction: '正在提炼每集概要',
          stages: [
            {
              id: 1,
              stageCode: 'GLOBAL_UNDERSTANDING',
              stageOrder: 1,
              status: 'SUCCEEDED',
              progressPercent: 100,
              completedUnits: 1,
              totalUnits: 1,
              currentAction: '已完成',
              resultJson:
                '{"logline":"主角回归","themes":["回家"],"characters":["林晚"],"relationships":["母女"],"coreConflict":"归来与阻拦","turningPoints":["雨夜回归"],"endingHook":"她推开了门"}',
              providerRequestId: 'req-1',
              aiCallLogId: 7001,
              durationMs: 2130,
            },
            {
              id: 2,
              stageCode: 'EPISODE_SPLITTING',
              stageOrder: 2,
              status: 'SUCCEEDED',
              progressPercent: 100,
              completedUnits: 1,
              totalUnits: 1,
              currentAction: '已完成',
              resultJson:
                '{"episodes":[{"episodeNo":1,"title":"第一集","content":"开端","summary":"主角回家","endingHook":"门后有人"},{"episodeNo":2,"title":"第二集","content":"冲突","summary":"家人阻拦","endingHook":"协议出现"}]}',
            },
            {
              id: 3,
              stageCode: 'EPISODE_SUMMARY',
              stageOrder: 3,
              status: 'RUNNING',
              progressPercent: 45,
              completedUnits: 0,
              totalUnits: 1,
              currentAction: '正在提炼每集概要',
            },
            {
              id: 4,
              stageCode: 'CHARACTER_SCENE_RECOGNITION',
              stageOrder: 4,
              status: 'PENDING',
              progressPercent: 0,
              completedUnits: 0,
              totalUnits: 1,
              currentAction: '等待上一阶段完成',
            },
          ],
        },
      },
    });

    render(<ProductionWorkbenchScript />);

    expect(await screen.findByText('剧本智能分析')).toBeInTheDocument();
    expect(screen.getByText('剧情全局理解')).toBeInTheDocument();
    expect(screen.getByText('剧集智能拆分')).toBeInTheDocument();
    expect(screen.getByText('剧集概要提炼')).toBeInTheDocument();
    expect(screen.getByText('角色场景识别')).toBeInTheDocument();
    expect(screen.getByText('一句话：主角回归')).toBeInTheDocument();
    expect(screen.getByText('主题：回家')).toBeInTheDocument();
    expect(screen.getByText('第1集 · 第一集')).toBeInTheDocument();
    expect(screen.getAllByText('100%').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('45%').length).toBeGreaterThanOrEqual(2);
  });

  it('follows shared execution and refreshes the analysis workspace after reanalysis', async () => {
    const completedWorkspace = {
      projectId: 1,
      script: {
        id: 11,
        projectId: 1,
        title: '分析剧本',
        sourceType: 'MANUAL_EDIT',
        content: '一段剧本。',
        status: 'DRAFT',
        currentVersionId: 7,
      },
      versions: [],
      characters: [],
      scenes: [],
      props: [],
      storyboards: [],
      episodes: [{ episodeNo: 1, title: '第1集', content: '一段剧本。' }],
      analysis: {
        id: 99,
        scriptVersionId: 7,
        status: 'COMPLETED',
        currentStage: null,
        overallProgress: 100,
        currentAction: '分析已完成',
        stages: [],
      },
    };
    mocks.queryScriptWorkspace.mockResolvedValue({ data: completedWorkspace });

    render(<ProductionWorkbenchScript />);

    fireEvent.click(
      await screen.findByRole('button', { name: '重新分析当前版本' }),
    );

    await waitFor(() => {
      expect(mocks.pollExecution).toHaveBeenCalledWith(
        10,
        501,
        expect.any(Function),
      );
      expect(
        mocks.queryScriptWorkspace.mock.calls.length,
      ).toBeGreaterThanOrEqual(2);
    });
    expect(screen.getByText('execution-501-SUCCEEDED')).toBeInTheDocument();
  });
});
