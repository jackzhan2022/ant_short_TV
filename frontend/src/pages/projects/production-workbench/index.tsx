import {
  ArrowLeftOutlined,
  BarsOutlined,
  BookOutlined,
  BulbOutlined,
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  SoundOutlined,
  SplitCellsOutlined,
  UploadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import { App, Button, Empty, Flex, Image, Tooltip, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { queryProject } from '../detail/service';
import {
  confirmScriptElement,
  createAiImageTask,
  deleteScriptElement,
  extractScriptElements,
  generateWorkflowPrompts,
  queryAiImageTasks,
  queryScriptWorkspace,
  updateScriptElement,
} from '../detail/components/service';
import type {
  AiImageResult,
  AiImageTask,
  CharacterAsset,
  CreateAiImageTaskValues,
  PropAsset,
  SceneAsset,
  ScriptWorkspace,
  UpdateScriptElementValues,
} from '../detail/components/service';

type ProjectLite = {
  id: number;
  name: string;
  code?: string;
  status?: string;
  coverUrl?: string | null;
};

type SettingTabKey = 'characters' | 'scenes' | 'props';

type SettingCard = {
  id: number;
  name: string;
  countText: string;
  summary: string;
  images: string[];
  placeholder: string;
  actions?: 'voice' | 'upload';
  prompt?: string;
  source: CharacterAsset | SceneAsset | PropAsset;
};

const activeStepKey = 'settings';

const topSteps = [
  { key: 'script', label: '剧本', icon: <BookOutlined /> },
  { key: 'settings', label: '设定', icon: <SettingOutlined /> },
  { key: 'storyboard', label: '分镜', icon: <SplitCellsOutlined /> },
  { key: 'video', label: '视频', icon: <VideoCameraOutlined /> },
];

const tabLabels: Record<SettingTabKey, string> = {
  characters: '角色',
  scenes: '场景',
  props: '道具',
};

const tabTargetTypes: Record<SettingTabKey, string> = {
  characters: 'CHARACTER',
  scenes: 'SCENE',
  props: 'PROP',
};

const tabElementTypes: Record<SettingTabKey, 'CHARACTER' | 'SCENE' | 'PROP'> = {
  characters: 'CHARACTER',
  scenes: 'SCENE',
  props: 'PROP',
};

const tabTaskTypes: Record<SettingTabKey, string> = {
  characters: 'CHARACTER',
  scenes: 'SCENE',
  props: 'SCENE',
};

const tabAspectRatios: Record<SettingTabKey, string> = {
  characters: '3:4',
  scenes: '16:9',
  props: '1:1',
};

const generatingStatuses = ['PENDING', 'RUNNING', 'SUBMITTING', 'GENERATING'];
const successStatuses = ['SUCCESS', 'SUCCEEDED'];

const getTaskImages = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId: number,
) => {
  const results = imageTasks
    .filter(
      (item) =>
        item.targetType === targetType &&
        item.targetId === targetId &&
        successStatuses.includes(item.status) &&
        item.results?.length,
    )
    .flatMap((task) => task.results)
    .filter((result) => successStatuses.includes(result.status));
  const sorted = [...results].sort(
    (left, right) => Number(right.selected) - Number(left.selected),
  );
  return sorted
    .map((result: AiImageResult) => result.thumbnailUrl || result.imageUrl)
    .filter(Boolean)
    .slice(0, 4) as string[];
};

const getImageCountText = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId: number,
) => {
  const count = getTaskImages(imageTasks, targetType, targetId).length || 1;
  return `${count}个形象`;
};

const buildCharacterCards = (
  characters: CharacterAsset[],
  imageTasks: AiImageTask[],
): SettingCard[] =>
  characters.map((item, index) => {
    const images = getTaskImages(imageTasks, 'CHARACTER', item.id);
    return {
      id: item.id,
      name: item.name,
      countText: `${images.length || 1}个形象`,
      summary: [
        `身份：${item.identity || item.roleType || '-'}`,
        `个性：${item.personality?.slice(0, 3).join('、') || '-'}`,
        `简介：${item.appearance || item.prompt || '-'}`,
      ].join(' '),
      images,
      placeholder: `character-${index % 4}`,
      actions: index === 2 ? 'upload' : 'voice',
      prompt: item.prompt,
      source: item,
    };
  });

