import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: {
    canEditWorkflowSkills: true,
    canViewWorkflowAgents: true,
    canManageTenantWorkflowSkills: true,
  },
  currentTenantId: 7,
  queryTenantSkills: vi.fn(),
  queryTenantSkill: vi.fn(),
  updateTenantSkill: vi.fn(),
  restoreTenantSkill: vi.fn(),
  queryAgents: vi.fn(),
  querySkills: vi.fn(),
  updateSkill: vi.fn(),
  querySkill: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useAccess: () => mocks.access,
  useModel: () => ({
    initialState: { currentTenantId: mocks.currentTenantId },
  }),
}));
vi.mock('../workflow-service', async () => {
  const actual = await vi.importActual<object>('../workflow-service');
  return {
    ...actual,
    queryWorkflowAgents: mocks.queryAgents,
    queryWorkflowSkills: mocks.querySkills,
    updateWorkflowSkill: mocks.updateSkill,
    queryWorkflowSkill: mocks.querySkill,
    queryTenantWorkflowSkills: mocks.queryTenantSkills,
    queryTenantWorkflowSkill: mocks.queryTenantSkill,
    updateTenantWorkflowSkill: mocks.updateTenantSkill,
    restoreTenantWorkflowSkill: mocks.restoreTenantSkill,
  };
});

import WorkflowSkillsPage, { TenantWorkflowSkillsPage } from './index';

const planning = {
  code: 'short-drama-storyboard-planning',
  name: '整集分镜规划',
  description: '分镜规则',
  content: '---\nname: 整集分镜规划\ndescription: 分镜规则\n---\n系统正文',
  revision: 'system-rev',
  scope: 'SYSTEM',
  tenantEditable: true,
  referencingAgentCodes: ['short-drama-storyboard'],
};

