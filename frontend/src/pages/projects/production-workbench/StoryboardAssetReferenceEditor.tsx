import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  rectSortingStrategy,
  sortableKeyboardCoordinates,
  SortableContext,
  useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  AudioOutlined,
  CheckOutlined,
  DeleteOutlined,
  HolderOutlined,
  PictureOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Button, Cascader, Empty, Flex, Input, Spin, Tag, Tooltip, Typography } from 'antd';
import LazyMediaImage from '@/components/LazyMediaImage';
import { type EffectiveStoryboardVoice, queryStoryboardCharacterVoice } from '@/pages/asset-library/audio-service';
import { type CSSProperties, lazy, Suspense, useEffect, useState } from 'react';
import type {
  CharacterAsset,
  PropAsset,
  SceneAsset,
  StoryboardAssetReference,
  VisualVariant,
} from './service';

type Asset = CharacterAsset | SceneAsset | PropAsset;
const CharacterVoiceModal = lazy(() => import('./CharacterVoiceModal'));
type AssetType = StoryboardAssetReference['assetType'];
type ClientKeyedReference = StoryboardAssetReference & { clientKey?: string };
type MaterialOption = {
  label: string;
  caption: string;
  value: number;
  thumbnail?: string | null;
  variant?: VisualVariant;
  isLeaf?: boolean;
  children?: MaterialOption[];
};

let nextDraftReferenceKey = 0;

const labels: Record<AssetType, string> = {
  CHARACTER: '角色',
  SCENE: '场景',
  PROP: '道具',
};

const statusLabel = (status: StoryboardAssetReference['resolutionStatus']) => ({
  RESOLVED: '已就绪',
  ASSET_PENDING: '待生成',
  UNRESOLVED: '待确认',
  SCENE_INCLUDED: '随场景呈现',
}[status]);

const statusColor = (status: StoryboardAssetReference['resolutionStatus']) => ({
  RESOLVED: 'success',
  ASSET_PENDING: 'warning',
  UNRESOLVED: 'error',
  SCENE_INCLUDED: 'default',
}[status]);

const variantsFor = (assets: Asset[], assetId?: number | null) => {
  const visual = assets.find((asset) => asset.id === assetId)?.visual;
  return [...new Map([visual?.primaryVariant, visual?.selectedVariant, ...(visual?.variants || [])]
    .filter((variant): variant is VisualVariant => Boolean(variant)).map((variant) => [variant.id, variant])).values()];
};

export const imageFor = (reference: StoryboardAssetReference, assets: Asset[]) => {
  if (reference.resolutionStatus === 'SCENE_INCLUDED') return undefined;
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = reference.variantId != null
    ? variantsFor(assets, reference.assetId)
      .find((candidate) => candidate.id === reference.variantId)
    : defaultVariant(asset);
  return variant?.currentImageThumbnailUrl
    || reference.imageThumbnailUrl || asset?.mainImageThumbnailUrl || undefined;
};

export const originalImageFor = (reference: StoryboardAssetReference, assets: Asset[]) => {
  const thumbnail = imageFor(reference, assets);
  if (!thumbnail) return undefined;
  const originalEndpoint = (url: string) => url.replace(
    /^(\/api\/projects\/\d+\/ai-image-results\/\d+)\/(?:thumbnail|display|image|file)(?:\?.*)?$/,
    '$1/download',
  );
  const original = originalEndpoint(thumbnail);
  if (original !== thumbnail) return original;
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = reference.variantId != null
    ? variantsFor(assets, reference.assetId).find((candidate) => candidate.id === reference.variantId)
    : defaultVariant(asset);
  const source = variant?.currentImageThumbnailUrl === thumbnail ? variant.currentImageUrl
    : reference.imageThumbnailUrl === thumbnail ? reference.imageUrl : undefined;
  return originalEndpoint(source || thumbnail);
};

export const referenceReady = (
  reference: StoryboardAssetReference,
  variant?: VisualVariant,
) => reference.resolutionStatus === 'RESOLVED'
  || reference.resolutionStatus === 'SCENE_INCLUDED'
  || (reference.resolutionStatus === 'ASSET_PENDING' && Boolean(variant?.usable));

