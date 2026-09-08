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

type CostComponent = {
  metric: string;
  unitSize: number;
  unitPrice: number;
  currency?: string;
};

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
  components.map(({ metric, unitSize, unitPrice }) => ({
    metric,
    unitSize,
    pointRate: unitPrice * multiplier,
  }));
