const tokenMetrics = new Set([
  'INPUT_TOKEN',
  'CACHED_INPUT_TOKEN',
  'CACHE_WRITE_TOKEN',
  'OUTPUT_TOKEN',
]);

type CostVersion = {
  status?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
};

export type PricingComponent = API.ModelPriceComponentResponse & API.PointPolicyComponentResponse & { dimensions?: Record<string, string> };

type CostComponent = {
  metric: string;
  unitSize: number;
  unitPrice: number;
  currency?: string;
  dimensions?: Record<string, string>;
};

export const parsePricingDimensions = (value?: string): Record<string, string> => {
  if (!value?.trim()) return {};
  let dimensions: unknown;
  try {
    dimensions = JSON.parse(value);
  } catch {
    throw new Error('计费条件必须是合法 JSON');
  }
  if (dimensions === null || typeof dimensions !== 'object' || Array.isArray(dimensions)) {
    throw new Error('计费条件必须是 JSON 对象，例如 {"resolution":"720p"}');
  }
  if (Object.values(dimensions).some((item) => typeof item !== 'string')) {
    throw new Error('计费条件的值必须是字符串，例如 {"referenceVideo":"false"}');
  }
  return dimensions as Record<string, string>;
};

export const formatPricingDimensions = (dimensions?: Record<string, string>) =>
  JSON.stringify(dimensions ?? {}, null, 2);

const normalizeLocalDateTime = (value: string) => value.replace(' ', 'T');

export const formatBillingUnit = (metric: string, unitSize: number) => {
  if (tokenMetrics.has(metric)) return `${unitSize / 1_000_000} 百万 Token`;
  if (metric === 'CALL') return `${unitSize} 次`;
  if (metric === 'IMAGE') return `${unitSize} 张`;
  if (metric === 'VIDEO_SECOND' || metric === 'AUDIO_SECOND')
    return `${unitSize} 秒`;
  if (metric === 'CHARACTER') return `${unitSize} 字符`;
  return `${unitSize}`;
};

export const findEffectiveCostVersion = <T extends CostVersion>(
  versions: T[],
  effectiveAt: string,
) => {
  const normalizedEffectiveAt = normalizeLocalDateTime(effectiveAt);
  return versions
    .filter((version) => {
      const effectiveFrom =
        version.effectiveFrom && normalizeLocalDateTime(version.effectiveFrom);
      const effectiveTo =
        version.effectiveTo && normalizeLocalDateTime(version.effectiveTo);
      return (
        version.status === 'PUBLISHED' &&
        effectiveFrom != null &&
        effectiveFrom <= normalizedEffectiveAt &&
        (effectiveTo == null || normalizedEffectiveAt < effectiveTo)
      );
    })
    .sort((left, right) =>
      normalizeLocalDateTime(right.effectiveFrom ?? '').localeCompare(
        normalizeLocalDateTime(left.effectiveFrom ?? ''),
      ),
    )[0];
};

export const buildPointComponents = (
  components: CostComponent[],
  multiplier: number,
) =>
  components.map(({ metric, unitSize, unitPrice, dimensions }) => ({
    metric,
    unitSize,
    pointRate: unitPrice * multiplier,
    dimensionsJson: formatPricingDimensions(dimensions),
  }));
