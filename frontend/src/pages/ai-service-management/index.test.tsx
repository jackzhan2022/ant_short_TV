import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: null as Record<string, boolean> | null,
  replace: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { replace: mocks.replace },
  useAccess: () => mocks.access,
}));

import AiServiceManagementIndex from './index';

describe('AiServiceManagementIndex', () => {
  it('waits for access initialization before redirecting', () => {
    expect(() => render(<AiServiceManagementIndex />)).not.toThrow();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it.each([
    'canViewWorkflowAgents',
    'canViewWorkflowSkills',
  ])('opens model management for %s permission alone', (permission) => {
    mocks.replace.mockClear();
    mocks.access = { [permission]: true };
    render(<AiServiceManagementIndex />);
    expect(mocks.replace).toHaveBeenCalledWith(
      '/ai-service-management/model-management',
    );
  });
});
