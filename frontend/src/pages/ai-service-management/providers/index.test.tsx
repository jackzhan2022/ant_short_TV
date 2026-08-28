import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canViewPlatformAiProviders: false },
  queryPlatformProviders: vi.fn(),
}));

vi.mock('@umijs/max', () => ({ useAccess: () => mocks.access }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ request }: any) => {
    void request();
    return null;
  },
}));
vi.mock('antd', () => ({ App: { useApp: () => ({ message: {} }) } }));
vi.mock('../platform-service', () => ({
  queryPlatformProviders: mocks.queryPlatformProviders,
}));

import PlatformProvidersPage from './index';

describe('PlatformProvidersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canViewPlatformAiProviders = false;
  });

  it('does not load Providers without Provider-view access', () => {
    render(<PlatformProvidersPage />);

    expect(mocks.queryPlatformProviders).not.toHaveBeenCalled();
  });
});
