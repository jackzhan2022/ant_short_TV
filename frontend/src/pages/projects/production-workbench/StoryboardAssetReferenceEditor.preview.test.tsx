import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StoryboardAssetReferenceEditor from './StoryboardAssetReferenceEditor';
import type { CharacterAsset, StoryboardAssetReference } from './service';

vi.mock('@/pages/asset-library/audio-service', () => ({
  queryStoryboardCharacterVoice: vi.fn(),
}));

const reference: StoryboardAssetReference = {
  id: 1,
  assetType: 'CHARACTER',
  assetId: 21,
  assetName: '王晓梅',
  variantId: 33,
  referenceRole: 'VISIBLE',
  sortOrder: 0,
  resolutionStatus: 'RESOLVED',
  sourceType: 'MANUAL',
  lockedByUser: true,
};
const asset: CharacterAsset = {
  id: 21,
  name: '王晓梅',
  roleType: 'MAIN',
  gender: '',
  ageRange: '',
  identity: '',
  personality: [],
  appearance: '',
  prompt: '',
  status: 'CONFIRMED',
  visual: {
    variantCount: 1,
    variants: [
      {
        id: 33,
        assetType: 'CHARACTER',
        assetId: 21,
        name: '默认形象',
        sourceType: 'MANUAL',
        generationStatus: 'COMPLETED',
        primary: true,
        usable: true,
        currentImageResultId: 77,
        currentImageUrl: '/api/projects/12/ai-image-results/77/display',
        currentImageThumbnailUrl:
          '/api/projects/12/ai-image-results/77/thumbnail',
      },
    ],
    generationSummary: {},
    episodeBindings: [],
  },
};
const renderEditor = (
  assetType: StoryboardAssetReference['assetType'] = 'CHARACTER',
) => {
  const onChange = vi.fn();
  const onVoice = vi.fn();
  const onGenerateImage = vi.fn();
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <StoryboardAssetReferenceEditor
        storyboardId={301}
        storyboardNo={6}
        references={[{ ...reference, assetType }]}
        characters={assetType === 'CHARACTER' ? [asset] : []}
        scenes={
          assetType === 'SCENE'
            ? [
                {
                  id: asset.id,
                  name: asset.name,
                  status: asset.status,
                  visual: asset.visual,
                  sceneType: 'INTERIOR',
                  atmosphere: '',
                  description: '',
                  visualStyle: '',
                  prompt: '',
                },
              ]
            : []
        }
        props={
          assetType === 'PROP'
            ? [
                {
                  id: asset.id,
                  name: asset.name,
                  status: asset.status,
                  visual: asset.visual,
                  propType: 'OTHER',
                  appearance: '',
                  plotFunction: '',
                  prompt: '',
                },
              ]
            : []
        }
        onChange={onChange}
        onVoice={onVoice}
        onGenerateImage={onGenerateImage}
        generatingReferenceKeys={new Set()}
      />
    </ConfigProvider>,
  );
  return { onChange, onVoice, onGenerateImage };
};

