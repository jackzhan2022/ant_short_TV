import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchScript from './script';

const mocks = vi.hoisted(() => ({
  queryScriptWorkspace: vi.fn(),
  queryProject: vi.fn(),
  retryScriptAnalysis: vi.fn(),
  reanalyzeScript: vi.fn(),
  regenerateEpisodeSplitting: vi.fn(),
  regenerateEpisodeSummary: vi.fn(),
  regenerateEpisodeAssets: vi.fn(),
  updateEpisodeSummary: vi.fn(),
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
  regenerateEpisodeSplitting: mocks.regenerateEpisodeSplitting,
  regenerateEpisodeSummary: mocks.regenerateEpisodeSummary,
  regenerateEpisodeAssets: mocks.regenerateEpisodeAssets,
  updateEpisodeSummary: mocks.updateEpisodeSummary,
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
              splitProgress: {
                mode: 'CHUNK_FALLBACK',
                fallbackReason: 'OUTPUT_TRUNCATED',
                totalChunks: 12,
                completedChunks: 7,
                failedChunks: 1,
                stale: false,
              },
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
              fanout: {
                snapshotId: 88,
                status: 'RUNNING',
                total: 3,
                completed: 1,
                failed: 1,
                currentEpisodeId: 102,
                currentEpisodeKey: 'episode-2',
                retryable: true,
                stale: false,
                units: [
                  { episodeId: 101, episodeKey: 'episode-1', status: 'SUCCEEDED', childRunId: 701 },
                  { episodeId: 102, episodeKey: 'episode-2', status: 'RUNNING', childRunId: 702 },
                  { episodeId: 103, episodeKey: 'episode-3', status: 'FAILED', errorCode: 'AI_PROVIDER_TIMEOUT' },
                ],
              },
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

    expect(await screen.findByText('当前剧集解析中')).toBeInTheDocument();
    expect(screen.getByText('剧情全局理解')).toBeInTheDocument();
    expect(screen.getByText('剧集智能拆分')).toBeInTheDocument();
    expect(screen.getByText('剧集概要提炼')).toBeInTheDocument();
    expect(screen.getByText('角色场景识别')).toBeInTheDocument();
    expect(screen.getByText('正在提炼每集概要')).toBeInTheDocument();
    expect(screen.getByText('45%')).toBeInTheDocument();
    expect(screen.getByText(/1\/3 集/)).toBeInTheDocument();
    expect(screen.getByText('episode-3失败')).toBeInTheDocument();
    expect(screen.getByText('分块分析 7/12')).toBeInTheDocument();
    expect(screen.getByText('全文输出达到上限，已自动切换')).toBeInTheDocument();
    expect(screen.queryByText('线上剧本正文')).not.toBeInTheDocument();
    expect(screen.queryByText('分集剧情')).not.toBeInTheDocument();
  });

  it('renders an actionable retry state when analysis fails', async () => {
    mocks.queryScriptWorkspace.mockResolvedValue({
      data: {
        projectId: 1,
        script: { id: 11, projectId: 1, title: '失败剧本', sourceType: 'MANUAL_EDIT', content: '正文', status: 'DRAFT', currentVersionId: 7 },
        versions: [], characters: [], scenes: [], props: [], storyboards: [], episodes: [],
        analysis: {
          id: 99, scriptVersionId: 7, status: 'FAILED', currentStage: 'EPISODE_SUMMARY', overallProgress: 45,
          currentAction: '分析失败', errorMessage: '模型服务暂时不可用',
          stages: [{ id: 3, stageCode: 'EPISODE_SUMMARY', stageOrder: 3, status: 'FAILED', progressPercent: 45, completedUnits: 0, totalUnits: 1, errorMessage: '模型服务暂时不可用', retryable: true }],
        },
      },
    });
    mocks.regenerateEpisodeSplitting.mockResolvedValue({ data: {} });
    mocks.regenerateEpisodeSummary.mockResolvedValue({ data: {} });
    mocks.regenerateEpisodeAssets.mockResolvedValue({ data: {} });
    mocks.updateEpisodeSummary.mockResolvedValue({ data: {} });
    render(<ProductionWorkbenchScript />);
    expect((await screen.findAllByText('模型服务暂时不可用')).length).toBeGreaterThan(0);
    expect(screen.queryByText('线上剧本正文')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试此步骤' }));
    await waitFor(() => expect(mocks.retryScriptAnalysis).toHaveBeenCalledWith(1, 'EPISODE_SUMMARY'));
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
      characters: [{ id: 1, name: '林晚', visual: { variantCount: 2 } }],
      scenes: [],
      props: [],
      storyboards: [],
      episodes: [{ episodeId: 21, episodeNo: 1, title: '第1集', content: '一段剧本。', formalSummary: { id: 9, schemaVersion: 1, source: 'AI', content: { summary: '林晚回家', highlights: ['雨夜', '归来'], endingHook: '门后有人' } } }],
      globalUnderstanding: { id: 5, schemaVersion: 1, content: { logline: '林晚雨夜归家' }, analyzedContentHash: 'hash', updatedAt: '' },
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
    expect(screen.getByText('林晚雨夜归家')).toBeInTheDocument();
    expect(screen.getByText(/角色 1 · 场景 0 · 道具 0/)).toBeInTheDocument();
    expect(screen.getByText('林晚回家')).toBeInTheDocument();
  });

  it('edits formal summaries and runs episode-scoped agents with overwrite warning', async () => {
    const completedWorkspace = {
      projectId: 1,
      script: { id: 11, projectId: 1, title: '分析剧本', sourceType: 'MANUAL_EDIT', content: '一段剧本。', status: 'DRAFT', currentVersionId: 7 },
      versions: [], characters: [], scenes: [], props: [], storyboards: [],
      episodes: [{ episodeId: 21, episodeNo: 1, title: '第1集', content: '一段剧本。', formalSummary: { id: 9, schemaVersion: 1, source: 'AI', content: { summary: '旧概要', highlights: ['雨夜', '归来'], endingHook: '旧钩子' } } }],
      analysis: { id: 99, scriptVersionId: 7, status: 'COMPLETED', currentStage: null, overallProgress: 100, currentAction: '分析已完成', stages: [] },
    };
    mocks.queryScriptWorkspace.mockResolvedValue({ data: completedWorkspace });
    render(<ProductionWorkbenchScript />);

    expect(await screen.findByText(/重跑 Agent 会覆盖对应正式数据/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '编辑概要' }));
    fireEvent.change(screen.getByLabelText('概要'), { target: { value: '新概要' } });
    fireEvent.change(screen.getByLabelText('亮点'), { target: { value: '亮点一\n亮点二' } });
    fireEvent.change(screen.getByLabelText('结尾钩子'), { target: { value: '新钩子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存概要' }));
    await waitFor(() => expect(mocks.updateEpisodeSummary).toHaveBeenCalledWith(1, 21, {
      summary: '新概要', highlights: ['亮点一', '亮点二'], endingHook: '新钩子', overwrite: true,
    }));

    fireEvent.click(screen.getByRole('button', { name: 'AI 重生成本集概要' }));
    await waitFor(() => expect(mocks.regenerateEpisodeSummary).toHaveBeenCalledWith(1, 21));
    fireEvent.click(screen.getByRole('button', { name: 'AI 重识别本集资产' }));
    await waitFor(() => expect(mocks.regenerateEpisodeAssets).toHaveBeenCalledWith(1, 21));
  });
});
