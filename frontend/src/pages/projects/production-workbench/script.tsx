import { CheckOutlined, LeftOutlined, ReloadOutlined, RightOutlined, UserOutlined } from '@ant-design/icons';
import { useParams } from '@umijs/max';
import {
  App,
  Button,
  Flex,
  Input,
  Modal,
  Skeleton,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { queryProject } from '@/services/account-team/project';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import {
  queryScriptWorkspace,
  reanalyzeScript,
  regenerateEpisodeAssets,
  regenerateEpisodeSplitting,
  regenerateEpisodeSummary,
  retryScriptAnalysis,
  updateEpisodeSummary,
  type ScriptAnalysisStage,
  type ScriptWorkspace,
} from './service';

type EpisodeBlock = {
  episodeNo: number;
  title: string;
  copy: string;
};

type ProjectLite = {
  tenantId?: number;
  coverUrl?: string | null;
  aspectRatio?: string | null;
  fileFormat?: string | null;
  scriptType?: string | null;
  breakdownStrength?: string | null;
  visualStyle?: string | null;
};

const fileFormatText: Record<string, string> = {
  SCRIPT: '剧本格式',
  NOVEL: '小说格式',
};

const scriptTypeText: Record<string, string> = {
  PREMIUM_DRAMA: '精品剧',
  COMMENTARY_COMIC: '解说漫',
};

const breakdownStrengthText: Record<string, string> = {
  LOW: '低强度',
  MEDIUM: '中强度',
  HIGH: '高强度',
};

const analysisStageLabels: Record<string, string> = {
  GLOBAL_UNDERSTANDING: '剧情全局理解',
  EPISODE_SPLITTING: '剧集智能拆分',
  EPISODE_SUMMARY: '剧集概要提炼',
  CHARACTER_SCENE_RECOGNITION: '角色场景识别',
};

const analysisStageDescriptions: Record<string, string> = {
  GLOBAL_UNDERSTANDING: '先把主线、人物和冲突看清楚。',
  EPISODE_SPLITTING: '按规则或 AI 把正文切成可追踪的分集。',
  EPISODE_SUMMARY: '把每集的概要和钩子提炼出来。',
  CHARACTER_SCENE_RECOGNITION: '识别角色、场景和关键道具。',
};

const safeJsonParse = (value?: string | null) => {
  if (!value) {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};

const listText = (value: unknown) => {
  if (!Array.isArray(value) || !value.length) {
    return '-';
  }
  return value
    .map((item) => (typeof item === 'string' ? item : ''))
    .filter(Boolean)
    .join(' / ');
};

const renderResultSummary = (stageCode: string, resultJson?: string | null) => {
  const parsed = safeJsonParse(resultJson);
  if (!parsed) {
    return (
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        暂无可解析结果
      </Typography.Text>
    );
  }

  if (stageCode === 'GLOBAL_UNDERSTANDING') {
    return (
      <div style={{ display: 'grid', gap: 6, fontSize: 12, color: 'var(--app-color-text)' }}>
        <div>一句话：{parsed.logline || '-'}</div>
        <div>主题：{listText(parsed.themes)}</div>
        <div>人物：{listText(parsed.characters)}</div>
        <div>关系：{listText(parsed.relationships)}</div>
        <div>核心冲突：{parsed.coreConflict || '-'}</div>
        <div>转折：{listText(parsed.turningPoints)}</div>
        <div>悬念：{parsed.endingHook || '-'}</div>
      </div>
    );
  }

  if (stageCode === 'EPISODE_SPLITTING' || stageCode === 'EPISODE_SUMMARY') {
    const episodes = Array.isArray(parsed.episodes) ? parsed.episodes : [];
    return (
      <div style={{ display: 'grid', gap: 8 }}>
        {episodes.slice(0, 3).map((episode: any) => (
          <div
            key={episode.episodeNo}
            style={{
              padding: '8px 10px',
              borderRadius: 6,
              background: 'var(--app-color-bg-layout)',
              border: '1px solid var(--app-color-border)',
              fontSize: 12,
              lineHeight: '18px',
            }}
          >
            <div style={{ fontWeight: 700, color: 'var(--app-color-text)' }}>
              第{episode.episodeNo || '-'}集{' '}
              {episode.title ? `· ${episode.title}` : ''}
            </div>
            {stageCode === 'EPISODE_SPLITTING' ? (
              <>
                <div>概要：{episode.summary || '-'}</div>
                <div>收尾：{episode.endingHook || '-'}</div>
                <div style={{ color: '#6b7280' }}>
                  正文：{episode.content || '-'}
                </div>
              </>
            ) : (
              <>
                <div>概要：{episode.summary || '-'}</div>
                <div>亮点：{listText(episode.highlights)}</div>
                <div>收尾：{episode.endingHook || '-'}</div>
              </>
            )}
          </div>
        ))}
      </div>
    );
  }

  if (stageCode === 'CHARACTER_SCENE_RECOGNITION') {
    const characters = Array.isArray(parsed.characters)
      ? parsed.characters
      : [];
    const scenes = Array.isArray(parsed.scenes) ? parsed.scenes : [];
    const props = Array.isArray(parsed.props) ? parsed.props : [];
    return (
      <div style={{ display: 'grid', gap: 8, fontSize: 12 }}>
        <div>
          角色：
          {characters
            .map((item: any) => item.name)
            .filter(Boolean)
            .join(' / ') || '-'}
        </div>
        <div>
          场景：
          {scenes
            .map((item: any) => item.name)
            .filter(Boolean)
            .join(' / ') || '-'}
        </div>
        <div>
          道具：
          {props
            .map((item: any) => item.name)
            .filter(Boolean)
            .join(' / ') || '-'}
        </div>
      </div>
    );
  }

  return (
    <pre
      style={{
        margin: 0,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        fontSize: 12,
        lineHeight: '18px',
        color: 'var(--app-color-text)',
      }}
    >
      {resultJson}
    </pre>
  );
};

const getEpisodeBlocks = (workspace: ScriptWorkspace): EpisodeBlock[] => {
  if (workspace.episodes?.length) {
    return workspace.episodes.map((episode) => ({
      episodeNo: episode.episodeNo,
      title: episode.title || `第${episode.episodeNo}集`,
      copy: episode.content,
    }));
  }
  if (workspace.script?.content?.trim()) {
    return [{ episodeNo: 1, title: '第1集', copy: workspace.script.content }];
  }
  return [];
};

const metricStyle = {
  minHeight: 68,
  borderRadius: 8,
  border: '1px solid var(--app-color-border-secondary)',
  background: 'var(--app-color-bg-container)',
  padding: '12px 14px',
} as const;

const labelStyle = {
  display: 'block',
  color: 'var(--app-color-text-tertiary)',
  fontSize: 12,
  lineHeight: '18px',
} as const;

const valueStyle = {
  marginTop: 6,
  color: 'var(--app-color-text)',
  fontSize: 15,
  fontWeight: 700,
  lineHeight: '22px',
} as const;

type ScriptAnalysisStateContainerProps = {
  analysis: NonNullable<ScriptWorkspace['analysis']>;
  onRetryStage: (stageCode: string) => void;
};

export const ScriptAnalysisStateContainer = ({
  analysis,
  onRetryStage,
}: ScriptAnalysisStateContainerProps) => {
  const isFailed = analysis.status === 'FAILED';
  const title = isFailed ? '剧本解析失败' : '当前剧集解析中';
  const guidance = isFailed
    ? analysis.errorMessage || '解析过程中遇到问题，请重试。'
    : analysis.currentAction || '当前剧情正在解析中，请耐心等待...';
  const stages: ScriptAnalysisStage[] = analysis.stages.length
    ? analysis.stages
    : Object.keys(analysisStageLabels).map((stageCode, index) => ({
        id: index,
        stageCode,
        stageOrder: index + 1,
        status: 'PENDING',
        progressPercent: 0,
        completedUnits: 0,
        totalUnits: 1,
      }));

  return (
    <section
      aria-label="剧本分析进度"
      style={{
        minHeight: 620,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '36px 18px 72px',
      }}
    >
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 56, lineHeight: 1, color: '#b8c4e8', marginBottom: 18 }}>▧</div>
        <Typography.Title level={4} style={{ margin: 0, fontSize: 20 }}>
          {title}
        </Typography.Title>
        <Typography.Text type={isFailed ? 'danger' : 'secondary'}>
          {guidance}
        </Typography.Text>
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'center',
          marginTop: 56,
          maxWidth: 560,
          width: '100%',
        }}
      >
        {stages.map((stage, index) => {
          const completed = stage.status === 'SUCCEEDED';
          return (
            <div key={stage.stageCode} style={{ display: 'flex', alignItems: 'flex-start', flex: 1 }}>
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ width: 58, height: 58, margin: '0 auto 16px', borderRadius: '50%', display: 'grid', placeItems: 'center', border: `5px solid ${completed ? '#16b979' : '#e1e5f1'}`, color: completed ? '#16b979' : '#24324a', background: '#fff', fontWeight: 700, fontSize: 15 }}>
                  {completed ? <CheckOutlined /> : `${stage.progressPercent}%`}
                </div>
                <Typography.Text type="secondary" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
                  {analysisStageLabels[stage.stageCode] || stage.stageCode}
                </Typography.Text>
                {stage.fanout ? (
                  <div style={{ marginTop: 6, fontSize: 11, color: 'var(--app-color-text-secondary)' }}>
                    {stage.fanout.completed}/{stage.fanout.total} 集
                    {stage.fanout.failed ? ` · ${stage.fanout.failed} 集失败` : ''}
                    {stage.fanout.currentEpisodeKey ? ` · 当前 ${stage.fanout.currentEpisodeKey}` : ''}
                  </div>
                ) : null}
                {stage.splitProgress?.mode === 'CHUNK_FALLBACK' ? (
                  <div style={{ marginTop: 6, fontSize: 11, color: 'var(--app-color-text-secondary)' }}>
                    <div>
                      <span>
                        分块分析 {stage.splitProgress.completedChunks}/{stage.splitProgress.totalChunks}
                      </span>
                      {stage.splitProgress.failedChunks ? (
                        <span> · {stage.splitProgress.failedChunks} 块失败</span>
                      ) : null}
                    </div>
                    <div>
                      {stage.splitProgress.fallbackReason === 'OUTPUT_TRUNCATED'
                        ? '全文输出达到上限，已自动切换'
                        : stage.splitProgress.fallbackReason === 'CONTEXT_PREFLIGHT'
                          ? '全文超过安全上下文，已自动切换'
                          : stage.splitProgress.fallbackReason === 'CONTEXT_ERROR'
                            ? '全文上下文调用失败，已自动切换'
                            : '已自动切换到分块分析'}
                    </div>
                  </div>
                ) : null}
                {stage.fanout?.units?.some((unit) => unit.status === 'FAILED') ? (
                  <div style={{ marginTop: 5 }}>
                    {stage.fanout.units
                      .filter((unit) => unit.status === 'FAILED')
                      .slice(0, 3)
                      .map((unit) => (
                        <Tag key={unit.episodeId} color="error" style={{ margin: '2px' }}>
                          {unit.episodeKey}失败
                        </Tag>
                      ))}
                  </div>
                ) : null}
                {isFailed && stage.status === 'FAILED' && stage.retryable ? (
                  <div>
                    <Button type="link" size="small" icon={<ReloadOutlined />} onClick={() => onRetryStage(stage.stageCode)} style={{ padding: 0, marginTop: 8 }}>
                      重试此步骤
                    </Button>
                  </div>
                ) : null}
              </div>
              {index < stages.length - 1 ? <div style={{ flex: '0 0 26px', height: 3, marginTop: 28, background: completed ? '#c5eede' : '#e5e8f2' }} /> : null}
            </div>
          );
        })}
      </div>
      {isFailed ? <Typography.Text type="danger" style={{ marginTop: 24 }}>{analysis.errorMessage || '解析失败，请重试。'}</Typography.Text> : null}
    </section>
  );
};

