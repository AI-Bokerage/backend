import { describe, it, expect, vi } from "vitest";
import { startIndexerWorker } from "./indexer.worker.ts";
import type { IEventConsumer, IndexerEventEnvelope } from "../domain/ports/event-bus.port.ts";

function makeBus() {
  let handler: ((envelope: IndexerEventEnvelope) => Promise<void>) | undefined;
  const bus: IEventConsumer = {
    connect: vi.fn().mockResolvedValue(undefined),
    assertExchange: vi.fn().mockResolvedValue(undefined),
    assertQueue: vi.fn().mockResolvedValue(undefined),
    bindQueue: vi.fn().mockResolvedValue(undefined),
    consume: vi.fn(async (_queue, h) => {
      handler = h;
    }),
    close: vi.fn().mockResolvedValue(undefined),
  };
  return { bus, emit: (envelope: IndexerEventEnvelope) => handler!(envelope) };
}

describe("startIndexerWorker", () => {
  it("routes a resolved document to the writer for its configured backend", async () => {
    const { bus, emit } = makeBus();
    const pgWriter = { upsert: vi.fn(), bulkUpsert: vi.fn().mockResolvedValue(undefined), remove: vi.fn(), removeByOwner: vi.fn(), removeByEntityType: vi.fn() };

    const worker = await startIndexerWorker({
      bus,
      exchange: "cpms.events",
      queue: "q.search.indexer",
      routingKeys: ["*.*.created"],
      writers: { pg: pgWriter },
      entities: { lead: { backend: "pg" } },
      resolveDocument: (envelope) => ({ tenantId: envelope.tenant_id, entityType: "lead", entityId: envelope.data.id as string, title: envelope.data.name as string }),
      batchSize: 1,
      flushIntervalMs: 10_000,
    });

    await emit({ event_type: "lead.created", occurred_at: new Date(0).toISOString(), tenant_id: "t1", data: { id: "l1", name: "Acme" } });

    expect(pgWriter.bulkUpsert).toHaveBeenCalledWith([{ tenantId: "t1", entityType: "lead", entityId: "l1", title: "Acme" }]);
    await worker.close();
  });

  it("calls remove() instead of indexing when resolveRemoval matches", async () => {
    const { bus, emit } = makeBus();
    const pgWriter = { upsert: vi.fn(), bulkUpsert: vi.fn(), remove: vi.fn().mockResolvedValue(undefined), removeByOwner: vi.fn(), removeByEntityType: vi.fn() };

    const worker = await startIndexerWorker({
      bus,
      exchange: "cpms.events",
      queue: "q.search.indexer",
      routingKeys: ["*.*.deleted"],
      writers: { pg: pgWriter },
      entities: { lead: { backend: "pg" } },
      resolveDocument: () => null,
      resolveRemoval: (envelope) => ({ tenantId: envelope.tenant_id, entityType: "lead", entityId: envelope.data.id as string }),
      batchSize: 100,
      flushIntervalMs: 10_000,
    });

    await emit({ event_type: "lead.deleted", occurred_at: new Date(0).toISOString(), tenant_id: "t1", data: { id: "l1" } });

    expect(pgWriter.remove).toHaveBeenCalledWith("t1", "lead", "l1");
    await worker.close();
  });

  it("ignores events resolveDocument maps to null", async () => {
    const { bus, emit } = makeBus();
    const pgWriter = { upsert: vi.fn(), bulkUpsert: vi.fn(), remove: vi.fn(), removeByOwner: vi.fn(), removeByEntityType: vi.fn() };

    const worker = await startIndexerWorker({
      bus,
      exchange: "cpms.events",
      queue: "q.search.indexer",
      routingKeys: ["*.*.created"],
      writers: { pg: pgWriter },
      entities: { lead: { backend: "pg" } },
      resolveDocument: () => null,
      batchSize: 1,
      flushIntervalMs: 10_000,
    });

    await emit({ event_type: "unrelated.thing", occurred_at: new Date(0).toISOString(), tenant_id: "t1", data: {} });

    expect(pgWriter.bulkUpsert).not.toHaveBeenCalled();
    await worker.close();
  });
});
