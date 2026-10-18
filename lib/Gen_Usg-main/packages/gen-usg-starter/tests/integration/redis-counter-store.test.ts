import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { startRedisContainer, type TestRedis } from "../support/redis-container.ts";

describe("RedisCounterStore against real Redis", () => {
  let redis: TestRedis;
  beforeAll(async () => { redis = await startRedisContainer(); }, 60_000);
  afterAll(async () => { await redis.stop(); });

  it("incrBy accumulates and get reads it back", async () => {
    await redis.counterStore.incrBy("genusg:t1:seats", 5);
    await redis.counterStore.incrBy("genusg:t1:seats", 3);
    expect(await redis.counterStore.get("genusg:t1:seats")).toBe(8);
  });

  it("setNX only succeeds once within the TTL window", async () => {
    const first = await redis.counterStore.setNX("genusg:dedup:evt-1", "1", 60);
    const second = await redis.counterStore.setNX("genusg:dedup:evt-1", "1", 60);
    expect(first).toBe(true);
    expect(second).toBe(false);
  });

  it("zadd/zrem/zsumScores stay exact across duplicate add/remove", async () => {
    const key = "genusg:resource:t1:storage_bytes";
    await redis.counterStore.zadd(key, 100, "file-a");
    await redis.counterStore.zadd(key, 200, "file-b");
    expect(await redis.counterStore.zsumScores(key)).toBe(300);
    await redis.counterStore.zrem(key, "file-a");
    expect(await redis.counterStore.zsumScores(key)).toBe(200);
  });

  it("setBatch seeds multiple keys with TTLs in one call", async () => {
    await redis.counterStore.setBatch([
      { key: "genusg:limit:t1:seats", value: "10", ttlSec: 300 },
      { key: "genusg:limit:t1:api_calls", value: "-1", ttlSec: 300 },
    ]);
    expect(await redis.counterStore.mget(["genusg:limit:t1:seats", "genusg:limit:t1:api_calls"])).toEqual([10, -1]);
  });
});
