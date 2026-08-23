import {
  App,
  Button,
  Descriptions,
  Collapse,
  Flex,
  Input,
  Progress,
  Skeleton,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from '@umijs/max';
import {
  queryScriptWorkspace,
  reanalyzeScript,
  retryScriptAnalysis,
  type ScriptWorkspace,
} from '../detail/components/service';
import { queryProject } from '../detail/service';

type EpisodeBlock = {
  episodeNo: number;
  title: string;
  copy: string;
};

type ProjectLite = {
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
      <div style={{ display: 'grid', gap: 6, fontSize: 12, color: '#374151' }}>
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
              background: '#f8fafc',
              border: '1px solid #e5e7eb',
              fontSize: 12,
              lineHeight: '18px',
            }}
          >
            <div style={{ fontWeight: 700, color: '#111827' }}>
              第{episode.episodeNo || '-'}集 {episode.title ? `· ${episode.title}` : ''}
            </div>
            {stageCode === 'EPISODE_SPLITTING' ? (
              <>
                <div>概要：{episode.summary || '-'}</div>
                <div>收尾：{episode.endingHook || '-'}</div>
                <div style={{ color: '#6b7280' }}>正文：{episode.content || '-'}</div>
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
    const characters = Array.isArray(parsed.characters) ? parsed.characters : [];
    const scenes = Array.isArray(parsed.scenes) ? parsed.scenes : [];
    const props = Array.isArray(parsed.props) ? parsed.props : [];
    return (
      <div style={{ display: 'grid', gap: 8, fontSize: 12 }}>
        <div>角色：{characters.map((item: any) => item.name).filter(Boolean).join(' / ') || '-'}</div>
        <div>场景：{scenes.map((item: any) => item.name).filter(Boolean).join(' / ') || '-'}</div>
        <div>道具：{props.map((item: any) => item.name).filter(Boolean).join(' / ') || '-'}</div>
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
        color: '#374151',
      }}
    >
      {resultJson}
    </pre>
  );
};

