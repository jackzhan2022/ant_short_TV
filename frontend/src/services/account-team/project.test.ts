import { describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/services/point-estimate', () => ({ pointEstimatedRequest: request }));
import { canUseProjectFeatures, queryProjectMetadata } from './project';

describe('canUseProjectFeatures', () => {
  it('uses only the effective permissions returned for that project', () => {
    const project = {
      effectivePermissions: ['MENU:PROJECTS'],
    } as any;

    expect(canUseProjectFeatures(project)).toBe(true);
    expect(canUseProjectFeatures({ ...project, effectivePermissions: [] })).toBe(false);
  });
});

it('requests project metadata without the initial script body', async () => {
  await queryProjectMetadata(7);
  expect(request).toHaveBeenCalledWith('/api/projects/7/metadata');
});
