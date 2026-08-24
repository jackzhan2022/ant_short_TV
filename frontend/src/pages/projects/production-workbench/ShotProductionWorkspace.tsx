import {
  AudioOutlined,
  EditOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProCard,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Empty, Popconfirm, Space, Tabs, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  cancelAiVoiceTask,
  cancelShotComposeTask,
  bindAiVoiceResultToStoryboard,
  bindShotComposeResultToStoryboard,
  deleteAiVoiceResult,
  deleteAiVoiceTask,
  deleteShotComposeTask,
  deleteShotComposeResult,
  deleteStoryboardSubtitle,
  createAiVoiceTask,
  createShotComposeTask,
  createStoryboardSubtitle,
  regenerateAiVoiceTask,
  regenerateShotComposeTask,
  queryAiVoiceTasks,
  queryScriptWorkspace,
  queryShotComposeTasks,
  queryStoryboardSubtitles,
  saveAiVoiceResultAsMaterial,
  saveShotComposeResultAsMaterial,
  selectStoryboardSubtitle,
  updateStoryboardSubtitle,
  type AiVoiceTask,
  type CreateAiVoiceTaskValues,
  type CreateShotComposeTaskValues,
  type CreateStoryboardSubtitleValues,
  type StoryboardSubtitle,
  type ShotComposeTask,
  type ShotTaskStatus,
  type StoryboardShot,
  type UpdateStoryboardSubtitleValues,
} from './service';

type ShotProductionWorkspaceProps = {
  projectId: number;
};

const voiceTypeOptions = [
  { label: '旁白', value: 'NARRATION' },
  { label: '对白', value: 'DIALOGUE' },
];

const subtitleTypeOptions = [
  { label: '旁白字幕', value: 'NARRATION' },
  { label: '对白字幕', value: 'DIALOGUE' },
  { label: '混合字幕', value: 'MIXED' },
];

const statusText: Record<ShotTaskStatus, string> = {
  PENDING: '待执行',
  PROCESSING: '合成中',
  SUCCEEDED: '合成成功',
  FAILED: '合成失败',
  CANCELED: '已取消',
};

const statusColor: Record<ShotTaskStatus, string> = {
  PENDING: 'default',
  PROCESSING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELED: 'default',
};

const subtitleStatusText: Record<string, string> = {
  ACTIVE: '有效',
  DELETED: '已删除',
};

const subtitleStatusColor: Record<string, string> = {
  ACTIVE: 'green',
  DELETED: 'default',
};

const emptyCaptionSrc = 'data:text/vtt,WEBVTT';

const buildTaskParams = (params: Record<string, unknown>) => {
  const next: { status?: string; storyboardId?: number } = {};
  if (params.status) {
    next.status = String(params.status);
  }
  if (params.storyboardId) {
    next.storyboardId = Number(params.storyboardId);
  }
  return next;
};

const storyboardLabel = (item: StoryboardShot) =>
  `第${item.episodeNo}集 / 镜头${item.shotNo}`;

const formatSeconds = (value?: number | null) =>
  value == null ? '-' : `${Number(value).toFixed(2)}s`;

const parseSubtitleStyle = (value?: string | null) => {
  if (!value) {
    return {};
  }
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return {};
  }
};

const firstSegment = (subtitle: StoryboardSubtitle) =>
  subtitle.segments?.[0] || {
    text: subtitle.textContent,
    startTime: 0,
    endTime: 5,
  };

