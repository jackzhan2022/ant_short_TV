import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AiExecutionTaskService,
  isTerminalExecution,
  type AiExecutionClient,
} from './task';

describe('AiExecutionTaskService', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('polls canonical tasks until they reach a terminal state', async () => {
    const client = clientWithDetails([
      { id: 11, status: 'PENDING' },
      { id: 11, status: 'RUNNING', progress: 60 },
      { id: 11, status: 'SUCCEEDED', progress: 100 },
    ]);
    const sleep = vi.fn().mockResolvedValue(undefined);
    const service = new AiExecutionTaskService(client, localStorage, sleep);
    const updates: API.AiExecutionResponse[] = [];

    const result = await service.poll(7, 11, (task) => updates.push(task));

    expect(result.status).toBe('SUCCEEDED');
    expect(updates.map((task) => task.status)).toEqual([
      'PENDING',
      'RUNNING',
      'SUCCEEDED',
    ]);
    expect(sleep).toHaveBeenCalledTimes(2);
    expect(service.recoverable(7)).toEqual([]);
  });

  it('delegates retry and cancel and retains only non-terminal tasks for recovery', async () => {
    const client = clientWithDetails([]);
    client.retry.mockResolvedValue({ id: 21, status: 'PENDING' });
    client.cancel.mockResolvedValue({ id: 22, status: 'CANCELED' });
    const service = new AiExecutionTaskService(client, localStorage);

    await service.retry(7, 21);
    await service.cancel(7, 22);

    expect(client.retry).toHaveBeenCalledWith(7, 21);
    expect(client.cancel).toHaveBeenCalledWith(7, 22);
    expect(service.recoverable(7)).toEqual([21]);
  });

  it('recovers pending tasks after a page reload and removes stale terminal entries', async () => {
    const firstClient = clientWithDetails([]);
    firstClient.retry.mockResolvedValue({ id: 31, status: 'PENDING' });
    await new AiExecutionTaskService(firstClient, localStorage).retry(7, 31);

    const restoredClient = clientWithDetails([
      { id: 31, status: 'SUCCEEDED', progress: 100 },
    ]);
    const restored = new AiExecutionTaskService(restoredClient, localStorage);

    expect(restored.recoverable(7)).toEqual([31]);
    expect(await restored.recover(7)).toEqual([
      { id: 31, status: 'SUCCEEDED', progress: 100 },
    ]);
    expect(restored.recoverable(7)).toEqual([]);
  });
});

describe('isTerminalExecution', () => {
  it.each(['SUCCEEDED', 'FAILED', 'CANCELED', 'TIMED_OUT'])(
    'treats %s as terminal',
    (status) => {
      expect(isTerminalExecution({ status })).toBe(true);
    },
  );

  it('does not expose provider-specific statuses', () => {
    expect(isTerminalExecution({ status: 'RUNNING' })).toBe(false);
  });
});

function clientWithDetails(details: API.AiExecutionResponse[]) {
  const client: {
    detail: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
    retry: ReturnType<typeof vi.fn>;
    regenerate: ReturnType<typeof vi.fn>;
  } = {
    detail: vi.fn(),
    cancel: vi.fn(),
    retry: vi.fn(),
    regenerate: vi.fn(),
  };
  for (const detail of details) {
    client.detail.mockResolvedValueOnce(detail);
  }
  return client as unknown as AiExecutionClient & typeof client;
}
