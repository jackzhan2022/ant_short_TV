import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: {
    canViewPlatformAiProviders: true,
    canViewPlatformAiModels: true,
    canViewAiCallLogs: true,
    canViewBuiltInAiAgents: true,
    canViewWorkflowAgents: true,
    canViewWorkflowSkills: true,
    canManageTenantWorkflowSkills: false,
    canViewTenantWorkflowSkills: false,
    canViewPlatformAiConcurrency: false,
  },
}));

vi.mock('@umijs/max', () => ({
  useAccess: () => mocks.access,
}));

vi.mock('./index.style', () => ({
  useStyles: () => ({ styles: { modules: '', functions: '' } }),
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

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => (
    <main data-testid="model-management-page">{children}</main>
  ),
}));

vi.mock('../providers', () => ({
  default: () => <div>service-provider-page</div>,
}));
vi.mock('../platform-models', () => ({
  default: () => <div>ai-model-page</div>,
}));
vi.mock('../logs', () => ({ default: () => <div>call-log-page</div> }));
vi.mock('../concurrency-config', () => ({
  default: () => <div>concurrency-config-page</div>,
}));
vi.mock('../workflow-agents', () => ({
  default: () => <div>workflow-agent-page</div>,
}));
vi.mock('../workflow-skills', () => ({
  default: () => <div>workflow-skill-page</div>,
  TenantWorkflowSkillsPage: () => <div>tenant-skill-page</div>,
}));

import ModelManagementPage from './index';

describe('ModelManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canViewPlatformAiProviders = true;
    mocks.access.canViewPlatformAiModels = true;
    mocks.access.canViewAiCallLogs = true;
    mocks.access.canViewBuiltInAiAgents = true;
    mocks.access.canViewWorkflowAgents = true;
    mocks.access.canViewWorkflowSkills = true;
    mocks.access.canManageTenantWorkflowSkills = false;
    mocks.access.canViewTenantWorkflowSkills = false;
    mocks.access.canViewPlatformAiConcurrency = false;
  });

  it('opens system management for a configuration-only user without a model module', () => {
    for (const key of Object.keys(mocks.access)) {
      mocks.access[key as keyof typeof mocks.access] = false;
    }
    mocks.access.canViewPlatformAiConcurrency = true;
    render(<ModelManagementPage />);
    expect(screen.getByRole('button', { name: '系统管理' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '并行配置' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '大模型管理' })).not.toBeInTheDocument();
    expect(screen.getByText('concurrency-config-page')).toBeInTheDocument();
  });

  it('switches between authorized modules while keeping the existing business tabs', () => {
    mocks.access.canViewPlatformAiConcurrency = true;
    render(<ModelManagementPage />);
    fireEvent.click(screen.getByRole('button', { name: 'AI 大模型' }));
    fireEvent.click(screen.getByRole('button', { name: '系统管理' }));
    expect(screen.getByText('concurrency-config-page')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '大模型管理' }));
    expect(screen.getByText('ai-model-page')).toBeInTheDocument();
  });

  it('hides system management without its view permission', () => {
    render(<ModelManagementPage />);
    expect(screen.queryByRole('button', { name: '系统管理' })).not.toBeInTheDocument();
  });

  it('opens the first authorized tab and renders all authorized tabs', () => {
    render(<ModelManagementPage />);

    expect(screen.getByTestId('model-management-page')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '模型服务商' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'AI 大模型' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '调用日志' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Agent 管理' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Skill 管理' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Agent（新）' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Skill（新）' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('service-provider-page')).toBeInTheDocument();
  });

  it('allows ordinary team viewers to use the tenant tab without platform Skill access', () => {
    mocks.access.canViewPlatformAiProviders = false;
    mocks.access.canViewPlatformAiModels = false;
    mocks.access.canViewAiCallLogs = false;
    mocks.access.canViewWorkflowAgents = false;
    mocks.access.canViewWorkflowSkills = false;
    mocks.access.canViewTenantWorkflowSkills = true;
    mocks.access.canManageTenantWorkflowSkills = false;
    render(<ModelManagementPage />);
    expect(
      screen.getByRole('button', { name: '团队 Skill' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Skill 管理' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('tenant-skill-page')).toBeInTheDocument();
  });

  it('switches content locally when a visible tab is selected', () => {
    render(<ModelManagementPage />);

    fireEvent.click(screen.getByRole('button', { name: 'AI 大模型' }));
    expect(screen.getByText('ai-model-page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '调用日志' }));
    expect(screen.getByText('call-log-page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Agent 管理' }));
    expect(screen.getByText('workflow-agent-page')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Skill 管理' }));
    expect(screen.getByText('workflow-skill-page')).toBeInTheDocument();
  });

  it('opens the first permitted tab without URL fallback', () => {
    mocks.access.canViewPlatformAiProviders = false;
    mocks.access.canViewAiCallLogs = false;
    mocks.access.canViewBuiltInAiAgents = false;
    mocks.access.canViewWorkflowAgents = false;
    mocks.access.canViewWorkflowSkills = false;

    render(<ModelManagementPage />);

    expect(
      screen.queryByRole('button', { name: '模型服务商' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '调用日志' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('ai-model-page')).toBeInTheDocument();
  });

  it('does not let old permissions grant access to workflow modules', () => {
    mocks.access.canViewPlatformAiProviders = false;
    mocks.access.canViewPlatformAiModels = false;
    mocks.access.canViewAiCallLogs = false;
    mocks.access.canViewBuiltInAiAgents = true;
    mocks.access.canViewWorkflowAgents = false;
    mocks.access.canViewWorkflowSkills = true;

    render(<ModelManagementPage />);

    expect(
      screen.getByRole('button', { name: 'Skill 管理' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Agent 管理' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('workflow-skill-page')).toBeInTheDocument();
  });
});
