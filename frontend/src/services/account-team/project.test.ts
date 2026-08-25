import { describe, expect, it } from 'vitest';
import { hasProjectPermission } from './project';

describe('hasProjectPermission', () => {
  it('uses only the effective permissions returned for that project', () => {
    const project = {
      effectivePermissions: ['PROJECT_AI_CONFIG_VIEW'],
    } as any;

    expect(hasProjectPermission(project, 'PROJECT_AI_CONFIG_VIEW')).toBe(true);
    expect(hasProjectPermission(project, 'PROJECT_AI_CONFIG_EDIT')).toBe(false);
  });
});
