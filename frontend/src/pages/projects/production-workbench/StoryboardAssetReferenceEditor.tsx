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
  DeleteOutlined,
  HolderOutlined,
  PictureOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Button, Cascader, Empty, Flex, Spin, Tag, Tooltip, Typography } from 'antd';
import LazyMediaImage from '@/components/LazyMediaImage';
import type { CSSProperties } from 'react';
import type {
  CharacterAsset,
  PropAsset,
  SceneAsset,
  StoryboardAssetReference,
  VisualVariant,
} from './service';

type Asset = CharacterAsset | SceneAsset | PropAsset;
type AssetType = StoryboardAssetReference['assetType'];
type ClientKeyedReference = StoryboardAssetReference & { clientKey?: string };

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
}[status]);

const statusColor = (status: StoryboardAssetReference['resolutionStatus']) => ({
  RESOLVED: 'success',
  ASSET_PENDING: 'warning',
  UNRESOLVED: 'error',
}[status]);

const variantsFor = (assets: Asset[], assetId?: number | null) => {
  const visual = assets.find((asset) => asset.id === assetId)?.visual;
  return [...new Map([visual?.primaryVariant, visual?.selectedVariant, ...(visual?.variants || [])]
    .filter((variant): variant is VisualVariant => Boolean(variant)).map((variant) => [variant.id, variant])).values()];
};

export const imageFor = (reference: StoryboardAssetReference, assets: Asset[]) => {
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = reference.variantId != null
    ? variantsFor(assets, reference.assetId)
      .find((candidate) => candidate.id === reference.variantId)
    : defaultVariant(asset);
  return variant?.currentImageThumbnailUrl
    || reference.imageThumbnailUrl || asset?.mainImageThumbnailUrl || undefined;
};

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

const cascaderOptionsFor = (assets: Asset[]) => assets.map((asset) => {
  const variants = variantsFor(assets, asset.id);
  return {
    label: asset.name,
    value: asset.id,
    ...(variants.length ? {
      children: variants.map((variant) => ({ label: variant.name, value: variant.id })),
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
  assetType,
  rowIndex,
  reference,
  assets,
  onReplace,
  onRemove,
  onVoice,
  onGenerateImage,
  isGenerating,
  onLoadVariants,
}: {
  id: string;
  storyboardNo: number;
  assetType: AssetType;
  rowIndex: number;
  reference: StoryboardAssetReference;
  assets: Asset[];
  onReplace: (next: StoryboardAssetReference) => void;
  onRemove: () => void;
  onVoice: (name: string) => void;
  onGenerateImage: () => void;
  isGenerating: boolean;
  onLoadVariants?: (
    assetType: AssetType, assetId: number, selectedVariantId?: number | null,
    current?: number, pageSize?: number,
  ) => Promise<void>;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const image = imageFor(reference, assets);
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = reference.variantId != null
    ? variantsFor(assets, reference.assetId)
      .find((candidate) => candidate.id === reference.variantId)
    : defaultVariant(asset);
  const generating = isGenerating || variant?.generationStatus === 'GENERATING';
  const isResolved = reference.resolutionStatus === 'RESOLVED';
  const cardStyle: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };
  const value = reference.assetId
    ? [reference.assetId, ...(reference.variantId ? [reference.variantId] : [])]
    : undefined;

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
            preview={false}
          />
        ) : <PictureOutlined />}
        {reference.resolutionStatus !== 'RESOLVED' ? (
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
      <Cascader
        className="storyboard-reference-cascader"
        aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产形态`}
        options={cascaderOptionsFor(assets)}
        placeholder={reference.sourceName || `选择${labels[assetType]}及形态`}
        value={value}
        allowClear={false}
        changeOnSelect
        showSearch
        onOpenChange={(open) => {
          if (open && reference.assetId) {
            void onLoadVariants?.(assetType, reference.assetId, reference.variantId);
          }
        }}
        displayRender={(selectedLabels) => selectedLabels.join(' / ')}
        onChange={(path) => {
          const [selectedAssetId, selectedVariantId] = path.map(Number);
          void onLoadVariants?.(assetType, selectedAssetId, selectedVariantId);
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
            imageUrl: variant?.currentImageThumbnailUrl,
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
    </li>
  );
}

export default function StoryboardAssetReferenceEditor({
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
      imageUrl: variant?.currentImageThumbnailUrl,
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
                        assetType={assetType}
                        rowIndex={rowIndex}
                        reference={reference}
                        assets={assets}
                        onLoadVariants={onLoadVariants}
                        onReplace={(next) => replace(reference, next)}
                        onRemove={() => remove(reference)}
                        onVoice={onVoice}
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
    </div>
  );
}
