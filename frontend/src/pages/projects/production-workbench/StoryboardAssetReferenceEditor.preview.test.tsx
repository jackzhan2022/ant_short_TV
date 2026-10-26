import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfigProvider } from 'antd';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import type { CharacterAsset, StoryboardAssetReference } from './service';

vi.mock('@/pages/asset-library/audio-service', () => ({ queryStoryboardCharacterVoice: vi.fn() }));

const reference: StoryboardAssetReference = {
  id: 1, assetType: 'CHARACTER', assetId: 21, assetName: '王晓梅', variantId: 33,
  referenceRole: 'VISIBLE', sortOrder: 0, resolutionStatus: 'RESOLVED',
  sourceType: 'MANUAL', lockedByUser: true,
};
const asset: CharacterAsset = {
  id: 21, name: '王晓梅', roleType: 'MAIN', gender: '', ageRange: '', identity: '',
  personality: [], appearance: '', prompt: '', status: 'CONFIRMED',
  visual: { variantCount: 1, variants: [{
    id: 33, assetType: 'CHARACTER', assetId: 21, name: '默认形象', sourceType: 'MANUAL',
    generationStatus: 'COMPLETED', primary: true, usable: true,
    currentImageResultId: 77, currentImageUrl: '/api/projects/12/ai-image-results/77/display',
    currentImageThumbnailUrl: '/api/projects/12/ai-image-results/77/thumbnail',
  }], generationSummary: {}, episodeBindings: [] },
};
const renderEditor = (assetType: StoryboardAssetReference['assetType'] = 'CHARACTER') => {
  const onChange = vi.fn();
  const onVoice = vi.fn();
  const onGenerateImage = vi.fn();
  render(<ConfigProvider theme={{ token: { motion: false } }}><StoryboardAssetReferenceEditor storyboardId={301} storyboardNo={6} references={[{ ...reference, assetType }]}
    characters={assetType === 'CHARACTER' ? [asset] : []}
    scenes={assetType === 'SCENE' ? [{ id: asset.id, name: asset.name, status: asset.status, visual: asset.visual,
      sceneType: 'INTERIOR', atmosphere: '', description: '', visualStyle: '', prompt: '' }] : []}
    props={assetType === 'PROP' ? [{ id: asset.id, name: asset.name, status: asset.status, visual: asset.visual,
      propType: 'OTHER', appearance: '', plotFunction: '', prompt: '' }] : []} onChange={onChange} onVoice={onVoice}
    onGenerateImage={onGenerateImage} generatingReferenceKeys={new Set()} /></ConfigProvider>);
  return { onChange, onVoice, onGenerateImage };
};

beforeEach(() => vi.stubGlobal('IntersectionObserver', undefined));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('storyboard reference image preview', () => {
  it.each(['CHARACTER', 'SCENE', 'PROP'] as const)('opens the %s original from the thumbnail without changing the binding', async (assetType) => {
    const { onChange, onGenerateImage } = renderEditor(assetType);
    const thumbnail = screen.getByAltText('王晓梅参考图');
    expect(thumbnail).toHaveAttribute('src', '/api/projects/12/ai-image-results/77/thumbnail');
    expect(document.querySelector('.ant-image-preview')).not.toBeInTheDocument();
    fireEvent.load(thumbnail);
    fireEvent.click(thumbnail);
    await waitFor(() => expect(document.querySelector('.ant-image-preview-img')).toHaveAttribute('src', '/api/projects/12/ai-image-results/77/download'));
    const close = document.querySelector<HTMLButtonElement>('.ant-image-preview-close');
    expect(close).not.toBeNull();
    if (close) fireEvent.click(close);
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '王晓梅参考图' })).not.toBeInTheDocument());
    expect(onChange).not.toHaveBeenCalled();
    expect(onGenerateImage).not.toHaveBeenCalled();
  });

  it('keeps the voice, regenerate and remove actions independent of preview', () => {
    const { onChange, onVoice, onGenerateImage } = renderEditor();
    fireEvent.click(screen.getByRole('button', { name: '分镜6王晓梅生成语音' }));
    expect(onVoice).toHaveBeenCalledWith('王晓梅');
    fireEvent.click(screen.getByRole('button', { name: '重新生成分镜6角色1资产图' }));
    expect(onGenerateImage).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '移除分镜6角色1' }));
    expect(onChange).toHaveBeenCalledWith([]);
    expect(document.querySelector('.ant-image-preview')).not.toBeInTheDocument();
  });
});
