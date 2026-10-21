import { describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/services/point-estimate', () => ({ pointEstimatedRequest: request }));
import { hasProjectPermission, queryProjectMetadata } from './project';

describe('hasProjectPermission', () => {
  it('uses only the effective permissions returned for that project', () => {
    const project = {
      effectivePermissions: ['PROJECT_AI_CONFIG_VIEW'],
    } as any;

    expect(hasProjectPermission(project, 'PROJECT_AI_CONFIG_VIEW')).toBe(true);
    expect(hasProjectPermission(project, 'PROJECT_AI_CONFIG_EDIT')).toBe(false);
  });
});

it('requests project metadata without the initial script body', async () => {
  await queryProjectMetadata(7);
  expect(request).toHaveBeenCalledWith('/api/projects/7/metadata');
});
