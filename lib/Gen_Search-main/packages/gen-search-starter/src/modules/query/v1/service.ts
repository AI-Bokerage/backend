import { SearchQueryTooBroadError, SearchInvalidQueryError, SearchTimeoutError, SearchIndexUnavailableError } from "../../../common/errors.ts";
import { mapResultToDto } from "./mapper.ts";
import type { SearchBackendName } from "../../../config/constants.ts";
import type { SearchEntitiesConfig } from "../../../domain/entities-config.ts";
import type { ISearchQueryEngine, SearchResultItem } from "../../../domain/ports/search-query.port.ts";
import type { IVisibilityFilter } from "../../../domain/ports/visibility-filter.port.ts";
import type { SearchQueryInput } from "./schema.ts";
import type { SearchResultDto } from "./types.ts";

const WILDCARD_ONLY = /^\*+$/;

export interface SearchQueryServiceDeps {
  backends: Partial<Record<SearchBackendName, ISearchQueryEngine>>;
  entities: SearchEntitiesConfig;
  visibilityFilter: IVisibilityFilter;
  timeoutMs: number;
}

// The shipped adapters (PgFtsAdapter, OpenSearchAdapter) cannot themselves
// produce a duplicate (entityType, entityId) — each entityType routes to
// exactly one backend, and both adapters key storage on that exact pair.
// This is defensive insurance for host-supplied custom ISearchQueryEngine
// implementations that might return the same document twice (e.g. it
// matched via more than one indexed field) — collapse to the higher-
// scoring copy. Deliberately NOT a title-based or cross-entity-type dedup:
// two different entities that happen to share a title are two real
// records, not a duplicate, so the key stays scoped to the same entity.
function dedupeByEntity(items: SearchResultItem[]): SearchResultItem[] {
  const byKey = new Map<string, SearchResultItem>();
  for (const item of items) {
    const key = `${item.entityType}:${item.entityId}`;
    const existing = byKey.get(key);
    if (!existing || item.score > existing.score) byKey.set(key, item);
  }
  return [...byKey.values()];
}

export class SearchQueryService {
  constructor(private readonly deps: SearchQueryServiceDeps) {}

  async search(tenantId: string, input: SearchQueryInput): Promise<SearchResultDto[]> {
    if (WILDCARD_ONLY.test(input.q)) {
      throw new SearchQueryTooBroadError();
    }

    const entityTypesToSearch = input.type ? [input.type] : Object.keys(this.deps.entities);
    if (input.type && !this.deps.entities[input.type]) {
      throw new SearchInvalidQueryError(`unknown entity type "${input.type}"`);
    }

    const byBackend = new Map<SearchBackendName, string[]>();
    for (const type of entityTypesToSearch) {
      const backend = this.deps.entities[type].backend;
      const list = byBackend.get(backend) ?? [];
      list.push(type);
      byBackend.set(backend, list);
    }

    // Each backend's own `limit` is reused as "how deep to fetch" — passing
    // offset + limit gives the merge step enough rows to skip past `offset`
    // after sorting, without adding an `offset` field to the query port.
    const fetchSize = input.offset + input.limit;
    const perBackendResults = await Promise.all([...byBackend.entries()].map(([backend, types]) => this.searchBackend(backend, types, tenantId, input, fetchSize)));

    const merged = dedupeByEntity(perBackendResults.flat());
    merged.sort((a, b) => b.score - a.score || `${a.entityType}:${a.entityId}`.localeCompare(`${b.entityType}:${b.entityId}`));

    const visible: SearchResultItem[] = [];
    let skipped = 0;
    for (const result of merged) {
      if (visible.length >= input.limit) break;
      const ok = await this.deps.visibilityFilter.isVisible({
        tenantId,
        roleId: input.roleId,
        entityType: result.entityType,
        entityId: result.entityId,
      });
      if (!ok) continue;
      if (skipped < input.offset) {
        skipped++;
        continue;
      }
      visible.push(result);
    }

    return visible.map(mapResultToDto);
  }

  private async searchBackend(backend: SearchBackendName, entityTypes: string[], tenantId: string, input: SearchQueryInput, fetchSize: number): Promise<SearchResultItem[]> {
    const engine = this.deps.backends[backend];
    if (!engine) return [];

    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new SearchTimeoutError(this.deps.timeoutMs)), this.deps.timeoutMs);
    });

    try {
      return await Promise.race([engine.search({ tenantId, query: input.q, entityTypes, limit: fetchSize }), timeout]);
    } catch (err) {
      if (err instanceof SearchTimeoutError) throw err;
      throw new SearchIndexUnavailableError(backend, err);
    } finally {
      clearTimeout(timer);
    }
  }
}
