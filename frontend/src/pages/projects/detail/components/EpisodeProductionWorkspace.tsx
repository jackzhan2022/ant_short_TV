import {
  DownloadOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProCard,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Descriptions, Empty, Flex, Popconfirm, Select, Space, Tabs, Tag } from 'antd';
import type { TabsProps } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  cancelEpisodeComposeTask,
  createEpisodeComposeTask,
  deleteEpisodeComposeTask,
  deleteEpisodeVideoVersion,
  downloadEpisodeVideoVersion,
  queryEpisodeComposeTasks,
  queryEpisodeExportRecords,
  queryEpisodeVideoVersions,
  queryScriptWorkspace,
  regenerateEpisodeComposeTask,
  renameEpisodeVideoVersion,
  saveEpisodeVideoMaterial,
  setCurrentEpisodeVideoVersion,
  type CreateEpisodeComposeTaskValues,
  type EpisodeComposeTask,
  type EpisodeComposeTaskStatus,
  type EpisodeExportRecord,
  type EpisodeVideoVersion,
  type StoryboardShot,
} from './service';

type EpisodeProductionWorkspaceProps = {
  projectId: number;
};

const taskStatusText: Record<EpisodeComposeTaskStatus, string> = {
  PENDING_VALIDATION: '待校验',
  VALIDATION_FAILED: '校验失败',
  PENDING: '待执行',
  PROCESSING: '合成中',
  SUCCEEDED: '合成成功',
  FAILED: '合成失败',
  CANCELED: '已取消',
};

const taskStatusColor: Record<EpisodeComposeTaskStatus, string> = {
  PENDING_VALIDATION: 'default',
  VALIDATION_FAILED: 'error',
  PENDING: 'default',
  PROCESSING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELED: 'default',
};

const exportTypeText: Record<string, string> = {
  DOWNLOAD: '下载',
  SAVE_MATERIAL: '保存素材',
};

const formatSeconds = (value?: number | null) =>
  value == null || !Number.isFinite(Number(value))
    ? '-'
    : `${Number(value).toFixed(2)}s`;