describe('WorkflowSkillsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canEditWorkflowSkills = true;
    mocks.access.canViewWorkflowAgents = true;
    mocks.access.canManageTenantWorkflowSkills = true;
    mocks.currentTenantId = 7;
    mocks.queryTenantSkills.mockResolvedValue({
      data: [
        planning,
        {
          ...planning,
          code: 'short-drama-analysis-foundation',
          name: '基础规范',
          tenantEditable: false,
        },
      ],
    });
    mocks.queryTenantSkill.mockResolvedValue({ data: planning });
    mocks.updateTenantSkill.mockResolvedValue({ success: true });
    mocks.restoreTenantSkill.mockResolvedValue({ success: true });
    mocks.queryAgents.mockResolvedValue({
      success: true,
      data: [{ code: 'screenplay-agent', name: '编剧 Agent' }],
    });
    mocks.querySkills.mockResolvedValue({
      success: true,
      data: [
        {
          code: 'rewrite-guide',
          name: '改写指南',
          description: '规范',
          content: '---\nname: rewrite-guide\ndescription: 规范\n---\n正文',
          revision: 'hash-1',
          referencingAgentCodes: ['screenplay-agent'],
        },
      ],
    });
    mocks.updateSkill.mockResolvedValue({ success: true, data: {} });
    mocks.querySkill.mockResolvedValue({
      success: true,
      data: {
        code: 'rewrite-guide',
        name: '改写指南',
        description: '新规范',
        content:
          '---\nname: rewrite-guide\ndescription: 新规范\n---\n服务器正文',
        revision: 'hash-2',
        referencingAgentCodes: ['screenplay-agent'],
      },
    });
  });

  it('edits the complete SKILL.md and shows reference impact', async () => {
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    expect(await screen.findByText('改写指南')).toBeInTheDocument();
    expect(await screen.findByText('编剧 Agent')).toHaveAttribute(
      'title',
      'screenplay-agent',
    );
    fireEvent.click(screen.getByRole('button', { name: /编辑/ }));
    expect(await screen.findByDisplayValue('rewrite-guide')).toBeDisabled();
    expect(screen.getByDisplayValue(/name: rewrite-guide/)).toBeInTheDocument();
    expect(screen.getAllByText(/screenplay-agent/).length).toBeGreaterThan(0);
    expect(
      screen.getByText('当前引用：编剧 Agent（screenplay-agent）'),
    ).toBeInTheDocument();
    expect(screen.getAllByText(/保存后立即生效/).length).toBeGreaterThan(0);
  });

  it('is read-only without edit permission', async () => {
    mocks.access.canEditWorkflowSkills = false;
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    await waitFor(() => expect(mocks.querySkills).toHaveBeenCalled());
    expect(
      screen.queryByRole('button', { name: /新增 Skill/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /编辑/ }),
    ).not.toBeInTheDocument();
  });

  it('maps built-in references without requesting unauthorized Agent data', async () => {
    mocks.access.canViewWorkflowAgents = false;
    mocks.querySkills.mockResolvedValueOnce({
      success: true,
      data: [
        {
          code: 'guide',
          name: '解析规范',
          description: '规范',
          referencingAgentCodes: ['short-drama-storyboard', 'unknown-agent'],
        },
      ],
    });
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    expect(await screen.findByText('分镜规划')).toHaveAttribute(
      'title',
      'short-drama-storyboard',
    );
    expect(screen.getByText('unknown-agent')).toBeInTheDocument();
    expect(mocks.queryAgents).not.toHaveBeenCalled();
  });

  it('keeps Skills available when Agent names fail to load', async () => {
    mocks.queryAgents.mockRejectedValueOnce(new Error('unavailable'));
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    expect(await screen.findByText('改写指南')).toBeInTheDocument();
    expect(screen.getByText('screenplay-agent')).toBeInTheDocument();
  });

  it('combines Agent and scope dropdown filters', async () => {
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    await screen.findByText('改写指南');
    fireEvent.mouseDown(screen.getByLabelText('按 Agent 筛选'));
    fireEvent.click(
      await screen.findByText('分镜规划（short-drama-storyboard）', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await waitFor(() =>
      expect(mocks.querySkills).toHaveBeenLastCalledWith(
        undefined,
        expect.objectContaining({ agentCode: 'short-drama-storyboard' }),
      ),
    );
    fireEvent.mouseDown(screen.getByLabelText('按系统内置筛选'));
    fireEvent.click(
      await screen.findByText('租户级', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await waitFor(() =>
      expect(mocks.querySkills).toHaveBeenLastCalledWith(
        undefined,
        expect.objectContaining({
          agentCode: 'short-drama-storyboard',
          scope: 'TENANT',
        }),
      ),
    );
  });

  it('customizes only allowed tenant Skills using the current effective revision', async () => {
    render(
      <App>
        <WorkflowSkillsPage tenantId={7} />
      </App>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /自定义/ }));
    const editor = await screen.findByLabelText('完整 SKILL.md');
    fireEvent.change(editor, {
      target: { value: `${planning.content}\n团队规则` },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存并立即生效' }));
    await waitFor(() =>
      expect(mocks.updateTenantSkill).toHaveBeenCalledWith(
        7,
        planning.code,
        `${planning.content}\n团队规则`,
        'system-rev',
      ),
    );
    expect(mocks.updateSkill).not.toHaveBeenCalled();
    expect(
      screen.queryByRole('button', { name: /新增 Skill/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看' })).toBeInTheDocument();
    expect(mocks.queryAgents).not.toHaveBeenCalled();
  });

  it('restores only the selected tenant override', async () => {
    mocks.queryTenantSkills.mockResolvedValue({
      data: [
        {
          ...planning,
          scope: 'TENANT',
          tenantId: 7,
          tenantName: '甲团队',
          revision: 'tenant-rev',
        },
      ],
    });
    render(
      <App>
        <WorkflowSkillsPage tenantId={7} />
      </App>,
    );
    expect(await screen.findByText('甲团队（7）')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '恢复系统默认' }));
    fireEvent.click(await screen.findByRole('button', { name: '恢复默认' }));
    await waitFor(() =>
      expect(mocks.restoreTenantSkill).toHaveBeenCalledWith(
        7,
        planning.code,
        'tenant-rev',
      ),
    );
  });

  it('does not carry stale responses or draft state across team changes', async () => {
    let resolveFirst: (value: unknown) => void = () => {};
    mocks.queryTenantSkills.mockImplementation((tenantId) =>
      tenantId === 7
        ? new Promise((resolve) => {
            resolveFirst = resolve;
          })
        : Promise.resolve({ data: [{ ...planning, name: '乙团队规则' }] }),
    );
    const page = render(
      <App>
        <TenantWorkflowSkillsPage />
      </App>,
    );
    await waitFor(() =>
      expect(mocks.queryTenantSkills).toHaveBeenCalledWith(
        7,
        undefined,
        expect.anything(),
      ),
    );
    mocks.currentTenantId = 8;
    page.rerender(
      <App>
        <TenantWorkflowSkillsPage />
      </App>,
    );
    await screen.findByText('乙团队规则');
    resolveFirst({ data: [{ ...planning, name: '甲团队旧响应' }] });
    await waitFor(() =>
      expect(screen.queryByText('甲团队旧响应')).not.toBeInTheDocument(),
    );
    expect(screen.getByText('乙团队规则')).toBeInTheDocument();
  });

  it('preserves unsaved content on revision conflict and reloads only on demand', async () => {
    mocks.updateSkill.mockRejectedValue({
      data: { errorMessage: 'Skill 已被其他人修改，发生冲突' },
    });
    render(
      <App>
        <WorkflowSkillsPage />
      </App>,
    );
    await screen.findByText('改写指南');
    fireEvent.click(screen.getByRole('button', { name: /编辑/ }));
    const editor = await screen.findByLabelText('完整 SKILL.md');
    const draft =
      '---\nname: rewrite-guide\ndescription: 我的修改\n---\n未保存正文';
    fireEvent.change(editor, { target: { value: draft } });
    fireEvent.click(screen.getByRole('button', { name: '保存并立即生效' }));

    expect(await screen.findByText(/发生冲突/)).toBeInTheDocument();
    expect(editor).toHaveValue(draft);
    expect(mocks.querySkill).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /加载最新文件/ }));
    await waitFor(() =>
      expect(mocks.querySkill).toHaveBeenCalledWith('rewrite-guide'),
    );
    expect(
      (screen.getByLabelText('完整 SKILL.md') as HTMLTextAreaElement).value,
    ).toContain('服务器正文');
  });
});
