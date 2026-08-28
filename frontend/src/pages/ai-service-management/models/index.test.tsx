import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: { canViewPlatformAiModels: false },
  queryPlatformModels: vi.fn(),
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
  queryPlatformModels: mocks.queryPlatformModels,
  queryPlatformProviders: mocks.queryPlatformProviders,
}));

import PlatformModelsPage from './index';

describe('PlatformModelsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canViewPlatformAiModels = false;
  });

  it('does not load Providers without Model-view access', () => {
    render(<PlatformModelsPage />);

    expect(mocks.queryPlatformProviders).not.toHaveBeenCalled();
  });
});
