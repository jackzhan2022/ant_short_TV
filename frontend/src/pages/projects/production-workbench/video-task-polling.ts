import type { AiVideoTask } from './service';

/** Follow the logical task: its provider execution may change during avatar preparation. */
export async function pollVideoTask(
  taskId: number,
  read: (taskId: number, signal?: AbortSignal) => Promise<AiVideoTask>,
  onUpdate: (task: AiVideoTask) => void,
  signal?: AbortSignal,
  sleep: () => Promise<void> = () =>
    new Promise((resolve) => setTimeout(resolve, 1500)),
): Promise<AiVideoTask> {
  while (true) {
    signal?.throwIfAborted();
    const task = await read(taskId, signal);
    signal?.throwIfAborted();
    onUpdate(task);
    if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(task.status)) return task;
    await sleep();
  }
}
