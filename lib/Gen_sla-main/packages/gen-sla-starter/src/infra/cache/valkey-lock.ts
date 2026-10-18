import { Redis } from "ioredis";
import type { IDistributedLock } from "../../domain/ports/distributed-lock.port.ts";

export class ValkeyLock implements IDistributedLock {
  private readonly client: Redis;

  constructor(redisUrl: string) {
    // lazyConnect: constructed unconditionally by createGenSla() whenever
    // modules.worker is enabled — an eager connection would open a real socket
    // for callers who override the worker's lock with their own implementation.
    this.client = new Redis(redisUrl, { lazyConnect: true });
  }

  async acquire(key: string, ttlSeconds: number): Promise<boolean> {
    const result = await this.client.set(key, "1", "EX", ttlSeconds, "NX");
    return result === "OK";
  }

  async disconnect(): Promise<void> {
    await this.client.quit();
  }
}
