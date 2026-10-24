import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { UploadFile, UploadProps } from 'antd';
import {
  App,
  Button,
  Col,
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
import { useEffect, useMemo, useRef, useState } from 'react';
import styles from './index.module.css';
import {
  pollingDelay,
  shouldPollVideoDecompositionBatch,
  VideoDecompositionQueries,
} from './queries';
import ScreenplayDrawer from './ScreenplayDrawer';

export { shouldPollVideoDecompositionBatch } from './queries';
export { buildCopyAllScreenplays } from './ScreenplayDrawer';

import PointEstimateButton from '@/components/PointEstimateButton';
import type { MediaUploadHandle } from '@/services/mediaUpload';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { statusText } from '@/utils/fieldDictionary';
import type {
  VideoDecompositionBatch,
  VideoDecompositionUpload,
  VideoUnderstandingModel,
} from './service';
import {
  createVideoDecompositionBatch,
  queryVideoDecompositionBatches,
  queryVideoUnderstandingModels,
  uploadEpisodeVideo,
} from './service';

type EpisodeUpload = {
  uid: string;
  episodeNo: number;
  fileName: string;
  size: number;
  mimeType?: string;
  status: 'READY';
  uploadStatus?: UploadFile['status'];
  storagePath?: string;
  uploadSessionToken?: string;
};

const MAX_VIDEO_SIZE = 1024 * 1024 * 1024;
const SUPPORTED_VIDEO_TYPES = [
  'video/mp4',
  'video/quicktime',
  'video/x-msvideo',
];
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
    uploadStatus: file.status,
    storagePath: (file.response as VideoDecompositionUpload | undefined)
      ?.storagePath,
    uploadSessionToken: (file.response as VideoDecompositionUpload | undefined)
      ?.uploadSessionToken,
  }));

const statusColor = (status: string) => {
  if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'red';
  if (status === 'SUCCEEDED' || status === 'CONFIRMED') return 'green';
  if (status.includes('ANALYZING') || status === 'RUNNING') return 'blue';
  return 'default';
};

