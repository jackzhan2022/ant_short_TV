import { Button, Empty, Flex, Select, Spin, Tabs, Tag } from 'antd';
import { useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import type {
  CharacterAsset, PropAsset, SceneAsset, StoryboardAssetReference,
  StoryboardPromptNode, VisualVariant,
} from './service';

type Reference = Extract<StoryboardPromptNode, { type: 'mention' }>;
type AssetType = StoryboardAssetReference['assetType'];
type ReferenceChoice = { reference: Reference; thumbnail?: string | null; disabled?: boolean; status?: string };

const variantsFor = (asset: CharacterAsset | SceneAsset | PropAsset) => [...new Map([
  asset.visual?.primaryVariant, asset.visual?.selectedVariant, ...(asset.visual?.variants || []),
].filter((variant): variant is VisualVariant => Boolean(variant))
  .map((variant) => [variant.id, variant])).values()];

export default function StoryboardPromptReferencePicker({
  storyboardNo, characters, scenes, props, references, onSelect, onLoadVariants,
}: {
  storyboardNo: number;
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  references: Record<'IMAGE' | 'VIDEO' | 'AUDIO', Reference[]>;
  onSelect: (reference: Reference) => void;
  onLoadVariants: (
    assetType: AssetType, assetId: number, selectedVariantId?: number | null,
    current?: number, pageSize?: number,
  ) => Promise<void>;
}) {
  const [assetKey, setAssetKey] = useState<string>();
  const [loadingKey, setLoadingKey] = useState<string>();
  const assets = [
    ...characters.map((asset) => ({ asset, type: 'CHARACTER' as const, label: '角色' })),
    ...scenes.map((asset) => ({ asset, type: 'SCENE' as const, label: '场景' })),
    ...props.map((asset) => ({ asset, type: 'PROP' as const, label: '道具' })),
  ].map((entry) => ({ ...entry, key: `${entry.type}:${entry.asset.id}` }));
  const selected = assets.find((entry) => entry.key === assetKey);
  const load = async (key: string, current = 1) => {
    const entry = assets.find((candidate) => candidate.key === key);
    if (!entry) return;
    setLoadingKey(key);
    try {
      await onLoadVariants(entry.type, entry.asset.id, undefined, current, 20);
    } finally {
      setLoadingKey((loading) => loading === key ? undefined : loading);
    }
  };
  const labelFor = (assetName: string, variantName: string) => `${assetName} / ${variantName}`;
  const imageChoices: ReferenceChoice[] = selected
    ? variantsFor(selected.asset).map((variant) => ({
      reference: {
        type: 'mention', mediaType: 'IMAGE', sourceType: 'ASSET_VISUAL_VARIANT',
        sourceId: variant.id, assetType: selected.type, assetId: selected.asset.id,
        variantId: variant.id, displayName: labelFor(selected.asset.name, variant.name),
      },
      thumbnail: variant.currentImageThumbnailUrl,
      disabled: !variant.usable || !variant.currentImageResultId || !variant.currentImageUrl,
      status: variant.generationStatus === 'GENERATING' ? '生成中'
        : variant.generationStatus === 'FAILED' ? '生成失败' : '暂无可用图片',
    }))
    : references.IMAGE.map((reference) => {
      const entry = assets.find(({ type, asset }) =>
        type === reference.assetType && asset.id === reference.assetId);
      const variant = entry && variantsFor(entry.asset).find((candidate) => candidate.id === reference.variantId);
      return {
        reference: entry && variant ? {
          ...reference, displayName: labelFor(entry.asset.name, variant.name),
        } : reference,
        thumbnail: variant?.currentImageThumbnailUrl,
      };
    });
  const visual = selected?.asset.visual;
  const hasMore = visual && (visual.current || 1) * (visual.pageSize || 20)
    < (visual.total ?? visual.variantCount);

  const choices = (items: ReferenceChoice[]) => items.length ? (
    <Flex vertical gap={4} style={{ maxHeight: 280, overflowY: 'auto' }}>
      {items.map(({ reference, thumbnail, disabled, status }) => (
        <Button
          key={`${reference.sourceType}-${reference.sourceId}`}
          type="text"
          block
          disabled={disabled}
          aria-label={`引用素材 ${reference.displayName}`}
          onClick={() => onSelect(reference)}
          style={{ justifyContent: 'flex-start', height: 'auto', padding: 6, whiteSpace: 'normal', textAlign: 'left' }}
        >
          {reference.mediaType === 'IMAGE' ? (
            <LazyMediaImage src={thumbnail || undefined} alt={`${reference.displayName}缩略图`} width={40} height={40} preview={false} style={{ flexShrink: 0 }} />
          ) : null}
          <span>{reference.displayName}</span>
          {disabled ? <Tag>{status}</Tag> : null}
        </Button>
      ))}
    </Flex>
  ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={selected ? '暂无形态，请先在资产设置中添加' : '暂无可用素材，可选择项目资产查看形态'} />;

  return (
    <div style={{ width: 320, maxWidth: 'calc(100vw - 48px)' }}>
      <Tabs
        size="small"
        items={(['IMAGE', 'VIDEO', 'AUDIO'] as const).map((mediaType) => ({
          key: mediaType,
          label: { IMAGE: '图片', VIDEO: '视频', AUDIO: '音频' }[mediaType],
          children: mediaType === 'IMAGE' ? (
            <Flex vertical gap={8}>
              <Select
                aria-label={`分镜${storyboardNo}参考图项目资产`}
                placeholder="选择角色、场景或道具，查看参考图"
                value={assetKey}
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                options={assets.map(({ key, asset, label }) => ({ value: key, label: `${label} · ${asset.name}` }))}
                onChange={(key?: string) => {
                  setAssetKey(key);
                  if (key) void load(key);
                }}
                style={{ width: '100%' }}
              />
              {loadingKey === assetKey && loadingKey ? <Spin size="small" /> : choices(imageChoices)}
              {selected && (hasMore || !imageChoices.length) ? (
                <Button
                  size="small"
                  loading={loadingKey === assetKey}
                  onClick={() => void load(selected.key, hasMore ? (visual?.current || 1) + 1 : 1)}
                >
                  {hasMore ? '加载更多形态' : '加载形态'}
                </Button>
              ) : null}
            </Flex>
          ) : choices(references[mediaType].map((reference) => ({ reference }))),
        }))}
      />
    </div>
  );
}