const defaultVariant = (asset?: Asset): VisualVariant | undefined =>
  asset?.visual?.variants.find((variant) => variant.primary)
  || asset?.visual?.variants.find((variant) => variant.usable)
  || asset?.visual?.variants[0];

const normalize = (references: StoryboardAssetReference[]) => {
  const counters: Record<AssetType, number> = { CHARACTER: 0, SCENE: 0, PROP: 0 };
  return references.map((reference) => ({
    ...reference,
    sortOrder: counters[reference.assetType]++,
  }));
};

export const reorderReferencesWithinType = (
  references: StoryboardAssetReference[],
  assetType: AssetType,
  sourceIndex: number,
  destinationIndex: number,
) => {
  const sameType = references.filter((reference) => reference.assetType === assetType);
  if (
    sourceIndex < 0
    || destinationIndex < 0
    || sourceIndex >= sameType.length
    || destinationIndex >= sameType.length
    || sourceIndex === destinationIndex
  ) return references;

  const reordered = [...sameType];
  const [moved] = reordered.splice(sourceIndex, 1);
  reordered.splice(destinationIndex, 0, moved);
  let cursor = 0;
  return normalize(references.map((reference) =>
    reference.assetType === assetType ? reordered[cursor++] : reference));
};

const variantCaption = (assetName: string, variantName: string) =>
  (variantName.startsWith(assetName)
    ? variantName.slice(assetName.length).replace(/^[\s\-–—·/：:]+/, '').trim()
    : variantName) || variantName;

const cascaderOptionsFor = (assets: Asset[], canLoad: boolean): MaterialOption[] => assets.map((asset) => {
  const variants = variantsFor(assets, asset.id);
  return {
    label: asset.name,
    caption: asset.name,
    value: asset.id,
    thumbnail: asset.mainImageThumbnailUrl || variants.find((variant) => variant.primary)?.currentImageThumbnailUrl,
    isLeaf: !variants.length && !(canLoad && (!asset.visual || asset.visual.summaryOnly)),
    ...(variants.length ? {
      children: variants.map((variant) => ({
        label: variant.name, caption: variantCaption(asset.name, variant.name), value: variant.id,
        thumbnail: variant.currentImageThumbnailUrl, variant,
      })),
    } : {}),
  };
});

export const sortableIdFor = (
  reference: StoryboardAssetReference,
  assetType: AssetType,
  rowIndex: number,
) => {
  const clientKey = (reference as ClientKeyedReference).clientKey;
  return `${assetType}:${clientKey || reference.id || `draft-${reference.assetId ?? 'empty'}-${rowIndex}`}`;
};

