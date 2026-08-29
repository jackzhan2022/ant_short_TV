import {
  cancel1,
  detail3,
  regenerate as regenerateRequest,
  retry1,
} from '@/services/ant-design-pro/aiExecutionController';

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMED_OUT']);
const STORAGE_PREFIX = 'ai-execution:pending:';

export interface AiExecutionClient {
  detail(tenantId: number, executionId: number): Promise<API.AiExecutionResponse>;
  cancel(tenantId: number, executionId: number): Promise<API.AiExecutionResponse>;
  retry(tenantId: number, executionId: number): Promise<API.AiExecutionResponse>;
  regenerate(
    tenantId: number,
    executionId: number,
    request: API.AiExecutionRegenerateRequest,
  ): Promise<API.AiExecutionResponse>;
}

export type Sleep = (milliseconds: number) => Promise<void>;

export function isTerminalExecution(task: Pick<API.AiExecutionResponse, 'status'>) {
  return typeof task.status === 'string' && TERMINAL_STATUSES.has(task.status);
}

export class AiExecutionTaskService {
  constructor(
    private readonly client: AiExecutionClient = generatedClient,
    private readonly storage: Storage = localStorage,
    private readonly sleep: Sleep = (milliseconds) =>
      new Promise((resolve) => setTimeout(resolve, milliseconds)),
  ) {}

  async poll(
    tenantId: number,
    executionId: number,
    onUpdate?: (task: API.AiExecutionResponse) => void,
    intervalMs = 1500,
  ): Promise<API.AiExecutionResponse> {
    this.remember(tenantId, executionId);
    while (true) {
      const task = await this.client.detail(tenantId, executionId);
      this.track(tenantId, task);
      onUpdate?.(task);
      if (isTerminalExecution(task)) {
        return task;
      }
      await this.sleep(intervalMs);
    }
  }

  async cancel(tenantId: number, executionId: number) {
    const task = await this.client.cancel(tenantId, executionId);
    this.track(tenantId, task);
    return task;
  }

  async retry(tenantId: number, executionId: number) {
    const task = await this.client.retry(tenantId, executionId);
    this.track(tenantId, task);
    return task;
  }

  async regenerate(
    tenantId: number,
    executionId: number,
    request: API.AiExecutionRegenerateRequest,
  ) {
    const task = await this.client.regenerate(tenantId, executionId, request);
    this.track(tenantId, task);
    return task;
  }

  recoverable(tenantId: number): number[] {
    try {
      const value = JSON.parse(this.storage.getItem(this.storageKey(tenantId)) ?? '[]');
      return Array.isArray(value)
        ? value.filter((id): id is number => Number.isSafeInteger(id) && id > 0)
        : [];
    } catch {
      return [];
    }
  }

  async recover(tenantId: number) {
    const tasks = await Promise.all(
      this.recoverable(tenantId).map((executionId) =>
        this.client.detail(tenantId, executionId),
      ),
    );
    for (const task of tasks) {
      this.track(tenantId, task);
    }
    return tasks;
  }

  private track(tenantId: number, task: API.AiExecutionResponse) {
    if (task.id === undefined) {
      return;
    }
    if (isTerminalExecution(task)) {
      this.forget(tenantId, task.id);
    } else {
      this.remember(tenantId, task.id);
    }
  }

  private remember(tenantId: number, executionId: number) {
    const ids = new Set(this.recoverable(tenantId));
    ids.add(executionId);
    this.storage.setItem(this.storageKey(tenantId), JSON.stringify([...ids]));
  }

  private forget(tenantId: number, executionId: number) {
    const ids = this.recoverable(tenantId).filter((id) => id !== executionId);
    if (ids.length === 0) {
      this.storage.removeItem(this.storageKey(tenantId));
      return;
    }
    this.storage.setItem(this.storageKey(tenantId), JSON.stringify(ids));
  }

  private storageKey(tenantId: number) {
    return `${STORAGE_PREFIX}${tenantId}`;
  }
}

function responseData(response: API.ApiResponseAiExecutionResponse) {
  if (!response.data) {
    throw new Error(response.errorMessage || 'AI execution response has no task data.');
  }
  return response.data;
}

const generatedClient: AiExecutionClient = {
  detail: async (tenantId, executionId) =>
    responseData(await detail3({ tenantId, executionId })),
  cancel: async (tenantId, executionId) =>
    responseData(await cancel1({ tenantId, executionId })),
  retry: async (tenantId, executionId) =>
    responseData(await retry1({ tenantId, executionId })),
  regenerate: async (tenantId, executionId, request) =>
    responseData(await regenerateRequest({ tenantId, executionId }, request)),
};

export const aiExecutionTaskService = new AiExecutionTaskService();
