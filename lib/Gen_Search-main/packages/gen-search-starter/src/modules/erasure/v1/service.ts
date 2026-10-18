import { logger } from "../../../common/logger.ts";
import type { SearchBackendName } from "../../../config/constants.ts";
import type { ISearchIndexWriter } from "../../../domain/ports/search-index.port.ts";

// LLD §9 (GDPR / right to erasure): SEARCH-SVC.removeUserDocuments(user_id,
// tenant_id) runs as one step of a multi-step erasure saga, called
// synchronously by whichever service orchestrates that pipeline. Exposed
// both as this exported service (for in-process callers) and as an HTTP
// route (see router.ts) — unlike Gen_SLA's stance of keeping lifecycle
// methods library-only, the spec explicitly requires a callable endpoint
// here so a separate orchestrating service can confirm completion.
export class ErasureService {
  constructor(private readonly writers: Partial<Record<SearchBackendName, ISearchIndexWriter>>) {}

  async removeUserDocuments(tenantId: string, ownerId: string): Promise<{ removed: number }> {
    const counts = await Promise.all(Object.values(this.writers).map((writer) => writer?.removeByOwner(tenantId, ownerId) ?? Promise.resolve(0)));
    const removed = counts.reduce((sum, count) => sum + count, 0);
    logger.info({ tenantId, ownerId, removed }, "[erasure] removed indexed documents for owner");
    return { removed };
  }
}
