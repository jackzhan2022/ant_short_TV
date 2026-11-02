import type { AiVideoTask } from './service';
import { pollingDelay } from '@/utils/polling';

/** Follow the logical task: its provider execution may change during avatar preparation. */
export async function pollVideoTask(
  taskId: number,
  read: (taskId: number, signal?: AbortSignal) => Promise<AiVideoTask>,
  onUpdate: (task: AiVideoTask) => void,
  signal?: AbortSignal,
  sleep: (signal?: AbortSignal) => Promise<void> = (signal) => pollingDelay(1500, signal),
): Promise<AiVideoTask> {
  while (true) {
    signal?.throwIfAborted();
    const task = await read(taskId, signal);
    signal?.throwIfAborted();
    onUpdate(task);
    if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(task.status)) return task;
    await sleep(signal);
  }
}