const collectOutline = (workspace: ScriptWorkspace) => {
  const contentLines = (workspace.script?.content || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
  const headings = contentLines.filter((line) => /^第\d+集/.test(line));
  if (headings.length) {
    return headings.slice(0, 8);
  }
  if (workspace.storyboards.length) {
    return Array.from(
      new Map(
        workspace.storyboards
          .slice()
          .sort((left, right) => left.episodeNo - right.episodeNo || left.shotNo - right.shotNo)
          .map((item) => [
            item.episodeNo,
            `第${item.episodeNo}集 · ${item.visualDescription || item.dialogue || '待补全'}`,
          ]),
      ).values(),
    ).slice(0, 8);
  }
  return contentLines.slice(0, 8);
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
  border: '1px solid #edf1f8',
  background: '#fbfcff',
  padding: '12px 14px',
} as const;

const labelStyle = {
  display: 'block',
  color: '#858fa5',
  fontSize: 12,
  lineHeight: '18px',
} as const;

const valueStyle = {
  marginTop: 6,
  color: '#1f2937',
  fontSize: 15,
  fontWeight: 700,
  lineHeight: '22px',
} as const;

const ProductionWorkbenchScript = () => {
  const params = useParams<{ id: string }>();
  const { message } = App.useApp();
  const projectId = Number(params.id);
  const [workspace, setWorkspace] = useState<ScriptWorkspace | null>(null);
  const [project, setProject] = useState<ProjectLite>();
  const [loading, setLoading] = useState(false);
  const [currentEpisodeNo, setCurrentEpisodeNo] = useState(1);

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

  const outline = useMemo(() => collectOutline(workspace || { projectId, script: null, versions: [], characters: [], scenes: [], props: [], storyboards: [] }), [projectId, workspace]);
  const episodeBlocks = useMemo(() => getEpisodeBlocks(workspace || { projectId, script: null, versions: [], characters: [], scenes: [], props: [], storyboards: [] }), [projectId, workspace]);
  const activeEpisode = episodeBlocks.find((item) => item.episodeNo === currentEpisodeNo) || episodeBlocks[0];
  const script = workspace?.script;
  const analysis = workspace?.analysis;
  const retryAnalysis = async (stageCode: string) => {
    try {
      await retryScriptAnalysis(projectId, stageCode);
      const response = await queryScriptWorkspace(projectId);
      setWorkspace(response.data);
      message.success('已重新加入分析队列');
    } catch {
      message.error('分析重试失败');
    }
  };
  const reanalyzeCurrent = async () => {
    try {
      await reanalyzeScript(projectId);
      const response = await queryScriptWorkspace(projectId);
      setWorkspace(response.data);
      message.success('已重新发起分析');
    } catch {
      message.error('重新分析失败');
    }
  };

  if (!projectId) {
    return null;
  }

  return (
    <div
      style={{
        minHeight: 'calc(100vh - 100px)',
        padding: '16px 28px 68px',
        boxSizing: 'border-box',
        background: '#f6f7fb',
      }}
    >
      <div style={{ maxWidth: 1500, margin: '0 auto' }}>
        <Flex justify="space-between" align="center" style={{ marginBottom: 12 }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 18 }}>
              线上剧本内容
            </Typography.Title>
            <Typography.Text type="secondary">
              {loading ? '正在加载剧本内容...' : '剧本信息、正文、大纲与分集剧情'}
            </Typography.Text>
          </div>
          <Flex gap={8}>
            <Tag color="blue">{project?.aspectRatio || '-'}</Tag>
            <Tag>720p</Tag>
            <Tag>{project?.visualStyle || '-'}</Tag>
          </Flex>
        </Flex>

        {loading && !workspace ? (
          <section
            style={{
              background: '#fff',
              border: '1px solid #e6ebf5',
              borderRadius: 8,
              padding: 18,
              marginBottom: 14,
            }}
          >
            <Skeleton active paragraph={{ rows: 4 }} />
          </section>
        ) : null}

        {analysis ? (
          <section
            aria-label="剧本分析进度"
            style={{
              background: '#fff',
              border: '1px solid #e6ebf5',
              borderRadius: 8,
              padding: 18,
              marginBottom: 14,
            }}
          >
            <Flex justify="space-between" align="center">
              <div>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  剧本智能分析
                </Typography.Title>
                <Typography.Text type="secondary">
                  {analysis.currentAction || '分析任务已创建'}
                </Typography.Text>
              </div>
              <Typography.Text strong>{analysis.overallProgress}%</Typography.Text>
            </Flex>
            <Progress
              percent={analysis.overallProgress}
              status={analysis.status === 'FAILED' ? 'exception' : undefined}
              showInfo={false}
              style={{ margin: '12px 0 16px' }}
            />
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
                gap: 10,
              }}
            >
              {analysis.stages.map((stage) => (
                <div
                  key={stage.stageCode}
                  style={{
                    minHeight: 92,
                    border: '1px solid #edf1f8',
                    borderRadius: 8,
                    padding: 12,
                    background: '#fbfcff',
                  }}
                >
                  <Flex justify="space-between" align="center">
                    <Typography.Text strong>
                      {analysisStageLabels[stage.stageCode] || stage.stageCode}
                    </Typography.Text>
                    <Typography.Text>{stage.progressPercent}%</Typography.Text>
                  </Flex>
                  <Progress
                    percent={stage.progressPercent}
                    size="small"
                    status={stage.status === 'FAILED' ? 'exception' : undefined}
                    showInfo={false}
                    style={{ margin: '8px 0 4px' }}
                  />
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {stage.errorMessage ||
                      stage.currentAction ||
                      (stage.status === 'SUCCEEDED' ? '已完成' : '等待中')}
                  </Typography.Text>
                  {stage.resultJson ? (
                    <div style={{ marginTop: 8 }}>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {analysisStageDescriptions[stage.stageCode] || '阶段结果'}
                      </Typography.Text>
                      <div style={{ marginTop: 6 }}>{renderResultSummary(stage.stageCode, stage.resultJson)}</div>
                      <Descriptions
                        size="small"
                        column={1}
                        style={{ marginTop: 8 }}
                        items={[
                          {
                            key: 'req',
                            label: '请求',
                            children: stage.providerRequestId || '-',
                          },
                          {
                            key: 'call',
                            label: '调用',
                            children: stage.aiCallLogId ? `#${stage.aiCallLogId}` : '-',
                          },
                          {
                            key: 'cost',
                            label: '耗时',
                            children: stage.durationMs ? `${Math.round(stage.durationMs / 1000)}s` : '-',
                          },
                          {
                            key: 'resultError',
                            label: '结果错误',
                            children: stage.resultErrorMessage || stage.resultErrorCode || '-',
                          },
                        ]}
                      />
                    </div>
                  ) : null}
                  {stage.resultJson ? (
                    <Collapse
                      size="small"
                      ghost
                      items={[
                        {
                          key: 'raw',
                          label: '查看原始内容',
                          children: (
                            <pre
                              style={{
                                margin: 0,
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-word',
                                fontSize: 12,
                                lineHeight: '18px',
                                color: '#374151',
                              }}
                            >
                              {stage.resultJson}
                            </pre>
                          ),
                        },
                      ]}
                    />
                  ) : null}
                  {stage.status === 'FAILED' && stage.retryable ? (
                    <Button
                      type="link"
                      size="small"
                      icon={<ReloadOutlined />}
                      onClick={() => void retryAnalysis(stage.stageCode)}
                      style={{ padding: 0, marginTop: 6 }}
                    >
                      重试此步骤
                    </Button>
                  ) : null}
                </div>
              ))}
            </div>
            {analysis.status === 'COMPLETED' ? (
              <div style={{ marginTop: 14 }}>
                <Button onClick={() => void reanalyzeCurrent()}>
                  重新分析当前版本
                </Button>
              </div>
            ) : null}
          </section>
        ) : null}

        <section
          style={{
            background: '#fff',
            border: '1px solid #e6ebf5',
            borderRadius: 8,
            padding: 18,
            marginBottom: 14,
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
              gap: 12,
              marginBottom: 14,
            }}
          >
            <div style={metricStyle}>
              <span style={labelStyle}>剧本名称</span>
              <div style={valueStyle}>
                {script?.title || '未命名剧本'}
              </div>
            </div>
            <div style={metricStyle}>
              <span style={labelStyle}>剧本类型</span>
              <div style={valueStyle}>
                {fileFormatText[project?.fileFormat || ''] || '-'}
              </div>
            </div>
            <div style={metricStyle}>
              <span style={labelStyle}>剧本状态</span>
              <div style={valueStyle}>
                {scriptTypeText[project?.scriptType || ''] || '-'}
              </div>
            </div>
            <div style={metricStyle}>
              <span style={labelStyle}>解析力度</span>
              <div style={valueStyle}>
                {breakdownStrengthText[project?.breakdownStrength || ''] ||
                  '-'}
              </div>
            </div>
          </div>

          <div style={{ marginTop: 10, color: '#7a849a', fontSize: 12 }}>
            当前版本 {script?.currentVersionId ? `#${script.currentVersionId}` : '-'}
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'minmax(0, 1.42fr) minmax(310px, 0.58fr)',
              gap: 14,
              alignItems: 'start',
            }}
          >
            <div>
              <Typography.Text strong>线上剧本正文</Typography.Text>
              <Input.TextArea
                value={script?.content || ''}
                readOnly
                autoSize={{ minRows: 20, maxRows: 30 }}
                placeholder="暂无线上剧本内容"
                style={{
                  marginTop: 8,
                  background: '#fafbfe',
                  borderColor: '#dfe5f1',
                  borderRadius: 8,
                  color: '#202736',
                  fontSize: 14,
                  lineHeight: '24px',
                }}
              />
            </div>
            <div>
              <Typography.Text strong>大纲</Typography.Text>
              <div
                style={{
                  marginTop: 8,
                  padding: '14px 15px',
                  minHeight: 528,
                  borderRadius: 8,
                  border: '1px solid #edf1f7',
                  background: '#fcfdff',
                }}
              >
                {outline.length ? (
                  outline.map((item) => (
                    <div
                      key={item}
                      style={{
                        fontSize: 13,
                        lineHeight: '22px',
                        marginBottom: 9,
                        color: '#2b3446',
                      }}
                    >
                      {item}
                    </div>
                  ))
                ) : loading ? (
                  <Skeleton active paragraph={{ rows: 6 }} />
                ) : (
                  <Typography.Text type="secondary">暂无大纲</Typography.Text>
                )}
              </div>
            </div>
          </div>
        </section>

        <section
          style={{
            background: '#fff',
            border: '1px solid #e6ebf5',
            borderRadius: 8,
            padding: 18,
          }}
        >
          <Flex justify="space-between" align="center">
            <Typography.Title level={5} style={{ margin: 0, fontSize: 16 }}>
              分集剧情
            </Typography.Title>
            <Typography.Text type="secondary">
              当前第{currentEpisodeNo}集
            </Typography.Text>
          </Flex>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 6,
              margin: '14px 0',
            }}
          >
            {episodeBlocks.map((item) => {
              const active = item.episodeNo === currentEpisodeNo;
              return (
                <button
                  key={item.episodeNo}
                  type="button"
                  onClick={() => setCurrentEpisodeNo(item.episodeNo)}
                  style={{
                    height: 28,
                    minWidth: 64,
                    padding: '0 12px',
                    borderRadius: 14,
                    border: active ? '1px solid #7d8cff' : '1px solid #e4e9f3',
                    background: active ? '#eef2ff' : '#fff',
                    color: active ? '#334be4' : '#4a5568',
                    fontSize: 12,
                    fontWeight: active ? 700 : 500,
                    cursor: 'pointer',
                  }}
                >
                  {item.title}
                </button>
              );
            })}
          </div>

          <Typography.Text strong>当前集剧情正文</Typography.Text>
          <Input.TextArea
            value={activeEpisode?.copy || ''}
            readOnly
            autoSize={{ minRows: 22, maxRows: 34 }}
            style={{
              marginTop: 8,
              background: '#fafbfe',
              borderColor: '#dfe5f1',
              borderRadius: 8,
              color: '#202736',
              fontSize: 14,
              lineHeight: '25px',
            }}
          />
        </section>
      </div>
    </div>
  );
};

export default ProductionWorkbenchScript;
