import { logger } from "../common/logger.ts";
import { Batcher } from "../infra/indexing/batcher.ts";
import type { SearchBackendName } from "../config/constants.ts";
import type { SearchEntitiesConfig } from "../domain/entities-config.ts";
import type { IEventConsumer, IndexerEventEnvelope } from "../domain/ports/event-bus.port.ts";
import type { ISearchIndexWriter, IndexDocument } from "../domain/ports/search-index.port.ts";

export interface RemovalTarget {
  tenantId: string;
  entityType: string;
  entityId: string;
}

export interface IndexerWorkerDeps {
  bus: IEventConsumer;
  exchange: string;
  queue: string;
  routingKeys: string[];
  deadLetterExchange?: string;
  writers: Partial<Record<SearchBackendName, ISearchIndexWriter>>;
  entities: SearchEntitiesConfig;
  // Host-owned mapping from its own event vocabulary to an IndexDocument —
  // this library doesn't assume any business event shape (see
  // docs/source-audit-notes.md). Return null to ignore an event.
  resolveDocument: (envelope: IndexerEventEnvelope) => IndexDocument | null | Promise<IndexDocument | null>;
  // Optional: events that mean "delete", not "upsert" (e.g. `*.*.deleted`).
  // Checked before resolveDocument on every message.
  resolveRemoval?: (envelope: IndexerEventEnvelope) => RemovalTarget | null;
  batchSize: number;
  flushIntervalMs: number;
  prefetch?: number;
}

export interface IndexerWorker {
  close(): Promise<void>;
}

export async function startIndexerWorker(deps: IndexerWorkerDeps): Promise<IndexerWorker> {
  await deps.bus.connect();
  await deps.bus.assertExchange(deps.exchange);
  await deps.bus.assertQueue(deps.queue, deps.deadLetterExchange);
  for (const routingKey of deps.routingKeys) {
    await deps.bus.bindQueue(deps.queue, deps.exchange, routingKey);
  }

  const batchers: Partial<Record<SearchBackendName, Batcher<IndexDocument>>> = {};
  function getBatcher(backend: SearchBackendName): Batcher<IndexDocument> {
    let batcher = batchers[backend];
    if (!batcher) {
      const writer = deps.writers[backend];
      batcher = new Batcher<IndexDocument>({
        batchSize: deps.batchSize,
        flushIntervalMs: deps.flushIntervalMs,
        flush: (docs) => (writer ? writer.bulkUpsert(docs) : Promise.resolve()),
      });
      batchers[backend] = batcher;
    }
    return batcher;
  }

  await deps.bus.consume(
    deps.queue,
    async (envelope) => {
      const removal = deps.resolveRemoval?.(envelope);
      if (removal) {
        const cfg = deps.entities[removal.entityType];
        if (cfg) await deps.writers[cfg.backend]?.remove(removal.tenantId, removal.entityType, removal.entityId);
        return;
      }

      const doc = await deps.resolveDocument(envelope);
      if (!doc) return;

      const cfg = deps.entities[doc.entityType];
      if (!cfg) {
        logger.warn({ entityType: doc.entityType }, "[indexer] event resolved to an unconfigured entity type — skipping");
        return;
      }

      await getBatcher(cfg.backend).add(doc);
    },
    { prefetch: deps.prefetch },
  );

  logger.info({ queue: deps.queue, routingKeys: deps.routingKeys }, "[indexer] worker started");

  return {
    close: async () => {
      await Promise.all(Object.values(batchers).map((batcher) => batcher?.close()));
      await deps.bus.close();
    },
  };
}
