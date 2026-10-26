import { describe, expect, it, vi } from 'vitest';
import { imageFor, originalImageFor, referenceReady } from './StoryboardAssetReferenceEditor';
import type { AssetVisualWorkspace, CharacterAsset, StoryboardAssetReference, VisualVariant } from './service';

vi.mock('@/pages/asset-library/audio-service', () => ({ queryStoryboardCharacterVoice: vi.fn() }));

const variant: VisualVariant = {
  id: 11,
  assetId: 1,
  assetType: 'CHARACTER',
  name: 'Primary',
  sourceType: 'USER',
  generationStatus: 'SUCCESS',
  primary: true,
  usable: true,
  currentImageUrl: '/original.png',
  currentImageThumbnailUrl: '/display.png',
};

const visual: AssetVisualWorkspace = {
  variantCount: 1,
  primaryVariant: variant,
  variants: [variant],
  generationSummary: { SUCCESS: 1 },
  episodeBindings: [],
};

const asset: CharacterAsset = {
  id: 1,
  name: 'Character',
  roleType: 'MAIN',
  gender: '',
  ageRange: '',
  identity: '',
  personality: [],
  appearance: '',
  prompt: '',
  status: 'CONFIRMED',
  visual,
};

const reference: StoryboardAssetReference = {
  assetType: 'CHARACTER',
  assetId: 1,
  variantId: null,
  imageUrl: '/stale-original.png',
  referenceRole: 'VISIBLE',
  sortOrder: 0,
  resolutionStatus: 'RESOLVED',
  sourceType: 'MANUAL',
  lockedByUser: true,
};

describe('StoryboardAssetReferenceEditor display delivery', () => {
  it('uses the default variant display when a reference has no bound variant', () => {
    expect(imageFor(reference, [asset])).toBe('/display.png');
  });

  it('does not use variant or reference originals when the display is absent', () => {
    const originalOnly = { ...variant, currentImageThumbnailUrl: undefined };
    const originalOnlyAsset = {
      ...asset,
      visual: { ...visual, primaryVariant: originalOnly, variants: [originalOnly] },
    };

    expect(imageFor({ ...reference, variantId: 11 }, [originalOnlyAsset])).toBeUndefined();
  });

  it('does not render a stale original when its asset metadata is missing', () => {
    expect(imageFor({ ...reference, assetId: 99 }, [asset])).toBeUndefined();
  });

  it('reflects a newly usable image while preserving unresolved references', () => {
    expect(referenceReady({ ...reference, resolutionStatus: 'ASSET_PENDING' }, variant)).toBe(true);
    expect(referenceReady({ ...reference, resolutionStatus: 'UNRESOLVED' }, variant)).toBe(false);
  });
});


describe('storyboard reference original delivery', () => {
  it('previews the original while retaining the display thumbnail', () => {
    expect(originalImageFor(reference, [asset])).toBe('/original.png');
    expect(imageFor(reference, [asset])).toBe('/display.png');
  });

  it.each(['thumbnail', 'display', 'image', 'file'])('uses download for a project %s endpoint', (endpoint) => {
    expect(originalImageFor({ ...reference, imageThumbnailUrl: `/api/projects/12/ai-image-results/77/${endpoint}?token=old` }, []))
      .toBe('/api/projects/12/ai-image-results/77/download');
  });

  it('uses the reference original only when its thumbnail is displayed', () => {
    expect(originalImageFor({ ...reference, imageThumbnailUrl: '/reference-thumb.png' }, []))
      .toBe('/stale-original.png');
    expect(originalImageFor(reference, [])).toBeUndefined();
    expect(originalImageFor({ ...reference, resolutionStatus: 'SCENE_INCLUDED' }, [asset])).toBeUndefined();
  });
});
