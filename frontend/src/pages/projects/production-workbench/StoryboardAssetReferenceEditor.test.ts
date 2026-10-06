import { describe, expect, it } from 'vitest';
import { imageFor, referenceReady } from './StoryboardAssetReferenceEditor';
import type { AssetVisualWorkspace, CharacterAsset, StoryboardAssetReference, VisualVariant } from './service';

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
