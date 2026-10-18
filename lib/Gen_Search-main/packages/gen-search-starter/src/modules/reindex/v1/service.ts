import { ReindexAlreadyRunningError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { SearchBackendName } from "../../../config/constants.ts";
import type { SearchEntitiesConfig } from "../../../domain/entities-config.ts";
import type { IDistributedLock } from "../../../domain/ports/distributed-lock.port.ts";
import type { ISearchIndexWriter, IndexDocument } from "../../../domain/ports/search-index.port.ts";

// Host-supplied: the library owns no source-of-truth data (see
// docs/source-audit-notes.md — search-svc's own scope is a read-optimized
// copy, never the source), so "reindex" means draining whatever the host
// streams back from its own services, not regenerating anything internally.
export type ReindexSource = (tenantId: string, entityTypes?: string[]) => AsyncIterable<IndexDocument>;

export interface ReindexServiceDeps {
  writers: Partial<Record<SearchBackendName, ISearchIndexWriter>>;
  entities: SearchEntitiesConfig;
  lock: IDistributedLock;
  reindexSource: ReindexSource;
  batchSize: number;
  lockTtlS: number;
}

export class ReindexService {
  constructor(private readonly deps: ReindexServiceDeps) {}

  async reindex(tenantId: string, entityTypes?: string[]): Promise<{ indexed: number }> {
    const lockKey = `gen-search:lock:reindex:${tenantId}`;
    // TTL-based lock, no explicit release (IDistributedLock has no release() —
    // same as Gen_SLA's port). A reindex that finishes early still blocks a
    // second one until lockTtlS elapses; keep lockTtlS generous but be aware
    // this isn't "released the moment we're done."
    const acquired = await this.deps.lock.acquire(lockKey, this.deps.lockTtlS);
    if (!acquired) throw new ReindexAlreadyRunningError(tenantId);

    const typesToClear = entityTypes ?? Object.keys(this.deps.entities);
    for (const type of typesToClear) {
      const cfg = this.deps.entities[type];
      if (!cfg) continue;
      await this.deps.writers[cfg.backend]?.removeByEntityType(tenantId, type);
    }

    let indexed = 0;
    const pending: Partial<Record<SearchBackendName, IndexDocument[]>> = {};

    const flush = async (backend: SearchBackendName): Promise<void> => {
      const docs = pending[backend];
      if (!docs || docs.length === 0) return;
      await this.deps.writers[backend]?.bulkUpsert(docs);
      pending[backend] = [];
    };

    for await (const doc of this.deps.reindexSource(tenantId, entityTypes)) {
      const cfg = this.deps.entities[doc.entityType];
      if (!cfg) {
        logger.warn({ entityType: doc.entityType, tenantId }, "[reindex] reindexSource yielded an unconfigured entity type — skipping");
        continue;
      }
      const list = pending[cfg.backend] ?? [];
      list.push(doc);
      pending[cfg.backend] = list;
      indexed++;
      if (list.length >= this.deps.batchSize) await flush(cfg.backend);
    }

    for (const backend of Object.keys(pending) as SearchBackendName[]) {
      await flush(backend);
    }

    logger.info({ tenantId, indexed }, "[reindex] completed");
    return { indexed };
  }
}
