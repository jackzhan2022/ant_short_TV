import {
  BulbOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  RobotOutlined,
  SaveOutlined,
  SplitCellsOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProCard,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import {
  App,
  Button,
  Drawer,
  Empty,
  Flex,
  Input,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  generateScript,
  queryScriptWorkspace,
  type CharacterAsset,
  type GenerateScriptValues,
  type PropAsset,
  type SceneAsset,
  type ScriptWorkspace,
  type StoryboardShot,
} from './service';

type ScriptCreationWorkspaceProps = {
  projectId: number;
  projectName?: string;
};

type ScriptGenerateFormValues = {
  title?: string;
  storyIdea: string;
  genre: string;
  episodeCount?: number;
  duration?: number;
  mainCharacter?: string;
  styleRequirement?: string[];
  referenceContent?: string;
};

type GeneratedScript = {
  title: string;
  summary: string;
  highlights: string[];
  outline: string[];
  content: string;
};

const genreOptions = [
  { label: '逆袭', value: '逆袭' },
  { label: '甜宠', value: '甜宠' },
  { label: '悬疑', value: '悬疑' },
  { label: '都市', value: '都市' },
  { label: '家庭', value: '家庭' },
];

const rewriteOptions = [
  { label: '节奏优化', value: '节奏优化' },
  { label: '冲突增强', value: '冲突增强' },
  { label: '对白优化', value: '对白优化' },
  { label: '风格转换', value: '风格转换' },
];

const createEmptyWorkspace = (projectId: number): ScriptWorkspace => ({
  projectId,
  script: null,
  versions: [],
  characters: [],
  scenes: [],
  props: [],
  storyboards: [],
});

const normalizeWorkspace = (
  workspace: ScriptWorkspace | undefined,
  projectId: number,
): ScriptWorkspace => ({
  ...createEmptyWorkspace(projectId),
  ...workspace,
  projectId: workspace?.projectId ?? projectId,
  versions: workspace?.versions ?? [],
  characters: workspace?.characters ?? [],
  scenes: workspace?.scenes ?? [],
  props: workspace?.props ?? [],
  storyboards: workspace?.storyboards ?? [],
});

const compactPayload = (
  values: ScriptGenerateFormValues,
): GenerateScriptValues => {
  const styleRequirement = values.styleRequirement?.length
    ? values.styleRequirement.join('、')
    : undefined;
  return {
    ...(values.title ? { title: values.title } : {}),
    storyIdea: values.storyIdea,
    genre: values.genre,
    ...(values.episodeCount ? { episodeCount: values.episodeCount } : {}),
    ...(values.duration ? { duration: values.duration } : {}),
    ...(values.mainCharacter ? { mainCharacter: values.mainCharacter } : {}),
    ...(styleRequirement ? { styleRequirement } : {}),
    ...(values.referenceContent ? { referenceContent: values.referenceContent } : {}),
  };
};

const buildPreview = (
  workspace: ScriptWorkspace,
  projectName?: string,
): GeneratedScript | null => {
  if (!workspace.script) {
    return null;
  }
  const episodeHeadings = workspace.script.content
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter((item) => /^第\d+集/.test(item))
    .slice(0, 5);
  const currentVersion = workspace.versions[0]?.versionNo;
  return {
    title: workspace.script.title || projectName || 'AI生成剧本',
    summary: '已生成剧本草稿，确认后可应用到剧本编辑区。',
    highlights: [
      '剧本正文已生成',
      currentVersion ? `版本 ${currentVersion}` : '新生成版本',
      workspace.script.status || 'DRAFT',
    ],
    outline: episodeHeadings.length ? episodeHeadings : ['第1集：剧本草稿已生成'],
    content: workspace.script.content,
  };
};

const renderTags = (items: string[] = []) => (
  <Space>
    {items.map((item) => (
      <Tag key={item}>{item}</Tag>
    ))}
  </Space>
);

const ScriptCreationWorkspace = ({
  projectId,
  projectName,
}: ScriptCreationWorkspaceProps) => {
  const { message } = App.useApp();
  const [workspace, setWorkspace] = useState<ScriptWorkspace>(() =>
    createEmptyWorkspace(projectId),
  );
  const [scriptContent, setScriptContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<GeneratedScript | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    queryScriptWorkspace(projectId)
      .then((response) => {
        if (!active) return;
        const nextWorkspace = normalizeWorkspace(response.data, projectId);
        setWorkspace(nextWorkspace);
        setScriptContent(nextWorkspace.script?.content ?? '');
      })
      .catch(() => {
        if (active) {
          message.error('剧本工作区加载失败');
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
  }, [projectId]);

  const characterColumns = useMemo<ProColumns<CharacterAsset>[]>(
    () => [
      { title: '角色名称', dataIndex: 'name', width: 120 },
      { title: '角色类型', dataIndex: 'roleType', width: 100 },
      { title: '性别', dataIndex: 'gender', width: 80, search: false },
      { title: '年龄段', dataIndex: 'ageRange', width: 90, search: false },
      { title: '身份职业', dataIndex: 'identity', width: 140, search: false },
      {
        title: '性格特点',
        dataIndex: 'personality',
        search: false,
        render: (_, record) => renderTags(record.personality),
      },
      { title: '外貌特征', dataIndex: 'appearance', ellipsis: true, search: false },
      { title: '角色提示词', dataIndex: 'prompt', ellipsis: true, search: false },
      {
        title: '操作',
        valueType: 'option',
        render: () => (
          <Space>
            <Button type="link" icon={<EditOutlined />}>
              编辑
            </Button>
            <Button type="link" icon={<BulbOutlined />}>
              刷新提示词
            </Button>
          </Space>
        ),
      },
    ],
    [],
  );

  const sceneColumns = useMemo<ProColumns<SceneAsset>[]>(
    () => [
      { title: '场景名称', dataIndex: 'name', width: 140 },
      { title: '场景类型', dataIndex: 'sceneType', width: 100 },
      { title: '时间氛围', dataIndex: 'atmosphere', width: 100, search: false },
      { title: '空间描述', dataIndex: 'description', ellipsis: true, search: false },
      { title: '视觉风格', dataIndex: 'visualStyle', width: 180, search: false },
      { title: '场景提示词', dataIndex: 'prompt', ellipsis: true, search: false },
    ],
    [],
  );

  const propColumns = useMemo<ProColumns<PropAsset>[]>(
    () => [
      { title: '道具名称', dataIndex: 'name', width: 140 },
      { title: '道具类型', dataIndex: 'propType', width: 120 },
      { title: '外观描述', dataIndex: 'appearance', ellipsis: true, search: false },
      { title: '剧情作用', dataIndex: 'plotFunction', ellipsis: true, search: false },
      { title: '道具提示词', dataIndex: 'prompt', ellipsis: true, search: false },
    ],
    [],
  );

  const storyboardColumns = useMemo<ProColumns<StoryboardShot>[]>(
    () => [
      { title: '序号', dataIndex: 'shotNo', width: 70, search: false },
      { title: '集数', dataIndex: 'episodeNo', width: 70, renderText: (value) => `第${value}集` },
      { title: '镜头类型', dataIndex: 'shotType', width: 100 },
      { title: '画面描述', dataIndex: 'visualDescription', ellipsis: true, search: false },
      { title: '出场角色', dataIndex: 'characters', width: 120 },
      { title: '场景', dataIndex: 'scene', width: 120 },
      { title: '对白/旁白', dataIndex: 'dialogue', ellipsis: true, search: false },
      {
        title: '时长',
        dataIndex: 'durationSeconds',
        width: 80,
        search: false,
        renderText: (value) => `${value}s`,
      },
      { title: '图片提示词', dataIndex: 'imagePrompt', ellipsis: true, search: false },
      { title: '视频提示词', dataIndex: 'videoPrompt', ellipsis: true, search: false },
      {
        title: '操作',
        valueType: 'option',
        fixed: 'right',
        render: () => (
          <Space>
            <Button type="link" icon={<EditOutlined />}>
              编辑
            </Button>
            <Button type="link" icon={<BulbOutlined />}>
              刷新提示词
            </Button>
          </Space>
        ),
      },
    ],
    [],
  );

  const applyPreview = () => {
    if (!preview) return;
    setScriptContent(preview.content);
    setPreview(null);
    message.success('剧本已应用到编辑区');
  };

  const scriptTab = (
    <ProCard
      title="剧本工作台"
      extra={
        <Space>
          <ModalForm<ScriptGenerateFormValues>
            title="AI生成剧本"
            trigger={
              <Button type="primary" icon={<RobotOutlined />}>
                AI生成剧本
              </Button>
            }
            modalProps={{ destroyOnHidden: true }}
            onFinish={async (values) => {
              try {
                const response = await generateScript(
                  projectId,
                  compactPayload(values),
                );
                const nextWorkspace = normalizeWorkspace(response.data, projectId);
                setWorkspace(nextWorkspace);
                const nextPreview = buildPreview(nextWorkspace, projectName);
                if (!nextPreview) {
                  message.warning('未返回剧本内容');
                  return false;
                }
                setPreview(nextPreview);
                return true;
              } catch {
                message.error('AI生成剧本失败');
                return false;
              }
            }}
          >
            <ProFormText name="title" label="剧名" placeholder="不填则由 AI 推荐" />
            <ProFormTextArea
              name="storyIdea"
              label="故事创意"
              rules={[{ required: true, message: '请输入故事创意' }]}
              fieldProps={{ autoSize: { minRows: 4, maxRows: 8 } }}
            />
            <ProFormSelect
              name="genre"
              label="题材类型"
              options={genreOptions}
              rules={[{ required: true, message: '请选择题材类型' }]}
            />
            <ProFormDigit name="episodeCount" label="目标集数" min={1} max={200} />
            <ProFormDigit name="duration" label="单集时长（秒）" min={15} max={600} />
            <ProFormTextArea name="mainCharacter" label="主角设定" />
            <ProFormSelect
              name="styleRequirement"
              label="风格要求"
              mode="multiple"
              options={[
                { label: '强冲突', value: '强冲突' },
                { label: '爽感', value: '爽感' },
                { label: '轻喜剧', value: '轻喜剧' },
                { label: '现实向', value: '现实向' },
              ]}
            />
            <ProFormTextArea name="referenceContent" label="参考内容" />
          </ModalForm>
          <ModalForm<{ rewriteType: string; requirement?: string }>
            title="AI改写剧本"
            trigger={<Button icon={<EditOutlined />}>AI改写剧本</Button>}
            modalProps={{ destroyOnHidden: true }}
            onFinish={async (values) => {
              setPreview({
                title: projectName || '改写剧本',
                summary: `已按${values.rewriteType}方向生成改写版本。`,
                highlights: ['保留核心剧情', '增强镜头钩子', '优化对白节奏'],
                outline: ['第1集：冲突前置', '第2集：关系升级', '第3集：反转加深'],
                content: `${scriptContent}\n\n【AI改写说明】已按${values.rewriteType}方向强化短剧节奏。`,
              });
              return true;
            }}
          >
            <ProFormSelect
              name="rewriteType"
              label="改写类型"
              options={rewriteOptions}
              rules={[{ required: true, message: '请选择改写类型' }]}
            />
            <ProFormTextArea name="requirement" label="改写要求" />
          </ModalForm>
          <Button icon={<SaveOutlined />}>保存草稿</Button>
        </Space>
      }
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '220px minmax(360px, 1fr) 280px',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <section>
          <Typography.Title level={5}>剧本版本</Typography.Title>
          <Flex vertical gap="small" style={{ width: '100%' }}>
            {workspace.versions.length ? (
              workspace.versions.map((item) => (
                <Button key={item.id} block>
                  版本 {item.versionNo}
                  <Tag>{item.status || item.sourceType}</Tag>
                </Button>
              ))
            ) : (
              <Empty description="暂无版本" />
            )}
          </Flex>
        </section>
        <section>
          <Typography.Title level={5}>剧本编辑区</Typography.Title>
          <Input.TextArea
            value={scriptContent}
            onChange={(event) => setScriptContent(event.target.value)}
            autoSize={{ minRows: 18, maxRows: 28 }}
            placeholder="暂无剧本内容"
          />
        </section>
        <section>
          <Typography.Title level={5}>AI 操作建议</Typography.Title>
          <Flex vertical gap="small" style={{ width: '100%' }}>
            <Button block icon={<TagsOutlined />}>
              提取角色/场景/道具
            </Button>
            <Button block icon={<SplitCellsOutlined />}>
              拆解分镜
            </Button>
            <Button block icon={<BulbOutlined />}>
              生成提示词
            </Button>
          </Flex>
          <Typography.Paragraph style={{ marginTop: 16 }}>
            AI 结果先进入预览态，用户确认后才写入正式数据。
          </Typography.Paragraph>
        </section>
      </div>
    </ProCard>
  );

  const elementTab = (
    <Tabs
      items={[
        {
          key: 'characters',
          label: '角色',
          children: (
            <ProTable<CharacterAsset>
              rowKey="id"
              search={false}
              options={false}
              pagination={false}
              columns={characterColumns}
              dataSource={workspace.characters}
              loading={loading}
              toolBarRender={() => [
                <Button key="extract" type="primary" icon={<RobotOutlined />}>
                  AI提取角色
                </Button>,
              ]}
            />
          ),
        },
        {
          key: 'scenes',
          label: '场景',
          children: (
            <ProTable<SceneAsset>
              rowKey="id"
              search={false}
              options={false}
              pagination={false}
              columns={sceneColumns}
              dataSource={workspace.scenes}
              loading={loading}
              toolBarRender={() => [
                <Button key="extract" type="primary" icon={<RobotOutlined />}>
                  AI提取场景
                </Button>,
              ]}
            />
          ),
        },
        {
          key: 'props',
          label: '道具',
          children: (
            <ProTable<PropAsset>
              rowKey="id"
              search={false}
              options={false}
              pagination={false}
              columns={propColumns}
              dataSource={workspace.props}
              loading={loading}
              toolBarRender={() => [
                <Button key="extract" type="primary" icon={<RobotOutlined />}>
                  AI提取道具
                </Button>,
              ]}
            />
          ),
        },
      ]}
    />
  );

  return (
    <ProCard
      title="剧本创作"
      subTitle={`项目ID：${projectId}`}
      extra={<Tag color="blue">二期文本工作流</Tag>}
    >
      <Tabs
        items={[
          {
            key: 'script',
            label: (
              <Space>
                <FileTextOutlined />
                剧本
              </Space>
            ),
            children: scriptTab,
          },
          {
            key: 'characters',
            label: (
              <Space>
                <TagsOutlined />
                角色
              </Space>
            ),
            children: (
              <ProTable<CharacterAsset>
                rowKey="id"
                search={false}
                options={false}
                pagination={false}
                columns={characterColumns}
                dataSource={workspace.characters}
                loading={loading}
                toolBarRender={() => [
                  <Button key="extract" type="primary" icon={<RobotOutlined />}>
                    AI提取角色
                  </Button>,
                  <Button key="confirm" icon={<SaveOutlined />}>
                    批量确认
                  </Button>,
                ]}
              />
            ),
          },
          {
            key: 'elements',
            label: '场景/道具',
            children: elementTab,
          },
          {
            key: 'storyboard',
            label: (
              <Space>
                <SplitCellsOutlined />
                分镜
              </Space>
            ),
            children: workspace.storyboards.length ? (
              <ProTable<StoryboardShot>
                rowKey="id"
                search={false}
                options={false}
                pagination={false}
                scroll={{ x: 1600 }}
                columns={storyboardColumns}
                dataSource={workspace.storyboards}
                loading={loading}
                toolBarRender={() => [
                  <Button key="breakdown" type="primary" icon={<RobotOutlined />}>
                    AI拆解分镜
                  </Button>,
                  <Button key="add" icon={<PlusOutlined />}>
                    新增分镜
                  </Button>,
                  <Button key="confirm" icon={<SaveOutlined />}>
                    确认分镜
                  </Button>,
                ]}
              />
            ) : (
              <Empty description="暂无分镜" />
            ),
          },
        ]}
      />
      <Drawer
        title="生成结果预览"
        size={720}
        open={Boolean(preview)}
        destroyOnHidden
        onClose={() => setPreview(null)}
        extra={
          <Space>
            <Button onClick={() => setPreview(null)}>重新生成</Button>
            <Button type="primary" onClick={applyPreview}>
              应用到剧本
            </Button>
          </Space>
        }
      >
        {preview && (
          <Flex vertical gap="large" style={{ width: '100%' }}>
            <section>
              <Typography.Text type="secondary">剧名建议</Typography.Text>
              <Typography.Title level={4}>{preview.title}</Typography.Title>
            </section>
            <section>
              <Typography.Text type="secondary">故事简介</Typography.Text>
              <Typography.Paragraph>{preview.summary}</Typography.Paragraph>
            </section>
            <section>
              <Typography.Text type="secondary">核心看点</Typography.Text>
              <div>{renderTags(preview.highlights)}</div>
            </section>
            <section>
              <Typography.Text type="secondary">分集大纲</Typography.Text>
              <Flex vertical gap="small" style={{ width: '100%', marginTop: 8 }}>
                {preview.outline.map((item) => (
                  <Typography.Paragraph key={item}>{item}</Typography.Paragraph>
                ))}
              </Flex>
            </section>
            <section>
              <Typography.Text type="secondary">剧本正文</Typography.Text>
              <Input.TextArea value={preview.content} readOnly autoSize={{ minRows: 10 }} />
            </section>
          </Flex>
        )}
      </Drawer>
    </ProCard>
  );
};

export default ScriptCreationWorkspace;
