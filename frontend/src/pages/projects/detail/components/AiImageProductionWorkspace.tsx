import {
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  PictureOutlined,
  ReloadOutlined,
  SaveOutlined,
  StarOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProCard,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { App, Button, Descriptions, Flex, Image, Popconfirm, Space, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  cancelAiImageTask,
  createAiImageTask,
  deleteAiImageResult,
  deleteAiImageTask,
  getAiImageResultDownloadUrl,
  queryAiImageTask,
  queryAiImageTasks,
  regenerateAiImageTask,
  saveAiImageResultAsMaterial,
  selectAiImageResult,
  type AiImageTask,
  type CreateAiImageTaskValues,
} from './service';

type AiImageProductionWorkspaceProps = {
  projectId: number;
};

const taskTypeOptions = [
  { label: '角色图', value: 'CHARACTER' },
  { label: '场景图', value: 'SCENE' },
  { label: '分镜首帧图', value: 'STORYBOARD_FIRST_FRAME' },
];

const targetTypeOptions = [
  { label: '角色', value: 'CHARACTER' },
  { label: '场景', value: 'SCENE' },
  { label: '分镜', value: 'STORYBOARD' },
];

const aspectRatioOptions = [
  { label: '1:1', value: '1:1' },
  { label: '3:4', value: '3:4' },
  { label: '4:3', value: '4:3' },
  { label: '9:16', value: '9:16' },
  { label: '16:9', value: '16:9' },
];

const statusText: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '生成中',
  SUCCESS: '生成成功',
  FAILED: '生成失败',
  CANCELED: '已取消',
};

const statusColor: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  CANCELED: 'warning',
};

