import { expect, it, vi } from 'vitest';
import type { AiVideoTask } from './service';
import { pollVideoTask } from './video-task-polling';

it('follows one business task through avatar preparation and the replacement execution', async () => {
  const read = vi
    .fn()
    .mockResolvedValueOnce({
      id: 62,
      status: 'GENERATING',
      executionPhase: 'AVATAR_PREPARING',
    })
    .mockResolvedValueOnce({
      id: 62,
      status: 'GENERATING',
      executionId: 999,
      executionPhase: 'VIDEO_QUERY',
    })
    .mockResolvedValueOnce({
      id: 62,
      status: 'SUCCEEDED',
      results: [{ id: 8 }],
    });
  const update = vi.fn();
  const result = await pollVideoTask(
    62,
    read,
    update,
    undefined,
    async () => {},
  );
  expect(read.mock.calls.map(([id]) => id)).toEqual([62, 62, 62]);
  expect(update.mock.calls.map(([task]) => task.executionPhase)).toEqual([
    'AVATAR_PREPARING',
    'VIDEO_QUERY',
    undefined,
  ]);
  expect(result.status).toBe('SUCCEEDED');
});
it('stops after final failure and never reports an obsolete response', async () => {
  const controller = new AbortController();
  const update = vi.fn();
  const failure = { id: 62, status: 'FAILED' } as AiVideoTask;
  expect(await pollVideoTask(62, async () => failure, update)).toBe(failure);
  update.mockClear();
  const read = async () => {
    controller.abort();
    return failure;
  };
  await expect(
    pollVideoTask(62, read, update, controller.signal),
  ).rejects.toMatchObject({ name: 'AbortError' });
  expect(update).not.toHaveBeenCalled();
});
