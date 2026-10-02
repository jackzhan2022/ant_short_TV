import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AssetVariantGenerationModal from './AssetVariantGenerationModal';

describe('AssetVariantGenerationModal', () => {
  beforeEach(() => vi.stubGlobal('IntersectionObserver', undefined));
  afterEach(() => vi.unstubAllGlobals());
  it('shows the complete asset generator and submits edited values', () => {
    const onChange = vi.fn();
    const onSubmit = vi.fn();
    render(
      <AssetVariantGenerationModal
        open
        variant={{
          id: 12,
          assetType: 'CHARACTER',
          assetId: 1,
          name: '婚礼礼服',
          prompt: '原提示词',
          sourceType: 'USER',
          generationStatus: 'NOT_STARTED',
          currentImageUrl: '/variant.png',
          currentImageThumbnailUrl: '/variant-display.png',
          primary: false,
          usable: false,
        }}
        primaryImageUrl="/primary-display.png"
        imageModels={[{ id: 7, name: '图片模型A' }]}
        values={{
          prompt: '原提示词',
          modelId: undefined,
          aspectRatio: '16:9',
          imageCount: 1,
        }}
        submitting={false}
        onChange={onChange}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    expect(screen.getByRole('img', { name: '婚礼礼服当前图' })).toHaveAttribute(
      'src',
      '/variant-display.png',
    );
    expect(screen.getByRole('img', { name: '婚礼礼服引用图' })).toHaveAttribute(
      'src',
      '/primary-display.png',
    );
    fireEvent.change(screen.getByLabelText('婚礼礼服生成提示词'), {
      target: { value: '新提示词' },
    });
    fireEvent.change(screen.getByLabelText('图片模型'), {
      target: { value: '7' },
    });
    fireEvent.change(screen.getByLabelText('画面比例'), {
      target: { value: '3:4' },
    });
    fireEvent.change(screen.getByLabelText('生成数量'), {
      target: { value: '2' },
    });
    fireEvent.click(screen.getByRole('button', { name: '提交婚礼礼服生成' }));

    expect(onChange).toHaveBeenNthCalledWith(1, { prompt: '新提示词' });
    expect(onChange).toHaveBeenNthCalledWith(2, { modelId: 7 });
    expect(onChange).toHaveBeenNthCalledWith(3, { aspectRatio: '3:4' });
    expect(onChange).toHaveBeenNthCalledWith(4, { imageCount: 2 });
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('uses the current asset type in the shared dialog title', () => {
    render(
      <AssetVariantGenerationModal
        open
        variant={{
          id: 21,
          assetType: 'SCENE',
          assetId: 2,
          name: '夜景',
          sourceType: 'USER',
          generationStatus: 'NOT_STARTED',
          primary: true,
          usable: false,
        }}
        imageModels={[]}
        values={{ prompt: '', aspectRatio: '16:9', imageCount: 1 }}
        submitting={false}
        onChange={vi.fn()}
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByText('场景生成')).toBeInTheDocument();
  });

  it('does not render the original when its display rendition is unavailable', () => {
    render(
      <AssetVariantGenerationModal
        open
        variant={{
          id: 22,
          assetType: 'SCENE',
          assetId: 2,
          name: 'Original-only',
          sourceType: 'USER',
          generationStatus: 'GENERATING',
          currentImageUrl: '/original-only.png',
          primary: true,
          usable: false,
        }}
        imageModels={[]}
        values={{ prompt: '', aspectRatio: '16:9', imageCount: 1 }}
        submitting={false}
        onChange={vi.fn()}
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.queryByRole('img', { name: 'Original-only当前图' })).not.toBeInTheDocument();
    expect(screen.getByText('暂无当前图')).toBeInTheDocument();
  });
});
