import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: {
    canViewPlatformAiProviders: true,
    canViewPlatformAiModels: true,
    canViewAiCallLogs: true,
  },
  location: { search: '' },
  replace: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { location: mocks.location, replace: mocks.replace },
  useAccess: () => mocks.access,
}));

vi.mock('antd', () => ({
  Tabs: ({ activeKey, items, onChange }: any) => (
    <div>
      {items.map((item: any) => (
        <button key={item.key} type="button" onClick={() => onChange(item.key)}>
          {item.label}
        </button>
      ))}
      <div>{items.find((item: any) => item.key === activeKey)?.children}</div>
    </div>
  ),
}));

vi.mock('../providers', () => ({ default: () => <div>service-provider-page</div> }));
vi.mock('../models', () => ({ default: () => <div>ai-model-page</div> }));
vi.mock('../logs', () => ({ default: () => <div>call-log-page</div> }));

import ModelManagementPage from './index';

describe('ModelManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canViewPlatformAiProviders = true;
    mocks.access.canViewPlatformAiModels = true;
    mocks.access.canViewAiCallLogs = true;
    mocks.location.search = '';
  });

  it('opens the first authorized tab and renders all authorized tabs', () => {
    render(<ModelManagementPage />);

    expect(screen.getByRole('button', { name: '模型服务商' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 大模型' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '调用日志' })).toBeInTheDocument();
    expect(screen.getByText('service-provider-page')).toBeInTheDocument();
  });

  it('falls back to the first authorized tab when the requested tab is unavailable', () => {
    mocks.access.canViewPlatformAiProviders = false;
    mocks.access.canViewAiCallLogs = false;
    mocks.location.search = '?tab=providers';

    render(<ModelManagementPage />);

    expect(screen.queryByRole('button', { name: '模型服务商' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '调用日志' })).not.toBeInTheDocument();
    expect(screen.getByText('ai-model-page')).toBeInTheDocument();
    expect(mocks.replace).toHaveBeenCalledWith('/ai-service-management/model-management?tab=models');
  });
});