const buildSceneCards = (
  scenes: SceneAsset[],
  imageTasks: AiImageTask[],
): SettingCard[] =>
  scenes.map((item, index) => ({
    id: item.id,
    name: item.name,
    countText: getImageCountText(imageTasks, 'SCENE', item.id),
    summary: [
      item.description || item.prompt || '-',
      item.atmosphere ? `氛围：${item.atmosphere}` : '',
      item.visualStyle ? `风格：${item.visualStyle}` : '',
    ]
      .filter(Boolean)
      .join(' '),
    images: getTaskImages(imageTasks, 'SCENE', item.id),
    placeholder: `scene-${index % 4}`,
    prompt: item.prompt,
    source: item,
  }));

const buildPropCards = (
  props: PropAsset[],
  imageTasks: AiImageTask[],
): SettingCard[] =>
  props.map((item, index) => ({
    id: item.id,
    name: item.name,
    countText: getImageCountText(imageTasks, 'PROP', item.id),
    summary: item.appearance || item.plotFunction || item.prompt || '-',
    images: getTaskImages(imageTasks, 'PROP', item.id),
    placeholder: `prop-${index % 4}`,
    prompt: item.prompt,
    source: item,
  }));

const getStatusCount = (imageTasks: AiImageTask[], targetType: string) => ({
  completed: imageTasks.filter(
    (item) =>
      item.targetType === targetType && successStatuses.includes(item.status),
  ).length,
  generating: imageTasks.filter(
    (item) =>
      item.targetType === targetType && generatingStatuses.includes(item.status),
  ).length,
  failed: imageTasks.filter(
    (item) => item.targetType === targetType && item.status === 'FAILED',
  ).length,
});

const truncateText = (text: string, length = 84) =>
  text.length > length ? `${text.slice(0, length)}...` : text;

const getVisualHeight = (activeTab: SettingTabKey) => {
  if (activeTab === 'characters') {
    return 212;
  }
  if (activeTab === 'scenes') {
    return 239;
  }
  return 243;
};

const getPlaceholderBackground = (key: string) => {
  if (key.startsWith('scene')) {
    return [
      'linear-gradient(135deg, #d8e9f8 0%, #f9fbff 52%, #b8c6d6 100%)',
      'linear-gradient(135deg, #20242d 0%, #3d4148 50%, #11141a 100%)',
      'linear-gradient(135deg, #d4ead8 0%, #eef6ff 48%, #a6b6ca 100%)',
      'linear-gradient(135deg, #101720 0%, #2d3542 52%, #111827 100%)',
    ][Number(key.at(-1)) || 0];
  }
  if (key.startsWith('prop')) {
    return [
      'radial-gradient(circle at 50% 38%, #d98a1f 0 17%, transparent 18%), linear-gradient(#fff, #fbfcff)',
      'linear-gradient(145deg, #2d3034 0%, #777b82 46%, #f5f7fb 47%, #ffffff 100%)',
      'radial-gradient(circle at 52% 44%, #35a2ff 0 18%, transparent 19%), linear-gradient(#fff, #fbfcff)',
      'radial-gradient(ellipse at 50% 52%, #edf0f5 0 32%, transparent 33%), linear-gradient(#fff, #fbfcff)',
    ][Number(key.at(-1)) || 0];
  }
  return [
    'linear-gradient(90deg, #fff 0%, #fff5d8 32%, #f8fbff 33%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #2f343b 34%, #f7f9ff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #e8f2ff 34%, #fbfdff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #8b1f2c 34%, #fff 35%, #fff 100%)',
  ][Number(key.at(-1)) || 0];
};

const toElementPayload = (
  card: SettingCard,
  activeTab: SettingTabKey,
): UpdateScriptElementValues => {
  if (activeTab === 'characters') {
    const source = card.source as CharacterAsset;
    return {
      name: card.name,
      roleType: source.roleType,
      gender: source.gender,
      ageRange: source.ageRange,
      identity: source.identity,
      personality: source.personality,
      appearance: source.appearance,
      prompt: source.prompt,
      status: 'CONFIRMED',
    };
  }
  if (activeTab === 'scenes') {
    const source = card.source as SceneAsset;
    return {
      name: card.name,
      sceneType: source.sceneType,
      atmosphere: source.atmosphere,
      description: source.description,
      visualStyle: source.visualStyle,
      prompt: source.prompt,
      status: 'CONFIRMED',
    };
  }
  const source = card.source as PropAsset;
  return {
    name: card.name,
    propType: source.propType,
    appearance: source.appearance,
    plotFunction: source.plotFunction,
    prompt: source.prompt,
    status: 'CONFIRMED',
  };
};

