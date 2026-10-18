import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { startOutboxDispatcher } from "./outbox-dispatcher.ts";
import type { IEventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import type { IDistributedLock } from "../../domain/ports/distributed-lock.port.ts";

function baseEvent(overrides: Record<string, unknown> = {}) {
  return {
    id: "evt-1",
    tenantId: "tenant-1",
    eventType: "sla.instance.created",
    exchange: "gen-sla.events",
    routingKey: "sla.instance.created",
    payload: { foo: "bar" },
    status: "PENDING",
    retryCount: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function fakePrisma(events: ReturnType<typeof baseEvent>[]) {
  const findMany = vi.fn(async () => events);
  const update = vi.fn(async () => undefined);
  return { slaOutboxEvent: { findMany, update } } as any;
}

function fakePublisher(overrides: Partial<IEventPublisher> = {}): IEventPublisher {
  return {
    publish: vi.fn(async (_exchange: string, _routingKey: string, _envelope: EventEnvelope) => undefined),
    ...overrides,
  };
}

function fakeLock(acquired = true): IDistributedLock {
  return { acquire: vi.fn(async () => acquired) };
}

const config = { pollIntervalMs: 5000, batchSize: 50, maxRetries: 3 };

describe("startOutboxDispatcher", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("marks an event FAILED without publishing when retryCount is already >= maxRetries", async () => {
    const prisma = fakePrisma([baseEvent({ retryCount: 3 })]);
    const publisher = fakePublisher();
    const dispatcher = startOutboxDispatcher(prisma, publisher, fakeLock(), config);

    await vi.advanceTimersByTimeAsync(config.pollIntervalMs);

    expect(publisher.publish).not.toHaveBeenCalled();
    expect(prisma.slaOutboxEvent.update).toHaveBeenCalledWith({
      where: { id: "evt-1" },
      data: { status: "FAILED", updatedAt: expect.any(Date) },
    });
    dispatcher.close();
  });

  it("marks a successfully published event PUBLISHED", async () => {
    const prisma = fakePrisma([baseEvent()]);
    const publisher = fakePublisher();
    const dispatcher = startOutboxDispatcher(prisma, publisher, fakeLock(), config);

    await vi.advanceTimersByTimeAsync(config.pollIntervalMs);

    expect(publisher.publish).toHaveBeenCalledWith("gen-sla.events", "sla.instance.created", { foo: "bar" });
    expect(prisma.slaOutboxEvent.update).toHaveBeenCalledWith({
      where: { id: "evt-1" },
      data: { status: "PUBLISHED", updatedAt: expect.any(Date) },
    });
    dispatcher.close();
  });

  it("increments retryCount and stays PENDING when a failed publish's next attempt is still under maxRetries", async () => {
    const prisma = fakePrisma([baseEvent({ retryCount: 0 })]);
    const publisher = fakePublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
    const dispatcher = startOutboxDispatcher(prisma, publisher, fakeLock(), config);

    await vi.advanceTimersByTimeAsync(config.pollIntervalMs);

    expect(prisma.slaOutboxEvent.update).toHaveBeenCalledWith({
      where: { id: "evt-1" },
      data: { retryCount: 1, status: "PENDING", updatedAt: expect.any(Date) },
    });
    dispatcher.close();
  });

  it("sets status FAILED when a failed publish's next attempt reaches maxRetries", async () => {
    const prisma = fakePrisma([baseEvent({ retryCount: 2 })]);
    const publisher = fakePublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
    const dispatcher = startOutboxDispatcher(prisma, publisher, fakeLock(), config);

    await vi.advanceTimersByTimeAsync(config.pollIntervalMs);

    expect(prisma.slaOutboxEvent.update).toHaveBeenCalledWith({
      where: { id: "evt-1" },
      data: { retryCount: 3, status: "FAILED", updatedAt: expect.any(Date) },
    });
    dispatcher.close();
  });

  it("skips the poll tick entirely when the lock is not acquired", async () => {
    const prisma = fakePrisma([baseEvent()]);
    const publisher = fakePublisher();
    const dispatcher = startOutboxDispatcher(prisma, publisher, fakeLock(false), config);

    await vi.advanceTimersByTimeAsync(config.pollIntervalMs);

    expect(prisma.slaOutboxEvent.findMany).not.toHaveBeenCalled();
    expect(publisher.publish).not.toHaveBeenCalled();
    dispatcher.close();
  });
});