const SubtitleEditor = ({
  projectId,
  subtitle,
  onDone,
}: {
  projectId: number;
  subtitle: StoryboardSubtitle;
  onDone: () => Promise<void>;
}) => {
  const { message } = App.useApp();
  const segment = firstSegment(subtitle);

  return (
    <ModalForm<UpdateStoryboardSubtitleValues>
      title="编辑字幕"
      trigger={
        <Button type="link" icon={<EditOutlined />}>
          编辑
        </Button>
      }
      modalProps={{ destroyOnHidden: true }}
      initialValues={{
        textContent: subtitle.textContent,
        startTime: segment.startTime,
        endTime: segment.endTime,
        styleConfig: parseSubtitleStyle(subtitle.styleConfig),
      }}
      onFinish={async (values) => {
        await updateStoryboardSubtitle(projectId, subtitle.id, values);
        message.success('字幕已更新');
        await onDone();
        return true;
      }}
    >
      <ProFormTextArea
        name="textContent"
        label="字幕文本"
        fieldProps={{ autoSize: { minRows: 4, maxRows: 8 } }}
        rules={[
          { required: true, message: '请输入字幕文本' },
          { max: 2000, message: '字幕文本最多 2000 字' },
        ]}
      />
      <ProFormDigit
        name="startTime"
        label="开始时间"
        min={0}
        fieldProps={{ precision: 2, step: 0.25 }}
      />
      <ProFormDigit
        name="endTime"
        label="结束时间"
        min={0.01}
        fieldProps={{ precision: 2, step: 0.25 }}
      />
      <ProFormSelect
        name={['styleConfig', 'fontSize']}
        label="字号"
        options={[
          { label: '小', value: 'SMALL' },
          { label: '中', value: 'MEDIUM' },
          { label: '大', value: 'LARGE' },
        ]}
      />
      <ProFormSelect
        name={['styleConfig', 'position']}
        label="位置"
        options={[
          { label: '顶部', value: 'TOP' },
          { label: '中部', value: 'MIDDLE' },
          { label: '底部', value: 'BOTTOM' },
        ]}
      />
    </ModalForm>
  );
};

