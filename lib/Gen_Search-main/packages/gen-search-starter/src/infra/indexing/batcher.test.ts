import { describe, it, expect, vi } from "vitest";
import { Batcher } from "./batcher.ts";

describe("Batcher", () => {
  it("flushes immediately once batchSize is reached", async () => {
    const flush = vi.fn().mockResolvedValue(undefined);
    const batcher = new Batcher<number>({ batchSize: 2, flushIntervalMs: 10_000, flush });

    const p1 = batcher.add(1);
    const p2 = batcher.add(2);
    await Promise.all([p1, p2]);

    expect(flush).toHaveBeenCalledTimes(1);
    expect(flush).toHaveBeenCalledWith([1, 2]);
  });

  it("flushes on the interval timer when batchSize isn't reached", async () => {
    vi.useFakeTimers();
    const flush = vi.fn().mockResolvedValue(undefined);
    const batcher = new Batcher<number>({ batchSize: 100, flushIntervalMs: 50, flush });

    const p = batcher.add(1);
    await vi.advanceTimersByTimeAsync(50);
    await p;

    expect(flush).toHaveBeenCalledTimes(1);
    expect(flush).toHaveBeenCalledWith([1]);
    vi.useRealTimers();
  });

  it("rejects every pending add() when flush throws", async () => {
    const flush = vi.fn().mockRejectedValue(new Error("db down"));
    const batcher = new Batcher<number>({ batchSize: 1, flushIntervalMs: 10_000, flush });

    await expect(batcher.add(1)).rejects.toThrow("db down");
  });

  it("close() flushes whatever is still buffered", async () => {
    const flush = vi.fn().mockResolvedValue(undefined);
    const batcher = new Batcher<number>({ batchSize: 100, flushIntervalMs: 10_000, flush });

    const p = batcher.add(1);
    await batcher.close();
    await p;

    expect(flush).toHaveBeenCalledWith([1]);
  });
});
