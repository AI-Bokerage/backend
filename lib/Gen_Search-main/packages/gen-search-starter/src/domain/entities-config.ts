import type { SearchBackendName } from "../config/constants.ts";

// The host names its own entity types (e.g. "lead", "ticket", "file") and
// says which backend serves each — the library has no opinion on what
// entities exist, only how to route them once told. See
// docs/source-audit-notes.md for why per-entity field mappings were dropped
// from this config: indexing is entirely host-driven, so the library never
// needs to know field names.
export type SearchEntitiesConfig = Record<string, { backend: SearchBackendName }>;

export function backendsUsed(entities: SearchEntitiesConfig): SearchBackendName[] {
  return [...new Set(Object.values(entities).map((e) => e.backend))];
}
