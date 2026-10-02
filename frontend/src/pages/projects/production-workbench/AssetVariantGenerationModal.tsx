import { Button, Flex, Input, Modal, Typography } from 'antd';
import type { ProjectModelOption } from './ai-config/service';
import type { VisualVariant } from './service';

export type AssetVariantGenerationValues = {
  prompt: string;
  modelId?: number;
  aspectRatio: string;
  imageCount: number;
};

type Props = {
  open: boolean;
  variant?: VisualVariant;
  assetType?: VisualVariant['assetType'];
  primaryImageUrl?: string | null;
  imageModels: ProjectModelOption[];
  values: AssetVariantGenerationValues;
  submitting: boolean;
  onChange: (values: Partial<AssetVariantGenerationValues>) => void;
  onCancel: () => void;
  onSubmit: () => void;
};

export default function AssetVariantGenerationModal({
  open,
  variant,
  assetType,
  primaryImageUrl,
  imageModels,
  values,
  submitting,
  onChange,
  onCancel,
  onSubmit,
}: Props) {
  const assetTypeLabel = {
    CHARACTER: '角色',
    SCENE: '场景',
    PROP: '道具',
  }[assetType || variant?.assetType || 'CHARACTER'];
  return (
    <Modal
      title={`${assetTypeLabel}生成`}
      open={open && Boolean(variant)}
      width={860}
      footer={null}
      onCancel={onCancel}
    >
      {variant ? (
        <div style={{ display: 'grid', gap: 18 }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: 14,
            }}
          >
            <div
              style={{
                display: 'grid',
                placeItems: 'center',
                minHeight: 260,
                overflow: 'hidden',
                borderRadius: 10,
                background: 'var(--app-color-fill-secondary)',
              }}
            >
              {variant.currentImageThumbnailUrl ? (
                <img
                  src={variant.currentImageThumbnailUrl}
                  alt={`${variant.name}当前图`}
                  style={{ maxWidth: '100%', maxHeight: 340, objectFit: 'contain' }}
                />
              ) : (
                <Typography.Text type="secondary">暂无当前图</Typography.Text>
              )}
            </div>
            <div
              style={{
                display: 'grid',
                placeItems: 'center',
                minHeight: 260,
                overflow: 'hidden',
                borderRadius: 10,
                background: 'var(--app-color-fill-secondary)',
              }}
            >
              {(assetType || variant.assetType) === 'CHARACTER'
              && !variant.primary
              && primaryImageUrl ? (
                <img
                  src={primaryImageUrl}
                  aria-label={`${variant.name}引用图`}
                  alt="主形象引用图"
                  style={{ maxWidth: '100%', maxHeight: 340, objectFit: 'contain' }}
                />
              ) : (
                <Typography.Text type="secondary">主形象无需引用图</Typography.Text>
              )}
            </div>
          </div>
          <div
            style={{
              padding: 16,
              border: '1px solid var(--app-color-primary)',
              borderRadius: 12,
            }}
          >
            <Typography.Text strong>{variant.name} · 视觉生成任务</Typography.Text>
            <Input.TextArea
              aria-label={`${variant.name}生成提示词`}
              value={values.prompt}
              onChange={(event) => onChange({ prompt: event.target.value })}
              autoSize={{ minRows: 6, maxRows: 10 }}
              style={{ marginTop: 12 }}
            />
            <Flex gap={8} style={{ marginTop: 12 }}>
              <select
                aria-label="图片模型"
                value={values.modelId ?? ''}
                onChange={(event) =>
                  onChange({
                    modelId: event.target.value ? Number(event.target.value) : undefined,
                  })}
              >
                <option value="">使用项目默认模型</option>
                {imageModels.map((model) => (
                  <option key={model.id} value={model.id}>{model.name}</option>
                ))}
              </select>
              <select
                aria-label="画面比例"
                value={values.aspectRatio}
                onChange={(event) => onChange({ aspectRatio: event.target.value })}
              >
                <option value="3:4">3:4</option>
                <option value="1:1">1:1</option>
                <option value="16:9">16:9</option>
              </select>
              <select
                aria-label="生成数量"
                value={values.imageCount}
                onChange={(event) => onChange({ imageCount: Number(event.target.value) })}
              >
                <option value={1}>1 张</option>
                <option value={2}>2 张</option>
                <option value={4}>4 张</option>
              </select>
              <Typography.Text type="secondary">积分按所选模型实时结算</Typography.Text>
            </Flex>
            <Flex justify="end" gap={8} style={{ marginTop: 12 }}>
              <Button onClick={onCancel}>取消</Button>
              <Button
                type="primary"
                aria-label={`提交${variant.name}生成`}
                loading={submitting}
                disabled={submitting}
                onClick={onSubmit}
              >
                生成图片
              </Button>
            </Flex>
          </div>
        </div>
      ) : null}
    </Modal>
  );
}