beforeEach(() => vi.stubGlobal('IntersectionObserver', undefined));
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('storyboard reference image preview', () => {
  it('opens the real character variants without changing the reference until confirmed', async () => {
    const onChange = vi.fn();
    const onLoadVariants = vi.fn().mockResolvedValue(undefined);
    const visual = asset.visual;
    if (!visual) throw new Error('Expected visual variants in the fixture');
    const second = {
      ...visual.variants[0],
      id: 34,
      name: '工装',
      primary: false,
      currentImageThumbnailUrl: '/workwear-thumb.png',
      currentImageUrl: '/workwear.png',
    };
    render(
      <ConfigProvider theme={{ token: { motion: false } }}>
        <StoryboardAssetReferenceEditor
          storyboardId={301}
          storyboardNo={6}
          references={[reference]}
          characters={[
            {
              ...asset,
              visual: {
                ...visual,
                variantCount: 4,
                variants: [visual.variants[0], second],
              },
            },
          ]}
          scenes={[]}
          props={[]}
          onChange={onChange}
          onVoice={vi.fn()}
          onGenerateImage={vi.fn()}
          generatingReferenceKeys={new Set()}
          onLoadVariants={onLoadVariants}
        />
      </ConfigProvider>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: '查看王晓梅的4种造型' }),
    );
    const gallery = await screen.findByRole('dialog');
    expect(within(gallery).getByText('王晓梅 · 造型')).toBeInTheDocument();
    expect(onLoadVariants).toHaveBeenCalledWith('CHARACTER', 21, 33, 1, 20);
    fireEvent.click(within(gallery).getByRole('button', { name: '预览工装' }));
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(
      within(gallery).getByRole('button', { name: '使用此造型' }),
    );
    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({
        assetId: 21,
        variantId: 34,
        variantName: '工装',
        sourceType: 'MANUAL',
        lockedByUser: true,
      }),
    ]);
  });

  it('opens a summary-only asset without presenting its loaded subset as the total', async () => {
    if (!asset.visual) throw new Error('Fixture needs a visual');
    const onLoadVariants = vi.fn().mockResolvedValue(undefined);
    render(
      <StoryboardAssetReferenceEditor
        storyboardId={301}
        storyboardNo={6}
        references={[reference]}
        characters={[
          {
            ...asset,
            visual: { ...asset.visual, summaryOnly: true, variantCount: 2 },
          },
        ]}
        scenes={[]}
        props={[]}
        onChange={vi.fn()}
        onVoice={vi.fn()}
        onGenerateImage={vi.fn()}
        generatingReferenceKeys={new Set()}
        onLoadVariants={onLoadVariants}
      />,
    );
    expect(screen.queryByText('2 种造型')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看王晓梅的造型' }));
    await screen.findByRole('dialog');
    expect(onLoadVariants).toHaveBeenCalledWith('CHARACTER', 21, 33, 1, 20);
  });

  it('distinguishes all referenced characters from the number of costumes and reveals the real references', () => {
    const characters = Array.from({ length: 6 }, (_, index) => ({
      ...asset,
      id: 21 + index,
      name: `角色${index + 1}`,
      visual: undefined,
    }));
    const references = characters.map((character, index) => ({
      ...reference,
      id: index + 1,
      assetId: character.id,
      assetName: character.name,
      variantId: null,
    }));
    const onChange = vi.fn();
    render(
      <StoryboardAssetReferenceEditor
        storyboardId={301}
        storyboardNo={6}
        references={references}
        characters={characters}
        scenes={[]}
        props={[]}
        onChange={onChange}
        onVoice={vi.fn()}
        onGenerateImage={vi.fn()}
        generatingReferenceKeys={new Set()}
      />,
    );
    const list = screen.getByRole('list', { name: '分镜6角色资产' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.queryByText('+4')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看全部 6 个角色' }));
    expect(within(list).getAllByRole('listitem')).toHaveLength(6);
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '收起角色' }));
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
  });

  it.each([
    'CHARACTER',
    'SCENE',
    'PROP',
  ] as const)('opens the %s original from the thumbnail without changing the binding', async (assetType) => {
    const { onChange, onGenerateImage } = renderEditor(assetType);
    const thumbnail = screen.getByAltText('王晓梅参考图');
    expect(thumbnail).toHaveAttribute(
      'src',
      '/api/projects/12/ai-image-results/77/thumbnail',
    );
    expect(
      document.querySelector('.ant-image-preview'),
    ).not.toBeInTheDocument();
    fireEvent.load(thumbnail);
    fireEvent.click(thumbnail);
    await waitFor(() =>
      expect(document.querySelector('.ant-image-preview-img')).toHaveAttribute(
        'src',
        '/api/projects/12/ai-image-results/77/download',
      ),
    );
    const close = document.querySelector<HTMLButtonElement>(
      '.ant-image-preview-close',
    );
    expect(close).not.toBeNull();
    if (close) fireEvent.click(close);
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: '王晓梅参考图' }),
      ).not.toBeInTheDocument(),
    );
    expect(onChange).not.toHaveBeenCalled();
    expect(onGenerateImage).not.toHaveBeenCalled();
  });

  it('keeps the voice, regenerate and remove actions independent of preview', async () => {
    const { onChange, onVoice, onGenerateImage } = renderEditor();
    fireEvent.click(screen.getByRole('button', { name: '分镜6角色1引用操作' }));
    fireEvent.click(
      await screen.findByRole('button', { name: '分镜6王晓梅生成语音' }),
    );
    expect(onVoice).toHaveBeenCalledWith('王晓梅');
    fireEvent.click(
      screen.getByRole('button', { name: '重新生成分镜6角色1资产图' }),
    );
    expect(onGenerateImage).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '移除分镜6角色1' }));
    expect(onChange).toHaveBeenCalledWith([]);
    expect(
      document.querySelector('.ant-image-preview'),
    ).not.toBeInTheDocument();
  });
});
