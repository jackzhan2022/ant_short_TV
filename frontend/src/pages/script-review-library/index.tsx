import {
  CloudUploadOutlined,
  FileTextOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import type { UploadFile } from 'antd';
import {
  App,
  Badge,
  Button,
  Card,
  Empty,
  Input,
  List,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
} from 'antd';
import mammoth from 'mammoth';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  filterLibraryProjects,
  type LibraryProject,
  type LibraryStateKey,
  libraryStateFromProject,
} from '../script-review/library';
import {
  importReviewProject,
  queryReviewProjectMetrics,
  queryReviewProjectSummaries,
} from '../script-review/service';

const stateColor: Record<LibraryStateKey, string> = {
  NOT_REVIEWED: 'default',
  RUNNING: 'processing',
  COMPLETED: 'success',
};

const filenameWithoutExtension = (name: string) =>
  name.replace(/\.[^.]+$/, '').trim();

const isTextScript = (file: File) =>
  file.type.startsWith('text/') || /\.(txt|md|markdown)$/i.test(file.name);

const isDocxScript = (file: File) => /\.docx$/i.test(file.name);

const ScriptReviewLibraryPage = () => {
  const { message } = App.useApp();
  const [items, setItems] = useState<LibraryProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [metricsLoading, setMetricsLoading] = useState(false);
  const [metricsFailed, setMetricsFailed] = useState(false);
  const [metricsReady, setMetricsReady] = useState(false);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<LibraryStateKey>();
  const [importOpen, setImportOpen] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [content, setContent] = useState('');
  const [uploadFile, setUploadFile] = useState<UploadFile>();
  const [saving, setSaving] = useState(false);
  const loadGeneration = useRef(0);

  const loadMetrics = async (generation: number) => {
    setMetricsLoading(true);
    setMetricsFailed(false);
    try {
      const metricsResponse = await queryReviewProjectMetrics();
      if (generation !== loadGeneration.current) return;
      const metricsByProjectId = new Map(
        (metricsResponse.data ?? []).map((metric) => [
          metric.projectId,
          metric,
        ]),
      );
      setItems((current) =>
        current.map((project) => ({
          ...project,
          ...metricsByProjectId.get(project.id),
        })),
      );
      setMetricsReady(true);
    } catch {
      if (generation === loadGeneration.current) setMetricsFailed(true);
    } finally {
      if (generation === loadGeneration.current) setMetricsLoading(false);
    }
  };

  const loadProjects = async () => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    setMetricsLoading(false);
    setMetricsFailed(false);
    setMetricsReady(false);
    const response = await queryReviewProjectSummaries();
    if (generation !== loadGeneration.current) return;
    const summaries = response.data ?? [];
    setItems(summaries);
    setLoading(false);
    void loadMetrics(generation);
  };

  useEffect(() => {
    loadProjects()
      .catch(() => message.error('加载剧本库失败'))
      .finally(() => setLoading(false));
  }, []);

  const states = useMemo(
    () =>
      new Map(
        items.map((project) => [project.id, libraryStateFromProject(project)]),
      ),
    [items],
  );
  const projects = useMemo(
    () =>
      filterLibraryProjects(
        items,
        states,
        query,
        metricsReady ? filter : undefined,
      ),
    [filter, items, metricsReady, query, states],
  );
  const stateFilters = useMemo(
    () => [
      { key: undefined, label: '全部剧本', count: items.length },
      ...(
        [
          ['NOT_REVIEWED', '未审核'],
          ['RUNNING', '审核中'],
          ['COMPLETED', '审核完成'],
        ] as const
      ).map(([key, label]) => ({
        key,
        label,
        count: [...states.values()].filter((state) => state.key === key).length,
      })),
    ],
    [items.length, states],
  );

  const closeImport = () => {
    setImportOpen(false);
  };

  const selectScriptFile = async (file: File) => {
    setProjectName(filenameWithoutExtension(file.name));
    try {
      if (isTextScript(file)) {
        setContent(await file.text());
      } else if (isDocxScript(file)) {
        const result = await mammoth.extractRawText({
          arrayBuffer: await file.arrayBuffer(),
        });
        setContent(result.value.trim());
      } else {
        setContent('');
        message.info('旧版 Word 文件将在导入后解析正文');
      }
    } catch {
      setContent('');
      message.error('读取剧本内容失败，请重新选择文件');
    }
    return false;
  };

  const createProject = async () => {
    if (!projectName.trim()) {
      message.warning('请输入剧本名称');
      return;
    }
    if (!content.trim() && !uploadFile?.originFileObj) {
      message.warning('请上传剧本文件或粘贴剧本内容');
      return;
    }
    setSaving(true);
    try {
      const response = await importReviewProject(
        projectName,
        content,
        uploadFile?.originFileObj,
      );
      const projectId = response.data?.project.id;
      if (!projectId) return;
      closeImport();
      setProjectName('');
      setContent('');
      setUploadFile(undefined);
      await loadProjects();
      message.success('已创建独立剧本审核项目');
      history.push(`/script-review/projects/${projectId}/reviews`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer
      title="剧本库"
      extra={[
        <Button
          key="new"
          icon={<PlusOutlined />}
          type="primary"
          onClick={() => setImportOpen(true)}
        >
          新建剧本
        </Button>,
      ]}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '240px minmax(0, 1fr)',
          gap: 16,
        }}
      >
        <Card size="small">
          <Space vertical size="middle" style={{ width: '100%' }}>
            <Typography.Text strong>查找剧本</Typography.Text>
            <Input.Search
              allowClear
              placeholder="搜索剧本名称"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <Typography.Text strong>审核状态</Typography.Text>
            <Space vertical size={4} style={{ width: '100%' }}>
              {stateFilters.map((item) => (
                <Button
                  block
                  key={item.key ?? 'ALL'}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    textAlign: 'left',
                  }}
                  type={filter === item.key ? 'primary' : 'text'}
                  disabled={!metricsReady && item.key !== undefined}
                  onClick={() => setFilter(item.key)}
                >
                  {item.label}
                  <Badge count={item.count} showZero />
                </Button>
              ))}
            </Space>
          </Space>
        </Card>
        <Card size="small" styles={{ body: { padding: 0 } }}>
          <div
            style={{
              alignItems: 'center',
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              justifyContent: 'space-between',
              padding: '12px 16px',
            }}
          >
            <Typography.Text strong>剧本列表</Typography.Text>
            <Space size="small">
              {metricsLoading ? (
                <Typography.Text type="secondary">
                  审核指标加载中…
                </Typography.Text>
              ) : null}
              {metricsFailed ? (
                <Button
                  size="small"
                  type="link"
                  onClick={() => void loadMetrics(loadGeneration.current)}
                >
                  重试审核指标
                </Button>
              ) : null}
              <Typography.Text type="secondary">按最近操作排序</Typography.Text>
            </Space>
          </div>
          {loading ? (
            <div
              style={{
                display: 'grid',
                minHeight: 240,
                placeItems: 'center',
              }}
            >
              <Spin description="正在加载剧本…" />
            </div>
          ) : (
            <List
              dataSource={projects}
              locale={{ emptyText: <Empty description="暂无独立剧本" /> }}
              renderItem={(project) => {
                const state = states.get(project.id);
                return (
                  <List.Item
                    actions={[
                      <Button
                        key="open"
                        type="link"
                        onClick={() =>
                          history.push(
                            `/script-review/projects/${project.id}/reviews`,
                          )
                        }
                      >
                        {metricsReady
                          ? (state?.actionLabel ?? '进入审核')
                          : '进入审核'}
                      </Button>,
                    ]}
                    style={{ padding: '16px' }}
                  >
                    <div
                      style={{
                        alignItems: 'center',
                        display: 'grid',
                        flex: 1,
                        gap: 16,
                        gridTemplateColumns:
                          'minmax(220px, 2fr) minmax(90px, 0.8fr) minmax(90px, 0.8fr) minmax(120px, 1fr)',
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <FileTextOutlined style={{ color: '#1677ff' }} />
                        }
                        title={project.name}
                        description={
                          <Typography.Text type="secondary">
                            {project.sourceFileName || '直接录入'} · V
                            {project.versionCount ?? '—'}
                          </Typography.Text>
                        }
                      />
                      <Typography.Text type="secondary">
                        第 {project.latestRoundNo ?? '—'} 轮审核
                      </Typography.Text>
                      <Tag color={state ? stateColor[state.key] : 'default'}>
                        {!metricsReady
                          ? metricsFailed
                            ? '指标暂不可用'
                            : '指标加载中'
                          : (state?.label ?? '未审核')}
                      </Tag>
                      <Typography.Text type="secondary">
                        {!metricsReady
                          ? metricsFailed
                            ? '请重试加载审核指标'
                            : '审核指标加载中'
                          : state?.key === 'COMPLETED'
                            ? '审核结果已生成'
                            : '尚无已完成报告'}
                      </Typography.Text>
                    </div>
                  </List.Item>
                );
              }}
            />
          )}
        </Card>
      </div>
      <Modal
        centered
        confirmLoading={saving}
        destroyOnHidden
        okText="导入剧本"
        open={importOpen}
        title="新建独立剧本"
        width={680}
        onCancel={closeImport}
        onOk={createProject}
      >
        <Space vertical size="middle" style={{ width: '100%' }}>
          <Input
            prefix={<FileTextOutlined />}
            placeholder="剧本名称"
            value={projectName}
            onChange={(event) => setProjectName(event.target.value)}
          />
          <Upload.Dragger
            accept=".doc,.docx,.txt,.md,.markdown"
            beforeUpload={selectScriptFile}
            fileList={uploadFile ? [uploadFile] : []}
            maxCount={1}
            onChange={({ fileList }) => setUploadFile(fileList[0])}
            onRemove={() => setUploadFile(undefined)}
          >
            <CloudUploadOutlined style={{ fontSize: 28 }} />
            <div>上传 Word / TXT / Markdown</div>
          </Upload.Dragger>
          <Input.TextArea
            autoSize={{ minRows: 7, maxRows: 14 }}
            placeholder="或直接粘贴剧本内容"
            value={content}
            onChange={(event) => setContent(event.target.value)}
          />
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default ScriptReviewLibraryPage;
