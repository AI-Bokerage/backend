import { Redis } from "ioredis";
import type { IWebhookDedupStore } from "../../domain/ports/webhook-dedup.port.ts";

export class ValkeyDedupStore implements IWebhookDedupStore {
  private readonly client: Redis;

  constructor(redisUrl: string) {
    // lazyConnect: this adapter gets constructed by createGenReg() whenever
    // modules.payment is enabled, even in tests/processes that never touch a
    // webhook route — an eager connection attempt would open a real socket
    // (and risk an unhandled 'error' event) for callers who never use it.
    this.client = new Redis(redisUrl, { lazyConnect: true });
  }

  async tryAcquire(key: string, ttlSeconds: number): Promise<boolean> {
    const result = await this.client.set(`wh:payment:${key}`, "1", "EX", ttlSeconds, "NX");
    return result === "OK";
  }

  async release(key: string): Promise<void> {
    await this.client.del(`wh:payment:${key}`);
  }

  async disconnect(): Promise<void> {
    await this.client.quit();
  }
}
