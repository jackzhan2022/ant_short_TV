import type { CharacterAsset, PropAsset, SceneAsset } from './service';

type Asset = CharacterAsset | SceneAsset | PropAsset;
export type AssetGenerationFilter =
  | 'ALL'
  | 'NOT_STARTED'
  | 'COMPLETED'
  | 'GENERATING'
  | 'FAILED';

// Rendering instructions remain available in the original prompt, never in a gallery summary.
export function assetAppearanceSummary(value?: string | null): string {
  const text = value?.trim() || '';
  if (!text) return '';
  const fields = new Map<string, string>();
  for (const line of text.split(/\r?\n/)) {
    const match = line
      .replace(/\*\*/g, '')
      .match(/^\s*[-*]?\s*([^:：]{1,16})[:：]\s*(.+)$/);
    if (match) fields.set(match[1].trim(), match[2].trim());
  }
  const appearance = [
    '外观',
    '外观描述',
    '上身着装',
    '下身着装',
    '服装',
    '发型',
    '身材',
    '材质',
    '颜色',
    '空间布局',
    '环境描述',
    '光线',
  ]
    .map((key) => fields.get(key))
    .filter(Boolean)
    .slice(0, 3);
  if (appearance.length) return appearance.join('；');
  if (/^\s*#|任务[:：]|三视图|\*\*|角色描述[:：]/m.test(text)) return '';
  return text.replace(/\s+/g, ' ');
}

// Summary API chooses one asset state in this priority order. Keep counts stable
// when a detail workspace with individual variants is subsequently loaded.
export function assetImageGenerationStatus(
  asset: Asset,
): AssetGenerationFilter {
  const summaryStatus = asset.imagePreview?.generationStatus;
  if (
    summaryStatus &&
    ['NOT_STARTED', 'COMPLETED', 'GENERATING', 'FAILED'].includes(summaryStatus)
  ) {
    return summaryStatus as AssetGenerationFilter;
  }
  for (const status of [
    'GENERATING',
    'FAILED',
    'NOT_STARTED',
    'COMPLETED',
  ] as const) {
    if (
      (asset.visual?.generationSummary?.[status] ?? 0) > 0 ||
      asset.visual?.variants.some(
        (variant) => variant.generationStatus === status,
      )
    )
      return status;
  }
  return asset.mainImageThumbnailUrl ? 'COMPLETED' : 'NOT_STARTED';
}

export function assetMatchesGeneration(
  asset: Asset,
  filter: AssetGenerationFilter,
): boolean {
  return filter === 'ALL' || assetImageGenerationStatus(asset) === filter;
}
