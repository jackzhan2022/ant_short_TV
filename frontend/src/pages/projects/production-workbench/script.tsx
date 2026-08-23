import { App, Flex, Input, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from '@umijs/max';
import {
  queryScriptWorkspace,
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
    setLoading(true);
    Promise.all([queryScriptWorkspace(projectId), queryProject(projectId)])
      .then(([workspaceResponse, projectResponse]) => {
        if (active) {
          setWorkspace(workspaceResponse.data);
          setProject(projectResponse.data);
        }
      })
      .catch(() => {
        if (active) {
          message.error('剧本页加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [message, projectId]);

  const outline = useMemo(() => collectOutline(workspace || { projectId, script: null, versions: [], characters: [], scenes: [], props: [], storyboards: [] }), [projectId, workspace]);
  const episodeBlocks = useMemo(() => getEpisodeBlocks(workspace || { projectId, script: null, versions: [], characters: [], scenes: [], props: [], storyboards: [] }), [projectId, workspace]);
  const activeEpisode = episodeBlocks.find((item) => item.episodeNo === currentEpisodeNo) || episodeBlocks[0];
  const script = workspace?.script;

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
