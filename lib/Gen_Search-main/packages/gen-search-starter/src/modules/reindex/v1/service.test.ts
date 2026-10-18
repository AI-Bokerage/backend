import { describe, it, expect, vi } from "vitest";
import { ReindexService } from "./service.ts";
import { ReindexAlreadyRunningError } from "../../../common/errors.ts";
import type { ISearchIndexWriter } from "../../../domain/ports/search-index.port.ts";

function makeWriter(): ISearchIndexWriter {
  return {
    upsert: vi.fn(),
    bulkUpsert: vi.fn().mockResolvedValue(undefined),
    remove: vi.fn(),
    removeByOwner: vi.fn().mockResolvedValue(0),
    removeByEntityType: vi.fn().mockResolvedValue(0),
  };
}

async function* docs(items: { entityType: string; entityId: string; title: string }[]) {
  for (const doc of items) yield { tenantId: "t1", ...doc };
}

describe("ReindexService", () => {
  it("throws ReindexAlreadyRunningError when the lock is already held", async () => {
    const service = new ReindexService({
      writers: { pg: makeWriter() },
      entities: { lead: { backend: "pg" } },
      lock: { acquire: vi.fn().mockResolvedValue(false) },
      reindexSource: () => docs([]),
      batchSize: 100,
      lockTtlS: 3600,
    });

    await expect(service.reindex("t1")).rejects.toThrow(ReindexAlreadyRunningError);
  });

  it("clears existing docs for the reindexed entity types before rebuilding, and flushes in batches", async () => {
    const pgWriter = makeWriter();
    const service = new ReindexService({
      writers: { pg: pgWriter },
      entities: { lead: { backend: "pg" } },
      lock: { acquire: vi.fn().mockResolvedValue(true) },
      reindexSource: () => docs([{ entityType: "lead", entityId: "l1", title: "A" }, { entityType: "lead", entityId: "l2", title: "B" }]),
      batchSize: 1,
      lockTtlS: 3600,
    });

    const result = await service.reindex("t1");

    expect(pgWriter.removeByEntityType).toHaveBeenCalledWith("t1", "lead");
    expect(pgWriter.bulkUpsert).toHaveBeenCalledTimes(2); // batchSize=1 forces a flush per doc
    expect(result.indexed).toBe(2);
  });

  it("skips documents for entity types not in the config", async () => {
    const pgWriter = makeWriter();
    const service = new ReindexService({
      writers: { pg: pgWriter },
      entities: { lead: { backend: "pg" } },
      lock: { acquire: vi.fn().mockResolvedValue(true) },
      reindexSource: () => docs([{ entityType: "ghost", entityId: "g1", title: "X" }]),
      batchSize: 100,
      lockTtlS: 3600,
    });

    const result = await service.reindex("t1");
    expect(result.indexed).toBe(0);
    expect(pgWriter.bulkUpsert).not.toHaveBeenCalled();
  });
});
