import { beforeEach, describe, expect, it, vi } from 'vitest';
import { pointEstimatedRequest } from '@/services/point-estimate';
import {
  queryCharacterVariantResolution,
  resolveCharacterVariants,
} from './character-similarity-service';

vi.mock('@/services/point-estimate', () => ({
  pointEstimatedRequest: vi.fn(),
}));
describe('character variant resolution transport', () => {
  beforeEach(() => vi.resetAllMocks());
  it('keeps the preflight request read-only and sends pair IDs as query params', async () => {
    vi.mocked(pointEstimatedRequest).mockResolvedValue({
      data: { fingerprint: 'v1' },
    });
    await queryCharacterVariantResolution(56, 7, 11, {
      leftVariantId: 21,
      rightVariantId: 22,
    });
    expect(pointEstimatedRequest).toHaveBeenCalledWith(
      '/api/projects/56/character-prompt-similarity/7/characters/11/variant-resolution',
      { params: { leftVariantId: 21, rightVariantId: 22 } },
    );
  });
  it.each([
    'MERGED',
    'DISTINCT',
  ] as const)('posts the fingerprint and %s decision', async (decision) => {
    const data = {
      leftVariantId: 21,
      rightVariantId: 22,
      decision,
      fingerprint: 'v1',
      ...(decision === 'MERGED' ? { keptVariantId: 21 } : {}),
    };
    await resolveCharacterVariants(56, 7, 11, data);
    expect(pointEstimatedRequest).toHaveBeenCalledWith(
      '/api/projects/56/character-prompt-similarity/7/characters/11/variant-resolution',
      { method: 'POST', data },
    );
  });
});
