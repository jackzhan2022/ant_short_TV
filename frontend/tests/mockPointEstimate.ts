import { beforeEach, vi } from 'vitest';
import type { PointEstimateOperation } from '@/services/point-estimate';

// Business-flow tests use an already approved quote. Loading, failures, races and real
// request/response behavior are covered by PointEstimateButton and free-creation tests.
const pointEstimateCalls = vi.hoisted(
  () =>
    [] as {
      operations: PointEstimateOperation[] | undefined;
      options: { tenantId?: number; enabled?: boolean };
    }[],
);
vi.mock('@/components/PointEstimateButton/usePointEstimate', () => ({
  default: (
    operations: PointEstimateOperation[] | undefined,
    options: { tenantId?: number; enabled?: boolean },
  ) => {
    pointEstimateCalls.push({ operations, options });
    return {
      loading: false,
      disabled: false,
      estimate: undefined,
      error: undefined,
      reason: undefined,
      refresh: () => {},
    };
  },
}));
beforeEach(() => {
  pointEstimateCalls.length = 0;
});
export function recordedPointEstimates() {
  return pointEstimateCalls;
}
