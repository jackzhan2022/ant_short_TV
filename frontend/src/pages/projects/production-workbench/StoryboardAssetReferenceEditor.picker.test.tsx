import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import type { SceneAsset, StoryboardAssetReference, VisualVariant } from './service';

vi.mock('@/components/LazyMediaImage', () => ({
  default: ({ src, alt }: { src?: string; alt: string }) => <img src={src} alt={alt} />,
}));

const variant: VisualVariant = {
  id: 33, assetType: 'SCENE', assetId: 21, name: '酒店后厨-夜 大图',
  sourceType: 'MANUAL', generationStatus: 'COMPLETED', primary: true, usable: true,
  currentImageResultId: 77, currentImageUrl: '/original.png', currentImageThumbnailUrl: '/thumb.png',
};
const asset: SceneAsset = {
  id: 21, name: '酒店后厨', sceneType: 'INTERIOR', atmosphere: '夜', description: '',
  visualStyle: '', prompt: '', status: 'CONFIRMED',
  visual: { variantCount: 2, variants: [variant, {
    ...variant, id: 34, name: '酒店后厨-商用烤炉故障版 第19—21集', primary: false,
    usable: false, generationStatus: 'GENERATING', currentImageThumbnailUrl: null,
  }], generationSummary: {}, episodeBindings: [] },
};
const reference: StoryboardAssetReference = {
  id: 1, assetType: 'SCENE', assetId: 21, assetName: '酒店后厨', variantId: 33,
  variantName: variant.name, referenceRole: 'MAIN', sortOrder: 0, resolutionStatus: 'RESOLVED',
  sourceType: 'MANUAL', lockedByUser: true,
};

afterEach(cleanup);

describe('storyboard asset cascader', () => {
  it('shows thumbnail options and searches full asset and variant names without duplicating the prefix', async () => {
    const onChange = vi.fn();
    const onLoadVariants = vi.fn().mockResolvedValue(undefined);
    render(<StoryboardAssetReferenceEditor
      storyboardId={301} storyboardNo={1} references={[reference]}
      characters={[]} scenes={[asset]} props={[]} onChange={onChange}
      onVoice={vi.fn()} onGenerateImage={vi.fn()} generatingReferenceKeys={new Set()}
      onLoadVariants={onLoadVariants}
    />);
    expect(screen.getByTitle('酒店后厨 / 夜 大图')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('combobox'));
    const search = await screen.findByPlaceholderText('搜索场景名称或形态');
    const popup = search.closest('.storyboard-asset-picker-popup');
    if (!(popup instanceof HTMLElement)) throw new Error('Expected cascader popup');
    expect(within(popup).getByText('选择场景及形态')).toBeInTheDocument();
    expect(within(popup).getByText('夜 大图')).toBeInTheDocument();
    expect(within(popup).getByText('生成中')).toBeInTheDocument();
    expect(within(popup).getByAltText(`${variant.name}缩略图`)).toHaveAttribute('src', '/thumb.png');
    expect(onLoadVariants).toHaveBeenCalledWith('SCENE', 21, 33, 1, 20);

    fireEvent.change(search, { target: { value: '酒店后厨 烤炉' } });
    const result = await within(popup).findByText('商用烤炉故障版 第19—21集');
    expect(within(popup).queryByText('夜 大图')).not.toBeInTheDocument();
    fireEvent.click(result);
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ assetId: 21, variantId: 34, variantName: '酒店后厨-商用烤炉故障版 第19—21集' }),
    ]));
  });
});
