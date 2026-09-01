import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  access: {
    canEditWorkflowAgents: true,
    canViewWorkflowSkills: true,
    canViewPlatformAiModels: true,
  },
  queryAgents: vi.fn(),
  querySkills: vi.fn(),
  queryTools: vi.fn(),
  queryModels: vi.fn(),
  runTemporary: vi.fn(),
  queryRuns: vi.fn(),
  queryRun: vi.fn(),
  updateAgent: vi.fn(),
}));

vi.mock('@umijs/max', () => ({ useAccess: () => mocks.access }));
vi.mock('../workflow-service', async () => {
  const actual = await vi.importActual<object>('../workflow-service');
  return {
    ...actual,
    queryWorkflowAgents: mocks.queryAgents,
    queryWorkflowSkills: mocks.querySkills,
    queryWorkflowTools: mocks.queryTools,
    runTemporaryWorkflowAgent: mocks.runTemporary,
    queryWorkflowAgentRuns: mocks.queryRuns,
    queryWorkflowAgentRun: mocks.queryRun,
    updateWorkflowAgent: mocks.updateAgent,
  };
});
vi.mock('../platform-service', async () => {
  const actual = await vi.importActual<object>('../platform-service');
  return { ...actual, queryPlatformModels: mocks.queryModels };
});

import WorkflowAgentsPage from './index';

