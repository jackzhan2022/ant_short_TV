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
  Row,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  confirmVideoDecompositionDraft,
  createVideoDecompositionBatch,
  queryVideoDecompositionEpisode,
  queryVideoDecompositionBatches,
  retryVideoDecompositionEpisode,
  updateVideoDecompositionDraft,
  uploadEpisodeVideo,
} from './service';
import type {
  VideoDecompositionBatch,
  VideoDecompositionEpisode,
  VideoDecompositionEpisodeDetail,
  VideoDecompositionUpload,
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
const SUPPORTED_VIDEO_TYPES = ['video/mp4', 'video/quicktime', 'video/x-msvideo'];

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
  const { message, modal } = App.useApp();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [projectId, setProjectId] = useState<number>();
  const [batchName, setBatchName] = useState('');
  const [modelId, setModelId] = useState<number>();
  const [batches, setBatches] = useState<VideoDecompositionBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedEpisodeId, setSelectedEpisodeId] = useState<number>();
  const [detail, setDetail] = useState<VideoDecompositionEpisodeDetail>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [draftContent, setDraftContent] = useState('');
  const [draftSaving, setDraftSaving] = useState(false);

  const episodes = useMemo(() => normalizeEpisodes(files), [files]);
  const canCreateBatch =
    Boolean(projectId && batchName.trim() && episodes.length) &&
    episodes.every((episode) => episode.storagePath);

  const loadBatches = async () => {
    const response = await queryVideoDecompositionBatches(projectId);
    setBatches(response.data ?? []);
  };

  const loadEpisodeDetail = async (episodeId: number) => {
    setDetailLoading(true);
    try {
      const response = await queryVideoDecompositionEpisode(episodeId);
      setDetail(response.data);
      setDraftContent(response.data?.draftContent ?? '');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    loadBatches().catch(() => undefined);
  }, [projectId]);

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
    await loadEpisodeDetail(episode.id);
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
    modal.confirm({
      title: `确认导入第 ${detail.episode.episodeNo} 集剧本？`,
      content:
        '确认后会创建一个来源为 VIDEO_IMPORT 的剧本版本，不会静默覆盖当前剧本内容。',
      okText: '确认导入',
      cancelText: '取消',
      onOk: async () => {
        await confirmVideoDecompositionDraft(
          detail.episode.id,
          draftContent,
          detail.episode.draftVersion,
          detail.currentScriptVersionId,
        );
        message.success('已创建视频导入剧本版本');
        await loadEpisodeDetail(detail.episode.id);
        await loadBatches();
      },
    });
  };

  const uploadProps: UploadProps = {
    accept: 'video/mp4,video/quicktime,video/x-msvideo,.mp4,.mov,.avi',
    multiple: true,
    fileList: files,
    beforeUpload: (file) => {
      if (!projectId) {
        message.error('请先填写项目 ID');
        return Upload.LIST_IGNORE;
      }
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
        if (!projectId) {
          throw new Error('请先填写项目 ID');
        }
        const response = await uploadEpisodeVideo(
          projectId,
          options.file as File,
        );
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
            <Col xs={24} md={8}>
              <Form.Item label="项目 ID" required>
                <InputNumber
                  min={1}
                  value={projectId}
                  onChange={(value) => setProjectId(value ?? undefined)}
                  style={{ width: '100%' }}
                  placeholder="选择或输入关联项目"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="批次名称" required>
                <Input
                  value={batchName}
                  onChange={(event) => setBatchName(event.target.value)}
                  placeholder="例如：第 1 季成片拆剧"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="视频理解模型">
                <InputNumber
                  min={1}
                  value={modelId}
                  onChange={(value) => setModelId(value ?? undefined)}
                  style={{ width: '100%' }}
                  placeholder="默认 qwen3.7-plus"
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
              render: (_, record) => <Tag color="blue">第 {record.episodeNo} 集</Tag>,
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
                if (!projectId || !canCreateBatch) {
                  message.warning('请填写项目信息并等待视频上传完成');
                  return;
                }
                setLoading(true);
                try {
                  await createVideoDecompositionBatch({
                    projectId,
                    name: batchName,
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
                  setBatchName('');
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
              title: '项目 ID',
              dataIndex: 'projectId',
              width: 100,
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
                  {record.status}
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
                      第 {episode.episodeNo} 集 · {episode.status}
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
                  disabled={!draftContent.trim() || detail.episode.status === 'CONFIRMED'}
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
                        {detail.episode.status}
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
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无解析结果" />
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
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无原始响应" />
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
                        {record.status}
                      </Tag>
                    ),
                  },
                  { title: '请求 ID', dataIndex: 'providerRequestId', ellipsis: true },
                  { title: '错误', dataIndex: 'errorMessage', ellipsis: true },
                ]}
              />
            </div>
          ) : null}
        </Drawer>
      </div>
    </PageContainer>
  );
};

export default VideoScriptDecompositionPage;