const CollageMedia = ({
  card,
  height,
}: {
  card: SettingCard;
  height: number;
}) => {
  const images = card.images.length ? card.images : [];
  if (!images.length) {
    return (
      <div
        role="img"
        aria-label={`${card.name}参考图`}
        style={{
          width: '100%',
          height,
          background: getPlaceholderBackground(card.placeholder),
        }}
      />
    );
  }
  if (images.length === 1) {
    return (
      <Image
        src={images[0]}
        alt={`${card.name}参考图`}
        width="100%"
        height="100%"
        preview={false}
        style={{ objectFit: 'cover' }}
      />
    );
  }
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gridTemplateRows: 'repeat(2, 1fr)',
        gap: 1,
        width: '100%',
        height: '100%',
        background: '#f1f4fb',
      }}
    >
      {Array.from({ length: 4 }, (_, index) => (
        <Image
          key={`${card.id}-${images[index] || index}`}
          src={images[index] || images[0]}
          alt={`${card.name}参考图`}
          width="100%"
          height="100%"
          preview={false}
          style={{ objectFit: 'cover' }}
        />
      ))}
    </div>
  );
};

const ProductionWorkbench = () => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const { message, modal } = App.useApp();
  const [project, setProject] = useState<ProjectLite>();
  const [activeTab, setActiveTab] = useState<SettingTabKey>('characters');
  const [noticeVisible, setNoticeVisible] = useState(true);
  const [actionLoading, setActionLoading] = useState<string>();
  const [imageTasks, setImageTasks] = useState<AiImageTask[]>([]);
  const [workspace, setWorkspace] = useState<ScriptWorkspace>({
    projectId: projectId || 0,
    script: null,
    versions: [],
    characters: [],
    scenes: [],
    props: [],
    storyboards: [],
  });

  const applyWorkspace = useCallback(
    (nextWorkspace?: ScriptWorkspace | null) => {
      if (!nextWorkspace) {
        return;
      }
      setWorkspace({
        projectId,
        script: nextWorkspace.script || null,
        versions: nextWorkspace.versions || [],
        characters: nextWorkspace.characters || [],
        scenes: nextWorkspace.scenes || [],
        props: nextWorkspace.props || [],
        storyboards: nextWorkspace.storyboards || [],
      });
    },
    [projectId],
  );

  const loadWorkbench = useCallback(async () => {
    if (!projectId) {
      return;
    }
    const [projectResponse, workspaceResponse, imageTaskResponse] = await Promise.all([
      queryProject(projectId),
      queryScriptWorkspace(projectId),
      queryAiImageTasks(projectId, undefined).catch(() => ({ data: [] })),
    ]);
    setProject(projectResponse.data);
    applyWorkspace(workspaceResponse.data);
    setImageTasks(imageTaskResponse.data || []);
  }, [applyWorkspace, projectId]);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    loadWorkbench()
      .then(() => {
        if (!active) {
          return;
        }
      })
      .catch(() => {
        if (active) {
          message.error('设定页面加载失败');
        }
      });
    return () => {
      active = false;
    };
  }, [loadWorkbench, message, projectId]);

  const characters = workspace.characters;
  const scenes = workspace.scenes;
  const props = workspace.props;
  const scriptTitle = project?.name || workspace.script?.title || '项目';
  const activeTargetType = tabTargetTypes[activeTab];
  const { completed, generating, failed } = getStatusCount(
    imageTasks,
    activeTargetType,
  );
  const settingCards = useMemo(() => {
    if (activeTab === 'scenes') {
      return buildSceneCards(scenes, imageTasks);
    }
    if (activeTab === 'props') {
      return buildPropCards(props, imageTasks);
    }
    return buildCharacterCards(characters, imageTasks);
  }, [activeTab, characters, imageTasks, props, scenes]);
  const activeTotal =
    activeTab === 'characters'
      ? characters.length
      : activeTab === 'scenes'
        ? scenes.length
        : props.length;

  const runAction = async (key: string, action: () => Promise<void>) => {
    setActionLoading(key);
    try {
      await action();
    } catch {
      message.error('操作失败，请稍后重试');
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleExtractActive = () =>
    runAction(`extract-${activeTab}`, async () => {
      const response = await extractScriptElements(projectId, {
        elementType: tabElementTypes[activeTab],
      });
      applyWorkspace(response.data);
      message.success(`${tabLabels[activeTab]}设定已同步`);
    });

  const handleGeneratePrompts = () =>
    runAction(`prompts-${activeTab}`, async () => {
      const response = await generateWorkflowPrompts(projectId, {
        targetType: tabElementTypes[activeTab],
      });
      applyWorkspace(response.data);
      message.success(`${tabLabels[activeTab]}提示词已生成`);
    });

  const handleRefresh = () =>
    runAction('refresh', async () => {
      await loadWorkbench();
      message.success('设定数据已刷新');
    });

  const handleGenerateCard = (card: SettingCard) =>
    runAction(`${activeTab}-${card.id}-image`, async () => {
      if (activeTab === 'props') {
        message.warning('当前后端暂不支持道具图片生成任务');
        return;
      }
      const payload: CreateAiImageTaskValues = {
        taskType: tabTaskTypes[activeTab],
        targetType: tabElementTypes[activeTab],
        targetId: card.id,
        prompt: card.prompt || card.summary || card.name,
        aspectRatio: tabAspectRatios[activeTab],
        imageCount: 4,
        quality: 'STANDARD',
      };
      await createAiImageTask(projectId, payload);
      message.success('图片生成任务已创建');
      await loadWorkbench();
    });

  const handleConfirmCard = (card: SettingCard) =>
    runAction(`${activeTab}-${card.id}-confirm`, async () => {
      const response = await confirmScriptElement(
        projectId,
        tabElementTypes[activeTab],
        card.id,
      );
      applyWorkspace(response.data);
      message.success(`${card.name}已确认`);
    });

  const handleUpdateCard = (card: SettingCard) =>
    runAction(`${activeTab}-${card.id}-update`, async () => {
      const response = await updateScriptElement(
        projectId,
        tabElementTypes[activeTab],
        card.id,
        toElementPayload(card, activeTab),
      );
      applyWorkspace(response.data);
      message.success(`${card.name}设定已更新`);
    });

  const handleDeleteCard = (card: SettingCard) => {
    modal.confirm({
      title: `删除${tabLabels[activeTab]}设定`,
      content: `确认删除「${card.name}」？`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await runAction(`${activeTab}-${card.id}-delete`, async () => {
          const response = await deleteScriptElement(
            projectId,
            tabElementTypes[activeTab],
            card.id,
          );
          applyWorkspace(response.data);
          message.success(`${card.name}已删除`);
        });
      },
    });
  };

  if (!projectId) {
    return <Empty description="项目不存在" />;
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f6f7fb' }}>
      <header
        style={{
          height: 68,
          background: '#fff',
          borderBottom: '1px solid #e4e9f3',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
        }}
      >
        <Flex align="center" gap={12}>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => history.push(`/projects/${projectId}`)}
            aria-label="返回"
            style={{ paddingInline: 0 }}
          />
          <div>
            <Flex align="center" gap={6}>
              <Typography.Text strong style={{ fontSize: 15 }}>
                {scriptTitle}
              </Typography.Text>
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                aria-label="编辑项目名"
                style={{ paddingInline: 2, height: 20 }}
              />
            </Flex>
            <div style={{ marginTop: 5, color: '#65708a', fontSize: 13 }}>
              <span style={{ marginRight: 12 }}>9:16</span>
              <span style={{ marginRight: 12 }}>720p</span>
              <span style={{ marginRight: 12 }}>写实都市</span>
              <Button
                type="link"
                size="small"
                icon={<BookOutlined />}
                style={{ padding: 0, height: 'auto' }}
              >
                查看剧本
              </Button>
            </div>
          </div>
        </Flex>

        <Flex align="center" gap={0} style={{ transform: 'translateX(-20px)' }}>
          {topSteps.map((step, index) => {
            const active = step.key === activeStepKey;
            return (
              <div key={step.key} style={{ display: 'flex', alignItems: 'center' }}>
                <div
                  style={{
                    width: 72,
                    height: 44,
                    borderRadius: 22,
                    border: active ? '1px solid #8fa2ff' : '1px solid #e8edf6',
                    color: active ? '#3156ff' : '#111827',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: active ? '#f5f7ff' : '#fff',
                    fontSize: 12,
                    boxShadow: active ? '0 0 0 1px rgba(49,86,255,0.03)' : 'none',
                  }}
                >
                  <span style={{ height: 16, lineHeight: '16px' }}>{step.icon}</span>
                  <span style={{ marginTop: 1 }}>{step.label}</span>
                </div>
                {index < topSteps.length - 1 && (
                  <div style={{ width: 18, height: 1, background: '#dfe5f2' }} />
                )}
              </div>
            );
          })}
        </Flex>

        <div
          style={{
            minWidth: 96,
            height: 32,
            borderRadius: 8,
            background: '#f1f4ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#6672a8',
            fontSize: 14,
            fontWeight: 600,
          }}
        >
          ✦ 803,290
        </div>
      </header>

      <main
        style={{
          margin: '0 auto',
          width: '100%',
          maxWidth: 1880,
          minWidth: 1100,
          boxSizing: 'border-box',
          padding: '24px 44px 88px',
        }}
      >
        {noticeVisible && (
          <div
            style={{
              height: 42,
              borderRadius: 8,
              border: '1px solid #e1e8ff',
              background: '#f3f6ff',
              color: '#265cff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '0 16px',
              fontSize: 14,
            }}
          >
            <Flex align="center" gap={8}>
              <BulbOutlined />
              <span>
                请确保角色、场景及道具已全部生成。 点击角色图片可配置【变装】，未配置的变装将导致分镜无参考图可用，直接影响视频准确性。
              </span>
            </Flex>
            <Button
              type="text"
              aria-label="关闭提示"
              icon={<CloseOutlined />}
              onClick={() => setNoticeVisible(false)}
              style={{ color: '#1f2937', paddingInline: 0 }}
            />
          </div>
        )}

        <section style={{ marginTop: 38 }}>
          <Flex align="center" justify="space-between">
            <Flex align="center" gap={30}>
              {(Object.keys(tabLabels) as SettingTabKey[]).map((key) => {
                const active = key === activeTab;
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setActiveTab(key)}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      padding: 0,
                      color: active ? '#1263ff' : '#66708a',
                      cursor: 'pointer',
                      fontSize: 20,
                      fontWeight: active ? 700 : 600,
                      lineHeight: '32px',
                    }}
                  >
                    {tabLabels[key]}
                  </button>
                );
              })}
            </Flex>

            <Flex align="center" gap={18} style={{ color: '#66708a', fontSize: 14 }}>
              <span>
                {tabLabels[activeTab]}总计 {activeTotal}
              </span>
              <span>已完成 {completed}</span>
              <span>生成中 {generating}</span>
              <span>
                失败 <span style={{ color: failed ? '#f04438' : '#66708a' }}>{failed}</span>
              </span>
              <Button
                type="text"
                aria-label="刷新设定"
                icon={<ReloadOutlined style={{ color: '#3156ff' }} />}
                loading={actionLoading === 'refresh'}
                onClick={handleRefresh}
                style={{ paddingInline: 0 }}
              />
              <Button
                type="text"
                aria-label={`添加${tabLabels[activeTab]}`}
                icon={<PlusOutlined />}
                loading={actionLoading === `extract-${activeTab}`}
                onClick={handleExtractActive}
                style={{ paddingInline: 4 }}
              >
                添加{tabLabels[activeTab]}
              </Button>
              <Button
                type="text"
                aria-label="批量生成"
                icon={<BarsOutlined />}
                loading={actionLoading === `prompts-${activeTab}`}
                onClick={handleGeneratePrompts}
                style={{ paddingInline: 4 }}
              >
                批量生成
              </Button>
              <Button
                type="text"
                disabled
                icon={<CopyOutlined />}
                style={{ paddingInline: 4, color: '#aab2c4' }}
              >
                批量匹配
              </Button>
            </Flex>
          </Flex>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
              columnGap: 22,
              rowGap: 36,
              marginTop: 24,
            }}
          >
            {settingCards.map((item, index) => (
              <article key={`${activeTab}-${item.id}`} style={{ minWidth: 0 }}>
                <div
                  style={{
                    position: 'relative',
                    height: getVisualHeight(activeTab),
                    border: '1px solid #e7edf8',
                    borderRadius: 7,
                    overflow: 'hidden',
                    background: '#fff',
                  }}
                >
                  <CollageMedia card={item} height={getVisualHeight(activeTab)} />
                  {(activeTab !== 'characters' || index === 2 || index === 1) && (
                    <Flex
                      align="center"
                      gap={15}
                      style={{
                        position: 'absolute',
                        right: 12,
                        top: 8,
                        height: 40,
                        padding: '0 13px',
                        borderRadius: 18,
                        background:
                          activeTab === 'props' && index === 2
                            ? 'rgba(28, 31, 36, 0.2)'
                            : 'rgba(25, 28, 34, 0.68)',
                        color: '#fff',
                        fontSize: 15,
                      }}
                    >
                      <Tooltip title="确认设定">
                        <Button
                          type="text"
                          aria-label={`确认${item.name}`}
                          icon={<CopyOutlined />}
                          onClick={() => handleConfirmCard(item)}
                          style={{ color: '#fff', paddingInline: 0 }}
                        />
                      </Tooltip>
                      <Tooltip title="生成图片">
                        <Button
                          type="text"
                          aria-label={`生成${item.name}图片`}
                          icon={<UploadOutlined />}
                          onClick={() => handleGenerateCard(item)}
                          style={{ color: '#fff', paddingInline: 0 }}
                        />
                      </Tooltip>
                      <Tooltip title="同步设定">
                        <Button
                          type="text"
                          aria-label={`同步${item.name}`}
                          icon={<EditOutlined />}
                          onClick={() => handleUpdateCard(item)}
                          style={{ color: '#fff', paddingInline: 0 }}
                        />
                      </Tooltip>
                      <Tooltip title="删除设定">
                        <Button
                          type="text"
                          aria-label={`删除${item.name}`}
                          icon={<DeleteOutlined />}
                          onClick={() => handleDeleteCard(item)}
                          style={{ color: '#fff', paddingInline: 0 }}
                        />
                      </Tooltip>
                      <MoreOutlined />
                    </Flex>
                  )}
                </div>
                <div
                  style={{
                    marginTop: 10,
                    color: '#1f2937',
                    fontSize: 15,
                    fontWeight: 500,
                  }}
                >
                  {item.name}
                </div>
                <div
                  style={{
                    marginTop: 7,
                    color: '#66708a',
                    fontSize: 13,
                    lineHeight: 1.45,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                  }}
                >
                  <span>{item.countText}</span>
                  <span style={{ margin: '0 8px', color: '#d3d8e5' }}>|</span>
                  {truncateText(item.summary)}
                </div>
                {activeTab === 'characters' && (
                  <Button
                    size="small"
                    icon={
                      item.actions === 'upload' ? (
                        <span
                          style={{
                            display: 'inline-grid',
                            placeItems: 'center',
                            width: 18,
                            height: 18,
                            borderRadius: 9,
                            background: '#111827',
                            color: '#fff',
                            fontSize: 10,
                          }}
                        >
                          ▶
                        </span>
                      ) : (
                        <SoundOutlined />
                      )
                    }
                    style={{
                      marginTop: 11,
                      height: 32,
                      borderRadius: 6,
                      color: '#1f2937',
                      borderColor: '#e6ebf5',
                      background: '#fff',
                      fontSize: 13,
                    }}
                    onClick={() =>
                      message.warning('角色音色配置请在语音字幕流程中完成')
                    }
                  >
                    {item.actions === 'upload' ? '本地上传 | 自定义音色' : '配置音色'}
                  </Button>
                )}
              </article>
            ))}
            {!settingCards.length && (
              <div style={{ gridColumn: '1 / -1', paddingTop: 72 }}>
                <Empty description={`暂无${tabLabels[activeTab]}设定`} />
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
};

export default ProductionWorkbench;
