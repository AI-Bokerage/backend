import { RedisContainer, type StartedRedisContainer } from "@testcontainers/redis";
import { RedisCounterStore } from "../../src/infra/cache/redis-counter-store.ts";

export interface TestRedis {
  counterStore: RedisCounterStore;
  stop: () => Promise<void>;
}

export async function startRedisContainer(): Promise<TestRedis> {
  const container: StartedRedisContainer = await new RedisContainer("redis:7").start();
  const counterStore = new RedisCounterStore(container.getConnectionUrl());
  return {
    counterStore,
    stop: async () => { await container.stop(); },
  };
}
