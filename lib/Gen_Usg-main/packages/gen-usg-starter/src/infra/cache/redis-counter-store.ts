// ponytail: named import (not brief's `import Redis from "ioredis"`) — the default
// import resolves to a namespace under this project's module:NodeNext + ioredis's
// CJS/ESM-mismatched .d.ts, breaking `tsc --noEmit` (TS2709/TS2351). Same class, same
// runtime behavior; only the import form changes.
import { Redis } from "ioredis";
import type { ICounterStore, SetBatchEntry } from "../../domain/ports/counter-store.port.ts";

export class RedisCounterStore implements ICounterStore {
  private readonly redis: Redis;

  constructor(redisUrl: string) {
    this.redis = new Redis(redisUrl);
  }

  async incrBy(key: string, delta: number): Promise<number> {
    return this.redis.incrby(key, delta);
  }

  async get(key: string): Promise<number | null> {
    const value = await this.redis.get(key);
    return value === null ? null : Number(value);
  }

  async mget(keys: string[]): Promise<(number | null)[]> {
    const values = await this.redis.mget(...keys);
    return values.map((v) => (v === null ? null : Number(v)));
  }

  async setNX(key: string, value: string, ttlSec: number): Promise<boolean> {
    const result = await this.redis.set(key, value, "EX", ttlSec, "NX");
    return result === "OK";
  }

  async del(key: string): Promise<void> {
    await this.redis.del(key);
  }

  async zadd(key: string, score: number, member: string): Promise<void> {
    await this.redis.zadd(key, score, member);
  }

  async zrem(key: string, member: string): Promise<void> {
    await this.redis.zrem(key, member);
  }

  async zsumScores(key: string): Promise<number> {
    const members = await this.redis.zrangebyscore(key, "-inf", "+inf", "WITHSCORES");
    let total = 0;
    for (let i = 1; i < members.length; i += 2) {
      total += Number(members[i]);
    }
    return total;
  }

  async setBatch(entries: SetBatchEntry[]): Promise<void> {
    const pipeline = this.redis.pipeline();
    for (const { key, value, ttlSec } of entries) {
      pipeline.set(key, value, "EX", ttlSec);
    }
    await pipeline.exec();
  }
}
