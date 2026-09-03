import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { useIntl, useParams } from '@umijs/max';
import {
  App,
  Button,
  Drawer,
  Empty,
  Flex,
  Input,
  Modal,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import {
  type AssetCandidate,
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

const assetSections = [
  { type: 'CHARACTER' as const, title: '角色设定' },
  { type: 'SCENE' as const, title: '场景设定' },
  { type: 'PROP' as const, title: '道具设定' },
];

type CandidateFilter = 'ALL' | 'MERGE' | 'NEW' | 'INVALID';

const getCandidateErrors = (candidate: AssetCandidate) => {
  try {
    const parsed = JSON.parse(candidate.validationErrorsJson || '[]');
    return Array.isArray(parsed) ? parsed.map(String) : [String(parsed)];
  } catch {
    return [candidate.validationErrorsJson || '字段校验失败'];
  }
};

const getCandidateFields = (candidate: AssetCandidate) => {
  try {
    const parsed: unknown = JSON.parse(candidate.candidateJson || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return [];
    }
    return Object.entries(parsed)
      .filter(([, value]) => value != null && value !== '')
      .slice(0, 3)
      .map(([key, value]) => [key, String(value)] as const);
  } catch {
    return [];
  }
};

const getCandidateCategory = (candidate: AssetCandidate): CandidateFilter => {
  if (candidate.validationStatus !== 'VALID') return 'INVALID';
  return candidate.proposedTargetId ? 'MERGE' : 'NEW';
};

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
}) => {
  const [imageHovered, setImageHovered] = useState(false);
  const [actionMenuOpen, setActionMenuOpen] = useState(false);
  const bindings = item.visual?.episodeBindings ?? [];
  const variantEpisodes = (item.visual?.variants ?? []).flatMap((variant) => {
    const episodeNos = [
      ...new Set(
        bindings
          .filter(
            (binding) =>
              binding.variantId === variant.id && binding.status === 'ACTIVE',
          )
          .map((binding) => binding.episodeNo),
      ),
    ];
    return episodeNos.length
      ? [
          {
            id: variant.id,
            label: `${variant.name}：第${episodeNos.join('、')}集`,
          },
        ]
      : [];
  });
  const episodeCount = new Set(
    bindings
      .filter((binding) => binding.status === 'ACTIVE')
      .map((binding) => binding.episodeId),
  ).size;
  const pendingCount = (item.visual?.variants ?? []).filter(
    (variant) =>
      variant.generationStatus === 'NOT_STARTED' ||
      variant.generationStatus === 'FAILED',
  ).length;
  const visualLabel = type === 'CHARACTER' ? '变装' : '视觉形象';

  return (
    <article style={{ minWidth: 0 }}>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <div
          data-testid={`asset-image-${type}-${item.id}`}
          onMouseEnter={() => setImageHovered(true)}
          onMouseLeave={() => {
            setImageHovered(false);
            setActionMenuOpen(false);
          }}
          style={{
            display: 'grid',
            placeItems: 'end start',
            position: 'relative',
            aspectRatio: '2 / 1',
            padding: item.visual?.resolvedImageUrl ? 0 : '16px 18px',
            border: '1px solid var(--app-color-border-secondary)',
            borderRadius: 8,
            overflow: 'hidden',
            background: 'var(--app-color-fill-secondary)',
            color: 'var(--app-color-text-tertiary)',
            fontSize: 34,
            fontWeight: 700,
          }}
        >
          {item.visual?.resolvedImageUrl ? (
            <img
              src={item.visual.resolvedImageUrl}
              alt={`${item.name}当前视觉形象`}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            item.name.slice(0, 1)
          )}
          <span
            style={{
              position: 'absolute',
              top: 8,
              left: 8,
              padding: '3px 8px',
              borderRadius: 4,
              background: 'var(--app-color-text)',
              color: 'var(--app-color-bg-container)',
              fontSize: 12,
              fontWeight: 500,
              opacity: 0.7,
            }}
          >
            {item.status === 'CONFIRMED' ? '已确认' : '待确认'}
          </span>
          {imageHovered || actionMenuOpen ? (
            <div style={{ position: 'absolute', top: 8, right: 8 }}>
              <Button
                size="small"
                aria-label={`${item.name}资产操作`}
                icon={<EditOutlined />}
                onClick={() => setActionMenuOpen((open) => !open)}
              >
                操作
              </Button>
              {actionMenuOpen ? (
                <Flex
                  vertical
                  gap={4}
                  style={{
                    position: 'absolute',
                    top: 34,
                    right: 0,
                    minWidth: 104,
                    padding: 4,
                    background: 'var(--app-color-bg-elevated)',
                    border: '1px solid var(--app-color-border)',
                    borderRadius: 6,
                    boxShadow: '0 4px 14px rgb(41 43 61 / 12%)',
                  }}
                >
                  <Button
                    type="text"
                    size="small"
                    icon={<CheckOutlined />}
                    aria-label={`确认${item.name}`}
                    onClick={() => onConfirm(type, item.id)}
                  >
                    确认资产
                  </Button>
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    aria-label={`保存${item.name}`}
                    onClick={() => onSave(type, item)}
                  >
                    保存修改
                  </Button>
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`删除${item.name}`}
                    onClick={() => onDelete(type, item.id)}
                  >
                    删除资产
                  </Button>
                </Flex>
              ) : null}
            </div>
          ) : null}
          {pendingCount ? (
            <span
              style={{
                position: 'absolute',
                bottom: 7,
                left: '50%',
                transform: 'translateX(-50%)',
                padding: '3px 10px',
                borderRadius: 6,
                background: 'var(--app-color-text)',
                color: 'var(--app-color-primary-hover)',
                fontSize: 12,
                fontWeight: 500,
                whiteSpace: 'nowrap',
              }}
            >
              {pendingCount} 个变装待生成
            </span>
          ) : null}
        </div>
        <div style={{ padding: '10px 0 0' }}>
          <Typography.Text
            strong
            ellipsis={{ tooltip: item.name }}
            style={{
              display: 'block',
              fontSize: 14,
              color: 'var(--app-color-text)',
            }}
          >
            {item.name}
          </Typography.Text>
          <div
            style={{
              display: '-webkit-box',
              marginTop: 4,
              overflow: 'hidden',
              color: 'var(--app-color-text-secondary)',
              fontSize: 12,
              lineHeight: '19px',
              WebkitBoxOrient: 'vertical',
              WebkitLineClamp: 2,
            }}
          >
            <span style={{ color: 'var(--app-color-text)' }}>
              {visualLabel} {item.visual?.variantCount ?? 0} 个
            </span>
            <span
              style={{ padding: '0 6px', color: 'var(--app-color-border)' }}
            >
              |
            </span>
            {getDescription(type, item) ||
              getSummary(type, item) ||
              '暂无设定描述'}
          </div>
          <Flex justify="space-between" align="center" style={{ marginTop: 8 }}>
            <span
              title={
                variantEpisodes.length
                  ? variantEpisodes.map((variant) => variant.label).join('\n')
                  : '尚未关联剧集'
              }
              style={{
                color: 'var(--app-color-text-tertiary)',
                fontSize: 12,
                cursor: variantEpisodes.length ? 'help' : 'default',
              }}
            >
              关联 {episodeCount} 集
            </span>
            <Button
              type="text"
              size="small"
              onClick={() => onManageVisual(type, item)}
              aria-label={`管理${item.name}视觉形象`}
              style={{ padding: 0, color: 'var(--app-color-primary)' }}
            >
              视觉形象
            </Button>
          </Flex>
        </div>
      </div>
    </article>
  );
};

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
  const [activeType, setActiveType] = useState<ElementType>('CHARACTER');
  const [processingAction, setProcessingAction] = useState<string>();
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();
  const [candidates, setCandidates] = useState<AssetCandidate[]>([]);
  const [reviewDrawerOpen, setReviewDrawerOpen] = useState(false);
  const [candidateFilter, setCandidateFilter] =
    useState<CandidateFilter>('ALL');
  const [activeCandidateId, setActiveCandidateId] = useState<number>();
  const [candidateTargets, setCandidateTargets] = useState<
    Record<number, number | undefined>
  >({});
  const [visualAsset, setVisualAsset] = useState<{
    type: ElementType;
    item: AssetRecord;
  }>();
  const [selectedVisualVariantId, setSelectedVisualVariantId] =
    useState<number>();
  const [newVariantName, setNewVariantName] = useState('');

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

  const activeAssets = assetsByType[activeType];
  const activeGeneration = activeAssets.reduce(
    (summary, item) => {
      Object.entries(item.visual?.generationSummary ?? {}).forEach(
        ([status, count]) => {
          if (status === 'COMPLETED') summary.completed += count;
          else if (status === 'FAILED') summary.failed += count;
          else summary.generating += count;
        },
      );
      return summary;
    },
    { completed: 0, generating: 0, failed: 0 },
  );

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
        padding: '24px 64px 96px',
        background: 'var(--app-color-bg-layout)',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ width: '100%', margin: '0 auto' }}>
        <section
          style={{
            marginBottom: 16,
            padding: '10px 16px',
            background: 'var(--app-color-primary-bg)',
            border: '1px solid var(--app-color-primary-bg)',
            borderRadius: 8,
            color: 'var(--app-color-primary)',
            fontSize: 13,
            lineHeight: '20px',
          }}
        >
          请确保角色、场景及道具已全部生成。点击角色图片可配置【变装】，未配置的变装将导致分镜无参考图可用，直接影响视频准确性。
        </section>

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

        <Drawer
          title={formatMessage({
            id: 'pages.productionWorkbench.assets.reviewQueue',
            defaultMessage: '待审核识别结果',
          })}
          placement="right"
          width={760}
          open={reviewDrawerOpen}
          onClose={() => setReviewDrawerOpen(false)}
          styles={{ body: { padding: 0 } }}
        >
          {(() => {
            const filteredCandidates = candidates.filter(
              (candidate) =>
                candidateFilter === 'ALL' ||
                getCandidateCategory(candidate) === candidateFilter,
            );
            const activeCandidate =
              filteredCandidates.find(
                (candidate) => candidate.id === activeCandidateId,
              ) ??
              filteredCandidates[0] ??
              candidates[0];
            if (!activeCandidate) {
              return <Empty description="暂无待审核资产" />;
            }
            const typeAssets = assetsByType[activeCandidate.assetType];
            const targetAsset = typeAssets.find(
              (asset) => asset.id === activeCandidate.proposedTargetId,
            );
            const targetRecord = targetAsset as unknown as
              | Record<string, unknown>
              | undefined;
            const candidateFields = getCandidateFields(activeCandidate);
            const errors = getCandidateErrors(activeCandidate);
            const category = getCandidateCategory(activeCandidate);
            const categoryLabel =
              category === 'MERGE'
                ? '建议合并'
                : category === 'NEW'
                  ? '可新建'
                  : '字段异常';
            const candidateName =
              activeCandidate.name ||
              `未命名${elementLabels[activeCandidate.assetType]}`;
            const summary = candidateFields.length
              ? candidateFields
                  .map(([key, value]) => `${key}：${value}`)
                  .join('；')
              : activeCandidate.aliases.map((alias) => alias.name).join('；') ||
                '暂无可展示的候选摘要';
            const categoryCount = (filter: CandidateFilter) =>
              filter === 'ALL'
                ? candidates.length
                : candidates.filter(
                    (candidate) => getCandidateCategory(candidate) === filter,
                  ).length;
            const comparisonRows = [
              ['名称', targetAsset?.name || '—', candidateName],
              [
                '匹配依据',
                activeCandidate.matchType || '—',
                activeCandidate.matchConfidence != null
                  ? `${Math.round(activeCandidate.matchConfidence * 100)}%`
                  : '—',
              ],
              ...candidateFields.map(([key, value]) => [
                key,
                String(targetRecord?.[key] ?? '—'),
                value,
              ]),
            ];
            return (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '218px minmax(0, 1fr)',
                  height: 'calc(100vh - 64px)',
                }}
              >
                <aside
                  style={{
                    overflow: 'auto',
                    background: 'var(--app-color-bg-layout)',
                    borderRight: '1px solid var(--app-color-border)',
                  }}
                >
                  <div
                    style={{
                      position: 'sticky',
                      top: 0,
                      zIndex: 1,
                      padding: 12,
                      background: 'var(--app-color-bg-layout)',
                      borderBottom:
                        '1px solid var(--app-color-border-secondary)',
                    }}
                  >
                    <Typography.Text strong style={{ fontSize: 13 }}>
                      审核队列
                    </Typography.Text>
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: 6,
                        marginTop: 10,
                      }}
                    >
                      {(
                        [
                          ['ALL', '全部'],
                          ['MERGE', '建议合并'],
                          ['NEW', '可新建'],
                          ['INVALID', '异常'],
                        ] as const
                      ).map(([filter, label]) => (
                        <button
                          key={filter}
                          type="button"
                          onClick={() => setCandidateFilter(filter)}
                          style={{
                            height: 28,
                            border: 0,
                            borderRadius: 5,
                            background:
                              candidateFilter === filter
                                ? 'var(--app-color-primary-bg)'
                                : 'transparent',
                            color:
                              candidateFilter === filter
                                ? 'var(--app-color-primary)'
                                : 'var(--app-color-text-secondary)',
                            fontSize: 12,
                            cursor: 'pointer',
                          }}
                        >
                          {label} {categoryCount(filter)}
                        </button>
                      ))}
                    </div>
                  </div>
                  {filteredCandidates.map((candidate) => {
                    const candidateCategory = getCandidateCategory(candidate);
                    const selected = candidate.id === activeCandidate.id;
                    const label =
                      candidateCategory === 'MERGE'
                        ? '建议合并'
                        : candidateCategory === 'NEW'
                          ? '可新建'
                          : '字段异常';
                    return (
                      <button
                        key={candidate.id}
                        type="button"
                        aria-label={`审核候选${candidate.name || candidate.id}`}
                        onClick={() => setActiveCandidateId(candidate.id)}
                        style={{
                          display: 'block',
                          width: '100%',
                          padding: '10px 12px',
                          border: 0,
                          borderLeft: selected
                            ? '2px solid var(--app-color-primary)'
                            : '2px solid transparent',
                          background: selected
                            ? 'var(--app-color-bg-container)'
                            : 'transparent',
                          color: 'var(--app-color-text)',
                          textAlign: 'left',
                          cursor: 'pointer',
                        }}
                      >
                        <Flex justify="space-between" gap={6}>
                          <span
                            style={{
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                              fontSize: 13,
                            }}
                          >
                            {candidate.name ||
                              `未命名${elementLabels[candidate.assetType]}`}
                          </span>
                          <span
                            style={{
                              flex: '0 0 auto',
                              color:
                                candidateCategory === 'INVALID'
                                  ? 'var(--app-color-primary-active)'
                                  : 'var(--app-color-primary)',
                              fontSize: 11,
                            }}
                          >
                            {label}
                          </span>
                        </Flex>
                        <span
                          style={{
                            display: 'block',
                            marginTop: 3,
                            overflow: 'hidden',
                            color: 'var(--app-color-text-tertiary)',
                            fontSize: 12,
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {candidate.matchConfidence != null
                            ? `匹配度 ${Math.round(candidate.matchConfidence * 100)}%`
                            : candidate.validationStatus === 'VALID'
                              ? '无重复候选'
                              : errors.join('；')}
                        </span>
                      </button>
                    );
                  })}
                </aside>
                <section
                  style={{ overflow: 'auto', padding: '20px 24px 30px' }}
                >
                  <Flex justify="space-between" align="flex-start" gap={12}>
                    <div>
                      <Flex gap={8} align="center">
                        <Tag color="blue">
                          {elementLabels[activeCandidate.assetType]}
                        </Tag>
                        <Typography.Text
                          type="secondary"
                          style={{ fontSize: 12 }}
                        >
                          {activeCandidate.matchConfidence != null
                            ? `匹配度 ${Math.round(activeCandidate.matchConfidence * 100)}%`
                            : categoryLabel}
                        </Typography.Text>
                      </Flex>
                      <Typography.Title
                        level={4}
                        style={{ margin: '7px 0 5px' }}
                      >
                        {candidateName}
                      </Typography.Title>
                    </div>
                    <Button
                      type="text"
                      size="small"
                      onClick={() => {
                        const index = filteredCandidates.findIndex(
                          (candidate) => candidate.id === activeCandidate.id,
                        );
                        const next =
                          filteredCandidates[index + 1] ??
                          filteredCandidates[0];
                        setActiveCandidateId(next?.id);
                      }}
                    >
                      下一条
                    </Button>
                  </Flex>
                  <Typography.Paragraph
                    type="secondary"
                    style={{ margin: 0, fontSize: 13, lineHeight: '21px' }}
                  >
                    {summary}
                  </Typography.Paragraph>
                  <div
                    style={{
                      marginTop: 18,
                      padding: 12,
                      background: 'var(--app-color-primary-bg)',
                      border: '1px solid var(--app-color-primary-bg)',
                      borderRadius: 8,
                    }}
                  >
                    <Typography.Text strong style={{ fontSize: 13 }}>
                      {category === 'MERGE' && targetAsset
                        ? `建议合并至「${targetAsset.name}」`
                        : category === 'NEW'
                          ? '建议作为新资产入库'
                          : '需补充字段后重新提取'}
                    </Typography.Text>
                    <Typography.Text
                      type="secondary"
                      style={{ display: 'block', marginTop: 2, fontSize: 12 }}
                    >
                      {category === 'MERGE'
                        ? '名称与现有资产匹配，请核对候选字段后确认。'
                        : category === 'NEW'
                          ? '未发现可合并的正式资产。'
                          : errors.join('；')}
                    </Typography.Text>
                  </div>
                  <Typography.Text
                    strong
                    style={{ display: 'block', marginTop: 20, fontSize: 14 }}
                  >
                    候选信息与正式资产对比
                  </Typography.Text>
                  <div
                    style={{
                      marginTop: 10,
                      overflow: 'hidden',
                      border: '1px solid var(--app-color-border-secondary)',
                      borderRadius: 8,
                    }}
                  >
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '86px 1fr 1fr',
                        gap: 10,
                        padding: '9px 12px',
                        background: 'var(--app-color-bg-layout)',
                        color: 'var(--app-color-text-tertiary)',
                        fontSize: 12,
                      }}
                    >
                      <span>字段</span>
                      <span>正式资产</span>
                      <span>候选识别</span>
                    </div>
                    {comparisonRows.map(([label, current, next]) => (
                      <div
                        key={`${label}-${next}`}
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '86px 1fr 1fr',
                          gap: 10,
                          padding: '9px 12px',
                          borderTop:
                            '1px solid var(--app-color-border-secondary)',
                          fontSize: 12,
                        }}
                      >
                        <span
                          style={{ color: 'var(--app-color-text-tertiary)' }}
                        >
                          {label}
                        </span>
                        <span
                          style={{ color: 'var(--app-color-text-secondary)' }}
                        >
                          {current}
                        </span>
                        <span>{next}</span>
                      </div>
                    ))}
                  </div>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr',
                      gap: 10,
                      marginTop: 18,
                    }}
                  >
                    <Button
                      type="primary"
                      disabled={category === 'INVALID'}
                      loading={
                        processingAction === `candidate-${activeCandidate.id}`
                      }
                      onClick={() =>
                        decideCandidate(
                          activeCandidate,
                          category === 'MERGE' ? 'ACCEPT_MERGE' : 'ACCEPT_NEW',
                        )
                      }
                    >
                      {category === 'MERGE' ? '确认合并' : '作为新资产入库'}
                    </Button>
                    <Button
                      disabled={category === 'INVALID'}
                      onClick={() =>
                        decideCandidate(activeCandidate, 'ACCEPT_NEW')
                      }
                      aria-label={`新建${activeCandidate.name || activeCandidate.id}`}
                    >
                      {category === 'MERGE' ? '作为新资产入库' : '查看相似资产'}
                    </Button>
                  </div>
                  <Flex justify="center" gap={16} style={{ marginTop: 12 }}>
                    <select
                      aria-label={`重定向${activeCandidate.name || activeCandidate.id}`}
                      value={candidateTargets[activeCandidate.id] ?? ''}
                      onChange={(event) =>
                        setCandidateTargets((current) => ({
                          ...current,
                          [activeCandidate.id]: event.target.value
                            ? Number(event.target.value)
                            : undefined,
                        }))
                      }
                    >
                      <option value="">更改合并目标</option>
                      {typeAssets.map((asset) => (
                        <option key={asset.id} value={asset.id}>
                          {asset.name}
                        </option>
                      ))}
                    </select>
                    <Button
                      type="text"
                      size="small"
                      disabled={
                        category === 'INVALID' ||
                        !candidateTargets[activeCandidate.id]
                      }
                      onClick={() =>
                        decideCandidate(activeCandidate, 'RETARGET')
                      }
                    >
                      重定向合并
                    </Button>
                    <Button
                      type="text"
                      size="small"
                      danger
                      onClick={() => decideCandidate(activeCandidate, 'REJECT')}
                    >
                      拒绝候选
                    </Button>
                  </Flex>
                </section>
              </div>
            );
          })()}
        </Drawer>

        {visualAsset
          ? (() => {
              const variants = visualAsset.item.visual?.variants ?? [];
              const selectedVariant =
                variants.find(
                  (variant) => variant.id === selectedVisualVariantId,
                ) ??
                variants.find((variant) => variant.primary) ??
                variants[0];
              const selectedVariantEpisodes = selectedVariant
                ? (visualAsset.item.visual?.episodeBindings ?? [])
                    .filter(
                      (binding) =>
                        binding.variantId === selectedVariant.id &&
                        binding.status === 'ACTIVE',
                    )
                    .sort((left, right) => left.episodeNo - right.episodeNo)
                : [];
              const episodeNumberLabel = selectedVariantEpisodes
                .map((binding) => binding.episodeNo)
                .join(' ');
              const episodeTooltip = selectedVariantEpisodes
                .map(
                  (binding) =>
                    `第${binding.episodeNo}集${binding.episodeTitle ? ` ${binding.episodeTitle}` : ''}`,
                )
                .join('、');
              return (
                <Modal
                  title="视觉形象画廊"
                  open
                  width={920}
                  footer={null}
                  onCancel={() => setVisualAsset(undefined)}
                >
                  <Typography.Title level={5} style={{ margin: '0 0 16px' }}>
                    {visualAsset.item.name} · 视觉形象管理
                  </Typography.Title>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '220px minmax(0, 1fr)',
                      gap: 20,
                      minHeight: 440,
                    }}
                  >
                    <aside
                      style={{
                        paddingRight: 16,
                        borderRight:
                          '1px solid var(--app-color-border-secondary)',
                      }}
                    >
                      <Flex gap={8} style={{ marginBottom: 12 }}>
                        <Input
                          aria-label="新视觉形象名称"
                          value={newVariantName}
                          onChange={(event) =>
                            setNewVariantName(event.target.value)
                          }
                          placeholder="新增变装名称"
                        />
                        <Button
                          type="primary"
                          aria-label="新增视觉形象"
                          onClick={addVariant}
                        >
                          新增
                        </Button>
                      </Flex>
                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '1fr 1fr',
                          gap: 8,
                        }}
                      >
                        {variants.map((variant) => {
                          const selected = variant.id === selectedVariant?.id;
                          return (
                            <button
                              key={variant.id}
                              type="button"
                              aria-label={`选择${variant.name}`}
                              onClick={() =>
                                setSelectedVisualVariantId(variant.id)
                              }
                              style={{
                                padding: 0,
                                overflow: 'hidden',
                                border: selected
                                  ? '2px solid var(--app-color-primary)'
                                  : '1px solid var(--app-color-border-secondary)',
                                borderRadius: 6,
                                background: 'var(--app-color-bg-container)',
                                textAlign: 'left',
                                cursor: 'pointer',
                              }}
                            >
                              <div
                                style={{
                                  display: 'grid',
                                  aspectRatio: '1 / 1',
                                  placeItems: 'center',
                                  overflow: 'hidden',
                                  background: 'var(--app-color-fill-secondary)',
                                  color: 'var(--app-color-text-tertiary)',
                                  fontSize: 22,
                                }}
                              >
                                {variant.currentImageUrl ? (
                                  <img
                                    src={variant.currentImageUrl}
                                    alt={`${variant.name}缩略图`}
                                    style={{
                                      width: '100%',
                                      height: '100%',
                                      objectFit: 'cover',
                                    }}
                                  />
                                ) : (
                                  variant.name.slice(0, 1)
                                )}
                              </div>
                              <span
                                style={{
                                  display: 'block',
                                  padding: '5px 6px',
                                  overflow: 'hidden',
                                  color: 'var(--app-color-text)',
                                  fontSize: 12,
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                {variant.name}
                                {variant.primary ? ' · 主图' : ''}
                              </span>
                            </button>
                          );
                        })}
                      </div>
                    </aside>
                    {selectedVariant ? (
                      <section>
                        <div
                          style={{
                            display: 'grid',
                            placeItems: 'center',
                            position: 'relative',
                            height: 230,
                            overflow: 'hidden',
                            border:
                              '1px solid var(--app-color-border-secondary)',
                            borderRadius: 8,
                            background: 'var(--app-color-fill-secondary)',
                            color: 'var(--app-color-text-tertiary)',
                            fontSize: 48,
                          }}
                        >
                          {selectedVariant.currentImageUrl ? (
                            <img
                              src={selectedVariant.currentImageUrl}
                              alt={`${selectedVariant.name}预览图`}
                              style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'contain',
                              }}
                            />
                          ) : (
                            selectedVariant.name.slice(0, 1)
                          )}
                          <span
                            role="status"
                            aria-label={`${selectedVariant.name}关联剧集`}
                            title={episodeTooltip || '暂未关联剧集'}
                            style={{
                              position: 'absolute',
                              top: 10,
                              left: 10,
                              maxWidth: 'calc(100% - 20px)',
                              overflow: 'hidden',
                              padding: '4px 8px',
                              borderRadius: 4,
                              background: 'rgb(0 0 0 / 52%)',
                              color: 'var(--app-color-bg-container)',
                              fontSize: 12,
                              lineHeight: '18px',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {episodeNumberLabel || '未关联'}
                          </span>
                        </div>
                        <Flex
                          justify="space-between"
                          align="center"
                          style={{ marginTop: 14 }}
                        >
                          <div>
                            <Typography.Text strong>
                              {selectedVariant.name}
                            </Typography.Text>
                            {selectedVariant.primary ? (
                              <Tag color="blue">主形象</Tag>
                            ) : null}
                            <Tag>{selectedVariant.generationStatus}</Tag>
                          </div>
                          <Flex gap={8}>
                            {!selectedVariant.primary ? (
                              <Button
                                size="small"
                                onClick={() =>
                                  mutateVariant(
                                    () =>
                                      selectPrimaryVisualVariant(
                                        projectId,
                                        selectedVariant.id,
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
                              onClick={() => generateVariant(selectedVariant)}
                            >
                              {selectedVariant.generationStatus === 'FAILED'
                                ? '重新生成'
                                : '生成图片'}
                            </Button>
                            <Button
                              size="small"
                              danger
                              onClick={() =>
                                mutateVariant(
                                  () =>
                                    deleteVisualVariant(
                                      projectId,
                                      selectedVariant.id,
                                    ),
                                  '视觉形象已删除',
                                )
                              }
                            >
                              删除
                            </Button>
                          </Flex>
                        </Flex>
                        {selectedVariant.errorMessage ? (
                          <Typography.Text
                            type="danger"
                            style={{ display: 'block', marginTop: 6 }}
                          >
                            {selectedVariant.errorMessage}
                          </Typography.Text>
                        ) : null}
                        <Input
                          defaultValue={selectedVariant.prompt || ''}
                          aria-label={`${selectedVariant.name}提示词`}
                          placeholder="视觉生成提示词"
                          onBlur={(event) => {
                            if (
                              event.target.value !==
                              (selectedVariant.prompt || '')
                            ) {
                              void mutateVariant(
                                () =>
                                  updateVisualVariant(
                                    projectId,
                                    selectedVariant.id,
                                    {
                                      ...selectedVariant,
                                      prompt: event.target.value,
                                    },
                                  ),
                                '视觉形象已保存',
                              );
                            }
                          }}
                          style={{ marginTop: 12 }}
                        />
                      </section>
                    ) : (
                      <Empty description="暂无视觉形象" />
                    )}
                  </div>
                </Modal>
              );
            })()
          : null}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 32,
            minHeight: 60,
            margin: '20px 0 16px',
            padding: '0 2px',
          }}
        >
          <div
            role="tablist"
            style={{ display: 'flex', alignSelf: 'stretch', gap: 28 }}
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
                    borderRadius: 0,
                    alignSelf: 'stretch',
                    background: 'transparent',
                    color: active
                      ? 'var(--app-color-primary)'
                      : 'var(--app-color-text-secondary)',
                    boxShadow: active
                      ? 'inset 0 -2px 0 var(--app-color-primary)'
                      : 'none',
                    padding: '0 2px',
                    cursor: 'pointer',
                    fontWeight: active ? 600 : 400,
                    fontSize: 18,
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
          <div
            role="toolbar"
            aria-label={`${elementLabels[activeType]}资产操作`}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: 14,
              color: 'var(--app-color-text-secondary)',
              fontSize: 13,
              whiteSpace: 'nowrap',
            }}
          >
            <span>
              {elementLabels[activeType]}总计 {activeAssets.length}
            </span>
            <span style={{ color: 'var(--app-color-border)' }}>|</span>
            <span>已完成 {activeGeneration.completed}</span>
            <span style={{ color: 'var(--app-color-border)' }}>|</span>
            <span>生成中 {activeGeneration.generating}</span>
            <span style={{ color: 'var(--app-color-border)' }}>|</span>
            <span>失败 {activeGeneration.failed}</span>
            {candidates.length ? (
              <Button
                type="text"
                size="small"
                onClick={() => setReviewDrawerOpen(true)}
              >
                审核资产 {candidates.length}
              </Button>
            ) : null}
            <Button
              type="text"
              size="small"
              icon={<CheckOutlined />}
              disabled={
                !activeAssets.some((item) => item.status !== 'CONFIRMED')
              }
              loading={processingAction === `confirm-all-${activeType}`}
              onClick={() => confirmAssets(activeType, activeAssets)}
            >
              批量确认
            </Button>
            <Button
              type="text"
              size="small"
              icon={<RobotOutlined />}
              aria-label={`AI提取${elementLabels[activeType]}`}
              loading={processingAction === `extract-${activeType}`}
              onClick={() => extractAssets(activeType)}
            >
              批量生成
            </Button>
          </div>
        </div>

        {(() => {
          const section =
            assetSections.find((item) => item.type === activeType) ??
            assetSections[0];
          const items = assetsByType[section.type];
          return (
            <section>
              {items.length ? (
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fill, minmax(360px, 1fr))',
                    gap: 20,
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
                        setSelectedVisualVariantId(
                          item.visual?.primaryVariant?.id ??
                            item.visual?.variants?.[0]?.id,
                        );
                      }}
                    />
                  ))}
                </div>
              ) : (
                <div
                  style={{
                    padding: '56px 0',
                    background: 'var(--app-color-bg-container)',
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
