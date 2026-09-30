import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  AudioOutlined,
  CloseOutlined,
  PictureOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Button, Empty, Flex, Image, Select, Tag, Tooltip, Typography } from 'antd';
import type {
  CharacterAsset,
  PropAsset,
  SceneAsset,
  StoryboardAssetReference,
  VisualVariant,
} from './service';

type Asset = CharacterAsset | SceneAsset | PropAsset;
type AssetType = StoryboardAssetReference['assetType'];

const labels: Record<AssetType, string> = {
  CHARACTER: '角色',
  SCENE: '场景',
  PROP: '道具',
};

const roles: Record<AssetType, StoryboardAssetReference['referenceRole'][]> = {
  CHARACTER: ['VISIBLE', 'SUPPORTING'],
  SCENE: ['MAIN', 'SUPPORTING', 'TRANSITION'],
  PROP: ['VISIBLE', 'SUPPORTING'],
};

const statusLabel = (status: StoryboardAssetReference['resolutionStatus']) => ({
  RESOLVED: '已就绪',
  ASSET_PENDING: '待生成资产图',
  UNRESOLVED: '待确认',
}[status]);

const statusColor = (status: StoryboardAssetReference['resolutionStatus']) => ({
  RESOLVED: 'success',
  ASSET_PENDING: 'warning',
  UNRESOLVED: 'error',
}[status]);

const variantsFor = (assets: Asset[], assetId?: number | null) =>
  assets.find((asset) => asset.id === assetId)?.visual?.variants || [];

