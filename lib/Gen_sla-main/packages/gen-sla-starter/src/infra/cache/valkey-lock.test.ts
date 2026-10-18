import { describe, it, expect, vi } from "vitest";
import { ValkeyLock } from "./valkey-lock.ts";

let setResult: string | null = "OK";

vi.mock("ioredis", () => {
  return {
    Redis: vi.fn().mockImplementation(() => ({
      set: vi.fn(async (_key: string, _val: string, _ex: string, _ttl: number, _nx: string) => setResult),
    })),
  };
});

describe("ValkeyLock", () => {
  it("acquire returns true when the underlying SET NX succeeds", async () => {
    setResult = "OK";
    const lock = new ValkeyLock("redis://localhost:6379");
    await expect(lock.acquire("k", 60)).resolves.toBe(true);
  });

  it("acquire returns false when the underlying SET NX finds the key already set (resolves null)", async () => {
    setResult = null;
    const lock = new ValkeyLock("redis://localhost:6379");
    await expect(lock.acquire("k", 60)).resolves.toBe(false);
  });
});