const formatFileSize = (value?: number | null) => {
  if (value == null || !Number.isFinite(Number(value))) {
    return '-';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  return `${(value / 1024).toFixed(1)} KB`;
};

const EpisodeProductionWorkspace = ({ projectId }: EpisodeProductionWorkspaceProps) => {
  const { message } = App.useApp();
  const access = useAccess();
  const taskActionRef = useRef<ActionType | null>(null);
  const versionActionRef = useRef<ActionType | null>(null);
  const exportActionRef = useRef<ActionType | null>(null);
  const [storyboards, setStoryboards] = useState<StoryboardShot[]>([]);
  const [selectedEpisodeNo, setSelectedEpisodeNo] = useState(1);

  const episodeOptions = useMemo(() => {
    const episodeNos = Array.from(
      new Set(storyboards.map((item) => item.episodeNo || 1)),
    ).sort((left, right) => left - right);
    return (episodeNos.length ? episodeNos : [1]).map((episodeNo) => ({
      label: `第${episodeNo}集`,
      value: episodeNo,
    }));
  }, [storyboards]);

  const selectedStoryboards = useMemo(
    () =>
      storyboards
        .filter((item) => (item.episodeNo || 1) === selectedEpisodeNo)
        .sort((left, right) => (left.shotNo || 0) - (right.shotNo || 0)),
    [selectedEpisodeNo, storyboards],
  );

  const currentShotCount = selectedStoryboards.filter(
    (item) => item.currentShotVideoUrl || item.currentVideoUrl,
  ).length;
  const canViewEpisodeCompose = Boolean(access.canViewEpisodeComposeTasks);
  const canViewEpisodeVersion = Boolean(access.canViewEpisodeVersions);

  const loadStoryboards = async () => {
    try {
      const response = await queryScriptWorkspace(projectId);
      const nextStoryboards = response.data?.storyboards || [];
      setStoryboards(nextStoryboards);
      if (!nextStoryboards.some((item) => item.episodeNo === selectedEpisodeNo)) {
        setSelectedEpisodeNo(nextStoryboards[0]?.episodeNo || 1);
      }
    } catch {
      message.warning('分镜数据加载失败');
    }
  };

  const reload = async () => {
    if (canViewEpisodeCompose) {
      taskActionRef.current?.reload();
    }
    if (canViewEpisodeVersion) {
      versionActionRef.current?.reload();
      exportActionRef.current?.reload();
    }
    await loadStoryboards();
  };

  useEffect(() => {
    loadStoryboards();
  }, [projectId]);

  const taskColumns = useMemo<ProColumns<EpisodeComposeTask>[]>(
    () => [
      { title: '任务ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '任务名称',
        dataIndex: 'taskName',
        ellipsis: true,
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: Object.fromEntries(
          Object.entries(taskStatusText).map(([value, text]) => [value, { text }]),
        ),
        render: (_, record) => (
          <Tag color={taskStatusColor[record.status]}>
            {taskStatusText[record.status] || record.status}
          </Tag>
        ),
      },
      {
        title: '分镜数',
        dataIndex: 'storyboardCount',
        width: 96,
        search: false,
      },
      {
        title: '预计时长',
        dataIndex: 'totalDurationSeconds',
        width: 110,
        search: false,
        renderText: (value) => formatSeconds(Number(value)),
      },
      {
        title: '成片版本',
        dataIndex: 'videoVersion',
        width: 180,
        search: false,
        render: (_, record) =>
          record.videoVersion ? (
            <Space>
              <Tag color={record.videoVersion.current ? 'green' : 'blue'}>
                v{record.videoVersion.versionNo}
              </Tag>
              {record.videoVersion.versionName}
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
        width: 168,
        search: false,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 240,
        render: (_, record) => {
          const canCancel = ['PENDING', 'PROCESSING'].includes(record.status);
          const hasActions =
            access.canCreateEpisodeComposeTasks ||
            access.canCancelEpisodeComposeTasks ||
            access.canDeleteEpisodeComposeTasks;
          if (!hasActions) {
            return '-';
          }
          return (
            <Space wrap>
              {access.canCreateEpisodeComposeTasks && (
                <Button
                  type="link"
                  icon={<ReloadOutlined />}
                  onClick={async () => {
                    await regenerateEpisodeComposeTask(projectId, record.id);
                    message.success('已重新发起单集合成');
                    await reload();
                  }}
                >
                  重合成
                </Button>
              )}
              {access.canCancelEpisodeComposeTasks && (
                <Popconfirm
                  title="确认取消该合成任务？"
                  onConfirm={async () => {
                    await cancelEpisodeComposeTask(projectId, record.id);
                    message.success('合成任务已取消');
                    await reload();
                  }}
                >
                  <Button type="link" disabled={!canCancel}>
                    取消
                  </Button>
                </Popconfirm>
              )}
              {access.canDeleteEpisodeComposeTasks && (
                <Popconfirm
                  title="确认删除该合成任务？"
                  onConfirm={async () => {
                    await deleteEpisodeComposeTask(projectId, record.id);
                    message.success('合成任务已删除');
                    await reload();
                  }}
                >
                  <Button type="link" danger>
                    删除
                  </Button>
                </Popconfirm>
              )}
            </Space>
          );
        },
      },
    ],
    [access, message, projectId],
  );

  const versionColumns = useMemo<ProColumns<EpisodeVideoVersion>[]>(
    () => [
      { title: '版本', dataIndex: 'versionNo', width: 88, renderText: (value) => `v${value}` },
      {
        title: '版本名称',
        dataIndex: 'versionName',
        ellipsis: true,
      },
      {
        title: '当前',
        dataIndex: 'current',
        width: 88,
        search: false,
        render: (_, record) => (record.current ? <Tag color="green">当前</Tag> : '-'),
      },
      {
        title: '预览',
        dataIndex: 'videoUrl',
        width: 220,
        search: false,
        render: (_, record) => (
          <video
            src={record.videoUrl}
            poster={record.coverUrl || undefined}
            controls
            style={{ width: 180, aspectRatio: '9 / 16', background: '#000' }}
          >
            <track kind="captions" label="暂无字幕" />
          </video>
        ),
      },
      {
        title: '时长',
        dataIndex: 'durationSeconds',
        width: 100,
        search: false,
        renderText: (value) => formatSeconds(Number(value)),
      },
      {
        title: '分辨率',
        search: false,
        render: (_, record) =>
          record.width && record.height ? `${record.width}x${record.height}` : '-',
      },
      {
        title: '文件大小',
        dataIndex: 'fileSize',
        width: 110,
        search: false,
        renderText: (value) => formatFileSize(Number(value)),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 360,
        render: (_, record) => {
          const hasActions =
            access.canSetCurrentEpisodeVersion ||
            access.canDownloadEpisodeVersions ||
            access.canSaveEpisodeVersions ||
            access.canDeleteEpisodeVersions;
          if (!hasActions) {
            return '-';
          }
          return (
            <Space wrap>
              {access.canSetCurrentEpisodeVersion && (
                <Button
                  type="link"
                  icon={<PlayCircleOutlined />}
                  disabled={record.current}
                  onClick={async () => {
                    await setCurrentEpisodeVideoVersion(projectId, record.id);
                    message.success('已设置为当前成片');
                    await reload();
                  }}
                >
                  设为当前
                </Button>
              )}
              {access.canSetCurrentEpisodeVersion && (
                <ModalForm<{ versionName: string }>
                  title="重命名成片版本"
                  trigger={
                    <Button type="link" icon={<EditOutlined />}>
                      重命名
                    </Button>
                  }
                  modalProps={{ destroyOnHidden: true }}
                  initialValues={{ versionName: record.versionName }}
                  onFinish={async (values) => {
                    await renameEpisodeVideoVersion(projectId, record.id, values);
                    message.success('成片版本已重命名');
                    await reload();
                    return true;
                  }}
                >
                  <ProFormText
                    name="versionName"
                    label="版本名称"
                    rules={[{ required: true, message: '请输入版本名称' }]}
                  />
                </ModalForm>
              )}
              {access.canDownloadEpisodeVersions && (
                <Button
                  type="link"
                  icon={<DownloadOutlined />}
                  onClick={async () => {
                    await downloadEpisodeVideoVersion(projectId, record.id);
                    message.success('已记录下载操作');
                    await reload();
                  }}
                >
                  下载
                </Button>
              )}
              {access.canSaveEpisodeVersions && (
                <Button
                  type="link"
                  icon={<SaveOutlined />}
                  onClick={async () => {
                    await saveEpisodeVideoMaterial(projectId, record.id);
                    message.success('成片已保存到素材库');
                    await reload();
                  }}
                >
                  保存素材
                </Button>
              )}
              {access.canDeleteEpisodeVersions && (
                <Popconfirm
                  title="确认删除该成片版本？"
                  description={record.current ? '当前版本删除会被后端阻止，请先切换当前版本。' : undefined}
                  onConfirm={async () => {
                    await deleteEpisodeVideoVersion(projectId, record.id);
                    message.success('成片版本已删除');
                    await reload();
                  }}
                >
                  <Button type="link" danger>
                    删除
                  </Button>
                </Popconfirm>
              )}
            </Space>
          );
        },
      },
    ],
    [access, message, projectId],
  );

  const exportColumns = useMemo<ProColumns<EpisodeExportRecord>[]>(
    () => [
      { title: '记录ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '类型',
        dataIndex: 'exportType',
        renderText: (value) => exportTypeText[String(value)] || String(value),
      },
      {
        title: '状态',
        dataIndex: 'exportStatus',
        render: (_, record) => (
          <Tag color={record.exportStatus === 'SUCCESS' ? 'green' : 'red'}>
            {record.exportStatus === 'SUCCESS' ? '成功' : '失败'}
          </Tag>
        ),
      },
      { title: '版本ID', dataIndex: 'videoVersionId', width: 100, search: false },
      { title: '文件名', dataIndex: 'fileName', ellipsis: true, search: false },
      {
        title: '文件大小',
        dataIndex: 'fileSize',
        width: 110,
        search: false,
        renderText: (value) => formatFileSize(Number(value)),
      },
      {
        title: '导出时间',
        dataIndex: 'createdAt',
        valueType: 'dateTime',
        width: 168,
        search: false,
      },
    ],
    [],
  );
  const tabItems = useMemo<NonNullable<TabsProps['items']>>(() => {
    const items: NonNullable<TabsProps['items']> = [];
    if (canViewEpisodeCompose) {
      items.push({
        key: 'tasks',
        label: '合成任务',
        children: (
          <ProTable<EpisodeComposeTask>
            actionRef={taskActionRef}
            rowKey="id"
            columns={taskColumns}
            params={{ episodeNo: selectedEpisodeNo }}
            scroll={{ x: 1200 }}
            request={async (params) => {
              const response = await queryEpisodeComposeTasks(projectId, {
                episodeNo: Number(params.episodeNo) || selectedEpisodeNo,
                ...(params.status
                  ? { status: params.status as EpisodeComposeTaskStatus }
                  : {}),
              });
              return { data: response.data, success: response.success };
            }}
          />
        ),
      });
    }
    if (canViewEpisodeVersion) {
      items.push(
        {
          key: 'versions',
          label: '成片版本',
          children: (
            <ProTable<EpisodeVideoVersion>
              actionRef={versionActionRef}
              rowKey="id"
              search={false}
              columns={versionColumns}
              params={{ episodeNo: selectedEpisodeNo }}
              scroll={{ x: 1200 }}
              request={async () => {
                const response = await queryEpisodeVideoVersions(
                  projectId,
                  selectedEpisodeNo,
                );
                return { data: response.data, success: response.success };
              }}
            />
          ),
        },
        {
          key: 'exports',
          label: '导出记录',
          children: (
            <ProTable<EpisodeExportRecord>
              actionRef={exportActionRef}
              rowKey="id"
              columns={exportColumns}
              params={{ episodeNo: selectedEpisodeNo }}
              scroll={{ x: 1000 }}
              request={async () => {
                const response = await queryEpisodeExportRecords(projectId, {
                  episodeNo: selectedEpisodeNo,
                });
                return { data: response.data, success: response.success };
              }}
            />
          ),
        },
      );
    }
    return items;
  }, [
    canViewEpisodeCompose,
    canViewEpisodeVersion,
    exportColumns,
    projectId,
    selectedEpisodeNo,
    taskColumns,
    versionColumns,
  ]);

  return (
    <ProCard
      title="单集合成与成片管理"
      subTitle={`项目ID：${projectId}`}
      extra={
        access.canCreateEpisodeComposeTasks ? (
          <ModalForm<CreateEpisodeComposeTaskValues>
            title="发起单集合成"
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                发起单集合成
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            initialValues={{
              episodeNo: selectedEpisodeNo,
              taskName: `第${selectedEpisodeNo}集成片合成`,
              versionName: `第${selectedEpisodeNo}集 成片 v1`,
              outputFormat: 'mp4',
              quality: 'STANDARD',
              generateCover: true,
            }}
            onFinish={async (values) => {
              await createEpisodeComposeTask(projectId, values);
              message.success('单集合成任务已创建');
              await reload();
              return true;
            }}
          >
            <ProFormSelect
              name="episodeNo"
              label="单集"
              options={episodeOptions}
              rules={[{ required: true, message: '请选择单集' }]}
            />
            <ProFormText name="taskName" label="任务名称" />
            <ProFormText name="versionName" label="版本名称" />
            <ProFormSelect
              name="outputFormat"
              label="输出格式"
              options={[{ label: 'MP4', value: 'mp4' }]}
            />
            <ProFormSelect
              name="quality"
              label="视频质量"
              options={[
                { label: '标准', value: 'STANDARD' },
                { label: '高清', value: 'HD' },
              ]}
            />
            <ProFormSwitch name="generateCover" label="生成封面" />
          </ModalForm>
        ) : null
      }
    >
      {selectedStoryboards.length ? (
        <Flex vertical gap="large" style={{ width: '100%' }}>
          <Space align="center">
            <span>单集</span>
            <Select
              aria-label="选择单集"
              style={{ width: 160 }}
              value={selectedEpisodeNo}
              options={episodeOptions}
              onChange={(value) => {
                setSelectedEpisodeNo(value);
              }}
            />
          </Space>
          <Descriptions
            bordered
            column={{ xs: 1, md: 4 }}
            items={[
              { label: '当前单集', children: `第${selectedEpisodeNo}集` },
              { label: '分镜数', children: selectedStoryboards.length },
              { label: '可用单镜头', children: currentShotCount },
              {
                label: '预计时长',
                children: formatSeconds(
                  selectedStoryboards.reduce(
                    (total, item) => total + (item.durationSeconds || 5),
                    0,
                  ),
                ),
              },
            ]}
          />
          <Tabs items={tabItems} />
        </Flex>
      ) : (
        <Empty description="暂无可合成分镜，请先完成单镜头合成" />
      )}
    </ProCard>
  );
};

export default EpisodeProductionWorkspace;
