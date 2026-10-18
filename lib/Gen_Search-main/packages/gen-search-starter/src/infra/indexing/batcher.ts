export interface BatcherOptions<T> {
  batchSize: number;
  flushIntervalMs: number;
  flush: (items: T[]) => Promise<void>;
}

// Size-or-time triggered batching (INDEXER_BATCH_SIZE from the LLD). Each
// `add()` resolves only after the batch it landed in actually flushed — the
// caller (the indexer worker, acking a RabbitMQ message per item) needs that
// to know a message is safe to ack, not just buffered in memory.
export class Batcher<T> {
  private buffer: { item: T; resolve: () => void; reject: (err: unknown) => void }[] = [];
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor(private readonly options: BatcherOptions<T>) {}

  add(item: T): Promise<void> {
    return new Promise((resolve, reject) => {
      this.buffer.push({ item, resolve, reject });
      if (this.buffer.length >= this.options.batchSize) {
        void this.flushNow();
      } else if (!this.timer) {
        this.timer = setTimeout(() => void this.flushNow(), this.options.flushIntervalMs);
      }
    });
  }

  private async flushNow(): Promise<void> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
    const batch = this.buffer.splice(0, this.buffer.length);
    if (batch.length === 0) return;

    try {
      await this.options.flush(batch.map((entry) => entry.item));
      batch.forEach((entry) => entry.resolve());
    } catch (err) {
      batch.forEach((entry) => entry.reject(err));
    }
  }

  /** Flushes any pending items immediately. Call on shutdown so nothing is lost sitting in the buffer. */
  async close(): Promise<void> {
    await this.flushNow();
  }
}
