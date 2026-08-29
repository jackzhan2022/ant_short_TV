import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  RobotOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useIntl, useParams } from '@umijs/max';
import { App, Button, Empty, Flex, Input, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import {
  type AssetCandidate,
  bindVisualVariantEpisodes,
  type CharacterAsset,
  confirmScriptElement,
  createAiImageTask,
  createVisualVariant,
  decideAssetCandidate,
  deleteScriptElement,
  deleteVisualVariant,
  extractScriptElements,
  type PropAsset,
  queryAssetCandidates,
  queryScriptWorkspace,
  type SceneAsset,
  type ScriptElementType,
  type ScriptWorkspace,
  selectPrimaryVisualVariant,
  updateScriptElement,
  updateVisualVariant,
  type VisualVariant,
} from './service';

type ElementType = Exclude<ScriptElementType, 'ALL'>;
type AssetRecord = CharacterAsset | SceneAsset | PropAsset;

const emptyWorkspace = (projectId: number): ScriptWorkspace => ({
  projectId,
  script: null,
  versions: [],
  characters: [],
  scenes: [],
  props: [],
  storyboards: [],
});

const elementLabels: Record<ElementType, string> = {
  CHARACTER: '角色',
  SCENE: '场景',
  PROP: '道具',
};

const getSummary = (type: ElementType, item: AssetRecord) => {
  if (type === 'CHARACTER') {
    const character = item as CharacterAsset;
    return [
      character.roleType,
      character.gender,
      character.ageRange,
      character.identity,
    ]
      .filter(Boolean)
      .join(' / ');
  }
  if (type === 'SCENE') {
    const scene = item as SceneAsset;
    return [scene.sceneType, scene.atmosphere, scene.visualStyle]
      .filter(Boolean)
      .join(' / ');
  }
  const prop = item as PropAsset;
  return [prop.propType, prop.plotFunction].filter(Boolean).join(' / ');
};

const getDescription = (type: ElementType, item: AssetRecord) => {
  if (type === 'CHARACTER') {
    const character = item as CharacterAsset;
    return character.appearance || character.identity || character.prompt;
  }
  if (type === 'SCENE') {
    const scene = item as SceneAsset;
    return scene.description || scene.visualStyle || scene.prompt;
  }
  const prop = item as PropAsset;
  return prop.appearance || prop.plotFunction || prop.prompt;
};

const getSearchText = (type: ElementType, item: AssetRecord) =>
  [item.name, getSummary(type, item), getDescription(type, item), item.prompt]
    .filter(Boolean)
    .join(' ');

const assetSections = [
  { type: 'CHARACTER' as const, title: '角色设定' },
  { type: 'SCENE' as const, title: '场景设定' },
  { type: 'PROP' as const, title: '道具设定' },
];

const AssetCard = ({
  item,
  type,
  onConfirm,
  onDelete,
  onSave,
  onManageVisual,
}: {
  item: AssetRecord;
  type: ElementType;
  onConfirm: (type: ElementType, id: number) => void;
  onDelete: (type: ElementType, id: number) => void;
  onSave: (type: ElementType, item: AssetRecord) => void;
  onManageVisual: (type: ElementType, item: AssetRecord) => void;
}) => (
  <article
    style={{
      minHeight: 252,
      border: '1px solid var(--app-color-border)',
      borderRadius: 10,
      background: '#fff',
      boxShadow: '0 8px 20px rgba(26, 39, 76, 0.05)',
      overflow: 'hidden',
    }}
  >
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        minHeight: 252,
      }}
    >
      <div
        style={{
          display: 'grid',
          placeItems: 'end start',
          minHeight: 112,
          padding: '16px 18px',
          background:
            type === 'CHARACTER'
              ? 'linear-gradient(135deg, #fff7d9 0%, #e9efff 100%)'
              : type === 'SCENE'
                ? 'linear-gradient(135deg, #dceafa 0%, #f8fbff 100%)'
                : 'linear-gradient(135deg, #f4edff 0%, #eaf7f1 100%)',
          color: 'var(--app-color-primary)',
          fontSize: 34,
          fontWeight: 700,
        }}
      >
        {item.visual?.resolvedImageUrl ? (
          <img
            src={item.visual.resolvedImageUrl}
            alt={`${item.name}当前视觉形象`}
            style={{ width: '100%', height: 112, objectFit: 'cover' }}
          />
        ) : (
          item.name.slice(0, 1)
        )}
      </div>
      <div style={{ padding: '13px 16px 14px' }}>
        <Flex justify="space-between" align="flex-start" gap={12}>
          <div>
            <Typography.Text
              strong
              style={{ fontSize: 15, color: 'var(--app-color-text)' }}
            >
              {item.name}
            </Typography.Text>
            <div style={{ marginTop: 7 }}>
              <Tag color="blue">{elementLabels[type]}</Tag>
              {getSummary(type, item) && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {getSummary(type, item)}
                </Typography.Text>
              )}
            </div>
          </div>
          <Flex gap={4}>
            <Button
              type="text"
              size="small"
              icon={<CheckOutlined />}
              aria-label={`确认${item.name}`}
              onClick={() => onConfirm(type, item.id)}
            />
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              aria-label={`保存${item.name}`}
              onClick={() => onSave(type, item)}
            />
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              aria-label={`删除${item.name}`}
              onClick={() => onDelete(type, item.id)}
            />
          </Flex>
        </Flex>
        <Typography.Paragraph
          style={{
            margin: '10px 0 0',
            color: 'var(--app-color-text-secondary)',
            fontSize: 13,
            lineHeight: '22px',
            height: 44,
            overflow: 'hidden',
          }}
        >
          {getDescription(type, item) || '暂无设定描述'}
        </Typography.Paragraph>
        <div
          style={{
            marginTop: 6,
            color: 'var(--app-color-text-tertiary)',
            fontSize: 12,
            lineHeight: '20px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {item.prompt || '暂无提示词'}
        </div>
        <Flex
          justify="space-between"
          align="center"
          gap={8}
          style={{ marginTop: 9 }}
        >
          <div
            style={{ fontSize: 12, color: 'var(--app-color-text-secondary)' }}
          >
            <div>{item.visual?.variantCount ?? 0} 个视觉形象</div>
            <div>
              {item.visual?.primaryVariant
                ? `主形象：${item.visual.primaryVariant.name}`
                : '尚未设置主形象'}
            </div>
            {item.visual?.resolvedImageSource ? (
              <div>
                来源：
                {item.visual.resolvedImageSource === 'EPISODE_PREFERRED'
                  ? '剧集首选'
                  : item.visual.resolvedImageSource === 'PRIMARY_VARIANT'
                    ? '主形象'
                    : item.visual.resolvedImageSource === 'LEGACY_FALLBACK'
                      ? '旧图片回退'
                      : '未解析'}
              </div>
            ) : null}
          </div>
          <Button
            size="small"
            onClick={() => onManageVisual(type, item)}
            aria-label={`管理${item.name}视觉形象`}
          >
            视觉形象
          </Button>
        </Flex>
      </div>
    </div>
  </article>
);

const ProductionWorkbenchSettings = () => {
  const params = useParams<{ id: string }>();
  const { formatMessage } = useIntl();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const messageRef = useRef(message);
  messageRef.current = message;
  const [workspace, setWorkspace] = useState<ScriptWorkspace>(() =>
    emptyWorkspace(projectId || 0),
  );
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeType, setActiveType] = useState<ElementType>('CHARACTER');
  const [processingAction, setProcessingAction] = useState<string>();
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();
  const [candidates, setCandidates] = useState<AssetCandidate[]>([]);
  const [candidateTargets, setCandidateTargets] = useState<
    Record<number, number | undefined>
  >({});
  const [visualAsset, setVisualAsset] = useState<{
    type: ElementType;
    item: AssetRecord;
  }>();
  const [newVariantName, setNewVariantName] = useState('');
  const [selectedEpisodes, setSelectedEpisodes] = useState<
    Record<number, number[]>
  >({});

  const reload = async () => {
    const [workspaceResponse, candidateResponse] = await Promise.all([
      queryScriptWorkspace(projectId),
      queryAssetCandidates(projectId, { reviewStatus: 'PENDING_REVIEW' }),
    ]);
    setWorkspace({ ...emptyWorkspace(projectId), ...workspaceResponse.data });
    setCandidates(candidateResponse.data?.items ?? []);
    return workspaceResponse.data;
  };

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    setLoading(true);
    Promise.all([
      queryScriptWorkspace(projectId),
      queryAssetCandidates(projectId, { reviewStatus: 'PENDING_REVIEW' }),
    ])
      .then(([response, candidateResponse]) => {
        if (active) {
          setWorkspace({ ...emptyWorkspace(projectId), ...response.data });
          setCandidates(candidateResponse.data?.items ?? []);
        }
      })
      .catch(() => {
        if (active) {
          messageRef.current.error('设定页加载失败');
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

  const assetsByType = useMemo(
    () => ({
      CHARACTER: workspace.characters,
      SCENE: workspace.scenes,
      PROP: workspace.props,
    }),
    [workspace.characters, workspace.props, workspace.scenes],
  );

  const filterAssets = (type: ElementType, items: AssetRecord[]) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (!normalizedKeyword) {
      return items;
    }
    return items.filter((item) =>
      getSearchText(type, item).toLowerCase().includes(normalizedKeyword),
    );
  };

  const applyWorkspace = (
    nextWorkspace: ScriptWorkspace | undefined,
    successText: string,
  ) => {
    setWorkspace({ ...emptyWorkspace(projectId), ...nextWorkspace });
    message.success(successText);
  };

  const extractAssets = async (type: ElementType) => {
    setProcessingAction(`extract-${type}`);
    try {
      const response = await extractScriptElements(projectId, {
        elementType: type,
      });
      if (!response.data?.id) {
        throw new Error('AI execution identity is missing');
      }
      setActiveExecution(response.data);
      const terminal = await aiExecutionTaskService.poll(
        Number(localStorage.getItem('currentTenantId')),
        response.data.id,
        setActiveExecution,
      );
      setActiveExecution(terminal);
      if (terminal.status === 'SUCCEEDED') {
        const nextWorkspace = await reload();
        applyWorkspace(
          nextWorkspace,
          `${elementLabels[type]}已提取，结果等待审核`,
        );
      }
    } catch {
      message.error('AI提取失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  const decideCandidate = async (
    candidate: AssetCandidate,
    decisionType: 'ACCEPT_NEW' | 'ACCEPT_MERGE' | 'RETARGET' | 'REJECT',
  ) => {
    const targetAssetId =
      decisionType === 'ACCEPT_MERGE'
        ? (candidate.proposedTargetId ?? undefined)
        : decisionType === 'RETARGET'
          ? candidateTargets[candidate.id]
          : undefined;
    setProcessingAction(`candidate-${candidate.id}`);
    try {
      await decideAssetCandidate(projectId, candidate.id, {
        decisionType,
        targetAssetId,
        idempotencyKey: crypto.randomUUID(),
      });
      await reload();
      message.success('识别结果已处理');
    } catch {
      message.error('识别结果处理失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  const addVariant = async () => {
    if (!visualAsset || !newVariantName.trim()) return;
    try {
      await createVisualVariant(
        projectId,
        visualAsset.type,
        visualAsset.item.id,
        {
          name: newVariantName.trim(),
          sourceType: 'MANUAL',
          generationStatus: 'NOT_STARTED',
        },
      );
      setNewVariantName('');
      await reload();
      message.success('视觉形象已新增');
    } catch {
      message.error('新增视觉形象失败');
    }
  };

  const mutateVariant = async (
    action: () => Promise<unknown>,
    successText: string,
  ) => {
    try {
      await action();
      const next = await reload();
      if (visualAsset) {
        const list =
          visualAsset.type === 'CHARACTER'
            ? next.characters
            : visualAsset.type === 'SCENE'
              ? next.scenes
              : next.props;
        const item = list.find((asset) => asset.id === visualAsset.item.id);
        if (item) setVisualAsset({ type: visualAsset.type, item });
      }
      message.success(successText);
    } catch {
      message.error('视觉形象操作失败');
    }
  };

  const generateVariant = (variant: VisualVariant) =>
    mutateVariant(
      () =>
        createAiImageTask(projectId, {
          taskType: visualAsset?.type ?? 'CHARACTER',
          targetType: 'VISUAL_VARIANT',
          targetId: variant.id,
          prompt: variant.prompt || variant.appearance || variant.name,
          aspectRatio: visualAsset?.type === 'CHARACTER' ? '3:4' : '16:9',
          imageCount: 1,
        }),
      variant.generationStatus === 'FAILED' ? '已重新提交生成' : '已提交生成',
    );

  const confirmAsset = async (type: ElementType, id: number) => {
    setProcessingAction(`confirm-${type}-${id}`);
    try {
      const response = await confirmScriptElement(projectId, type, id);
      applyWorkspace(response.data, '设定已确认');
    } catch {
      message.error('确认设定失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  const confirmAssets = async (type: ElementType, items: AssetRecord[]) => {
    const pendingItems = items.filter((item) => item.status !== 'CONFIRMED');
    if (!pendingItems.length) {
      return;
    }
    setProcessingAction(`confirm-all-${type}`);
    try {
      let nextWorkspace: ScriptWorkspace | undefined;
      for (const item of pendingItems) {
        const response = await confirmScriptElement(projectId, type, item.id);
        nextWorkspace = response.data;
      }
      applyWorkspace(nextWorkspace, `${elementLabels[type]}已批量确认`);
    } catch {
      message.error('批量确认失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  const deleteAsset = async (type: ElementType, id: number) => {
    setProcessingAction(`delete-${type}-${id}`);
    try {
      const response = await deleteScriptElement(projectId, type, id);
      applyWorkspace(response.data, '设定已删除');
    } catch {
      message.error('删除设定失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  const saveAsset = async (type: ElementType, item: AssetRecord) => {
    setProcessingAction(`save-${type}-${item.id}`);
    try {
      const response = await updateScriptElement(projectId, type, item.id, {
        ...item,
        status: 'DRAFT',
      });
      applyWorkspace(response.data, '设定已保存');
    } catch {
      message.error('保存设定失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

  if (!projectId) {
    return null;
  }

  return (
    <div
      style={{
        minHeight: 'calc(100vh - 100px)',
        padding: '18px 28px 70px',
        background: 'var(--app-color-bg-layout)',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ maxWidth: 1540, margin: '0 auto' }}>
        <Flex
          justify="space-between"
          align="center"
          style={{ marginBottom: 18 }}
        >
          <div>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 18 }}>
              设定资产
            </Typography.Title>
            <Typography.Text type="secondary">
              {loading
                ? '正在加载设定内容...'
                : '角色、场景、道具统一管理，确认后进入分镜'}
            </Typography.Text>
          </div>
          <div
            style={{ color: 'var(--app-color-text-secondary)', fontSize: 13 }}
          >
            共{' '}
            {workspace.characters.length +
              workspace.scenes.length +
              workspace.props.length}{' '}
            项设定
          </div>
        </Flex>

        {activeExecution ? (
          <div
            style={{
              background: 'var(--app-color-primary-bg)',
              border: '1px solid var(--app-color-border-secondary)',
              borderRadius: 8,
              padding: '12px 16px',
              marginBottom: 16,
            }}
          >
            <AiExecutionStatus task={activeExecution} />
          </div>
        ) : null}

        {candidates.length ? (
          <section
            style={{
              marginBottom: 18,
              padding: 16,
              background: '#fff',
              border: '1px solid var(--app-color-border)',
              borderRadius: 10,
            }}
          >
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {formatMessage({
                id: 'pages.productionWorkbench.assets.reviewQueue',
                defaultMessage: '待审核识别结果',
              })}
            </Typography.Title>
            <Typography.Paragraph type="secondary">
              AI
              原始结果不会直接写入正式资产库。请先检查无效字段、重复分组和建议合并目标。
            </Typography.Paragraph>
            <div style={{ display: 'grid', gap: 10 }}>
              {candidates.map((candidate) => {
                const typeAssets = assetsByType[candidate.assetType];
                let errors: string[] = [];
                try {
                  const parsed = JSON.parse(
                    candidate.validationErrorsJson || '[]',
                  );
                  errors = Array.isArray(parsed)
                    ? parsed.map(String)
                    : [String(parsed)];
                } catch {
                  errors = [candidate.validationErrorsJson || '字段校验失败'];
                }
                return (
                  <div
                    key={candidate.id}
                    style={{
                      border: '1px solid var(--app-color-border-secondary)',
                      borderRadius: 8,
                      padding: 12,
                    }}
                  >
                    <Flex justify="space-between" align="center" gap={12}>
                      <div>
                        <Typography.Text strong>
                          {candidate.name ||
                            `未命名${elementLabels[candidate.assetType]}`}
                        </Typography.Text>
                        <Tag
                          color={
                            candidate.validationStatus === 'VALID'
                              ? 'green'
                              : 'red'
                          }
                        >
                          {candidate.validationStatus === 'VALID'
                            ? '可审核'
                            : '字段无效'}
                        </Tag>
                        {candidate.duplicateGroupKey ? (
                          <Tag>重复组：{candidate.duplicateGroupKey}</Tag>
                        ) : null}
                        {candidate.proposedTargetId ? (
                          <span style={{ fontSize: 12 }}>
                            建议合并到 #{candidate.proposedTargetId}
                            {candidate.matchConfidence != null
                              ? `（${Math.round(candidate.matchConfidence * 100)}%）`
                              : ''}
                          </span>
                        ) : null}
                        {errors.map((error) => (
                          <div
                            key={error}
                            style={{
                              color: 'var(--app-color-error)',
                              fontSize: 12,
                            }}
                          >
                            {error}
                          </div>
                        ))}
                      </div>
                      <Flex gap={6} wrap>
                        <Button
                          size="small"
                          disabled={candidate.validationStatus !== 'VALID'}
                          onClick={() =>
                            decideCandidate(candidate, 'ACCEPT_NEW')
                          }
                          aria-label={`新建${candidate.name || candidate.id}`}
                        >
                          接受为新资产
                        </Button>
                        {candidate.proposedTargetId ? (
                          <Button
                            size="small"
                            onClick={() =>
                              decideCandidate(candidate, 'ACCEPT_MERGE')
                            }
                            aria-label={`合并${candidate.name || candidate.id}`}
                          >
                            接受合并
                          </Button>
                        ) : null}
                        <select
                          aria-label={`重定向${candidate.name || candidate.id}`}
                          value={candidateTargets[candidate.id] ?? ''}
                          onChange={(event) =>
                            setCandidateTargets((current) => ({
                              ...current,
                              [candidate.id]: event.target.value
                                ? Number(event.target.value)
                                : undefined,
                            }))
                          }
                        >
                          <option value="">选择正式资产</option>
                          {typeAssets.map((asset) => (
                            <option key={asset.id} value={asset.id}>
                              {asset.name}
                            </option>
                          ))}
                        </select>
                        <Button
                          size="small"
                          disabled={!candidateTargets[candidate.id]}
                          onClick={() => decideCandidate(candidate, 'RETARGET')}
                        >
                          重定向合并
                        </Button>
                        <Button
                          size="small"
                          danger
                          onClick={() => decideCandidate(candidate, 'REJECT')}
                        >
                          拒绝
                        </Button>
                      </Flex>
                    </Flex>
                  </div>
                );
              })}
            </div>
          </section>
        ) : null}

        {visualAsset ? (
          <section
            style={{
              marginBottom: 18,
              padding: 16,
              background: '#fff',
              border: '1px solid var(--app-color-primary)',
              borderRadius: 10,
            }}
          >
            <Flex justify="space-between" align="center">
              <Typography.Title level={5} style={{ margin: 0 }}>
                {visualAsset.item.name} ·{' '}
                {formatMessage({
                  id: 'pages.productionWorkbench.assets.variantsAndBindings',
                  defaultMessage: '视觉形象与剧集绑定',
                })}
              </Typography.Title>
              <Button size="small" onClick={() => setVisualAsset(undefined)}>
                关闭
              </Button>
            </Flex>
            <Flex gap={8} style={{ margin: '12px 0' }}>
              <Input
                aria-label="新视觉形象名称"
                value={newVariantName}
                onChange={(event) => setNewVariantName(event.target.value)}
                placeholder="例如：婚礼礼服、雨夜造型"
              />
              <Button type="primary" onClick={addVariant}>
                新增视觉形象
              </Button>
            </Flex>
            <div style={{ display: 'grid', gap: 10 }}>
              {(visualAsset.item.visual?.variants ?? []).map((variant) => (
                <div
                  key={variant.id}
                  style={{
                    border: '1px solid var(--app-color-border-secondary)',
                    borderRadius: 8,
                    padding: 12,
                  }}
                >
                  <Flex justify="space-between" align="flex-start" gap={12}>
                    <div>
                      <Typography.Text strong>{variant.name}</Typography.Text>
                      {variant.primary ? <Tag color="blue">主形象</Tag> : null}
                      <Tag
                        color={
                          variant.generationStatus === 'FAILED'
                            ? 'red'
                            : variant.usable
                              ? 'green'
                              : 'default'
                        }
                      >
                        {variant.generationStatus}
                      </Tag>
                      {variant.errorMessage ? (
                        <div style={{ color: 'var(--app-color-error)' }}>
                          {variant.errorMessage}
                        </div>
                      ) : null}
                      <Input
                        defaultValue={variant.prompt || ''}
                        aria-label={`${variant.name}提示词`}
                        placeholder="视觉生成提示词"
                        onBlur={(event) => {
                          if (event.target.value !== (variant.prompt || '')) {
                            void mutateVariant(
                              () =>
                                updateVisualVariant(projectId, variant.id, {
                                  ...variant,
                                  prompt: event.target.value,
                                }),
                              '视觉形象已保存',
                            );
                          }
                        }}
                        style={{ marginTop: 8 }}
                      />
                    </div>
                    <Flex gap={6} wrap>
                      {!variant.primary ? (
                        <Button
                          size="small"
                          onClick={() =>
                            mutateVariant(
                              () =>
                                selectPrimaryVisualVariant(
                                  projectId,
                                  variant.id,
                                ),
                              '主形象已更新',
                            )
                          }
                        >
                          设为主形象
                        </Button>
                      ) : null}
                      <Button
                        size="small"
                        onClick={() => generateVariant(variant)}
                      >
                        {variant.generationStatus === 'FAILED'
                          ? '重新生成'
                          : '生成图片'}
                      </Button>
                      <Button
                        size="small"
                        danger
                        onClick={() =>
                          mutateVariant(
                            () => deleteVisualVariant(projectId, variant.id),
                            '视觉形象已删除',
                          )
                        }
                      >
                        删除
                      </Button>
                    </Flex>
                  </Flex>
                  <fieldset style={{ marginTop: 10, border: 0, padding: 0 }}>
                    <legend
                      style={{
                        fontSize: 12,
                        color: 'var(--app-color-text-secondary)',
                      }}
                    >
                      作为以下剧集的首选形象
                    </legend>
                    <Flex gap={12} wrap>
                      {(workspace.episodes ?? []).map((episode) => {
                        const episodeId = episode.episodeId;
                        if (!episodeId) return null;
                        return (
                          <label key={episodeId}>
                            <input
                              type="checkbox"
                              aria-label={`第${episode.episodeNo}集 ${episode.title}`}
                              checked={(
                                selectedEpisodes[variant.id] ?? []
                              ).includes(episodeId)}
                              onChange={(event) =>
                                setSelectedEpisodes((current) => ({
                                  ...current,
                                  [variant.id]: event.target.checked
                                    ? [
                                        ...(current[variant.id] ?? []),
                                        episodeId,
                                      ]
                                    : (current[variant.id] ?? []).filter(
                                        (id) => id !== episodeId,
                                      ),
                                }))
                              }
                            />
                            第{episode.episodeNo}集 {episode.title}
                          </label>
                        );
                      })}
                    </Flex>
                    <Button
                      size="small"
                      aria-label={`绑定${variant.name}到剧集`}
                      style={{ marginTop: 8 }}
                      onClick={() =>
                        mutateVariant(
                          () =>
                            bindVisualVariantEpisodes(projectId, variant.id, {
                              episodeIds: selectedEpisodes[variant.id] ?? [],
                              preferred: true,
                            }),
                          '剧集绑定已保存',
                        )
                      }
                    >
                      保存剧集绑定
                    </Button>
                  </fieldset>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            borderBottom: '1px solid var(--app-color-border)',
            marginBottom: 16,
          }}
        >
          {assetSections.map((section) => {
            const count = assetsByType[section.type].length;
            const completed = assetsByType[section.type].filter(
              (item) => item.status === 'CONFIRMED',
            ).length;
            const active = activeType === section.type;
            return (
              <button
                key={section.type}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setActiveType(section.type)}
                style={{
                  border: 0,
                  borderBottom: active
                    ? '2px solid var(--app-color-primary)'
                    : '2px solid transparent',
                  background: 'transparent',
                  color: active
                    ? 'var(--app-color-primary-active)'
                    : 'var(--app-color-text-secondary)',
                  padding: '10px 14px',
                  cursor: 'pointer',
                  fontWeight: active ? 600 : 400,
                }}
              >
                {elementLabels[section.type]}
                <span
                  style={{
                    marginLeft: 6,
                    fontSize: 12,
                    color: active
                      ? 'var(--app-color-primary)'
                      : 'var(--app-color-text-tertiary)',
                  }}
                >
                  {completed}/{count}
                </span>
              </button>
            );
          })}
        </div>

        {(() => {
          const section =
            assetSections.find((item) => item.type === activeType) ??
            assetSections[0];
          const items = filterAssets(section.type, assetsByType[section.type]);
          return (
            <section>
              <Flex
                justify="space-between"
                align="center"
                style={{ marginBottom: 14 }}
              >
                <div>
                  <Typography.Text strong style={{ fontSize: 16 }}>
                    {section.title}
                  </Typography.Text>
                  <span
                    style={{
                      marginLeft: 10,
                      color: 'var(--app-color-text-tertiary)',
                      fontSize: 13,
                    }}
                  >
                    {items.length} 项
                  </span>
                </div>
                <Flex gap={8}>
                  <Input
                    aria-label="搜索设定资产"
                    prefix={<SearchOutlined />}
                    value={keyword}
                    onChange={(event) => setKeyword(event.target.value)}
                    placeholder="搜索名称、类型、提示词"
                    style={{ width: 250, height: 34, borderRadius: 8 }}
                  />
                  <Button
                    icon={<CheckOutlined />}
                    disabled={
                      !items.some((item) => item.status !== 'CONFIRMED')
                    }
                    loading={processingAction === `confirm-all-${section.type}`}
                    onClick={() => confirmAssets(section.type, items)}
                  >
                    批量确认
                  </Button>
                  <Button
                    type="primary"
                    icon={<RobotOutlined />}
                    loading={processingAction === `extract-${section.type}`}
                    onClick={() => extractAssets(section.type)}
                  >
                    AI提取{elementLabels[section.type]}
                  </Button>
                </Flex>
              </Flex>
              {items.length ? (
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fill, minmax(280px, 1fr))',
                    gap: 16,
                  }}
                >
                  {items.map((item) => (
                    <AssetCard
                      key={`${section.type}-${item.id}`}
                      item={item}
                      type={section.type}
                      onConfirm={confirmAsset}
                      onDelete={deleteAsset}
                      onSave={saveAsset}
                      onManageVisual={(type, item) => {
                        setVisualAsset({ type, item });
                        const bindings = item.visual?.episodeBindings ?? [];
                        setSelectedEpisodes(
                          Object.fromEntries(
                            (item.visual?.variants ?? []).map((variant) => [
                              variant.id,
                              bindings
                                .filter(
                                  (binding) =>
                                    binding.variantId === variant.id &&
                                    binding.preferred,
                                )
                                .map((binding) => binding.episodeId),
                            ]),
                          ),
                        );
                      }}
                    />
                  ))}
                </div>
              ) : (
                <div
                  style={{
                    padding: '56px 0',
                    background: '#fff',
                    border: '1px dashed var(--app-color-border-secondary)',
                    borderRadius: 10,
                  }}
                >
                  <Empty
                    description={`暂无${elementLabels[section.type]}设定`}
                  />
                </div>
              )}
            </section>
          );
        })()}
      </div>
    </div>
  );
};

export default ProductionWorkbenchSettings;