const ShotProductionWorkspace = ({
  projectId,
}: ShotProductionWorkspaceProps) => {
  const { message } = App.useApp();
  const voiceActionRef = useRef<ActionType | null>(null);
  const subtitleActionRef = useRef<ActionType | null>(null);
  const composeActionRef = useRef<ActionType | null>(null);
  const [storyboards, setStoryboards] = useState<StoryboardShot[]>([]);
  const [loadingStoryboards, setLoadingStoryboards] = useState(false);

  const storyboardOptions = useMemo(
    () =>
      storyboards.map((item) => ({
        label: storyboardLabel(item),
        value: item.id,
      })),
    [storyboards],
  );

  const loadStoryboards = async () => {
    setLoadingStoryboards(true);
    try {
      const response = await queryScriptWorkspace(projectId);
      setStoryboards(response.data?.storyboards || []);
    } catch {
      message.warning('分镜数据加载失败');
    } finally {
      setLoadingStoryboards(false);
    }
  };

  useEffect(() => {
    loadStoryboards();
  }, [projectId]);

  const reloadTasks = async () => {
    voiceActionRef.current?.reload();
    subtitleActionRef.current?.reload();
    composeActionRef.current?.reload();
    await loadStoryboards();
  };

  const voiceColumns = useMemo<ProColumns<AiVoiceTask>[]>(
    () => [
      { title: '任务ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: Object.fromEntries(
          Object.entries(statusText).map(([value, text]) => [value, { text }]),
        ),
        render: (_, record) => (
          <Tag color={statusColor[record.status]}>
            {statusText[record.status] || record.status}
          </Tag>
        ),
      },
      {
        title: '分镜',
        dataIndex: 'storyboardId',
        valueType: 'digit',
        width: 108,
        renderText: (value) => `#${value}`,
      },
      { title: '类型', dataIndex: 'voiceType', width: 100 },
      { title: '说话人', dataIndex: 'speakerName', width: 120, search: false },
      { title: '音色', dataIndex: 'voiceId', width: 144, search: false },
      {
        title: '文本内容',
        dataIndex: 'textContent',
        ellipsis: true,
        search: false,
      },
      {
        title: '音频结果',
        dataIndex: 'results',
        width: 260,
        search: false,
        render: (_, record) =>
          record.results.length ? (
            <Space vertical>
              {record.results.map((result) => (
                <Space key={result.id}>
                  <audio src={result.audioUrl} controls>
                    <track
                      kind="captions"
                      label="语音字幕"
                      src={emptyCaptionSrc}
                    />
                    </audio>
                  {result.selected && <Tag color="green">当前音频</Tag>}
                  {result.materialId && <Tag color="blue">已入素材库</Tag>}
                  <Button
                    type="link"
                    onClick={async () => {
                      await bindAiVoiceResultToStoryboard(projectId, result.id);
                      message.success('已设为当前音频');
                      await reloadTasks();
                    }}
                  >
                    设为当前
                  </Button>
                  {!result.materialId && (
                    <Button
                      type="link"
                      onClick={async () => {
                        await saveAiVoiceResultAsMaterial(projectId, result.id);
                        message.success('语音结果已保存到素材库');
                        await reloadTasks();
                      }}
                    >
                      保存素材
                    </Button>
                  )}
                  <Button type="link" href={result.audioUrl} target="_blank">
                    下载
                  </Button>
                  <Popconfirm
                    title="确认删除该语音结果？"
                    onConfirm={async () => {
                      await deleteAiVoiceResult(projectId, result.id);
                      message.success('语音结果已删除');
                      await reloadTasks();
                    }}
                  >
                    <Button type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ))}
            </Space>
          ) : (
            '-'
          ),
      },
      {
        title: '失败原因',
        dataIndex: 'errorMessage',
        ellipsis: true,
        search: false,
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        valueType: 'dateTime',
        search: false,
        width: 168,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        search: false,
        render: (_, record) => (
          <Space wrap>
            <Button
              type="link"
              disabled={
                record.status !== 'PENDING' && record.status !== 'PROCESSING'
              }
              onClick={async () => {
                await cancelAiVoiceTask(projectId, record.id);
                message.success('语音任务已取消');
                await reloadTasks();
              }}
            >
              取消
            </Button>
            <Button
              type="link"
              onClick={async () => {
                await regenerateAiVoiceTask(projectId, record.id);
                message.success('已重新发起语音合成');
                await reloadTasks();
              }}
            >
              重生成
            </Button>
            <Popconfirm
              title="确认删除该语音任务？"
              onConfirm={async () => {
                await deleteAiVoiceTask(projectId, record.id);
                message.success('语音任务已删除');
                await reloadTasks();
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [message, projectId, reloadTasks],
  );

  const composeColumns = useMemo<ProColumns<ShotComposeTask>[]>(
    () => [
      { title: '任务ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: Object.fromEntries(
          Object.entries(statusText).map(([value, text]) => [value, { text }]),
        ),
        render: (_, record) => (
          <Tag color={statusColor[record.status]}>
            {statusText[record.status] || record.status}
          </Tag>
        ),
      },
      {
        title: '分镜',
        dataIndex: 'storyboardId',
        valueType: 'digit',
        width: 108,
        renderText: (value) => `#${value}`,
      },
      { title: '语音结果', dataIndex: 'voiceResultId', width: 112, search: false },
      { title: '字幕', dataIndex: 'subtitleId', width: 96, search: false },
      {
        title: '单镜头结果',
        dataIndex: 'results',
        width: 320,
        search: false,
        render: (_, record) =>
          record.results.length ? (
            <Space vertical>
              {record.results.map((result) => (
                <Space key={result.id}>
                  <video
                    src={result.videoUrl}
                    poster={result.coverUrl || undefined}
                    controls
                    style={{ width: 160, background: '#000' }}
                  >
                    <track kind="captions" label="字幕" src={emptyCaptionSrc} />
                  </video>
                  {result.selected && <Tag color="green">当前单镜头</Tag>}
                  {result.materialId && <Tag color="blue">已入素材库</Tag>}
                  <Button
                    type="link"
                    onClick={async () => {
                      await saveShotComposeResultAsMaterial(projectId, result.id);
                      message.success('单镜头已保存到素材库');
                      await reloadTasks();
                    }}
                  >
                    保存素材
                  </Button>
                  <Button
                    type="link"
                    onClick={async () => {
                      await bindShotComposeResultToStoryboard(projectId, result.id);
                      message.success('已设为当前单镜头');
                      await reloadTasks();
                    }}
                  >
                    设为当前
                  </Button>
                  <Button type="link" href={result.videoUrl} target="_blank">
                    下载
                  </Button>
                  <Popconfirm
                    title="确认删除该单镜头结果？"
                    onConfirm={async () => {
                      await deleteShotComposeResult(projectId, result.id);
                      message.success('单镜头结果已删除');
                      await reloadTasks();
                    }}
                  >
                    <Button type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ))}
            </Space>
          ) : (
            '-'
          ),
      },
      {
        title: '失败原因',
        dataIndex: 'errorMessage',
        ellipsis: true,
        search: false,
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        valueType: 'dateTime',
        search: false,
        width: 168,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        search: false,
        render: (_, record) => (
          <Space wrap>
            <Button
              type="link"
              disabled={
                record.status !== 'PENDING' && record.status !== 'PROCESSING'
              }
              onClick={async () => {
                await cancelShotComposeTask(projectId, record.id);
                message.success('单镜头合成任务已取消');
                await reloadTasks();
              }}
            >
              取消
            </Button>
            <Button
              type="link"
              onClick={async () => {
                await regenerateShotComposeTask(projectId, record.id);
                message.success('已重新发起单镜头合成');
                await reloadTasks();
              }}
            >
              重生成
            </Button>
            <Popconfirm
              title="确认删除该单镜头合成任务？"
              onConfirm={async () => {
                await deleteShotComposeTask(projectId, record.id);
                message.success('单镜头合成任务已删除');
                await reloadTasks();
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [message, projectId, reloadTasks],
  );

  const subtitleColumns = useMemo<ProColumns<StoryboardSubtitle>[]>(
    () => [
      { title: '字幕ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '状态',
        dataIndex: 'status',
        render: (_, record) => (
          <Tag color={subtitleStatusColor[record.status] || 'default'}>
            {subtitleStatusText[record.status] || record.status}
          </Tag>
        ),
      },
      {
        title: '分镜',
        dataIndex: 'storyboardId',
        valueType: 'digit',
        width: 108,
        renderText: (value) => `#${value}`,
      },
      { title: '字幕类型', dataIndex: 'subtitleType', width: 120, search: false },
      {
        title: '字幕文本',
        dataIndex: 'textContent',
        ellipsis: true,
        search: false,
      },
      {
        title: '时间轴',
        search: false,
        render: (_, record) => {
          const segment = firstSegment(record);
          return `${formatSeconds(segment.startTime)} - ${formatSeconds(segment.endTime)}`;
        },
      },
      {
        title: '样式',
        dataIndex: 'styleConfig',
        search: false,
        render: (_, record) => {
          const styleConfig = parseSubtitleStyle(record.styleConfig);
          const fontSize = String(styleConfig.fontSize || '-');
          const position = String(styleConfig.position || '-');
          return `${fontSize} / ${position}`;
        },
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        valueType: 'dateTime',
        search: false,
        width: 168,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 240,
        search: false,
        render: (_, record) => (
          <Space wrap>
            <SubtitleEditor
              projectId={projectId}
              subtitle={record}
              onDone={reloadTasks}
            />
            <Button
              type="link"
              disabled={record.status !== 'ACTIVE'}
              onClick={async () => {
                await selectStoryboardSubtitle(projectId, record.id);
                message.success('已设为当前字幕');
                await reloadTasks();
              }}
            >
              设为当前
            </Button>
            <Button type="link" href={record.srtUrl || undefined} target="_blank">
              导出
            </Button>
            <Popconfirm
              title="确认删除该字幕？"
              onConfirm={async () => {
                await deleteStoryboardSubtitle(projectId, record.id);
                message.success('字幕已删除');
                await reloadTasks();
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [message, projectId, reloadTasks],
  );

  return (
    <ProCard
      title="语音字幕与单镜头"
      subTitle={`项目ID：${projectId}`}
      extra={
        <Space wrap>
          <ModalForm<CreateAiVoiceTaskValues>
            title="新建语音合成任务"
            trigger={
              <Button type="primary" icon={<AudioOutlined />}>
                新建语音任务
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            initialValues={{
              voiceType: 'NARRATION',
              voiceId: 'default-cn-voice',
              speed: 1,
              pitch: 1,
              volume: 1,
            }}
            onFinish={async (values) => {
              await createAiVoiceTask(projectId, values);
              message.success('语音合成任务已创建');
              await reloadTasks();
              return true;
            }}
          >
            <ProFormSelect
              name="storyboardId"
              label="关联分镜"
              options={storyboardOptions}
              fieldProps={{ loading: loadingStoryboards }}
              rules={[{ required: true, message: '请选择分镜' }]}
            />
            <ProFormSelect
              name="voiceType"
              label="语音类型"
              options={voiceTypeOptions}
              rules={[{ required: true, message: '请选择语音类型' }]}
            />
            <ProFormText name="speakerName" label="说话人" />
            <ProFormText
              name="voiceId"
              label="音色"
              rules={[{ required: true, message: '请输入音色ID' }]}
            />
            <ProFormTextArea
              name="textContent"
              label="合成文本"
              fieldProps={{ autoSize: { minRows: 4, maxRows: 8 } }}
              rules={[
                { required: true, message: '请输入合成文本' },
                { max: 2000, message: '合成文本最多 2000 字' },
              ]}
            />
            <ProFormDigit name="speed" label="语速" min={0.5} max={2} />
            <ProFormDigit name="pitch" label="音调" min={0.5} max={2} />
            <ProFormDigit name="volume" label="音量" min={0.1} max={2} />
          </ModalForm>
          <ModalForm<CreateStoryboardSubtitleValues>
            title="生成字幕"
            trigger={
              <Button icon={<EditOutlined />}>
                生成字幕
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            initialValues={{
              subtitleType: 'NARRATION',
              startTime: 0,
            }}
            onFinish={async (values) => {
              await createStoryboardSubtitle(projectId, values);
              message.success('字幕已生成');
              await reloadTasks();
              return true;
            }}
          >
            <ProFormSelect
              name="storyboardId"
              label="关联分镜"
              options={storyboardOptions}
              fieldProps={{ loading: loadingStoryboards }}
              rules={[{ required: true, message: '请选择分镜' }]}
            />
            <ProFormDigit name="voiceResultId" label="语音结果ID" min={1} />
            <ProFormSelect
              name="subtitleType"
              label="字幕类型"
              options={subtitleTypeOptions}
              rules={[{ required: true, message: '请选择字幕类型' }]}
            />
            <ProFormTextArea
              name="textContent"
              label="字幕文本"
              fieldProps={{ autoSize: { minRows: 4, maxRows: 8 } }}
              rules={[
                { required: true, message: '请输入字幕文本' },
                { max: 2000, message: '字幕文本最多 2000 字' },
              ]}
            />
            <ProFormDigit
              name="startTime"
              label="开始时间"
              min={0}
              fieldProps={{ precision: 2, step: 0.25 }}
            />
            <ProFormDigit
              name="endTime"
              label="结束时间"
              min={0.01}
              fieldProps={{ precision: 2, step: 0.25 }}
            />
          </ModalForm>
          <ModalForm<CreateShotComposeTaskValues>
            title="新建单镜头合成任务"
            trigger={
              <Button icon={<VideoCameraOutlined />}>
                开始单镜头合成
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            initialValues={{
              includeSubtitle: true,
              audioVolume: 1,
              outputFormat: 'mp4',
            }}
            onFinish={async (values) => {
              await createShotComposeTask(projectId, values);
              message.success('单镜头合成任务已创建');
              await reloadTasks();
              return true;
            }}
          >
            <ProFormSelect
              name="storyboardId"
              label="关联分镜"
              options={storyboardOptions}
              fieldProps={{ loading: loadingStoryboards }}
              rules={[{ required: true, message: '请选择分镜' }]}
            />
            <ProFormDigit name="voiceResultId" label="语音结果ID" min={1} />
            <ProFormDigit name="subtitleId" label="字幕ID" min={1} />
            <ProFormSwitch name="includeSubtitle" label="烧录字幕" />
            <ProFormDigit name="audioVolume" label="音频音量" min={0} max={2} />
            <ProFormSelect
              name="outputFormat"
              label="输出格式"
              options={[{ label: 'MP4', value: 'mp4' }]}
            />
          </ModalForm>
        </Space>
      }
    >
      {storyboards.length ? (
        <Tabs
          items={[
            {
              key: 'voice',
              label: '语音合成',
              children: (
                <ProTable<AiVoiceTask>
                  actionRef={voiceActionRef}
                  rowKey="id"
                  columns={voiceColumns}
                  scroll={{ x: 1400 }}
                  request={async (params) => {
                    const response = await queryAiVoiceTasks(
                      projectId,
                      buildTaskParams(params),
                    );
                    return { data: response.data, success: response.success };
                  }}
                />
              ),
            },
            {
              key: 'subtitle',
              label: '字幕编辑',
              children: (
                <ProTable<StoryboardSubtitle>
                  actionRef={subtitleActionRef}
                  rowKey="id"
                  columns={subtitleColumns}
                  scroll={{ x: 1400 }}
                  request={async (params) => {
                    const response = await queryStoryboardSubtitles(
                      projectId,
                      buildTaskParams(params),
                    );
                    return { data: response.data, success: response.success };
                  }}
                />
              ),
            },
            {
              key: 'compose',
              label: '单镜头合成',
              children: (
                <ProTable<ShotComposeTask>
                  actionRef={composeActionRef}
                  rowKey="id"
                  columns={composeColumns}
                  scroll={{ x: 1400 }}
                  request={async (params) => {
                    const response = await queryShotComposeTasks(
                      projectId,
                      buildTaskParams(params),
                    );
                    return { data: response.data, success: response.success };
                  }}
                />
              ),
            },
          ]}
        />
      ) : (
        <Empty description="暂无分镜，请先完成剧本分镜拆解" />
      )}
    </ProCard>
  );
};

export default ShotProductionWorkspace;
