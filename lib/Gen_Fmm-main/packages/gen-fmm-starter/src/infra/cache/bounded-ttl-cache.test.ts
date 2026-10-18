import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { BoundedTtlCache } from "./bounded-ttl-cache.ts";

describe("BoundedTtlCache", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("returns undefined for a missing key", () => {
    const cache = new BoundedTtlCache<number>(10);
    expect(cache.get("missing")).toBeUndefined();
  });

  it("stores and retrieves a value within TTL", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 42, 1000);
    expect(cache.get("a")).toBe(42);
  });

  it("expires a value after its TTL", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 42, 1000);
    vi.advanceTimersByTime(1001);
    expect(cache.get("a")).toBeUndefined();
  });

  it("evicts the oldest entry when maxEntries is exceeded", () => {
    const cache = new BoundedTtlCache<number>(2);
    cache.set("a", 1, 10_000);
    cache.set("b", 2, 10_000);
    cache.set("c", 3, 10_000);
    expect(cache.get("a")).toBeUndefined();
    expect(cache.get("b")).toBe(2);
    expect(cache.get("c")).toBe(3);
    expect(cache.size()).toBe(2);
  });

  it("delete removes a single key", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 1, 10_000);
    cache.delete("a");
    expect(cache.get("a")).toBeUndefined();
  });

  it("deleteByPrefix removes only matching keys", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("fmm:flags:tenant-1", 1, 10_000);
    cache.set("fmm:flags:tenant-2", 2, 10_000);
    cache.set("fmm:check:tenant-1:x", 3, 10_000);
    cache.deleteByPrefix("fmm:flags:tenant-1");
    expect(cache.get("fmm:flags:tenant-1")).toBeUndefined();
    expect(cache.get("fmm:flags:tenant-2")).toBe(2);
    expect(cache.get("fmm:check:tenant-1:x")).toBe(3);
  });
});
