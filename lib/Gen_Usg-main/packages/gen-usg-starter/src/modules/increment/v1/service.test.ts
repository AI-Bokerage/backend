import { describe, it, expect, vi, afterEach } from "vitest";
import { IncrementService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../../domain/ports/idempotency.repository.port.ts";
import { MeterNotRegisteredError, IncrementDeltaInvalidError, UsageEventOutOfWindowError, ResourceIdRequiredError } from "../../../common/errors.ts";

function makeCounterStore(overrides: Partial<ICounterStore> = {}): ICounterStore {
  return {
    incrBy: vi.fn().mockResolvedValue(1),
    get: vi.fn(),
    mget: vi.fn(),
    setNX: vi.fn().mockResolvedValue(true),
    del: vi.fn(),
    zadd: vi.fn(),
    zrem: vi.fn(),
    zsumScores: vi.fn().mockResolvedValue(0),
    setBatch: vi.fn(),
    ...overrides,
  };
}

function makeIdempotencyRepo(overrides: Partial<IIdempotencyRepo> = {}): IIdempotencyRepo {
  return { exists: vi.fn().mockResolvedValue(false), insert: vi.fn().mockResolvedValue(undefined), ...overrides };
}

describe("IncrementService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("throws MeterNotRegisteredError for an unregistered metric", async () => {
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "nope", delta: 1 })).rejects.toThrow(MeterNotRegisteredError);
  });

  it("throws IncrementDeltaInvalidError for a zero delta", async () => {
    registerMeter("seats", { unit: "seat" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "seats", delta: 0 })).rejects.toThrow(IncrementDeltaInvalidError);
  });

  it("throws UsageEventOutOfWindowError when occurredAt is too old", async () => {
    registerMeter("seats", { unit: "seat" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const tooOld = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000);
    await expect(service.increment({ tenantId: "t1", metric: "seats", delta: 1, occurredAt: tooOld })).rejects.toThrow(UsageEventOutOfWindowError);
  });

  it("dedup layer 1 (eventId) short-circuits as replayed", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ setNX: vi.fn().mockResolvedValueOnce(false) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result).toEqual({ newValue: 0, metric: "seats", replayed: true });
  });

  it("dedup layer 2 (idempotencyKey) short-circuits as replayed when reused across different eventIds", async () => {
    registerMeter("seats", { unit: "seat" });
    // Real setNX semantics: the same key can only be acquired once. eventId
    // differs between the two calls (layer 1 passes both times), but the
    // shared idempotencyKey collides on the second call — this is the only
    // case that exercises layer 2 as distinct from layer 1.
    const locked = new Set<string>();
    const counterStore = makeCounterStore({
      setNX: vi.fn().mockImplementation(async (key: string) => {
        if (locked.has(key)) return false;
        locked.add(key);
        return true;
      }),
    });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const first = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-a", idempotencyKey: "idem-shared" });
    expect(first.replayed).toBe(false);
    const second = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-b", idempotencyKey: "idem-shared" });
    expect(second.replayed).toBe(true);
  });

  it("dedup layer 3 (Postgres fallback) short-circuits as replayed", async () => {
    registerMeter("seats", { unit: "seat" });
    const idempotencyRepo = makeIdempotencyRepo({ exists: vi.fn().mockResolvedValue(true) });
    const service = new IncrementService(makeCounterStore(), idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result.replayed).toBe(true);
  });

  it("mode:'resource' meters require resourceId", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "storage_bytes", delta: 100 })).rejects.toThrow(ResourceIdRequiredError);
  });

  it("mode:'resource' meters zadd on positive delta and re-sum", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const counterStore = makeCounterStore({ zsumScores: vi.fn().mockResolvedValue(500) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "storage_bytes", delta: 500, resourceId: "file-1" });
    expect(counterStore.zadd).toHaveBeenCalledWith("genusg:resource:t1:storage_bytes", 500, "file-1");
    expect(result.newValue).toBe(500);
  });

  it("mode:'resource' meters zrem on negative delta", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const counterStore = makeCounterStore({ zsumScores: vi.fn().mockResolvedValue(0) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await service.increment({ tenantId: "t1", metric: "storage_bytes", delta: -500, resourceId: "file-1" });
    expect(counterStore.zrem).toHaveBeenCalledWith("genusg:resource:t1:storage_bytes", "file-1");
  });

  it("mode:'counter' clamps a negative result to 0", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ incrBy: vi.fn().mockResolvedValueOnce(-3).mockResolvedValueOnce(0) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: -3 });
    expect(counterStore.incrBy).toHaveBeenCalledWith("genusg:t1:seats", -3);
    expect(counterStore.incrBy).toHaveBeenCalledWith("genusg:t1:seats", 3);
    expect(result.newValue).toBe(0);
  });

  it("fire-and-forget idempotency insert failure never rejects the call", async () => {
    registerMeter("seats", { unit: "seat" });
    const idempotencyRepo = makeIdempotencyRepo({ insert: vi.fn().mockRejectedValue(new Error("db down")) });
    const service = new IncrementService(makeCounterStore(), idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result.replayed).toBe(false);
  });
});
