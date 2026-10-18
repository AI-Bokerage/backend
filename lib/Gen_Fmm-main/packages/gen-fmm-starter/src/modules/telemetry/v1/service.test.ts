import { describe, it, expect, vi } from "vitest";
import { TelemetryService } from "./service.ts";
import type { ITelemetryRepo } from "../../../domain/ports/telemetry.repository.port.ts";

function fakeRepo(overrides: Partial<ITelemetryRepo> = {}): ITelemetryRepo {
  return {
    insertMany: vi.fn(async (events) => events.length),
    query: vi.fn(async () => ({ events: [], total: 0 })),
    ...overrides,
  };
}

describe("TelemetryService", () => {
  it("record() buffers events without touching the repo", () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    expect(service.bufferSize()).toBe(1);
    expect(repo.insertMany).not.toHaveBeenCalled();
  });

  it("flush() drains the buffer and inserts via the repo", async () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    service.record("t1", "f2", false, "ROLLOUT", "pro");
    const flushed = await service.flush();
    expect(flushed).toBe(2);
    expect(service.bufferSize()).toBe(0);
    expect(repo.insertMany).toHaveBeenCalledTimes(1);
  });

  it("flush() on an empty buffer is a no-op", async () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    expect(await service.flush()).toBe(0);
    expect(repo.insertMany).not.toHaveBeenCalled();
  });

  it("drops the oldest event when the buffer overflows", () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 2);
    service.record("t1", "first", true, "FLAG_DEFAULT", null);
    service.record("t1", "second", true, "FLAG_DEFAULT", null);
    service.record("t1", "third", true, "FLAG_DEFAULT", null);
    expect(service.bufferSize()).toBe(2);
  });

  it("flush() swallows repo failures and returns 0 rather than throwing", async () => {
    const repo = fakeRepo({ insertMany: vi.fn(async () => { throw new Error("db down"); }) });
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    const flushed = await service.flush();
    expect(flushed).toBe(0);
  });
});
