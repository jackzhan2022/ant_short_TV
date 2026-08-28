import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  RobotOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useParams } from '@umijs/max';
import { App, Button, Empty, Flex, Input, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import AiExecutionStatus from '@/components/AiExecutionStatus';
import { aiExecutionTaskService } from '@/services/ai-execution/task';
import {
  type CharacterAsset,
  confirmScriptElement,
  deleteScriptElement,
  extractScriptElements,
  type PropAsset,
  queryScriptWorkspace,
  type SceneAsset,
  type ScriptElementType,
  type ScriptWorkspace,
  updateScriptElement,
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
}: {
  item: AssetRecord;
  type: ElementType;
  onConfirm: (type: ElementType, id: number) => void;
  onDelete: (type: ElementType, id: number) => void;
  onSave: (type: ElementType, item: AssetRecord) => void;
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
        {item.name.slice(0, 1)}
      </div>
      <div style={{ padding: '13px 16px 14px' }}>
        <Flex justify="space-between" align="flex-start" gap={12}>
          <div>
            <Typography.Text strong style={{ fontSize: 15, color: 'var(--app-color-text)' }}>
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
      </div>
    </div>
  </article>
);

const ProductionWorkbenchSettings = () => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const [workspace, setWorkspace] = useState<ScriptWorkspace>(() =>
    emptyWorkspace(projectId || 0),
  );
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeType, setActiveType] = useState<ElementType>('CHARACTER');
  const [processingAction, setProcessingAction] = useState<string>();
  const [activeExecution, setActiveExecution] =
    useState<API.AiExecutionResponse>();

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    setLoading(true);
    queryScriptWorkspace(projectId)
      .then((response) => {
        if (active) {
          setWorkspace({ ...emptyWorkspace(projectId), ...response.data });
        }
      })
      .catch(() => {
        if (active) {
          message.error('设定页加载失败');
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
  }, [message, projectId]);

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
        const workspaceResponse = await queryScriptWorkspace(projectId);
        applyWorkspace(workspaceResponse.data, `${elementLabels[type]}已提取`);
      }
    } catch {
      message.error('AI提取失败');
    } finally {
      setProcessingAction(undefined);
    }
  };

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
              {loading ? '正在加载设定内容...' : '角色、场景、道具统一管理，确认后进入分镜'}
            </Typography.Text>
          </div>
          <div style={{ color: 'var(--app-color-text-secondary)', fontSize: 13 }}>
            共 {workspace.characters.length + workspace.scenes.length + workspace.props.length} 项设定
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
                  borderBottom: active ? '2px solid var(--app-color-primary)' : '2px solid transparent',
                  background: 'transparent',
                  color: active ? 'var(--app-color-primary-active)' : 'var(--app-color-text-secondary)',
                  padding: '10px 14px',
                  cursor: 'pointer',
                  fontWeight: active ? 600 : 400,
                }}
              >
                {elementLabels[section.type]}
                <span style={{ marginLeft: 6, fontSize: 12, color: active ? 'var(--app-color-primary)' : 'var(--app-color-text-tertiary)' }}>
                  {completed}/{count}
                </span>
              </button>
            );
          })}
        </div>

        {(() => {
          const section = assetSections.find((item) => item.type === activeType) ?? assetSections[0];
          const items = filterAssets(section.type, assetsByType[section.type]);
          return (
            <section>
              <Flex justify="space-between" align="center" style={{ marginBottom: 14 }}>
                <div>
                  <Typography.Text strong style={{ fontSize: 16 }}>{section.title}</Typography.Text>
                  <span style={{ marginLeft: 10, color: 'var(--app-color-text-tertiary)', fontSize: 13 }}>{items.length} 项</span>
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
                    disabled={!items.some((item) => item.status !== 'CONFIRMED')}
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
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
                  {items.map((item) => (
                    <AssetCard
                      key={`${section.type}-${item.id}`}
                      item={item}
                      type={section.type}
                      onConfirm={confirmAsset}
                      onDelete={deleteAsset}
                      onSave={saveAsset}
                    />
                  ))}
                </div>
              ) : (
                <div style={{ padding: '56px 0', background: '#fff', border: '1px dashed var(--app-color-border-secondary)', borderRadius: 10 }}>
                  <Empty description={`暂无${elementLabels[section.type]}设定`} />
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
