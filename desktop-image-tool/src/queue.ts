import type { QueueState } from './shared';

/** A single scheduler is shared by free creation and all project batches. */
export class ImageQueue {
  private limit: number;
  private paused = true;
  private started: boolean;
  private waiting: string[];
  private running = new Set<string>();

  constructor(
    private work: (id: string) => Promise<void>,
    private changed: () => void,
    options: { limit?: number; waiting?: string[] } = {},
  ) {
    this.limit = this.validLimit(options.limit ?? 5);
    this.waiting = [...new Set(options.waiting ?? [])];
    this.started = this.waiting.length > 0;
  }

  state(): QueueState {
    return {
      limit: this.limit,
      paused: this.paused,
      waiting: [...this.waiting],
      running: [...this.running],
    };
  }

  add(ids: string[]) {
    for (const id of ids) {
      if (!this.running.has(id) && !this.waiting.includes(id))
        this.waiting.push(id);
    }
    // Adding new work does not implicitly resume an existing paused queue.
    if (!this.started && this.waiting.length) {
      this.paused = false;
      this.started = true;
    }
    this.pump();
  }

  pause() {
    this.started = true;
    this.paused = true;
    this.changed();
  }

  resume() {
    this.started = true;
    this.paused = false;
    this.pump();
  }

  setLimit(limit: number) {
    this.limit = this.validLimit(limit);
    this.pump();
  }

  cancel(id: string): boolean {
    const index = this.waiting.indexOf(id);
    if (index < 0) return false;
    this.waiting.splice(index, 1);
    this.changed();
    return true;
  }

  private validLimit(value: number): number {
    if (!Number.isInteger(value) || value < 1 || value > 5)
      throw new Error('图片任务并发数必须为 1～5。');
    return value;
  }

  private pump() {
    while (
      !this.paused &&
      this.running.size < this.limit &&
      this.waiting.length
    ) {
      const id = this.waiting.shift() as string;
      this.running.add(id);
      // work executes synchronously until its first await, so a slot is reserved
      // before another caller can enqueue a second batch.
      let result: Promise<void>;
      try {
        result = this.work(id);
      } catch {
        result = Promise.resolve();
      }
      void result
        .catch(() => {})
        .finally(() => {
          this.running.delete(id);
          this.pump();
        });
    }
    this.changed();
  }
}