describe('WorkflowAgentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.access.canEditWorkflowAgents = true;
    mocks.queryAgents.mockResolvedValue({
      success: true,
      data: [
        {
          id: 1,
          code: 'screenplay-agent',
          name: '编剧 Agent',
          description: '改写',
          systemPrompt: '调用 read_episode_script',
          modelId: 8,
          temperature: 0.2,
          maxTokens: 2048,
          maxSteps: 3,
          status: 'ENABLED',
          revision: 0,
          createdAt: '2026-08-30',
          updatedAt: '2026-08-30',
          skillCodes: ['guide'],
          toolCodes: ['read_episode_script'],
        },
      ],
    });
    mocks.querySkills.mockResolvedValue({
      success: true,
      data: [
        {
          code: 'guide',
          name: '改写指南',
          description: '规范',
          content: 'body',
          revision: 'hash',
          referencingAgentCodes: ['screenplay-agent'],
        },
      ],
    });
    mocks.queryTools.mockResolvedValue({
      success: true,
      data: [
        {
          code: 'read_episode_script',
          name: '读取剧集',
          description: '读取当前剧集',
          inputSchema: { type: 'object' },
          outputSchema: { type: 'object' },
          riskLevel: 'READ_ONLY',
          failurePolicy: 'TERMINAL',
        },
      ],
    });
    mocks.queryModels.mockResolvedValue({
      success: true,
      data: [
        {
          id: 8,
          code: 'gpt',
          name: 'GPT',
          modelCode: 'gpt',
          serviceType: 'TEXT',
          status: 'ENABLED',
          providerId: 1,
          capabilities: ['TEXT_GENERATION', 'TOOL_CALLING'],
        },
      ],
    });
    mocks.runTemporary.mockResolvedValue({
      success: true,
      data: { runId: 10, output: 'ok' },
    });
    mocks.queryRuns.mockResolvedValue({
      success: true,
      data: [
        {
          id: 49,
          agentCode: 'screenplay-agent',
          runType: 'FORMAL',
          status: 'FAILED',
          errorCode: 'REQUIRED_TOOL_NOT_CALLED',
          errorMessage: '必须先完成可信读取',
          startedAt: '2026-09-01 10:00:00',
        },
      ],
    });
    mocks.queryRun.mockResolvedValue({
      success: true,
      data: {
        id: 10,
        status: 'SUCCESS',
        finalOutput: '测试完成',
        steps: [
          {
            stepNo: 1,
            stepType: 'TOOL',
            toolCode: 'read_episode_script',
            status: 'SUCCESS',
            outputJson: '{"content":"第一集"}',
          },
        ],
      },
    });
  });

  it('loads Agents and exposes create, edit, test and tool metadata actions', async () => {
    render(
      <App>
        <WorkflowAgentsPage />
      </App>,
    );
    expect(await screen.findByText('编剧 Agent')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /新增 Agent/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /编辑/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /测试/ })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /编辑/ }));
    expect(await screen.findByDisplayValue('screenplay-agent')).toBeDisabled();
    expect(screen.getAllByText(/保存后立即生效/).length).toBeGreaterThan(0);
    expect(screen.getByText('读取剧集')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '读取剧集' }));
    expect(
      (
        screen.getByLabelText(
          '系统提示词 / 纯文本工作流',
        ) as HTMLTextAreaElement
      ).value,
    ).toContain('先调用 read_episode_script');
  });

  it('is read-only without edit permission', async () => {
    mocks.access.canEditWorkflowAgents = false;
    render(
      <App>
        <WorkflowAgentsPage />
      </App>,
    );
    await waitFor(() => expect(mocks.queryAgents).toHaveBeenCalled());
    expect(
      screen.queryByRole('button', { name: /新增 Agent/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /编辑/ }),
    ).not.toBeInTheDocument();
  });

  it('explains tools on the prompt helper button', async () => {
    render(
      <App>
        <WorkflowAgentsPage />
      </App>,
    );
    await screen.findByText('编剧 Agent');
    fireEvent.click(screen.getByRole('button', { name: /编辑/ }));

    fireEvent.mouseEnter(
      await screen.findByRole('button', { name: '读取剧集' }),
    );

    expect(await screen.findByRole('tooltip')).toHaveTextContent('读取当前剧集');
  });

  it('runs unsaved form values without saving the Agent', async () => {
    render(
      <App>
        <WorkflowAgentsPage />
      </App>,
    );
    await screen.findByText('编剧 Agent');
    fireEvent.click(screen.getByRole('button', { name: /编辑/ }));
    const prompt = await screen.findByLabelText('系统提示词 / 纯文本工作流');
    fireEvent.change(prompt, { target: { value: '未保存的临时提示词' } });
    fireEvent.click(screen.getByRole('button', { name: '测试当前配置' }));
    expect(
      await screen.findByText(/不会覆盖已保存的 Agent/),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('输入内容'), {
      target: { value: '测试输入' },
    });
    fireEvent.change(screen.getByLabelText('项目 ID'), {
      target: { value: '25' },
    });
    fireEvent.click(screen.getByRole('button', { name: '开始测试' }));

    await waitFor(() => expect(mocks.runTemporary).toHaveBeenCalled());
    expect(mocks.runTemporary.mock.calls[0][0]).toMatchObject({
      systemPrompt: '未保存的临时提示词',
      input: '测试输入',
      projectId: 25,
    });
    expect(mocks.updateAgent).not.toHaveBeenCalled();
    expect(await screen.findByText('测试完成')).toBeInTheDocument();
    expect(screen.getAllByText('read_episode_script').length).toBeGreaterThan(
      0,
    );
  });

  it('shows failure diagnostics and steps for a historical run', async () => {
    mocks.queryRun.mockResolvedValueOnce({
      success: true,
      data: {
        id: 49,
        agentCode: 'screenplay-agent',
        runType: 'FORMAL',
        status: 'FAILED',
        userId: 9,
        modelId: 8,
        temperature: 0.2,
        maxTokens: 16384,
        maxSteps: 20,
        errorCode: 'REQUIRED_TOOL_NOT_CALLED',
        errorMessage: '必须先完成可信读取',
        finalOutput: '模型未能完成保存',
        promptSnapshot: '审核提示词快照',
        toolCodes: ['read_review_context', 'save_review_unit_result'],
        skillSnapshots: [],
        startedAt: '2026-09-01 10:00:00',
        steps: [
          {
            stepNo: 2,
            stepType: 'TOOL',
            toolCode: 'save_review_unit_result',
            status: 'FAILED',
            errorCode: 'REQUIRED_TOOL_NOT_CALLED',
            errorMessage: '必须先完成可信读取',
            startedAt: '2026-09-01 10:00:01',
          },
        ],
      },
    });
    render(
      <App>
        <WorkflowAgentsPage />
      </App>,
    );

    await screen.findByText('编剧 Agent');
    fireEvent.click(screen.getByRole('button', { name: /运行记录/ }));
    fireEvent.click(await screen.findByRole('button', { name: '详情' }));

    expect(
      await screen.findByText('REQUIRED_TOOL_NOT_CALLED'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('必须先完成可信读取').length).toBeGreaterThan(0);
    expect(screen.getByText('模型未能完成保存')).toBeInTheDocument();
    expect(screen.getByText('save_review_unit_result')).toBeInTheDocument();
    expect(screen.getByText('审核提示词快照')).toBeInTheDocument();
  });
});
