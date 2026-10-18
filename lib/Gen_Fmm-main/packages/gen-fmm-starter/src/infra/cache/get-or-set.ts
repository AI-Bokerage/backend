import type { ICacheStore } from "../../domain/ports/cache-store.port.ts";

function withJitter(ttlSeconds: number): number {
  const jitter = ttlSeconds * 0.1;
  return Math.round(ttlSeconds + (Math.random() * 2 - 1) * jitter);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getOrSet<T>(
  cache: ICacheStore,
  key: string,
  fetcher: () => Promise<T>,
  ttlSeconds: number,
  lockTtlSeconds = 5,
): Promise<T> {
  const cached = await cache.get(key);
  if (cached !== null) return JSON.parse(cached) as T;

  const lockKey = `${key}:_lock`;
  const acquired = await cache.setNx(lockKey, "1", lockTtlSeconds);

  if (acquired) {
    try {
      const value = await fetcher();
      if (value != null) await cache.set(key, JSON.stringify(value), withJitter(ttlSeconds));
      return value;
    } finally {
      await cache.del(lockKey);
    }
  }

  // Lost the race — poll every 50ms until the lock's deadline, then re-read;
  // if the lock is released without populating the value, bail early and
  // fetch directly rather than waiting out the full deadline.
  const deadlineMs = Date.now() + lockTtlSeconds * 1000;
  while (Date.now() < deadlineMs) {
    await sleep(50);
    const retried = await cache.get(key);
    if (retried !== null) return JSON.parse(retried) as T;
    const stillLocked = await cache.get(lockKey);
    if (stillLocked === null) break;
  }
  return fetcher();
}
