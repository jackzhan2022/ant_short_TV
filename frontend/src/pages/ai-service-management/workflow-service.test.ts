import { request } from '@umijs/max';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createWorkflowAgent,
  createWorkflowSkill,
  queryWorkflowAgentRuns,
  queryWorkflowAgents,
  queryWorkflowSkills,
  runTemporaryWorkflowAgent,
  updateWorkflowAgent,
  updateWorkflowSkill,
} from './workflow-service';

vi.mock('@umijs/max', () => ({ request: vi.fn() }));

describe('workflow Agent and Skill services', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(request).mockResolvedValue({ success: true, data: {} });
  });

  it('uses independent Agent CRUD and run endpoints', async () => {
    const payload = {
      code: 'screenplay-agent',
      name: '编剧',
      description: '',
      systemPrompt: '执行工作流',
      modelId: 8,
      temperature: 0.2,
      maxTokens: 2048,
      maxSteps: 3,
      status: 'ENABLED' as const,
      skillCodes: ['guide'],
      toolCodes: ['read_episode_script'],
    };
    await queryWorkflowAgents('screenplay');
    await createWorkflowAgent(payload);
    await updateWorkflowAgent('screenplay-agent', {
      ...payload,
      expectedRevision: 2,
    });
    await runTemporaryWorkflowAgent({
      ...payload,
      input: '改写',
      projectId: 25,
      episodeId: 91,
    });
    await queryWorkflowAgentRuns('screenplay-agent', 20);

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/api/platform/ai/workflow-agents',
      {
        params: { query: 'screenplay' },
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/platform/ai/workflow-agents',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: payload,
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/api/platform/ai/workflow-agents/screenplay-agent',
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        data: { ...payload, expectedRevision: 2 },
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/api/platform/ai/workflow-agent-runs/test',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: {
          ...payload,
          input: '改写',
          projectId: 25,
          episodeId: 91,
        },
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      5,
      '/api/platform/ai/workflow-agent-runs',
      {
        params: { agentCode: 'screenplay-agent', limit: 20 },
      },
    );
  });

  it('sends complete SKILL.md content and revision tokens', async () => {
    await queryWorkflowSkills('rewrite');
    await createWorkflowSkill(
      'rewrite-guide',
      '---\nname: rewrite\ndescription: guide\n---\nbody',
    );
    await updateWorkflowSkill('rewrite-guide', 'new complete file', 'hash-1');

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/api/platform/ai/workflow-skills',
      {
        params: { query: 'rewrite' },
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/platform/ai/workflow-skills',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: {
          code: 'rewrite-guide',
          content: '---\nname: rewrite\ndescription: guide\n---\nbody',
        },
      },
    );
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/api/platform/ai/workflow-skills/rewrite-guide',
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        data: { content: 'new complete file', expectedRevision: 'hash-1' },
      },
    );
  });
});
