import { Button, Empty, Modal, Spin } from 'antd';
import { useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import type { VisualVariant } from './service';

export default function StoryboardVariantGallery({
  name,
  variants,
  currentId,
  loading,
  hasMore,
  onLoadMore,
  onSelect,
  onClose,
}: {
  name: string;
  variants: VisualVariant[];
  currentId?: number | null;
  loading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  onSelect: (variant: VisualVariant) => void;
  onClose: () => void;
}) {
  const [selectedId, setSelectedId] = useState(currentId);
  const selected =
    variants.find((variant) => variant.id === selectedId) || variants[0];
  return (
    <Modal
      open
      title={`${name} · 造型`}
      width={900}
      centered
      onCancel={onClose}
      className="storyboard-variant-gallery"
      destroyOnHidden
      footer={
        <div className="storyboard-gallery-footer">
          <span>
            {selected?.id === currentId
              ? '当前引用'
              : selected?.usable
                ? selected.name
                : '该造型暂无可用图片'}
          </span>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            disabled={!selected?.usable || selected.id === currentId}
            onClick={() => selected && onSelect(selected)}
          >
            使用此造型
          </Button>
        </div>
      }
    >
      <Spin spinning={loading}>
        <div className="storyboard-gallery-stage">
          {selected?.currentImageUrl || selected?.currentImageThumbnailUrl ? (
            <LazyMediaImage
              key={selected.id}
              native
              src={
                selected.currentImageUrl ||
                selected.currentImageThumbnailUrl ||
                undefined
              }
              alt={`${name} · ${selected.name}`}
              height="100%"
              imageStyle={{ objectFit: 'contain' }}
              preview={false}
            />
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无造型图片"
            />
          )}
        </div>
      </Spin>
      <fieldset
        className="storyboard-gallery-thumbnails"
        aria-label={`${name}造型切换`}
      >
        {variants.map((variant) => (
          <button
            key={variant.id}
            type="button"
            aria-label={`预览${variant.name}`}
            aria-pressed={selected?.id === variant.id}
            onClick={() => setSelectedId(variant.id)}
          >
            <LazyMediaImage
              native
              src={variant.currentImageThumbnailUrl || undefined}
              alt={`${variant.name}缩略图`}
              height={56}
              preview={false}
            />
            <span>{variant.name}</span>
          </button>
        ))}
        {hasMore ? (
          <Button loading={loading} onClick={onLoadMore}>
            加载更多造型
          </Button>
        ) : null}
      </fieldset>
    </Modal>
  );
}
