import { describe, it, expect, afterAll } from "vitest";
import { RedisContainer, StartedRedisContainer } from "@testcontainers/redis";
import { ValkeyDedupStore } from "./valkey-dedup-store.ts";

describe("ValkeyDedupStore", () => {
  let container: StartedRedisContainer;
  let store: ValkeyDedupStore;

  afterAll(async () => {
    await store?.disconnect();
    await container?.stop();
  });

  it("tryAcquire returns true the first time and false on a repeat within the TTL", async () => {
    container = await new RedisContainer("valkey/valkey:8-alpine").start();
    store = new ValkeyDedupStore(container.getConnectionUrl());

    const first = await store.tryAcquire("evt-1", 60);
    const second = await store.tryAcquire("evt-1", 60);

    expect(first).toBe(true);
    expect(second).toBe(false);
  });

  it("release lets a subsequent tryAcquire for the same key succeed again", async () => {
    await store.tryAcquire("evt-2", 60);
    await store.release("evt-2");
    const reacquired = await store.tryAcquire("evt-2", 60);
    expect(reacquired).toBe(true);
  });

  it("different keys are independent", async () => {
    const a = await store.tryAcquire("evt-3", 60);
    const b = await store.tryAcquire("evt-4", 60);
    expect(a).toBe(true);
    expect(b).toBe(true);
  });
});
