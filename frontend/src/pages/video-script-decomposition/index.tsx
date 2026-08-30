import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import XMarkdown from '@ant-design/x-markdown';
import type { UploadFile, UploadProps } from 'antd';
import {
  App,
  Button,
  Col,
  Collapse,
  Drawer,
  Empty,
  Form,
  Input,
  Progress,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { statusText } from '@/utils/fieldDictionary';
import type {
  VideoDecompositionBatch,
  VideoDecompositionBatchScreenplays,
  VideoDecompositionEpisode,
  VideoDecompositionUpload,
  VideoUnderstandingModel,
} from './service';
import {
  createVideoDecompositionBatch,
  queryVideoDecompositionBatches,
  queryVideoDecompositionBatchScreenplays,
  queryVideoUnderstandingModels,
  retryVideoDecompositionEpisode,
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
const TERMINAL_BATCH_STATUSES = new Set([
  'SUCCEEDED',
  'FAILED',
  'PARTIAL_FAILED',
  'PENDING_REVIEW',
  'CONFIRMED',
]);
const padTimePart = (value: number) => String(value).padStart(2, '0');

export const buildDefaultVideoDecompositionBatchName = (date = new Date()) =>
  '拆剧批次-' +
  date.getFullYear() +
  padTimePart(date.getMonth() + 1) +
  padTimePart(date.getDate()) +
  '-' +
  padTimePart(date.getHours()) +
  padTimePart(date.getMinutes()) +
  padTimePart(date.getSeconds());

export const canCreateVideoDecompositionBatch = (episodes: EpisodeUpload[]) =>
  episodes.length > 0 && episodes.every((episode) => episode.storagePath);

export const shouldPollVideoDecompositionBatch = (status?: string) =>
  Boolean(status && !TERMINAL_BATCH_STATUSES.has(status));

export const buildCopyAllScreenplays = (
  batch?: VideoDecompositionBatchScreenplays,
) =>
  (batch?.episodes ?? [])
    .filter((item) => item.screenplayContent)
    .sort((left, right) => left.episode.episodeNo - right.episode.episodeNo)
    .map((item) => item.screenplayContent?.trim())
    .join('\n\n');

const formatFileSize = (size: number) =>
  size >= MAX_VIDEO_SIZE
    ? `${(size / MAX_VIDEO_SIZE).toFixed(2)} GB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`;

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

const statusColor = (status: string) => {
  if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'red';
  if (status === 'SUCCEEDED' || status === 'CONFIRMED') return 'green';
  if (status.includes('ANALYZING') || status === 'RUNNING') return 'blue';
  return 'default';
};

const VideoScriptDecompositionPage = () => {
  const { message } = App.useApp();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [batchName, setBatchName] = useState(
    buildDefaultVideoDecompositionBatchName,
  );
  const [modelId, setModelId] = useState<number>();
  const [models, setModels] = useState<VideoUnderstandingModel[]>([]);
  const [batches, setBatches] = useState<VideoDecompositionBatch[]>([]);
  const [creating, setCreating] = useState(false);
  const [openBatchId, setOpenBatchId] = useState<number>();
  const [screenplays, setScreenplays] =
    useState<VideoDecompositionBatchScreenplays>();
  const [screenplaysLoading, setScreenplaysLoading] = useState(false);
  const [retryingEpisodeId, setRetryingEpisodeId] = useState<number>();
  const episodes = useMemo(() => normalizeEpisodes(files), [files]);

  const loadBatches = useCallback(async () => {
    const response = await queryVideoDecompositionBatches();
    setBatches(response.data ?? []);
  }, []);

  const loadScreenplays = useCallback(async (batchId: number) => {
    setScreenplaysLoading(true);
    try {
      const response = await queryVideoDecompositionBatchScreenplays(batchId);
      setScreenplays(response.data);
    } finally {
      setScreenplaysLoading(false);
    }
  }, []);

  const refreshOpenBatch = useCallback(async () => {
    if (!openBatchId) return;
    await Promise.all([loadBatches(), loadScreenplays(openBatchId)]);
  }, [loadBatches, loadScreenplays, openBatchId]);

  useEffect(() => {
    void loadBatches().catch(() => undefined);
    void queryVideoUnderstandingModels()
      .then((response) => {
        const available = (response.data ?? []).filter(
          (model) =>
            model.serviceType === 'VIDEO_UNDERSTANDING' &&
            model.status === 'ENABLED',
        );
        setModels(available);
        setModelId(
          available.find(
            (model) => model.modelCode === 'qwen3.7-plus' && model.isDefault,
          )?.id ?? available[0]?.id,
        );
      })
      .catch(() => undefined);
  }, [loadBatches]);

  useEffect(() => {
    if (
      !batches.some((batch) => shouldPollVideoDecompositionBatch(batch.status))
    ) {
      return undefined;
    }
    const timer = window.setInterval(
      () => void loadBatches().catch(() => undefined),
      3000,
    );
    return () => window.clearInterval(timer);
  }, [batches, loadBatches]);

  const moveFile = (uid: string, direction: -1 | 1) => {
    setFiles((current) => {
      const index = current.findIndex((file) => file.uid === uid);
      const target = index + direction;
      if (index < 0 || target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
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
    onChange: ({ fileList }) => setFiles(fileList),
    onRemove: (file) =>
      setFiles((current) => current.filter((item) => item.uid !== file.uid)),
  };

  const retryEpisode = async (episode: VideoDecompositionEpisode) => {
    setRetryingEpisodeId(episode.id);
    try {
      await retryVideoDecompositionEpisode(episode.id);
      message.success(`第 ${episode.episodeNo} 集已重新加入生成队列`);
      await refreshOpenBatch();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    } finally {
      setRetryingEpisodeId(undefined);
    }
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
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="视频理解模型">
                <Select
                  value={modelId}
                  onChange={setModelId}
                  style={{ width: '100%' }}
                  placeholder="请选择视频理解模型"
                  options={models.map((model) => ({
                    value: model.id,
                    label: `${model.name} (${model.modelCode})`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <Upload.Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <CloudUploadOutlined />
          </p>
          <Typography.Text strong>上传短剧视频</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            一个视频生成一集剧本，提交顺序就是集数顺序
          </Typography.Paragraph>
        </Upload.Dragger>

        <ProTable<EpisodeUpload>
          rowKey="uid"
          search={false}
          pagination={false}
          options={false}
          headerTitle="待生成剧本"
          dataSource={episodes}
          columns={[
            {
              title: '集数',
              dataIndex: 'episodeNo',
              render: (_, record) => <Tag>第 {record.episodeNo} 集</Tag>,
            },
            { title: '视频文件', dataIndex: 'fileName' },
            {
              title: '大小',
              dataIndex: 'size',
              renderText: (value) => formatFileSize(Number(value)),
            },
            {
              title: '操作',
              valueType: 'option',
              render: (_, record) => (
                <Space>
                  <Button
                    aria-label="上移"
                    icon={<ArrowUpOutlined />}
                    disabled={record.episodeNo === 1}
                    onClick={() => moveFile(record.uid, -1)}
                  />
                  <Button
                    aria-label="下移"
                    icon={<ArrowDownOutlined />}
                    disabled={record.episodeNo === episodes.length}
                    onClick={() => moveFile(record.uid, 1)}
                  />
                  <Button
                    aria-label="删除"
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
              key="create"
              type="primary"
              icon={<PlayCircleOutlined />}
              disabled={!canCreateVideoDecompositionBatch(episodes)}
              loading={creating}
              onClick={async () => {
                setCreating(true);
                try {
                  await createVideoDecompositionBatch({
                    name:
                      batchName.trim() ||
                      buildDefaultVideoDecompositionBatchName(),
                    modelId,
                    videos: episodes.map((episode) => ({
                      fileName: episode.fileName,
                      storagePath: episode.storagePath || '',
                      mimeType: episode.mimeType,
                      fileSize: episode.size,
                    })),
                  });
                  message.success('剧本生成批次已创建');
                  setFiles([]);
                  await loadBatches();
                } catch (error) {
                  message.error(
                    error instanceof Error ? error.message : '创建批次失败',
                  );
                } finally {
                  setCreating(false);
                }
              }}
            >
              开始生成剧本
            </Button>,
          ]}
        />

        <ProTable<VideoDecompositionBatch>
          rowKey="id"
          search={false}
          options={false}
          headerTitle="生成批次"
          dataSource={batches}
          columns={[
            { title: '批次', dataIndex: 'name' },
            {
              title: '进度',
              render: (_, record) => (
                <div>
                  <Progress
                    percent={record.percentage}
                    size="small"
                    status={
                      record.status === 'FAILED' ? 'exception' : undefined
                    }
                  />
                  <Typography.Text type="secondary">
                    成功 {record.succeededEpisodes} · 处理中{' '}
                    {record.processingEpisodes} · 待处理{' '}
                    {record.pendingEpisodes} · 失败 {record.failedEpisodes}
                  </Typography.Text>
                </div>
              ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              render: (value) => (
                <Tag color={statusColor(String(value))}>
                  {statusText(String(value))}
                </Tag>
              ),
            },
            {
              title: '操作',
              valueType: 'option',
              render: (_, record) => (
                <Button
                  icon={<EyeOutlined />}
                  onClick={() => {
                    setOpenBatchId(record.id);
                    setScreenplays(undefined);
                    void loadScreenplays(record.id).catch((error: unknown) =>
                      message.error(
                        error instanceof Error ? error.message : '剧本加载失败',
                      ),
                    );
                  }}
                >
                  查看全部剧本
                </Button>
              ),
            },
          ]}
          pagination={{ pageSize: 5 }}
        />

        <Drawer
          title={
            screenplays ? `${screenplays.batchName} · 全部剧本` : '全部剧本'
          }
          size={900}
          open={Boolean(openBatchId)}
          loading={screenplaysLoading}
          destroyOnHidden
          onClose={() => {
            setOpenBatchId(undefined);
            setScreenplays(undefined);
          }}
          extra={
            <Space>
              <Button
                icon={<ReloadOutlined />}
                loading={screenplaysLoading}
                onClick={() =>
                  void refreshOpenBatch().catch((error: unknown) =>
                    message.error(
                      error instanceof Error ? error.message : '剧本加载失败',
                    ),
                  )
                }
              >
                刷新
              </Button>
              <Button
                icon={<CopyOutlined />}
                disabled={!buildCopyAllScreenplays(screenplays)}
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(
                      buildCopyAllScreenplays(screenplays),
                    );
                    message.success('已复制全部成功剧本');
                  } catch {
                    message.error('复制失败，请检查浏览器剪贴板权限');
                  }
                }}
              >
                复制全部剧本
              </Button>
            </Space>
          }
        >
          {screenplays ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <Typography.Text>
                总进度 {screenplays.percentage}%：成功{' '}
                {screenplays.succeededEpisodes}，处理中{' '}
                {screenplays.processingEpisodes}，待处理{' '}
                {screenplays.pendingEpisodes}，失败 {screenplays.failedEpisodes}
              </Typography.Text>
              <Collapse
                defaultActiveKey={screenplays.episodes
                  .filter((item) => item.screenplayContent)
                  .map((item) => String(item.episode.id))}
                items={screenplays.episodes.map((item) => ({
                  key: String(item.episode.id),
                  label: (
                    <Space>
                      第 {item.episode.episodeNo} 集
                      <Tag color={statusColor(item.episode.status)}>
                        {statusText(item.episode.status)}
                      </Tag>
                      {item.episode.percentage}%
                    </Space>
                  ),
                  extra:
                    item.episode.status === 'FAILED' &&
                    item.episode.retryable ? (
                      <Button
                        size="small"
                        icon={<ReloadOutlined />}
                        loading={retryingEpisodeId === item.episode.id}
                        onClick={(event) => {
                          event.stopPropagation();
                          void retryEpisode(item.episode);
                        }}
                      >
                        重试
                      </Button>
                    ) : null,
                  children: item.screenplayContent ? (
                    <XMarkdown>{item.screenplayContent}</XMarkdown>
                  ) : item.episode.status === 'FAILED' ? (
                    <Typography.Paragraph type="danger">
                      {item.episode.errorMessage || '生成失败，请稍后重试'}
                    </Typography.Paragraph>
                  ) : item.episode.status === 'PENDING_REVIEW' ||
                    item.episode.status === 'CONFIRMED' ? (
                    <Typography.Paragraph type="secondary">
                      历史拆剧记录仅供只读查看。
                    </Typography.Paragraph>
                  ) : (
                    <Progress
                      percent={item.episode.percentage}
                      status="active"
                    />
                  ),
                }))}
              />
            </div>
          ) : (
            <Empty description="暂无剧本" />
          )}
        </Drawer>
      </div>
    </PageContainer>
  );
};

export default VideoScriptDecompositionPage;