const ProductionWorkbenchScript = () => {
  const params = useParams<{ id: string }>();
  const { message } = App.useApp();
  const projectId = Number(params.id);
  const [workspace, setWorkspace] = useState<ScriptWorkspace | null>(null);
  const [project, setProject] = useState<ProjectLite>();
  const [loading, setLoading] = useState(false);
  const [currentEpisodeNo, setCurrentEpisodeNo] = useState(1);
  const [showAllCharacters, setShowAllCharacters] = useState(false);
  const episodeSelectorRef = useRef<HTMLDivElement>(null);
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();
  const [executionBusy, setExecutionBusy] = useState(false);
  const [summaryEditing, setSummaryEditing] = useState(false);
  const [summarySaving, setSummarySaving] = useState(false);
  const [summaryDraft, setSummaryDraft] = useState('');
  const [highlightsDraft, setHighlightsDraft] = useState('');
  const [endingHookDraft, setEndingHookDraft] = useState('');

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    const loadWorkspace = async (showLoading: boolean) => {
      if (showLoading) {
        setLoading(true);
      }
      try {
        const [workspaceResponse, projectResponse] = await Promise.all([
          queryScriptWorkspace(projectId),
          queryProject(projectId),
        ]);
        if (active) {
          setWorkspace(workspaceResponse.data);
          setProject(projectResponse.data);
        }
      } catch {
        if (active) {
          message.error('剧本页加载失败');
        }
      } finally {
        if (active && showLoading) {
          setLoading(false);
        }
      }
    };
    void loadWorkspace(true);
    return () => {
      active = false;
    };
  }, [message, projectId]);

  useEffect(() => {
    const stages = workspace?.analysis?.stages || [];
    const activeAnalysis = stages.some((stage) =>
      ['PENDING', 'RUNNING', 'RETRYING'].includes(stage.status),
    );
    if (!activeAnalysis) {
      return undefined;
    }
    const timer = window.setInterval(async () => {
      const response = await queryScriptWorkspace(projectId);
      setWorkspace(response.data);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [projectId, workspace?.analysis]);

  /*
   * The page keeps its shell visible while the initial workspace request is
   * pending, then polls only when the server reports unfinished analysis.
   */
  useEffect(() => {
    if (workspace?.analysis?.status !== 'FAILED') {
      return;
    }
    if (workspace.analysis.errorMessage) {
      message.error(workspace.analysis.errorMessage);
    }
  }, [message, workspace?.analysis?.errorMessage, workspace?.analysis?.status]);

  const episodeBlocks = useMemo(
    () =>
      getEpisodeBlocks(
        workspace || {
          projectId,
          script: null,
          versions: [],
          characters: [],
          scenes: [],
          props: [],
          storyboards: [],
        },
      ),
    [projectId, workspace],
  );
  const activeEpisode =
    episodeBlocks.find((item) => item.episodeNo === currentEpisodeNo) ||
    episodeBlocks[0];
  const script = workspace?.script;
  const analysis = workspace?.analysis;
  const tenantId =
    project?.tenantId ?? Number(localStorage.getItem('currentTenantId'));
  const refreshWorkspace = async () => {
    const response = await queryScriptWorkspace(projectId);
    setWorkspace(response.data);
  };
  const followExecution = async (task?: API.AiExecutionResponse) => {
    if (!task?.id || !tenantId) {
      throw new Error('AI execution identity is missing');
    }
    setActiveExecution(task);
    const terminal = await aiExecutionTaskService.poll(
      tenantId,
      task.id,
      setActiveExecution,
    );
    setActiveExecution(terminal);
    await refreshWorkspace();
    return terminal;
  };
  const retryAnalysis = async (stageCode: string) => {
    try {
      const response = await retryScriptAnalysis(projectId, stageCode);
      await followExecution(response.data);
      message.success('已重新加入分析队列');
    } catch {
      message.error('分析重试失败');
    }
  };
  const reanalyzeCurrent = async () => {
    try {
      const response = await reanalyzeScript(projectId);
      await followExecution(response.data);
      message.success('已重新发起分析');
    } catch {
      message.error('重新分析失败');
    }
  };

  const showReanalyzeConfirm = () => {
    Modal.confirm({
      title: '重新AI分析',
      content: '重新分析会覆盖对应正式数据；用户后续仍可继续编辑。',
      okText: '确认重分析',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: reanalyzeCurrent,
    });
  };
  const currentEpisode = workspace?.episodes?.find(
    (item) => item.episodeNo === currentEpisodeNo,
  );
  const beginSummaryEdit = () => {
    const content = currentEpisode?.formalSummary?.content;
    setSummaryDraft(content?.summary || '');
    setHighlightsDraft((content?.highlights || []).join('\n'));
    setEndingHookDraft(content?.endingHook || '');
    setSummaryEditing(true);
  };
  const saveSummary = async () => {
    if (!currentEpisode?.episodeId) return;
    const highlights = highlightsDraft
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean);
    if (!summaryDraft.trim() || highlights.length < 2 || highlights.length > 5) {
      message.warning('概要不能为空，亮点需按行填写 2–5 条');
      return;
    }
    setSummarySaving(true);
    try {
      await updateEpisodeSummary(projectId, currentEpisode.episodeId, {
        summary: summaryDraft.trim(),
        highlights,
        endingHook: endingHookDraft.trim() || null,
        overwrite: true,
      });
      await refreshWorkspace();
      setSummaryEditing(false);
      message.success('本集概要已保存为正式数据');
    } catch {
      message.error('本集概要保存失败');
    } finally {
      setSummarySaving(false);
    }
  };
  const runDirectAgent = async (
    action: () => Promise<unknown>,
    successMessage: string,
  ) => {
    setExecutionBusy(true);
    try {
      await action();
      await refreshWorkspace();
      message.success(successMessage);
    } catch {
      message.error('Agent 运行失败');
    } finally {
      setExecutionBusy(false);
    }
  };

  if (!projectId) {
    return null;
  }

  return (
    <div
      style={{
        minHeight: 'calc(100vh - 100px)',
        padding: '48px 0 88px',
        boxSizing: 'border-box',
        background: 'var(--app-color-bg-layout)',
      }}
    >
      <div style={{ width: 1000, margin: '0 auto' }}>
        <Flex
          justify="space-between"
          align="center"
          style={{ marginBottom: 12 }}
        >
          <div>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 18 }}>
              线上剧本内容
            </Typography.Title>
            <Typography.Text type="secondary">
              {loading
                ? '正在加载剧本内容...'
                : '剧本信息、正文、大纲与分集剧情'}
            </Typography.Text>
          </div>
          {analysis?.status === 'COMPLETED' ? (
            <Button onClick={showReanalyzeConfirm}>重新AI分析</Button>
          ) : null}
        </Flex>

        {loading && !workspace ? (
          <section
            style={{
              background: '#fff',
              border: '1px solid var(--app-color-border)',
              borderRadius: 8,
              padding: 18,
              marginBottom: 14,
            }}
          >
            <Skeleton active paragraph={{ rows: 4 }} />
          </section>
        ) : null}

        {analysis && analysis.status !== 'COMPLETED' ? (
          <ScriptAnalysisStateContainer
            analysis={analysis}
            onRetryStage={(stageCode) => void retryAnalysis(stageCode)}
          />
        ) : null}

        {activeExecution ? (
          <section
            aria-label="AI执行状态"
            style={{
              background: '#fff',
              border: '1px solid var(--app-color-border)',
              borderRadius: 8,
              padding: 18,
              marginBottom: 14,
            }}
          >
            <AiExecutionStatus
              task={activeExecution}
              busy={executionBusy}
              onCancel={() => {
                if (!activeExecution.id || !tenantId) return;
                setExecutionBusy(true);
                aiExecutionTaskService
                  .cancel(tenantId, activeExecution.id)
                  .then(setActiveExecution)
                  .then(refreshWorkspace)
                  .finally(() => setExecutionBusy(false));
              }}
              onRetry={() => {
                if (!activeExecution.id || !tenantId) return;
                setExecutionBusy(true);
                aiExecutionTaskService
                  .retry(tenantId, activeExecution.id)
                  .then(followExecution)
                  .finally(() => setExecutionBusy(false));
              }}
            />
          </section>
        ) : null}

        {analysis && analysis.status !== 'COMPLETED' ? null : (
          <>
            <section style={{ display: 'grid', gridTemplateColumns: '160px minmax(0, 1fr)', gap: 32, minHeight: 220, marginBottom: 48 }}>
              <div style={{ width: 160, height: 220, overflow: 'hidden', borderRadius: 8, background: 'var(--app-color-bg-container)', boxShadow: '0 4px 12px rgb(0 0 0 / 10%)' }}>
                {project?.coverUrl ? <img src={project.coverUrl} alt="项目封面" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : null}
              </div>
              <div>
                <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
                  <Typography.Title level={3} style={{ margin: 0, fontSize: 24 }}>{script?.title || '未命名剧本'}</Typography.Title>
                  <Button>补充剧本</Button>
                </Flex>
                <div style={{ color: 'var(--app-color-text-secondary)', fontSize: 12, marginBottom: 16 }}>
                  {episodeBlocks.length} 集　|　{project?.aspectRatio || '-'}　|　720p　|　{project?.visualStyle || '-'}
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <Tag color="blue">{scriptTypeText[project?.scriptType || ''] || '剧本'}</Tag>
                  <Tag>{fileFormatText[project?.fileFormat || ''] || '剧本格式'}</Tag>
                  <Tag>{breakdownStrengthText[project?.breakdownStrength || ''] || '标准解析'}</Tag>
                </div>
              </div>
            </section>

            <section style={{ marginBottom: 48 }}>
              <Typography.Title level={5} style={{ margin: '0 0 12px', fontSize: 16 }}>剧本类型</Typography.Title>
              <div style={{ border: '1px solid var(--app-color-border-secondary)', borderRadius: 8, background: '#fff', padding: '16px 12px' }}>
                <div style={{ fontWeight: 600 }}>AI短剧</div>
                <div style={{ marginTop: 4, color: 'var(--app-color-text-secondary)', fontSize: 13 }}>根据剧本内容智能规划叙事镜头，分角色演绎故事</div>
              </div>
            </section>

            <section style={{ marginBottom: 48 }}>
              <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}><Typography.Title level={5} style={{ margin: 0, fontSize: 16 }}>故事概览</Typography.Title><Typography.Text type="secondary">AI 全局理解结果</Typography.Text></Flex>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0, border: '1px solid var(--app-color-border-secondary)', borderRadius: 8, background: '#fff', padding: '12px 24px' }}>
                {[
                  ['一句话梗概', String(workspace?.globalUnderstanding?.content?.logline || '暂无全局说明')],
                  ['核心冲突', String(workspace?.globalUnderstanding?.content?.coreConflict || '-')],
                  ['主题', listText(workspace?.globalUnderstanding?.content?.themes)],
                  ['人物关系', listText(workspace?.globalUnderstanding?.content?.relationships)],
                  ['结尾钩子', String(workspace?.globalUnderstanding?.content?.endingHook || '-')],
                ].map(([label, value]) => <div key={label} style={{ gridColumn: label === '一句话梗概' ? '1 / -1' : undefined, display: 'grid', gridTemplateColumns: '94px 1fr', gap: 12, padding: '12px 0', borderBottom: '1px solid var(--app-color-border-secondary)', fontSize: 13, lineHeight: '22px' }}><span style={{ color: 'var(--app-color-text-tertiary)' }}>{label}</span><span>{value}</span></div>)}
              </div>
            </section>

            <section style={{ marginBottom: 48 }}>
              <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}><Typography.Title level={5} style={{ margin: 0, fontSize: 16 }}>人物小传</Typography.Title><Typography.Text type="secondary">已有角色信息与视觉资产</Typography.Text></Flex>
              {workspace?.characters.length ? (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '24px 32px' }}>
                  {(showAllCharacters ? workspace.characters : workspace.characters.slice(0, 6)).map((character) => {
                    const variants = character.visual?.variants || [];
                    const bindings = character.visual?.episodeBindings || [];
                    return (
                      <article key={character.id} style={{ padding: 12, border: '1px solid var(--app-color-border-secondary)', borderRadius: 8, background: '#fff' }}>
                        <div style={{ marginBottom: 8, fontWeight: 600 }}>{character.name}</div>
                        <div style={{ minHeight: 48, padding: '6px 10px', borderRadius: 6, background: 'var(--app-color-bg-layout)', color: 'var(--app-color-text-secondary)', fontSize: 12, lineHeight: '19px' }}>
                          身份：{character.identity || '-'}；性格：{character.personality?.join('、') || '-'}；简介：{character.appearance || '-'}
                        </div>
                        <div style={{ margin: '12px 0 8px', fontSize: 13, fontWeight: 600 }}>角色变装列表</div>
                        {variants.length ? (
                          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 8 }}>
                            {variants.map((variant) => {
                              const episodeNos = bindings
                                .filter((binding) => binding.variantId === variant.id && binding.status === 'ACTIVE')
                                .map((binding) => binding.episodeNo);
                              return (
                                <div key={variant.id} style={{ display: 'grid', gridTemplateColumns: '28px minmax(0, 1fr) auto', alignItems: 'center', gap: 6, minWidth: 0, padding: '6px 8px', border: '1px solid var(--app-color-border-secondary)', borderRadius: 6, fontSize: 12 }}>
                                  <span style={{ display: 'grid', width: 24, height: 24, placeItems: 'center', borderRadius: 6, background: 'var(--app-color-primary-bg)', color: 'var(--app-color-primary)' }}><UserOutlined /></span>
                                  <Typography.Text ellipsis={{ tooltip: variant.name }} style={{ minWidth: 0, fontSize: 12 }}>{variant.name}</Typography.Text>
                                  <span style={{ color: 'var(--app-color-text-secondary)', whiteSpace: 'nowrap' }}>{episodeNos.length ? `第${episodeNos.join('、')}集` : '未绑定'}</span>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>暂无变装资产，已生成 {character.visual?.variantCount || 0} 个角色形态。</Typography.Text>
                        )}
                      </article>
                    );
                  })}
                </div>
              ) : <Typography.Text type="secondary">暂无角色数据，完成角色场景识别后将在此展示。</Typography.Text>}
              {(workspace?.characters.length || 0) > 6 ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 24 }}>
                  <span style={{ flex: 1, height: 1, background: 'var(--app-color-border-secondary)' }} />
                  <Button type="text" onClick={() => setShowAllCharacters((current) => !current)}>
                    {showAllCharacters ? '收起人物' : '查看更多人物'}
                  </Button>
                  <span style={{ flex: 1, height: 1, background: 'var(--app-color-border-secondary)' }} />
                </div>
              ) : null}
            </section>
          </>
        )}

        {analysis?.status === 'COMPLETED' && workspace ? (
          <section
            aria-label="正式分析结果"
            style={{
              background: '#fff',
              border: '1px solid var(--app-color-border)',
              borderRadius: 8,
              padding: 18,
              marginBottom: 14,
            }}
          >
            <Typography.Title level={5} style={{ marginTop: 0 }}>正式分析结果</Typography.Title>
            <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
              <div style={metricStyle}>
                <span style={labelStyle}>剧情全局理解</span>
                <div style={{ marginTop: 6, lineHeight: '22px' }}>
                  {String(workspace.globalUnderstanding?.content?.logline || '暂无全局说明')}
                </div>
              </div>
              <div style={metricStyle}>
                <span style={labelStyle}>正式资产</span>
                <div style={{ marginTop: 6, lineHeight: '22px' }}>
                  角色 {workspace.characters.length} · 场景 {workspace.scenes.length} · 道具 {workspace.props.length}
                </div>
                <div style={{ marginTop: 4, fontSize: 12, color: 'var(--app-color-text-secondary)' }}>
                  角色形态 {workspace.characters.reduce((sum, item) => sum + (item.visual?.variantCount || 0), 0)} ·
                  道具形态 {workspace.props.reduce((sum, item) => sum + (item.visual?.variantCount || 0), 0)}
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {analysis && analysis.status !== 'COMPLETED' ? null : <section
          style={{
            background: '#fff',
            border: '1px solid var(--app-color-border)',
            borderRadius: 8,
            padding: 18,
          }}
        >
          <Flex justify="space-between" align="center">
            <Typography.Title level={5} style={{ margin: 0, fontSize: 16 }}>
              分集剧情
            </Typography.Title>
            {analysis?.status === 'COMPLETED' ? (
              <Button
                loading={executionBusy || undefined}
                onClick={() =>
                  void runDirectAgent(
                    () => regenerateEpisodeSplitting(projectId),
                    '剧集拆分已按当前剧本覆盖生成',
                  )
                }
              >
                单独重跑剧集拆分
              </Button>
            ) : null}
          </Flex>
          <div style={{ display: 'grid', gridTemplateColumns: '32px minmax(0, 1fr) 32px', gap: 8, alignItems: 'center', margin: '14px 0' }}>
            <Tooltip title="上一组剧集">
              <button type="button" aria-label="上一组剧集" onClick={() => episodeSelectorRef.current?.scrollBy({ left: -320, behavior: 'smooth' })} style={{ width: 32, height: 32, padding: 0, borderRadius: 4, border: '1px solid var(--app-color-border)', background: '#fff', color: 'var(--app-color-text-secondary)', cursor: 'pointer' }}><LeftOutlined /></button>
            </Tooltip>
            <div ref={episodeSelectorRef} aria-label="分集选择器" style={{ display: 'flex', gap: 8, overflowX: 'auto', scrollbarWidth: 'none', scrollBehavior: 'smooth', padding: '1px 0' }}>
              {episodeBlocks.map((item) => {
                const active = item.episodeNo === currentEpisodeNo;
                return (
                  <button
                    key={item.episodeNo}
                    type="button"
                    onClick={() => setCurrentEpisodeNo(item.episodeNo)}
                    style={{
                      flex: '0 0 32px',
                      width: 32,
                      height: 32,
                      padding: 0,
                      borderRadius: 4,
                      border: active ? '1px solid var(--app-color-primary)' : '1px solid var(--app-color-border)',
                      background: active ? 'var(--app-color-primary-bg)' : '#fff',
                      color: active ? '#334be4' : 'var(--app-color-text-secondary)',
                      fontSize: 12,
                      fontWeight: active ? 700 : 500,
                      cursor: 'pointer',
                    }}
                  >
                    {item.episodeNo}
                  </button>
                );
              })}
            </div>
            <Tooltip title="下一组剧集">
              <button type="button" aria-label="下一组剧集" onClick={() => episodeSelectorRef.current?.scrollBy({ left: 320, behavior: 'smooth' })} style={{ width: 32, height: 32, padding: 0, borderRadius: 4, border: '1px solid var(--app-color-border)', background: '#fff', color: 'var(--app-color-text-secondary)', cursor: 'pointer' }}><RightOutlined /></button>
            </Tooltip>
          </div>

          <Typography.Text strong>当前集剧情正文</Typography.Text>
          {currentEpisode?.formalSummary ? (
            <div style={{ margin: '0 0 12px', padding: 12, borderRadius: 8, background: 'var(--app-color-bg-layout)' }}>
              {summaryEditing ? (
                <div style={{ display: 'grid', gap: 8 }}>
                  <Input.TextArea aria-label="概要" value={summaryDraft} onChange={(event) => setSummaryDraft(event.target.value)} autoSize={{ minRows: 3 }} />
                  <Input.TextArea aria-label="亮点" value={highlightsDraft} onChange={(event) => setHighlightsDraft(event.target.value)} autoSize={{ minRows: 2 }} placeholder="每行一条亮点，共 2–5 条" />
                  <Input.TextArea aria-label="结尾钩子" value={endingHookDraft} onChange={(event) => setEndingHookDraft(event.target.value)} autoSize={{ minRows: 1 }} placeholder="没有明确证据可留空" />
                  <Flex gap={8}>
                    <Button type="primary" loading={summarySaving || undefined} onClick={() => void saveSummary()}>保存概要</Button>
                    <Button onClick={() => setSummaryEditing(false)}>取消</Button>
                  </Flex>
                </div>
              ) : (
                <>
                  <div>{currentEpisode.formalSummary.content.summary}</div>
                  <div style={{ marginTop: 4, fontSize: 12, color: 'var(--app-color-text-secondary)' }}>
                    亮点：{listText(currentEpisode.formalSummary.content.highlights)}
                    {' · '}钩子：{currentEpisode.formalSummary.content.endingHook || '-'}
                  </div>
                  <Flex gap={8} wrap style={{ marginTop: 8 }}>
                    <Button size="small" onClick={beginSummaryEdit}>编辑概要</Button>
                    <Button
                      size="small"
                      loading={executionBusy || undefined}
                      onClick={() => currentEpisode.episodeId && void runDirectAgent(
                        () => regenerateEpisodeSummary(projectId, currentEpisode.episodeId as number),
                        '本集概要已覆盖生成',
                      )}
                    >
                      AI 重生成本集概要
                    </Button>
                    <Button
                      size="small"
                      loading={executionBusy || undefined}
                      onClick={() => currentEpisode.episodeId && void runDirectAgent(
                        () => regenerateEpisodeAssets(projectId, currentEpisode.episodeId as number),
                        '本集角色、场景、道具已重新识别',
                      )}
                    >
                      AI 重识别本集资产
                    </Button>
                  </Flex>
                </>
              )}
            </div>
          ) : null}
          <Input.TextArea
            value={activeEpisode?.copy || ''}
            readOnly
            autoSize={{ minRows: 22, maxRows: 34 }}
            style={{
              marginTop: 8,
              background: 'var(--app-color-bg-container)',
              borderColor: 'var(--app-color-border-secondary)',
              borderRadius: 8,
              color: 'var(--app-color-text)',
              fontSize: 14,
              lineHeight: '25px',
            }}
          />
        </section>}
      </div>
    </div>
  );
};

export default ProductionWorkbenchScript;
