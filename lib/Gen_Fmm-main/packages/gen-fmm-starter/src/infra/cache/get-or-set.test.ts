import { describe, it, expect, vi } from "vitest";
import { getOrSet } from "./get-or-set.ts";
import type { ICacheStore } from "../../domain/ports/cache-store.port.ts";

function fakeCache(): ICacheStore & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    async get(key) { return data.get(key) ?? null; },
    async set(key, value) { data.set(key, value); },
    async del(key) { data.delete(key); },
    async setNx(key, value) {
      if (data.has(key)) return false;
      data.set(key, value);
      return true;
    },
  };
}

describe("getOrSet", () => {
  it("returns the cached value on a hit without calling the fetcher", async () => {
    const cache = fakeCache();
    cache.data.set("k", JSON.stringify({ v: 1 }));
    const fetcher = vi.fn(async () => ({ v: 2 }));
    const result = await getOrSet(cache, "k", fetcher, 60);
    expect(result).toEqual({ v: 1 });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("calls the fetcher and populates the cache on a miss", async () => {
    const cache = fakeCache();
    const fetcher = vi.fn(async () => ({ v: 42 }));
    const result = await getOrSet(cache, "k", fetcher, 60);
    expect(result).toEqual({ v: 42 });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(JSON.parse(cache.data.get("k")!)).toEqual({ v: 42 });
  });

  it("releases the lock after fetching so a subsequent call can acquire it again", async () => {
    const cache = fakeCache();
    await getOrSet(cache, "k", async () => ({ v: 1 }), 60);
    expect(cache.data.has("k:_lock")).toBe(false);
  });

  it("a concurrent miss that loses the lock race waits and reads the winner's value", async () => {
    const cache = fakeCache();
    // Simulate: another process already holds the lock and will populate
    // the key shortly.
    await cache.setNx("k:_lock", "1", 5);
    setTimeout(() => { cache.data.set("k", JSON.stringify({ v: 99 })); cache.data.delete("k:_lock"); }, 20);
    const fetcher = vi.fn(async () => ({ v: -1 }));
    const result = await getOrSet(cache, "k", fetcher, 60, 5);
    expect(result).toEqual({ v: 99 });
    expect(fetcher).not.toHaveBeenCalled();
  }, 10_000);
});