const imageFor = (reference: StoryboardAssetReference, assets: Asset[]) => {
  const asset = assets.find((candidate) => candidate.id === reference.assetId);
  const variant = variantsFor(assets, reference.assetId)
    .find((candidate) => candidate.id === reference.variantId);
  return variant?.currentImageThumbnailUrl
    || asset?.mainImageThumbnailUrl || undefined;
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

export default function StoryboardAssetReferenceEditor({
  storyboardNo,
  references,
  characters,
  scenes,
  props,
  onChange,
  onVoice,
  onGenerateImage,
}: {
  storyboardNo: number;
  references: StoryboardAssetReference[];
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  onChange: (references: StoryboardAssetReference[]) => void;
  onVoice: (name: string) => void;
  onGenerateImage: (reference: StoryboardAssetReference) => void;
}) {
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
  const move = (target: StoryboardAssetReference, offset: -1 | 1) => {
    const sameType = references.filter((reference) => reference.assetType === target.assetType);
    const source = sameType.indexOf(target);
    const destination = source + offset;
    if (source < 0 || destination < 0 || destination >= sameType.length) return;
    const reordered = [...sameType];
    [reordered[source], reordered[destination]] = [reordered[destination], reordered[source]];
    let cursor = 0;
    onChange(normalize(references.map((reference) =>
      reference.assetType === target.assetType ? reordered[cursor++] : reference,
    )));
  };
  const add = (assetType: AssetType) => {
    const assets = assetsByType[assetType];
    const used = new Set(references
      .filter((reference) => reference.assetType === assetType)
      .map((reference) => reference.assetId));
    const asset = assets.find((candidate) => !used.has(candidate.id));
    if (!asset) return;
    const variant = defaultVariant(asset);
    onChange(normalize([...references, {
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
    }]));
  };

  return (
    <div className="storyboard-reference-editor">
      {(['CHARACTER', 'SCENE', 'PROP'] as const).map((assetType) => {
        const assets = assetsByType[assetType];
        const rows = references.filter((reference) => reference.assetType === assetType);
        return (
          <section className={`storyboard-reference-group storyboard-reference-${assetType.toLowerCase()}`} key={assetType}>
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
            {rows.length ? rows.map((reference, rowIndex) => {
              const variants = variantsFor(assets, reference.assetId);
              const image = imageFor(reference, assets);
              return (
                <div className={assetType === 'SCENE' ? 'storyboard-scene-reference' : 'storyboard-reference-row'}
                  key={reference.id || `${assetType}-${rowIndex}-${reference.sourceName || ''}`}>
                  <div className="storyboard-reference-thumb">
                    {image ? <Image src={image} alt={`${reference.assetName || reference.sourceName || labels[assetType]}参考图`}
                      width="100%" height="100%" preview={false} /> : <PictureOutlined />}
                  </div>
                  <div className="storyboard-reference-fields">
                    <Select
                      showSearch={{ optionFilterProp: 'label' }}
                      aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}`}
                      placeholder={reference.sourceName || `选择${labels[assetType]}`}
                      value={reference.assetId || undefined}
                      options={assets.map((asset) => ({ label: asset.name, value: asset.id }))}
                      onChange={(assetId) => {
                        const asset = assets.find((candidate) => candidate.id === assetId);
                        const variant = defaultVariant(asset);
                        replace(reference, {
                          ...reference,
                          assetId,
                          assetName: asset?.name,
                          sourceName: reference.sourceName || asset?.name,
                          variantId: variant?.id,
                          variantName: variant?.name,
                          imageUrl: variant?.currentImageThumbnailUrl,
                          resolutionStatus: variant?.usable ? 'RESOLVED' : 'ASSET_PENDING',
                          sourceType: 'MANUAL',
                          lockedByUser: true,
                        });
                      }}
                    />
                    <Select
                      allowClear
                      aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}视觉形态`}
                      placeholder="视觉形态"
                      value={reference.variantId || undefined}
                      options={variants.map((variant) => ({ label: variant.name, value: variant.id }))}
                      onChange={(variantId) => {
                        const variant = variants.find((candidate) => candidate.id === variantId);
                        replace(reference, {
                          ...reference,
                          variantId: variantId || null,
                          variantName: variant?.name,
                          imageUrl: variant?.currentImageThumbnailUrl,
                          resolutionStatus: variant?.usable ? 'RESOLVED' : 'ASSET_PENDING',
                        });
                      }}
                    />
                    <Select
                      aria-label={`分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}作用`}
                      value={reference.referenceRole}
                      options={roles[assetType].map((role) => ({ label: role, value: role }))}
                      onChange={(referenceRole) => replace(reference, { ...reference, referenceRole })}
                    />
                    <Tag color={statusColor(reference.resolutionStatus)}>
                      {statusLabel(reference.resolutionStatus)}
                    </Tag>
                  </div>
                  <Flex className="storyboard-reference-actions" gap={2}>
                    {assetType === 'CHARACTER' ? (
                      <Tooltip title="生成语音">
                        <Button type="text" size="small" icon={<AudioOutlined />}
                          aria-label={`分镜${storyboardNo}${reference.assetName || reference.sourceName || '角色'}生成语音`}
                          onClick={() => onVoice(reference.assetName || reference.sourceName || '')} />
                      </Tooltip>
                    ) : null}
                    {reference.resolutionStatus === 'ASSET_PENDING' && reference.assetId ? (
                      <Button size="small"
                        aria-label={`生成分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}资产图`}
                        onClick={() => onGenerateImage(reference)}>
                        生成资产图
                      </Button>
                    ) : null}
                    <Tooltip title="上移">
                      <Button type="text" size="small" icon={<ArrowUpOutlined />}
                        aria-label={`上移分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}`}
                        disabled={rowIndex === 0} onClick={() => move(reference, -1)} />
                    </Tooltip>
                    <Tooltip title="下移">
                      <Button type="text" size="small" icon={<ArrowDownOutlined />}
                        aria-label={`下移分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}`}
                        disabled={rowIndex === rows.length - 1} onClick={() => move(reference, 1)} />
                    </Tooltip>
                    <Tooltip title="移除">
                      <Button type="text" size="small" danger icon={<CloseOutlined />}
                        aria-label={`移除分镜${storyboardNo}${labels[assetType]}${rowIndex + 1}`}
                        onClick={() => remove(reference)} />
                    </Tooltip>
                  </Flex>
                </div>
              );
            }) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`暂无${labels[assetType]}`} />}
          </section>
        );
      })}
    </div>
  );
}