function SortableAssetCard({
  id,
  storyboardNo,
  projectId,
  storyboardId,
  voiceRevision,
  assetType,
  rowIndex,
  reference,
  assets,
  onReplace,
  onRemove,
  onVoice,
  onConfigureVoice,
  onGenerateImage,
  isGenerating,
  onLoadVariants,
}: {
  id: string;
  storyboardNo: number;
  projectId?: number;
  storyboardId: number;
  voiceRevision: number;
  assetType: AssetType;
  rowIndex: number;
  reference: StoryboardAssetReference;
  assets: Asset[];
  onReplace: (next: StoryboardAssetReference) => void;
  onRemove: () => void;
  onVoice: (name: string) => void;
  onConfigureVoice?: (reference: StoryboardAssetReference) => void;
  onGenerateImage: () => void;
  isGenerating: boolean;
  onLoadVariants?: (
    assetType: AssetType, assetId: number, selectedVariantId?: number | null,
    current?: number, pageSize?: number,
  ) => Promise<void>;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const voiceKey = `${projectId}:${storyboardId}:${reference.assetId}:${reference.variantId ?? 'default'}:${reference.id ?? 'draft'}:${voiceRevision}`;
  const [voice, setVoice] = useState<{ key: string; data?: EffectiveStoryboardVoice; failed?: boolean }>();
  useEffect(() => {
    if (assetType !== 'CHARACTER' || !projectId || !reference.assetId) return;
    const controller = new AbortController();
    void queryStoryboardCharacterVoice(
      projectId, reference.assetId, { id: storyboardId, variantId: reference.variantId }, controller.signal,
    ).then((response) => {
      if (controller.signal.aborted) return;
      if (!response.success) throw new Error(response.errorMessage);
      setVoice({ key: voiceKey, data: response.data });
    }).catch(() => {
      if (!controller.signal.aborted) setVoice({ key: voiceKey, failed: true });
    });
    return () => controller.abort();
  }, [assetType, projectId, storyboardId, reference.assetId, reference.variantId, voiceKey]);
  const effectiveVoice = voice?.key === voiceKey ? voice : undefined;
  const [search, setSearch] = useState('');
  const [loadingAssetId, setLoadingAssetId] = useState<number>();
  const loadVariants = async (assetId: number, variantId?: number | null, current = 1) => {
    if (!onLoadVariants) return;
    setLoadingAssetId(assetId);
    try {
      await onLoadVariants(assetType, assetId, variantId, current, 20);
    } finally {
      setLoadingAssetId((loading) => loading === assetId ? undefined : loading);
    }
  };
  const image = imageFor(reference, assets);
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = reference.variantId != null
    ? variantsFor(assets, reference.assetId)
      .find((candidate) => candidate.id === reference.variantId)
    : defaultVariant(asset);
  const generating = isGenerating || variant?.generationStatus === 'GENERATING';
  const isResolved = referenceReady(reference, variant);
  const cardStyle: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };
  const value = reference.assetId
    ? [reference.assetId, ...(reference.variantId ? [reference.variantId] : [])]
    : undefined;
  const renderOption = (option: MaterialOption, breadcrumb?: string) => {
    const selected = option.variant ? option.value === reference.variantId : option.value === reference.assetId;
    const status = option.variant && !option.variant.usable
      ? option.variant.generationStatus === 'GENERATING' ? '生成中'
        : option.variant.generationStatus === 'FAILED' ? '生成失败' : '待生成'
      : undefined;
    return (
      <div className="storyboard-asset-picker-option" title={breadcrumb ? `${breadcrumb} / ${option.label}` : option.label}>
        <LazyMediaImage
          src={option.thumbnail || undefined}
          alt={`${option.label}缩略图`}
          width={32}
          height={32}
          preview={false}
          placeholderFallback={<PictureOutlined />}
          style={{ flexShrink: 0, borderRadius: 6 }}
        />
        <span className="storyboard-asset-picker-option-copy">
          {breadcrumb ? <span className="storyboard-asset-picker-breadcrumb">{breadcrumb}</span> : null}
          <span className="storyboard-asset-picker-option-name">{option.caption}</span>
          {status ? <span className="storyboard-asset-picker-option-status">{status}</span> : null}
        </span>
        {loadingAssetId === option.value && !option.variant ? <Spin size="small" /> : null}
        {option.variant?.primary ? <span className="storyboard-asset-picker-default">默认</span> : null}
        {selected ? <CheckOutlined className="storyboard-asset-picker-check" aria-label="已选中" /> : null}
      </div>
    );
  };

  return (
    <li
      ref={setNodeRef}
      className={`storyboard-reference-card${isDragging ? ' is-dragging' : ''}`}
      style={cardStyle}
    >
      <div className="storyboard-reference-thumb">
        {image ? (
          <LazyMediaImage
            src={image}
            alt={`${reference.assetName || reference.sourceName || labels[assetType]}参考图`}
            width="100%"
            height="100%"
            preview={{ src: originalImageFor(reference, assets) }}
          />
        ) : <PictureOutlined />}
        {!isResolved ? (
          <Tag className="storyboard-reference-status" color={statusColor(reference.resolutionStatus)}>
            {statusLabel(reference.resolutionStatus)}
          </Tag>
        ) : null}
        {generating ? (
          <div
            className="storyboard-reference-generating"
            role="status"
            aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产图生成中`}
          >
            <Spin size="small" />
            <span>生成中</span>
          </div>
        ) : null}
        <Tooltip title="拖拽排序">
          <Button
            {...attributes}
            {...listeners}
            className="storyboard-reference-drag-handle"
            type="text"
            size="small"
            icon={<HolderOutlined />}
            aria-label={`拖拽分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}排序`}
          />
        </Tooltip>
        <div className="storyboard-reference-hover-actions">
          {assetType === 'CHARACTER' ? (
            <Tooltip title="生成语音">
              <Button
                type="text"
                size="small"
                icon={<AudioOutlined />}
                aria-label={`分镜${storyboardNo}${reference.assetName || reference.sourceName || '角色'}生成语音`}
                onClick={() => onVoice(reference.assetName || reference.sourceName || '')}
              />
            </Tooltip>
          ) : null}
          <Tooltip title={isResolved ? '重新生成资产图' : '生成资产图'}>
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              disabled={!reference.assetId || generating}
              aria-label={`${isResolved ? '重新生成' : '生成'}分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产图`}
              onClick={onGenerateImage}
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              aria-label={`移除分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}`}
              onClick={onRemove}
            />
          </Tooltip>
        </div>
      </div>
      <Cascader<MaterialOption>
        className="storyboard-reference-cascader"
        classNames={{ popup: { root: 'storyboard-asset-picker-popup' } }}
        popupAlign={{ offset: [0, 0], htmlRegion: 'visible', overflow: { adjustX: 1, adjustY: 1, shiftX: true, shiftY: true } }}
        aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产形态`}
        options={cascaderOptionsFor(assets, Boolean(onLoadVariants))}
        placeholder={reference.sourceName || `选择${labels[assetType]}及形态`}
        value={value}
        allowClear={false}
        changeOnSelect
        showSearch={{
          searchValue: search,
          onSearch: setSearch,
          matchInputWidth: false,
          filter: (input, path) => {
            const text = path.map((option) => option.label).join(' ').toLocaleLowerCase();
            return input.trim().toLocaleLowerCase().split(/\s+/).every((word) => text.includes(word));
          },
          render: (_input, path) => renderOption(path[path.length - 1], path.length > 1 ? path[0].label : undefined),
        }}
        optionRender={(option) => typeof option.label === 'string' ? renderOption(option) : option.label}
        popupRender={(menus) => (
          <div className="storyboard-asset-picker-panel">
            <div className="storyboard-asset-picker-header">
              <strong>选择{labels[assetType]}及形态</strong>
              <span>{assets.length} 个{labels[assetType]}</span>
            </div>
            <div className="storyboard-asset-picker-search">
              <Input
                aria-label={`搜索分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产形态`}
                placeholder={`搜索${labels[assetType]}名称或形态`}
                prefix={<SearchOutlined />}
                allowClear
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            {menus}
            <div className="storyboard-asset-picker-footer">
              {asset ? `当前：${asset.name}${variant ? ` / ${variantCaption(asset.name, variant.name)}` : ''}` : `请选择${labels[assetType]}`}
            </div>
          </div>
        )}
        onOpenChange={(open) => {
          if (!open) setSearch('');
          if (open && reference.assetId) {
            void loadVariants(reference.assetId, reference.variantId);
          }
        }}
        displayRender={(selectedLabels) => {
          // Cascader falls back to numeric IDs until the selected options load.
          const assetName = asset?.name || reference.assetName || reference.sourceName
            || String(selectedLabels[0] ?? '');
          const variantName = variant?.name || reference.variantName
            || (typeof selectedLabels[1] === 'string' ? selectedLabels[1] : undefined);
          const name = variantName ? `${assetName} / ${variantCaption(assetName, variantName)}` : assetName;
          return <span title={name}>{name}</span>;
        }}
        onChange={(path) => {
          const [selectedAssetId, selectedVariantId] = path.map(Number);
          void loadVariants(selectedAssetId, selectedVariantId);
          const asset = assets.find((candidate) => candidate.id === selectedAssetId);
          const variant = variantsFor(assets, selectedAssetId)
            .find((candidate) => candidate.id === selectedVariantId);
          onReplace({
            ...reference,
            assetId: selectedAssetId,
            assetName: asset?.name,
            sourceName: reference.sourceName || asset?.name,
            variantId: variant?.id || null,
            variantName: variant?.name,
            imageUrl: variant?.currentImageUrl,
            imageThumbnailUrl: variant?.currentImageThumbnailUrl,
            resolutionStatus: variant?.usable ? 'RESOLVED' : 'ASSET_PENDING',
            sourceType: 'MANUAL',
            lockedByUser: true,
          });
        }}
      />
      {(asset?.visual?.current || 1) * (asset?.visual?.pageSize || 20)
        < (asset?.visual?.total || asset?.visual?.variantCount || 0) ? (
          <Button
            type="link"
            size="small"
            onClick={() => reference.assetId && void onLoadVariants?.(
              assetType,
              reference.assetId,
              reference.variantId,
              (asset?.visual?.current || 1) + 1,
              asset?.visual?.pageSize || 20,
            )}
          >
            加载更多{labels[assetType]}形态
          </Button>
        ) : null}
      {assetType === 'CHARACTER' && projectId && reference.assetId ? (
        <div aria-live="polite" style={{ marginTop: 6, minWidth: 0 }}>
          <Typography.Text style={{ display: 'block', fontSize: 12 }} ellipsis={{ tooltip: effectiveVoice?.data?.audio?.name }}>
            {effectiveVoice?.failed ? '音色加载失败'
              : !effectiveVoice?.data ? '音色加载中…'
              : effectiveVoice.data.audio?.name || '未配置音色'}
          </Typography.Text>
          {effectiveVoice?.data?.audio ? <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}
            ellipsis={{ tooltip: effectiveVoice.data.audio.sourceProjectName }}>
            {effectiveVoice.data.overridden ? '当前分镜形态覆盖' : '角色默认音色'} · {effectiveVoice.data.audio.sourceProjectName}
          </Typography.Text> : null}
        </div>
      ) : null}
      {assetType === 'CHARACTER' && onConfigureVoice ? <Button size="small" icon={<AudioOutlined />}
        disabled={!reference.assetId} aria-label={`分镜${storyboardNo}${reference.assetName || reference.sourceName || '角色'}${reference.variantName ? ` ${reference.variantName}` : ''}配置音色`}
        onClick={() => onConfigureVoice(reference)}>配置音色</Button> : null}
    </li>
  );
}

