import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EyeOutlined,
  FileTextOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import type { UploadFile, UploadProps } from 'antd';
import {
  App,
  Button,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Modal,
  Progress,
  Row,
  Space,
  Steps,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { statusText } from '@/utils/fieldDictionary';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import type {
  VideoDecompositionBatch,
  VideoDecompositionEpisode,
  VideoDecompositionEpisodeDetail,
  VideoDecompositionUpload,
  VideoUnderstandingModel,
} from './service';
import {
  confirmVideoDecompositionDraft,
  createVideoDecompositionBatch,
  queryVideoDecompositionBatches,
  queryVideoDecompositionEpisode,
  queryVideoUnderstandingModels,
  retryVideoDecompositionEpisode,
  updateVideoDecompositionDraft,
  uploadEpisodeVideo,
} from './service';

type EpisodeUpload = {
  uid: string;
  episodeNo: number;
  fileName: string;
  size: number;
  mimeType?: string;
  status: 'READY';
  storagePath?: string;
};

const MAX_VIDEO_SIZE = 1024 * 1024 * 1024;
const SUPPORTED_VIDEO_TYPES = [
  'video/mp4',
  'video/quicktime',
  'video/x-msvideo',
];

const padTimePart = (value: number) => String(value).padStart(2, '0');

export const buildDefaultVideoDecompositionBatchName = (date = new Date()) =>
  `拆剧批次-${date.getFullYear()}${padTimePart(date.getMonth() + 1)}${padTimePart(date.getDate())}-${padTimePart(date.getHours())}${padTimePart(date.getMinutes())}${padTimePart(date.getSeconds())}`;

export const canCreateVideoDecompositionBatch = (episodes: EpisodeUpload[]) =>
  episodes.length > 0 &&
  episodes.every((episode) => Boolean(episode.storagePath));

const formatFileSize = (size: number) => {
  if (size >= 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const normalizeEpisodes = (files: UploadFile[]): EpisodeUpload[] =>
  files.map((file, index) => ({
    uid: file.uid,
    episodeNo: index + 1,
    fileName: file.name,
    size: file.size ?? 0,
    mimeType: file.type,
    status: 'READY',
    storagePath: (file.response as VideoDecompositionUpload | undefined)
      ?.storagePath,
  }));

const tryFormatJson = (value?: string | null) => {
  if (!value) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

const episodeStatusColor = (status: string) => {
  if (status === 'FAILED') {
    return 'red';
  }
  if (status === 'CONFIRMED') {
    return 'green';
  }
  if (status === 'PENDING_REVIEW') {
    return 'gold';
  }
  if (status.includes('GENERATING') || status.includes('ANALYZING')) {
    return 'blue';
  }
  return 'default';
};

const VideoScriptDecompositionPage = () => {
  const { message } = App.useApp();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [batchName, setBatchName] = useState(() =>
    buildDefaultVideoDecompositionBatchName(),
  );
  const [modelId, setModelId] = useState<number>();
  const [models, setModels] = useState<VideoUnderstandingModel[]>([]);
  const [batches, setBatches] = useState<VideoDecompositionBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedEpisodeId, setSelectedEpisodeId] = useState<number>();
  const [detail, setDetail] = useState<VideoDecompositionEpisodeDetail>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();
  const [draftContent, setDraftContent] = useState('');
  const [draftSaving, setDraftSaving] = useState(false);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [confirmProjectId, setConfirmProjectId] = useState<number>();
  const [confirmSaving, setConfirmSaving] = useState(false);

  const episodes = useMemo(() => normalizeEpisodes(files), [files]);
  const canCreateBatch = canCreateVideoDecompositionBatch(episodes);

  const loadBatches = async () => {
    const response = await queryVideoDecompositionBatches();
    setBatches(response.data ?? []);
  };

  const loadEpisodeDetail = async (episodeId: number) => {
    setDetailLoading(true);
    try {
      const response = await queryVideoDecompositionEpisode(episodeId);
      setDetail(response.data);
      setDraftContent(response.data?.draftContent ?? '');
      return response.data;
    } finally {
      setDetailLoading(false);
    }
  };

  const followEpisodeExecution = async (episode: VideoDecompositionEpisode) => {
    const tenantId = Number(localStorage.getItem('currentTenantId'));
    if (!episode.executionId || !tenantId) {
      return;
    }
    await aiExecutionTaskService.poll(
      tenantId,
      episode.executionId,
      setActiveExecution,
    );
    await loadEpisodeDetail(episode.id);
    await loadBatches();
  };

  useEffect(() => {
    loadBatches().catch(() => undefined);
    queryVideoUnderstandingModels()
      .then((response) => {
        const available = (response.data ?? []).filter(
          (model) =>
            model.serviceType === 'VIDEO_UNDERSTANDING' &&
            model.status === 'ENABLED',
        );
        setModels(available);
        const defaultModel = available.find(
          (model) => model.modelCode === 'qwen3.7-plus' && model.isDefault,
        );
        setModelId(defaultModel?.id ?? available[0]?.id);
      })
      .catch(() => undefined);
  }, []);

  const currentBatch = batches[0];
  const currentAnalysisPercent = currentBatch
    ? Math.round(
        ((currentBatch.completedEpisodes + currentBatch.failedEpisodes) /
          Math.max(currentBatch.totalEpisodes, 1)) *
          100,
      )
    : 0;
  const activeStep = detail
    ? detail.episode.status === 'CONFIRMED'
      ? 3
      : detail.episode.status === 'PENDING_REVIEW'
        ? 2
        : 1
    : currentBatch
      ? currentAnalysisPercent >= 100
        ? 2
        : 1
      : episodes.length > 0
        ? 0
        : 0;
  const overallPercent =
    activeStep === 0
      ? episodes.length > 0 && episodes.every((episode) => episode.storagePath)
        ? 25
        : 0
      : activeStep === 1
        ? 25 + Math.round(currentAnalysisPercent * 0.25)
        : activeStep === 2
          ? 75
          : 100;

  const moveFile = (uid: string, direction: -1 | 1) => {
    setFiles((current) => {
      const index = current.findIndex((file) => file.uid === uid);
      const nextIndex = index + direction;
      if (index < 0 || nextIndex < 0 || nextIndex >= current.length) {
        return current;
      }
      const next = [...current];
      [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
      return next;
    });
  };

  const openEpisode = async (episode: VideoDecompositionEpisode) => {
    setSelectedEpisodeId(episode.id);
    setActiveExecution(undefined);
    const nextDetail = await loadEpisodeDetail(episode.id);
    if (nextDetail?.episode.executionId) {
      void followEpisodeExecution(nextDetail.episode).catch(() =>
        message.error('拆剧任务状态刷新失败'),
      );
    }
  };

  const saveDraft = async () => {
    if (!detail) {
      return;
    }
    setDraftSaving(true);
    try {
      await updateVideoDecompositionDraft(
        detail.episode.id,
        draftContent,
        detail.episode.draftVersion,
      );
      message.success('草稿已保存');
      await loadEpisodeDetail(detail.episode.id);
      await loadBatches();
    } finally {
      setDraftSaving(false);
    }
  };

  const confirmDraft = () => {
    if (!detail) {
      return;
    }
    setConfirmProjectId(detail.episode.projectId ?? undefined);
    setConfirmVisible(true);
  };

  const submitConfirmDraft = async () => {
    if (!detail) {
      return;
    }
    if (!confirmProjectId) {
      message.warning('确认导入前请选择目标项目');
      return;
    }
    setConfirmSaving(true);
    try {
      await confirmVideoDecompositionDraft(
        detail.episode.id,
        draftContent,
        detail.episode.draftVersion,
        detail.currentScriptVersionId,
        confirmProjectId,
      );
      message.success('已创建视频导入剧本版本');
      setConfirmVisible(false);
      await loadEpisodeDetail(detail.episode.id);
      await loadBatches();
    } finally {
      setConfirmSaving(false);
    }
  };

  const uploadProps: UploadProps = {
    accept: 'video/mp4,video/quicktime,video/x-msvideo,.mp4,.mov,.avi',
    multiple: true,
    fileList: files,
    beforeUpload: (file) => {
      if (file.size > MAX_VIDEO_SIZE) {
        message.error(`${file.name} 超过 1GB，不能加入拆剧批次`);
        return Upload.LIST_IGNORE;
      }
      if (file.type && !SUPPORTED_VIDEO_TYPES.includes(file.type)) {
        message.error(`${file.name} 暂不支持该视频格式`);
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: async (options) => {
      try {
        const response = await uploadEpisodeVideo(options.file as File);
        options.onSuccess?.(response.data);
      } catch (error) {
        options.onError?.(error as Error);
      }
    },
    onChange: ({ fileList }) => {
      setFiles(fileList);
    },
    onRemove: (file) => {
      setFiles((current) => current.filter((item) => item.uid !== file.uid));
    },
  };

  return (
    <PageContainer title="视频拆剧">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Form layout="vertical">
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item label="批次名称">
                <Input
                  value={batchName}
                  onChange={(event) => setBatchName(event.target.value)}
                  placeholder="例如：第 1 季成片拆剧"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="视频理解模型">
                <Select
                  value={modelId}
                  onChange={(value) => setModelId(value)}
                  style={{ width: '100%' }}
                  placeholder="请选择视频理解模型"
                  loading={!models.length}
                  options={models.map((model) => ({
                    value: model.id,
                    label: `${model.name} (${model.modelCode})`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <div>
          <Steps
            current={activeStep}
            percent={overallPercent}
            items={[
              {
                title: '上传视频 · 25%',
                description: '选择视频并生成租户级拆剧批次。',
              },
              {
                title: '智能分析 · 50%',
                description: '系统自动分析视频并生成每集草稿。',
              },
              {
                title: '审核草稿 · 75%',
                description: '检查结构化结果并编辑单集草稿。',
              },
              {
                title: '确认导入 · 100%',
                description: '选择目标项目后导入正式剧本版本。',
              },
            ]}
          />
          <Progress
            percent={overallPercent}
            status={activeStep === 3 ? 'success' : 'active'}
            format={(percent) => `当前流程 ${percent}%`}
          />
        </div>

        <Upload.Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <CloudUploadOutlined />
          </p>
          <Typography.Text strong>上传短剧视频</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            每个视频固定对应一集，提交后按当前列表顺序生成集数
          </Typography.Paragraph>
        </Upload.Dragger>

        <ProTable<EpisodeUpload>
          rowKey="uid"
          search={false}
          pagination={false}
          headerTitle="待拆剧集"
          dataSource={episodes}
          options={false}
          columns={[
            {
              title: '集数',
              dataIndex: 'episodeNo',
              width: 88,
              render: (_, record) => (
                <Tag color="blue">第 {record.episodeNo} 集</Tag>
              ),
            },
            {
              title: '视频文件',
              dataIndex: 'fileName',
              ellipsis: true,
            },
            {
              title: '格式',
              dataIndex: 'mimeType',
              width: 160,
              renderText: (value) => value || '-',
            },
            {
              title: '大小',
              dataIndex: 'size',
              width: 120,
              renderText: (value) => formatFileSize(Number(value)),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: () => <Tag color="default">待提交</Tag>,
            },
            {
              title: '操作',
              valueType: 'option',
              width: 180,
              render: (_, record) => (
                <Space>
                  <Button
                    type="text"
                    icon={<ArrowUpOutlined />}
                    disabled={record.episodeNo === 1}
                    onClick={() => moveFile(record.uid, -1)}
                  />
                  <Button
                    type="text"
                    icon={<ArrowDownOutlined />}
                    disabled={record.episodeNo === episodes.length}
                    onClick={() => moveFile(record.uid, 1)}
                  />
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() =>
                      setFiles((current) =>
                        current.filter((file) => file.uid !== record.uid),
                      )
                    }
                  />
                </Space>
              ),
            },
          ]}
          toolBarRender={() => [
            <Button
              key="submit"
              type="primary"
              icon={<PlayCircleOutlined />}
              disabled={!canCreateBatch}
              loading={loading}
              onClick={async () => {
                if (!canCreateBatch) {
                  message.warning('请等待视频上传完成');
                  return;
                }
                setLoading(true);
                try {
                  const resolvedBatchName =
                    batchName.trim() ||
                    buildDefaultVideoDecompositionBatchName();
                  await createVideoDecompositionBatch({
                    name: resolvedBatchName,
                    modelId,
                    videos: episodes.map((episode) => ({
                      fileName: episode.fileName,
                      storagePath: episode.storagePath || '',
                      mimeType: episode.mimeType,
                      fileSize: episode.size,
                    })),
                  });
                  message.success('拆剧批次已创建');
                  setFiles([]);
                  setBatchName(buildDefaultVideoDecompositionBatchName());
                  await loadBatches();
                } finally {
                  setLoading(false);
                }
              }}
            >
              创建拆剧批次
            </Button>,
          ]}
        />

        <ProTable<VideoDecompositionBatch>
          rowKey="id"
          search={false}
          headerTitle="拆剧批次"
          dataSource={batches}
          loading={loading}
          options={false}
          columns={[
            {
              title: '批次',
              dataIndex: 'name',
              ellipsis: true,
            },
            {
              title: '项目编号',
              dataIndex: 'projectId',
              width: 100,
              render: (value) => value ?? '未绑定',
            },
            {
              title: '进度',
              width: 160,
              render: (_, record) =>
                `${record.completedEpisodes}/${record.totalEpisodes} 完成，${record.failedEpisodes} 失败`,
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 140,
              render: (_, record) => (
                <Tag color={record.failedEpisodes > 0 ? 'red' : 'blue'}>
                  {statusText(record.status)}
                </Tag>
              ),
            },
            {
              title: '单集',
              render: (_, record) => (
                <Space wrap>
                  {record.episodes.map((episode) => (
                    <Button
                      key={episode.id}
                      size="small"
                      danger={episode.status === 'FAILED'}
                      icon={<EyeOutlined />}
                      onClick={() => openEpisode(episode)}
                    >
                      第 {episode.episodeNo} 集 · {statusText(episode.status)}
                    </Button>
                  ))}
                </Space>
              ),
            },
          ]}
          pagination={{ pageSize: 5 }}
        />

        <Drawer
          title={
            detail
              ? `第 ${detail.episode.episodeNo} 集拆剧详情`
              : '单集拆剧详情'
          }
          size={840}
          open={Boolean(selectedEpisodeId)}
          loading={detailLoading}
          destroyOnHidden
          onClose={() => {
            setSelectedEpisodeId(undefined);
            setDetail(undefined);
            setActiveExecution(undefined);
            setDraftContent('');
          }}
          extra={
            detail ? (
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  disabled={detail.episode.status !== 'FAILED'}
                  onClick={async () => {
                    await retryVideoDecompositionEpisode(detail.episode.id);
                    message.success('已重新加入视频解析队列');
                    await loadEpisodeDetail(detail.episode.id);
                    await loadBatches();
                  }}
                >
                  重试解析
                </Button>
                <Button
                  icon={<ReloadOutlined />}
                  disabled={!detail.normalizedJson}
                  onClick={async () => {
                    await retryVideoDecompositionEpisode(
                      detail.episode.id,
                      'DRAFT_GENERATION',
                    );
                    message.success('已重新加入草稿生成队列');
                    await loadEpisodeDetail(detail.episode.id);
                    await loadBatches();
                  }}
                >
                  重生成草稿
                </Button>
                <Button
                  icon={<SaveOutlined />}
                  loading={draftSaving}
                  disabled={!draftContent.trim()}
                  onClick={saveDraft}
                >
                  保存草稿
                </Button>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  disabled={
                    !draftContent.trim() ||
                    detail.episode.status === 'CONFIRMED'
                  }
                  onClick={confirmDraft}
                >
                  确认导入
                </Button>
                <Button
                  icon={<FileTextOutlined />}
                  disabled={!detail.episode.confirmedScriptVersionId}
                  onClick={() =>
                    history.push(
                      `/projects/${detail.episode.projectId}/production-workbench/script`,
                    )
                  }
                >
                  查看剧本版本
                </Button>
              </Space>
            ) : null
          }
        >
          {detail ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {activeExecution ? (
                <AiExecutionStatus task={activeExecution} />
              ) : null}
              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  {
                    key: 'status',
                    label: '状态',
                    children: (
                      <Tag color={episodeStatusColor(detail.episode.status)}>
                        {statusText(detail.episode.status)}
                      </Tag>
                    ),
                  },
                  {
                    key: 'file',
                    label: '视频文件',
                    children: detail.episode.sourceFileName,
                  },
                  {
                    key: 'analysisVersion',
                    label: '解析版本',
                    children: detail.episode.analysisVersion ?? 0,
                  },
                  {
                    key: 'draftVersion',
                    label: '草稿版本',
                    children: detail.episode.draftVersion ?? 0,
                  },
                  {
                    key: 'scriptVersion',
                    label: '已导入版本',
                    children: detail.episode.confirmedScriptVersionId || '-',
                  },
                  {
                    key: 'error',
                    label: '错误信息',
                    children: detail.episode.errorMessage || '-',
                  },
                ]}
              />

              <div>
                <Typography.Title level={5}>剧本草稿</Typography.Title>
                <Input.TextArea
                  value={draftContent}
                  onChange={(event) => setDraftContent(event.target.value)}
                  autoSize={{ minRows: 10, maxRows: 18 }}
                  maxLength={200000}
                  showCount
                  placeholder="等待草稿生成后可在此审核和编辑"
                />
              </div>

              <div>
                <Typography.Title level={5}>结构化解析</Typography.Title>
                {detail.normalizedJson ? (
                  <Typography.Paragraph
                    copyable
                    style={{
                      whiteSpace: 'pre-wrap',
                      maxHeight: 280,
                      overflow: 'auto',
                    }}
                  >
                    {tryFormatJson(detail.normalizedJson)}
                  </Typography.Paragraph>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无解析结果"
                  />
                )}
              </div>

              <div>
                <Typography.Title level={5}>原始响应</Typography.Title>
                {detail.rawResponse ? (
                  <Typography.Paragraph
                    copyable
                    style={{
                      whiteSpace: 'pre-wrap',
                      maxHeight: 220,
                      overflow: 'auto',
                    }}
                  >
                    {detail.rawResponse}
                  </Typography.Paragraph>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无原始响应"
                  />
                )}
              </div>

              <ProTable
                rowKey="id"
                search={false}
                pagination={false}
                options={false}
                headerTitle="执行尝试"
                dataSource={detail.attempts}
                columns={[
                  { title: '阶段', dataIndex: 'phase', width: 150 },
                  { title: '次数', dataIndex: 'attemptNo', width: 80 },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    width: 120,
                    render: (_, record) => (
                      <Tag color={record.status === 'FAILED' ? 'red' : 'blue'}>
                        {statusText(record.status)}
                      </Tag>
                    ),
                  },
                  {
                    title: '请求编号',
                    dataIndex: 'providerRequestId',
                    ellipsis: true,
                  },
                  { title: '错误', dataIndex: 'errorMessage', ellipsis: true },
                ]}
              />
            </div>
          ) : null}
        </Drawer>

        <Modal
          title={
            detail
              ? `确认导入第 ${detail.episode.episodeNo} 集剧本？`
              : '确认导入剧本'
          }
          open={confirmVisible}
          okText="确认导入"
          cancelText="取消"
          confirmLoading={confirmSaving}
          destroyOnHidden
          onCancel={() => setConfirmVisible(false)}
          onOk={submitConfirmDraft}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              确认后会创建一个来源为 VIDEO_IMPORT
              的剧本版本，不会静默覆盖当前剧本内容。
            </Typography.Paragraph>
            <InputNumber
              min={1}
              value={confirmProjectId}
              onChange={(value) => setConfirmProjectId(value ?? undefined)}
              style={{ width: '100%' }}
              placeholder="请输入要导入的项目编号"
            />
          </div>
        </Modal>
      </div>
    </PageContainer>
  );
};

export default VideoScriptDecompositionPage;