const AiImageProductionWorkspace = ({
  projectId,
}: AiImageProductionWorkspaceProps) => {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | null>(null);
  const [detail, setDetail] = useState<AiImageTask>();
  const [hasActiveTasks, setHasActiveTasks] = useState(false);

  const reload = () => {
    actionRef.current?.reload();
  };

  const isActiveTask = (status?: string) =>
    status === 'PENDING' || status === 'RUNNING';

  const refreshDetail = useCallback(
    async (taskId: number) => {
      const response = await queryAiImageTask(projectId, taskId);
      setDetail(response.data);
      return response.data;
    },
    [projectId],
  );

  useEffect(() => {
    const shouldPoll = hasActiveTasks || isActiveTask(detail?.status);
    if (!shouldPoll) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      reload();
      if (detail && isActiveTask(detail.status)) {
        refreshDetail(detail.id);
      }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [detail, hasActiveTasks, refreshDetail]);

  const columns = useMemo<ProColumns<AiImageTask>[]>(
    () => [
      { title: '任务ID', dataIndex: 'id', width: 88, search: false },
      {
        title: '任务类型',
        dataIndex: 'taskType',
        valueEnum: Object.fromEntries(
          taskTypeOptions.map((item) => [item.value, { text: item.label }]),
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: Object.fromEntries(
          Object.entries(statusText).map(([value, text]) => [value, { text }]),
        ),
        render: (_, record) => (
          <Tag color={statusColor[record.status] || 'default'}>
            {statusText[record.status] || record.status}
          </Tag>
        ),
      },
      { title: '关联对象', dataIndex: 'targetId', width: 120 },
      { title: '服务商', dataIndex: 'providerCode', width: 120, search: false },
      { title: '模型', dataIndex: 'model', width: 160, search: false },
      { title: '比例', dataIndex: 'aspectRatio', width: 88, search: false },
      {
        title: '缩略图',
        dataIndex: 'results',
        width: 96,
        search: false,
        render: (_, record) =>
          record.results[0] ? (
            <Image
              src={record.results[0].thumbnailUrl || record.results[0].imageUrl}
              width={56}
              height={72}
              alt="图片结果"
            />
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
        fixed: 'right',
        render: (_, record) => (
          <Space>
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={async () => {
                await refreshDetail(record.id);
              }}
            >
              查看
            </Button>
            <Button
              type="link"
              icon={<ReloadOutlined />}
              onClick={async () => {
                await regenerateAiImageTask(projectId, record.id);
                message.success('已创建重新生成任务');
                setHasActiveTasks(true);
                reload();
              }}
            >
              重新生成
            </Button>
            <Button
              type="link"
              disabled={!['PENDING', 'RUNNING'].includes(record.status)}
              onClick={async () => {
                await cancelAiImageTask(projectId, record.id);
                message.success('图片任务已取消');
                reload();
              }}
            >
              取消
            </Button>
            <Popconfirm
              title="确认删除该图片任务记录？"
              onConfirm={async () => {
                await deleteAiImageTask(projectId, record.id);
                message.success('图片任务记录已删除');
                reload();
              }}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [message, projectId, refreshDetail],
  );

  return (
    <ProCard
      title="图片生产"
      subTitle={`项目ID：${projectId}`}
      extra={
        <ModalForm<CreateAiImageTaskValues>
          title="新建图片生成任务"
          trigger={
            <Button type="primary" icon={<PictureOutlined />}>
              新建图片任务
            </Button>
          }
          modalProps={{ destroyOnHidden: true }}
          initialValues={{
            taskType: 'STORYBOARD_FIRST_FRAME',
            targetType: 'STORYBOARD',
            aspectRatio: '9:16',
            imageCount: 1,
            quality: 'STANDARD',
          }}
          onFinish={async (values) => {
            await createAiImageTask(projectId, values);
            message.success('图片生成任务已创建');
            setHasActiveTasks(true);
            reload();
            return true;
          }}
        >
          <ProFormSelect
            name="taskType"
            label="任务类型"
            options={taskTypeOptions}
            rules={[{ required: true, message: '请选择任务类型' }]}
          />
          <ProFormSelect
            name="targetType"
            label="关联对象"
            options={targetTypeOptions}
            rules={[{ required: true, message: '请选择关联对象' }]}
          />
          <ProFormDigit
            name="targetId"
            label="关联对象ID"
            min={1}
            rules={[{ required: true, message: '请输入关联对象ID' }]}
          />
          <ProFormTextArea
            name="prompt"
            label="正向提示词"
            fieldProps={{ autoSize: { minRows: 4, maxRows: 8 } }}
            rules={[{ required: true, message: '请输入图片生成提示词' }]}
          />
          <ProFormTextArea name="negativePrompt" label="负向提示词" />
          <ProFormSelect
            name="aspectRatio"
            label="图片比例"
            options={aspectRatioOptions}
            rules={[{ required: true, message: '请选择图片比例' }]}
          />
          <ProFormDigit
            name="imageCount"
            label="生成数量"
            min={1}
            max={4}
            rules={[{ required: true, message: '请输入生成数量' }]}
          />
          <ProFormText name="style" label="风格" />
          <ProFormSelect
            name="quality"
            label="清晰度"
            options={[
              { label: '标准', value: 'STANDARD' },
              { label: '高清', value: 'HD' },
            ]}
          />
          <ProFormText name="seed" label="随机种子" />
        </ModalForm>
      }
    >
      <ProTable<AiImageTask>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        scroll={{ x: 1400 }}
        request={async (params) => {
          const response = await queryAiImageTasks(projectId, {
            taskType: params.taskType as string | undefined,
            status: params.status as string | undefined,
          });
          setHasActiveTasks(response.data.some((task) => isActiveTask(task.status)));
          return { data: response.data, success: response.success };
        }}
      />
      <ModalForm
        title="图片任务详情"
        open={Boolean(detail)}
        submitter={false}
        modalProps={{
          destroyOnHidden: true,
          onCancel: () => setDetail(undefined),
        }}
      >
        {detail && (
          <Flex vertical gap="large" style={{ width: '100%' }}>
            <Descriptions
              bordered
              column={2}
              items={[
                { label: '任务ID', children: detail.id },
                {
                  label: '状态',
                  children: (
                    <Tag color={statusColor[detail.status]}>
                      {statusText[detail.status]}
                    </Tag>
                  ),
                },
                { label: '任务类型', children: detail.taskType },
                { label: '关联对象', children: `${detail.targetType} #${detail.targetId}` },
                { label: '服务商', children: detail.providerCode },
                { label: '模型', children: detail.model },
                { label: '比例', children: detail.aspectRatio },
                { label: '数量', children: detail.imageCount },
                { label: '正向提示词', span: 'filled', children: detail.prompt },
                {
                  label: '负向提示词',
                  span: 'filled',
                  children: detail.negativePrompt || '-',
                },
                {
                  label: '失败原因',
                  span: 'filled',
                  children: detail.errorMessage || '-',
                },
              ]}
            />
            <Image.PreviewGroup>
              <Space wrap>
                {detail.results.map((result) => (
                  <ProCard
                    key={result.id}
                    title={`结果 ${result.id}`}
                    style={{ width: 220 }}
                    actions={[
                      <Button
                        key="download"
                        type="link"
                        icon={<DownloadOutlined />}
                        href={getAiImageResultDownloadUrl(projectId, result.id)}
                      >
                        下载
                      </Button>,
                      <Button
                        key="save"
                        type="link"
                        icon={<SaveOutlined />}
                        onClick={async () => {
                          await saveAiImageResultAsMaterial(projectId, result.id);
                          message.success('图片已保存到素材库');
                          await refreshDetail(detail.id);
                          reload();
                        }}
                      >
                        保存素材
                      </Button>,
                      <Button
                        key="select"
                        type="link"
                        icon={<StarOutlined />}
                        onClick={async () => {
                          await selectAiImageResult(projectId, result.id);
                          message.success('已设为主图');
                          await refreshDetail(detail.id);
                          reload();
                        }}
                      >
                        设为主图
                      </Button>,
                      <Popconfirm
                        key="delete"
                        title="确认删除该图片结果？"
                        description="已被主图引用时会保留并提示引用保护。"
                        onConfirm={async () => {
                          try {
                            await deleteAiImageResult(projectId, result.id);
                            message.success('图片结果已删除');
                            await refreshDetail(detail.id);
                            reload();
                          } catch (error: any) {
                            if (error?.data?.errorCode === 'AI_IMAGE_RESULT_IN_USE') {
                              message.error('当前图片已被引用，请使用强制删除解除引用');
                              return;
                            }
                            throw error;
                          }
                        }}
                      >
                        <Button type="link" danger icon={<DeleteOutlined />}>
                          删除
                        </Button>
                      </Popconfirm>,
                      <Popconfirm
                        key="force-delete"
                        title="确认强制删除该图片结果？"
                        description="会同步解除角色、场景或分镜中的主图引用。"
                        onConfirm={async () => {
                          await deleteAiImageResult(projectId, result.id, true);
                          message.success('图片结果已强制删除');
                          await refreshDetail(detail.id);
                          reload();
                        }}
                      >
                        <Button type="link" danger>
                          强制删除
                        </Button>
                      </Popconfirm>,
                    ]}
                  >
                    <Image
                      src={result.imageUrl}
                      width="100%"
                      alt={`图片结果 ${result.id}`}
                    />
                    {result.selected && <Tag color="gold">当前主图</Tag>}
                    {result.materialId && <Tag color="green">已入素材库</Tag>}
                  </ProCard>
                ))}
              </Space>
            </Image.PreviewGroup>
          </Flex>
        )}
      </ModalForm>
    </ProCard>
  );
};

export default AiImageProductionWorkspace;
