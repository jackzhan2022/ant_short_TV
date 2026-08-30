import { request } from '@umijs/max';
import type { ApiResponse } from './platform-service';

export type WorkflowAgentStatus = 'ENABLED' | 'DISABLED';

export type WorkflowAgent = {
  id: number;
  code: string;
  name: string;
  description?: string;
  systemPrompt: string;
  modelId: number;
  temperature: number;
  maxTokens: number;
  maxSteps: number;
  status: WorkflowAgentStatus;
  revision: number;
  createdBy?: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
  skillCodes: string[];
  toolCodes: string[];
};

export type WorkflowAgentPayload = Omit<
  WorkflowAgent,
  'id' | 'revision' | 'createdBy' | 'updatedBy' | 'createdAt' | 'updatedAt'
>;

export type WorkflowSkill = {
  code: string;
  name: string;
  description: string;
  content: string;
  revision: string;
  referencingAgentCodes: string[];
};

export type WorkflowTool = {
  code: string;
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  riskLevel: 'READ_ONLY' | 'WRITE';
  failurePolicy: 'TERMINAL' | 'RETURN_TO_MODEL';
};

export type WorkflowRunInput = {
  input: string;
  projectId?: number;
  episodeId?: number;
  taskId?: number;
};

export type WorkflowRunResult = { runId: number; output: string };

export type WorkflowRunStep = {
  stepNo: number;
  stepType: 'MODEL' | 'TOOL';
  status: string;
  aiCallLogId?: number;
  toolCode?: string;
  inputJson?: string;
  outputJson?: string;
  errorCode?: string;
  errorMessage?: string;
  startedAt: string;
  finishedAt?: string;
};

export type WorkflowRunSummary = {
  id: number;
  agentCode: string;
  runType: 'FORMAL' | 'TEST';
  status: string;
  projectId?: number;
  episodeId?: number;
  finalOutput?: string;
  errorCode?: string;
  errorMessage?: string;
  startedAt: string;
  finishedAt?: string;
};

export type WorkflowRunDetail = WorkflowRunSummary & {
  agentId?: number;
  tenantId?: number;
  userId: number;
  taskId?: number;
  modelId: number;
  temperature: number;
  maxTokens: number;
  maxSteps: number;
  promptSnapshot: string;
  skillSnapshots: Array<{
    code: string;
    name: string;
    revision: string;
    content: string;
  }>;
  toolCodes: string[];
  steps: WorkflowRunStep[];
};

const jsonOptions = (method: string, data?: unknown) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  ...(data === undefined ? {} : { data }),
});

export const queryWorkflowAgents = async (query?: string) =>
  request<ApiResponse<WorkflowAgent[]>>('/api/platform/ai/workflow-agents', {
    params: { query },
  });

export const queryWorkflowAgent = async (code: string) =>
  request<ApiResponse<WorkflowAgent>>(
    `/api/platform/ai/workflow-agents/${code}`,
  );

export const createWorkflowAgent = async (data: WorkflowAgentPayload) =>
  request<ApiResponse<WorkflowAgent>>(
    '/api/platform/ai/workflow-agents',
    jsonOptions('POST', data),
  );

export const updateWorkflowAgent = async (
  code: string,
  data: WorkflowAgentPayload & { expectedRevision: number },
) =>
  request<ApiResponse<WorkflowAgent>>(
    `/api/platform/ai/workflow-agents/${code}`,
    jsonOptions('PUT', data),
  );

export const copyWorkflowAgent = async (code: string, targetCode: string) =>
  request<ApiResponse<WorkflowAgent>>(
    `/api/platform/ai/workflow-agents/${code}/copy`,
    jsonOptions('POST', { targetCode }),
  );

export const setWorkflowAgentEnabled = async (code: string, enabled: boolean) =>
  request<ApiResponse<WorkflowAgent>>(
    `/api/platform/ai/workflow-agents/${code}/${enabled ? 'enable' : 'disable'}`,
    { method: 'POST' },
  );

export const deleteWorkflowAgent = async (code: string) =>
  request<ApiResponse<void>>(`/api/platform/ai/workflow-agents/${code}`, {
    method: 'DELETE',
  });

export const queryWorkflowSkills = async (query?: string) =>
  request<ApiResponse<WorkflowSkill[]>>('/api/platform/ai/workflow-skills', {
    params: { query },
  });

export const queryWorkflowSkill = async (code: string) =>
  request<ApiResponse<WorkflowSkill>>(
    `/api/platform/ai/workflow-skills/${code}`,
  );

export const createWorkflowSkill = async (code: string, content: string) =>
  request<ApiResponse<WorkflowSkill>>(
    '/api/platform/ai/workflow-skills',
    jsonOptions('POST', { code, content }),
  );

export const updateWorkflowSkill = async (
  code: string,
  content: string,
  expectedRevision: string,
) =>
  request<ApiResponse<WorkflowSkill>>(
    `/api/platform/ai/workflow-skills/${code}`,
    jsonOptions('PUT', { content, expectedRevision }),
  );

export const copyWorkflowSkill = async (code: string, targetCode: string) =>
  request<ApiResponse<WorkflowSkill>>(
    `/api/platform/ai/workflow-skills/${code}/copy`,
    jsonOptions('POST', { targetCode }),
  );

export const deleteWorkflowSkill = async (code: string) =>
  request<ApiResponse<void>>(`/api/platform/ai/workflow-skills/${code}`, {
    method: 'DELETE',
  });

export const queryWorkflowTools = async () =>
  request<ApiResponse<WorkflowTool[]>>('/api/platform/ai/agent-tools');

export const runFormalWorkflowAgent = async (
  agentCode: string,
  scope: WorkflowRunInput,
) =>
  request<ApiResponse<WorkflowRunResult>>(
    '/api/platform/ai/workflow-agent-runs',
    jsonOptions('POST', { agentCode, ...scope }),
  );

export const runTemporaryWorkflowAgent = async (
  data: WorkflowAgentPayload & WorkflowRunInput,
) =>
  request<ApiResponse<WorkflowRunResult>>(
    '/api/platform/ai/workflow-agent-runs/test',
    jsonOptions('POST', data),
  );

export const queryWorkflowAgentRuns = async (agentCode?: string, limit = 50) =>
  request<ApiResponse<WorkflowRunSummary[]>>(
    '/api/platform/ai/workflow-agent-runs',
    {
      params: { agentCode, limit },
    },
  );

export const queryWorkflowAgentRun = async (runId: number) =>
  request<ApiResponse<WorkflowRunDetail>>(
    `/api/platform/ai/workflow-agent-runs/${runId}`,
  );