const VideoScriptDecompositionPage = () => {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const uploadHandles = useRef(new Map<string, MediaUploadHandle>());
  const activeUploads = useRef(new Set<string>());
  const [batchName, setBatchName] = useState(
    buildDefaultVideoDecompositionBatchName,
  );
  const [modelId, setModelId] = useState<number>();
  const [models, setModels] = useState<VideoUnderstandingModel[]>([]);
  const [batchPage, setBatchPage] = useState(1);
  const [batchPageSize, setBatchPageSize] = useState(20);
  const [creating, setCreating] = useState(false);
  const [openBatchId, setOpenBatchId] = useState<number>();
  const batchesQuery = useQuery({
    queryKey: ['video-batches', batchPage, batchPageSize],
    queryFn: async ({ signal }) =>
      (
        await queryVideoDecompositionBatches(
          undefined,
          { current: batchPage, pageSize: batchPageSize },
          signal,
        )
      ).data,
    enabled: !openBatchId,
    refetchInterval: (query) =>
      query.state.fetchStatus !== 'idle'
        ? false
        : query.state.status === 'error' ||
            query.state.data?.data.some((batch) =>
              shouldPollVideoDecompositionBatch(batch.status),
            )
          ? pollingDelay(
              query.state.status === 'error' ? query.state.errorUpdateCount : 0,
            )
          : false,
  });
  const batches = batchesQuery.data?.data ?? [];
  const batchTotal = batchesQuery.data?.total ?? 0;
  const episodes = useMemo(() => normalizeEpisodes(files), [files]);

  useEffect(() => {
    const handles = uploadHandles.current;
    const active = activeUploads.current;
    return () => {
      active.clear();
      for (const handle of handles.values()) handle.cancel();
      handles.clear();
    };
  }, []);

  const removeUpload = (uid: string) => {
    activeUploads.current.delete(uid);
    uploadHandles.current.get(uid)?.cancel();
    uploadHandles.current.delete(uid);
    setFiles((current) => current.filter((file) => file.uid !== uid));
  };

  const uploadRecovery = (uid: string) => ({
    handle: uploadHandles.current.get(uid),
    onHandle: (handle: MediaUploadHandle) => {
      if (activeUploads.current.has(uid))
        uploadHandles.current.set(uid, handle);
      else handle.cancel();
    },
  });

  const updateUploadProgress = (uid: string, percent: number) => {
    setFiles((current) =>
      current.map((file) => (file.uid === uid ? { ...file, percent } : file)),
    );
  };

  const retryUpload = async (uid: string) => {
    const file = files.find((item) => item.uid === uid);
    if (!file?.originFileObj || file.status !== 'error') return;
    activeUploads.current.add(uid);
    setFiles((current) =>
      current.map((item) =>
        item.uid === uid
          ? { ...item, status: 'uploading', error: undefined }
          : item,
      ),
    );
    try {
      const response = await uploadEpisodeVideo(
        file.originFileObj,
        (percent) => updateUploadProgress(uid, percent),
        uploadRecovery(uid),
      );
      if (activeUploads.current.has(uid)) {
        setFiles((current) =>
          current.map((item) =>
            item.uid === uid
              ? {
                  ...item,
                  status: 'done',
                  percent: 100,
                  response: response.data,
                }
              : item,
          ),
        );
      }
    } catch (error) {
      if (activeUploads.current.has(uid)) {
        setFiles((current) =>
          current.map((item) =>
            item.uid === uid ? { ...item, status: 'error', error } : item,
          ),
        );
      }
    }
  };

  useEffect(() => {
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
  }, []);

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
    showUploadList: false,
    beforeUpload: (file) => {
      if (file.size > MAX_VIDEO_SIZE) {
        message.error(`${file.name} 超过单文件 1GB 上限`);
        return Upload.LIST_IGNORE;
      }
      if (file.type && !SUPPORTED_VIDEO_TYPES.includes(file.type)) {
        message.error(`${file.name} 暂不支持该视频格式`);
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: (options) => {
      const file = options.file as File & { uid: string };
      const uid = file.uid;
      activeUploads.current.add(uid);
      void uploadEpisodeVideo(
        file,
        (percent) => {
          updateUploadProgress(uid, percent);
          options.onProgress?.({ percent });
        },
        uploadRecovery(uid),
      ).then(
        (response) => {
          if (activeUploads.current.has(uid))
            options.onSuccess?.(response.data);
        },
        (error) => {
          if (activeUploads.current.has(uid)) options.onError?.(error as Error);
        },
      );
      return { abort: () => removeUpload(uid) };
    },
    onChange: ({ fileList }) => setFiles(fileList),
    onRemove: (file) => {
      removeUpload(file.uid);
      return true;
    },
  };

  return (
    <PageContainer title="视频拆剧">
      <div className={styles.page}>
        <div className={styles.flow}>
          上传并排序 <span>→</span> 开始生成剧本 <span>→</span> 查看与复制
        </div>
        <section className={styles.uploadPanel} aria-label="新建生成批次">
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
                title: '上传状态',
                render: (_, record) =>
                  record.uploadStatus === 'error' ? (
                    <span className={styles.failed}>上传失败，请重试</span>
                  ) : record.storagePath ? (
                    <span className={styles.succeeded}>上传完成</span>
                  ) : (
                    <Progress
                      size="small"
                      percent={Math.round(
                        files.find((file) => file.uid === record.uid)
                          ?.percent ?? 0,
                      )}
                    />
                  ),
              },
              {
                title: '操作',
                valueType: 'option',
                render: (_, record) => (
                  <Space>
                    {record.uploadStatus === 'error' && (
                      <Button
                        aria-label="重试上传"
                        title="重试上传"
                        icon={<ReloadOutlined />}
                        onClick={() => void retryUpload(record.uid)}
                      />
                    )}
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
                      onClick={() => removeUpload(record.uid)}
                    />
                  </Space>
                ),
              },
            ]}
            toolBarRender={() => [
              <PointEstimateButton
                operations={
                  modelId && canCreateVideoDecompositionBatch(episodes)
                    ? [
                        {
                          url: '/api/video-script-decomposition/batches',
                          data: {
                            name: batchName.trim(),
                            modelId,
                            videos: episodes.map((episode) => ({
                              fileName: episode.fileName,
                              storagePath: episode.storagePath || '',
                              uploadSessionToken:
                                episode.uploadSessionToken || '',
                              mimeType: episode.mimeType,
                              fileSize: episode.size,
                            })),
                          },
                        },
                      ]
                    : undefined
                }
                key="create"
                type="primary"
                icon={<PlayCircleOutlined />}
                disabled={
                  !canCreateVideoDecompositionBatch(episodes) ||
                  !modelId ||
                  creating
                }
                loading={creating}
                onClick={async () => {
                  setCreating(true);
                  try {
                    const response = await createVideoDecompositionBatch({
                      name:
                        batchName.trim() ||
                        buildDefaultVideoDecompositionBatchName(),
                      modelId,
                      videos: episodes.map((episode) => ({
                        fileName: episode.fileName,
                        storagePath: episode.storagePath || '',
                        uploadSessionToken: episode.uploadSessionToken || '',
                        mimeType: episode.mimeType,
                        fileSize: episode.size,
                      })),
                    });
                    message.success('剧本生成批次已创建');
                    setFiles([]);
                    uploadHandles.current.clear();
                    activeUploads.current.clear();
                    client.setQueryData(
                      ['video-batch', response.data.id],
                      response.data,
                    );
                    setOpenBatchId(response.data.id);
                    setBatchPage(1);
                    void client.invalidateQueries({
                      queryKey: ['video-batches'],
                      refetchType: 'none',
                    });
                  } catch (error) {
                    if (isPointEstimateCancelled(error)) return;
                    message.error(
                      error instanceof Error ? error.message : '创建批次失败',
                    );
                  } finally {
                    setCreating(false);
                  }
                }}
              >
                开始生成剧本
              </PointEstimateButton>,
            ]}
          />
          <div className={styles.uploadHint}>
            已上传 {episodes.filter((episode) => episode.storagePath).length} /{' '}
            {episodes.length} 个视频 · 提交顺序即集数顺序
          </div>
        </section>

        {batchesQuery.isError && (
          <div role="alert">
            批次加载失败{' '}
            <Button onClick={() => void batchesQuery.refetch()}>
              重试加载批次
            </Button>
          </div>
        )}
        <ProTable<VideoDecompositionBatch>
          rowKey="id"
          search={false}
          options={false}
          headerTitle="生成批次"
          loading={batchesQuery.isPending}
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
                  }}
                >
                  查看全部剧本
                </Button>
              ),
            },
          ]}
          pagination={{
            current: batchPage,
            pageSize: batchPageSize,
            total: batchTotal,
            onChange: (current, size) => {
              setBatchPage(current);
              setBatchPageSize(size);
            },
          }}
        />

        {openBatchId && (
          <ScreenplayDrawer
            key={openBatchId}
            batchId={openBatchId}
            onClose={() => setOpenBatchId(undefined)}
          />
        )}
      </div>
    </PageContainer>
  );
};

export default function VideoScriptDecomposition() {
  return (
    <VideoDecompositionQueries>
      <VideoScriptDecompositionPage />
    </VideoDecompositionQueries>
  );
}