export default function StoryboardAssetReferenceEditor({
  projectId,
  storyboardId,
  storyboardNo,
  references,
  characters,
  scenes,
  props,
  onChange,
  onVoice,
  onGenerateImage,
  generatingReferenceKeys,
  onLoadVariants,
}: {
  projectId?: number;
  storyboardId: number;
  storyboardNo: number;
  references: StoryboardAssetReference[];
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  onChange: (references: StoryboardAssetReference[]) => void;
  onVoice: (name: string) => void;
  onGenerateImage: (reference: StoryboardAssetReference, generationKey: string) => void;
  generatingReferenceKeys: ReadonlySet<string>;
  onLoadVariants?: (
    assetType: AssetType, assetId: number, selectedVariantId?: number | null,
    current?: number, pageSize?: number,
  ) => Promise<void>;
}) {
  const [voiceTarget, setVoiceTarget] = useState<StoryboardAssetReference>();
  const [voiceRevision, setVoiceRevision] = useState(0);
  useEffect(() => { setVoiceTarget(undefined); }, [projectId, storyboardId]);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const assetsByType: Record<AssetType, Asset[]> = {
    CHARACTER: characters,
    SCENE: scenes,
    PROP: props,
  };

  const replace = (target: StoryboardAssetReference, next: StoryboardAssetReference) => {
    onChange(normalize(references.map((reference) => reference === target ? next : reference)));
  };
  const remove = (target: StoryboardAssetReference) => {
    onChange(normalize(references.filter((reference) => reference !== target)));
  };
  const add = (assetType: AssetType) => {
    const assets = assetsByType[assetType];
    const used = new Set(references
      .filter((reference) => reference.assetType === assetType)
      .map((reference) => reference.assetId));
    const asset = assets.find((candidate) => !used.has(candidate.id));
    if (!asset) return;
    const variant = defaultVariant(asset);
    const nextReference: ClientKeyedReference = {
      clientKey: `draft-${++nextDraftReferenceKey}`,
      assetType,
      assetId: asset.id,
      assetName: asset.name,
      variantId: variant?.id,
      variantName: variant?.name,
      imageUrl: variant?.currentImageUrl,
      imageThumbnailUrl: variant?.currentImageThumbnailUrl,
      referenceRole: assetType === 'SCENE' ? 'MAIN' : 'VISIBLE',
      sortOrder: 0,
      resolutionStatus: variant?.usable ? 'RESOLVED' : 'ASSET_PENDING',
      sourceType: 'MANUAL',
      sourceName: asset.name,
      lockedByUser: true,
    };
    onChange(normalize([...references, nextReference]));
  };
  const onDragEnd = (assetType: AssetType, rows: StoryboardAssetReference[]) =>
    ({ active, over }: DragEndEvent) => {
      if (!over || active.id === over.id) return;
      const ids = rows.map((reference, rowIndex) => sortableIdFor(reference, assetType, rowIndex));
      const sourceIndex = ids.indexOf(String(active.id));
      const destinationIndex = ids.indexOf(String(over.id));
      onChange(reorderReferencesWithinType(references, assetType, sourceIndex, destinationIndex));
    };

  return (
    <div className="storyboard-reference-editor">
      {(['CHARACTER', 'SCENE', 'PROP'] as const).map((assetType) => {
        const assets = assetsByType[assetType];
        const rows = references.filter((reference) => reference.assetType === assetType);
        const sortableIds = rows.map((reference, rowIndex) =>
          sortableIdFor(reference, assetType, rowIndex));
        return (
          <section className="storyboard-reference-group" key={assetType}>
            <Flex align="center" justify="space-between">
              <Typography.Text strong>{labels[assetType]}</Typography.Text>
              <Tooltip title={`添加${labels[assetType]}`}>
                <Button
                  type="text"
                  size="small"
                  icon={<PlusOutlined />}
                  aria-label={`添加分镜${storyboardNo}${labels[assetType]}`}
                  disabled={!assets.some((asset) => !rows.some((row) => row.assetId === asset.id))}
                  onClick={() => add(assetType)}
                />
              </Tooltip>
            </Flex>
            {rows.length ? (
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={onDragEnd(assetType, rows)}
              >
                <SortableContext items={sortableIds} strategy={rectSortingStrategy}>
                  <ul className="storyboard-reference-grid" aria-label={`分镜${storyboardNo}${labels[assetType]}资产`}>
                    {rows.map((reference, rowIndex) => (
                      <SortableAssetCard
                        key={sortableIds[rowIndex]}
                        id={sortableIds[rowIndex]}
                        storyboardNo={storyboardNo}
                        projectId={projectId}
                        storyboardId={storyboardId}
                        voiceRevision={voiceRevision}
                        assetType={assetType}
                        rowIndex={rowIndex}
                        reference={reference}
                        assets={assets}
                        onLoadVariants={onLoadVariants}
                        onReplace={(next) => replace(reference, next)}
                        onRemove={() => remove(reference)}
                        onVoice={onVoice}
                        onConfigureVoice={projectId ? setVoiceTarget : undefined}
                        onGenerateImage={() => onGenerateImage(
                          reference,
                          `${storyboardId}:${sortableIds[rowIndex]}`,
                        )}
                        isGenerating={generatingReferenceKeys.has(
                          `${storyboardId}:${sortableIds[rowIndex]}`,
                        )}
                      />
                    ))}
                  </ul>
                </SortableContext>
              </DndContext>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`暂无${labels[assetType]}`} />}
          </section>
        );
      })}
      {projectId && voiceTarget?.assetId ? <Suspense fallback={null}>
        <CharacterVoiceModal projectId={projectId} characterId={voiceTarget.assetId}
          characterName={voiceTarget.assetName || voiceTarget.sourceName || '角色'}
          storyboard={{ id: storyboardId, variantId: voiceTarget.variantId, variantName: voiceTarget.variantName }}
          onClose={() => setVoiceTarget(undefined)} onSaved={() => setVoiceTarget(undefined)}
          onChanged={() => setVoiceRevision((revision) => revision + 1)} />
      </Suspense> : null}
    </div>
  );
}
