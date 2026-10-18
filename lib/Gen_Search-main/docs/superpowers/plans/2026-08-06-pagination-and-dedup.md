# Pagination and Cross-Backend Dedup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offset-based pagination to `GET /api/v1/search/:tenantId`, and deduplicate merged search results by `(entityType, entityId)` so a row appearing twice in a merged result set collapses to its highest-scoring copy.

**Architecture:** Both features live entirely in `packages/gen-search-starter/src/modules/query/v1/` (`schema.ts`, `service.ts`) plus the config plumbing (`config/env.ts`, `create-gen-search.ts`) that already threads `minQueryLength`/`maxQueryLength`/`defaultResults`/`maxResults` through to the schema. `offset` is a new query param, bounded by a new `maxOffset` limit that follows the exact same override-or-env-default pattern as `maxResults`. Pagination is implemented by widening each backend's per-call fetch to `offset + limit` rows (the `ISearchQueryEngine.search()` port already takes a `limit`; no port or adapter change needed — the service just passes a bigger number into the existing param) and then skipping the first `offset` *visible* results after the visibility filter runs, so pagination stays consistent with the existing "filter after merge" design instead of skipping rows the caller isn't even allowed to see. Dedup is a single merge-step function keyed on `${entityType}:${entityId}`, run on the flattened per-backend results before sorting, keeping whichever copy has the higher score.

**Tech Stack:** TypeScript, Express, Zod, Vitest. No new dependencies.

## Global Constraints

- Follow the existing override-or-default-from-env pattern for every new config knob (see `docs/integration-guide.md`'s env var table) — a new limit gets a `GenSearchLimits` field, a `SearchQueryLimits` field, and an `env.ts` entry with the same default-has-no-required-var shape as `MAX_RESULTS`.
- No new files unless a file would otherwise mix unrelated responsibilities — `dedupeByEntity` is small and used only by `service.ts`, so it stays in that file.
- Match existing test style: `vitest`, `describe`/`it`, mock `ISearchQueryEngine` via `vi.fn().mockResolvedValue(...)`, same helper pattern already in `service.test.ts`.
- Do not touch `PgFtsAdapter` or `OpenSearchAdapter` — both already forward `params.limit` straight into their query/search call; passing a larger `limit` value into the existing port is sufficient for pagination.

---

## File Structure

- Modify: `packages/gen-search-starter/src/config/env.ts` — add `MAX_SEARCH_OFFSET` env var.
- Modify: `packages/gen-search-starter/src/modules/query/v1/schema.ts` — add `offset` field to the query schema and `maxOffset` to `SearchQueryLimits`.
- Test: `packages/gen-search-starter/src/modules/query/v1/schema.test.ts` — new file, unit tests for the offset field's default/bound behavior.
- Modify: `packages/gen-search-starter/src/create-gen-search.ts` — add `maxOffset` to `GenSearchLimits` and wire it into the `limits` object passed to the query router.
- Modify: `packages/gen-search-starter/src/modules/query/v1/service.ts` — widen per-backend fetch size to `offset + limit`, apply offset-skipping after the visibility filter, add `dedupeByEntity()` and call it on the merged results before sorting.
- Modify: `packages/gen-search-starter/src/modules/query/v1/service.test.ts` — add pagination and dedup test cases.
- Modify: `docs/integration-guide.md` — document `offset`, `MAX_SEARCH_OFFSET`, and rewrite the "merged results aren't deduplicated" known limitation to describe the new (narrower) behavior.

---

### Task 1: `offset` query param with a configurable `maxOffset` bound

**Files:**
- Modify: `packages/gen-search-starter/src/config/env.ts`
- Modify: `packages/gen-search-starter/src/modules/query/v1/schema.ts`
- Test: `packages/gen-search-starter/src/modules/query/v1/schema.test.ts`

**Interfaces:**
- Consumes: nothing new — `env.ts` and `schema.ts` are leaf config modules.
- Produces: `SearchQueryLimits.maxOffset: number` (consumed by Task 2's `create-gen-search.ts` change) and `SearchQueryInput.offset: number` (consumed by Task 3's `service.ts` change).

- [ ] **Step 1: Write the failing test**

Create `packages/gen-search-starter/src/modules/query/v1/schema.test.ts`:

```typescript
import { describe, it, expect } from "vitest";
import { makeSearchQuerySchema, type SearchQueryLimits } from "./schema.ts";

const limits: SearchQueryLimits = {
  minQueryLength: 1,
  maxQueryLength: 500,
  defaultResults: 20,
  maxResults: 100,
  maxOffset: 10_000,
};

describe("makeSearchQuerySchema offset", () => {
  it("defaults offset to 0 when omitted", () => {
    const schema = makeSearchQuerySchema(limits);
    const parsed = schema.parse({ q: "acme" });
    expect(parsed.offset).toBe(0);
  });

  it("coerces a string offset to a number", () => {
    const schema = makeSearchQuerySchema(limits);
    const parsed = schema.parse({ q: "acme", offset: "50" });
    expect(parsed.offset).toBe(50);
  });

  it("rejects a negative offset", () => {
    const schema = makeSearchQuerySchema(limits);
    expect(() => schema.parse({ q: "acme", offset: -1 })).toThrow();
  });

  it("rejects an offset beyond maxOffset", () => {
    const schema = makeSearchQuerySchema(limits);
    expect(() => schema.parse({ q: "acme", offset: 10_001 })).toThrow();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-search-starter -- schema.test.ts`
Expected: FAIL — `makeSearchQuerySchema` doesn't produce an `offset` field yet (`parsed.offset` is `undefined`, first assertion fails), and `SearchQueryLimits` has no `maxOffset` property (TypeScript error on the `limits` object literal).

- [ ] **Step 3: Add `MAX_SEARCH_OFFSET` to env config**

In `packages/gen-search-starter/src/config/env.ts`, add the new var next to the other search limits:

```typescript
  SEARCH_TIMEOUT_MS: z.coerce.number().default(1500),
  MIN_QUERY_LENGTH: z.coerce.number().default(1),
  MAX_QUERY_LENGTH: z.coerce.number().default(500),
  DEFAULT_RESULTS: z.coerce.number().default(20),
  MAX_RESULTS: z.coerce.number().default(100),
  MAX_SEARCH_OFFSET: z.coerce.number().default(10_000),
```

- [ ] **Step 4: Add `offset`/`maxOffset` to the schema module**

Replace the full contents of `packages/gen-search-starter/src/modules/query/v1/schema.ts`:

```typescript
import { z } from "zod";

export interface SearchQueryLimits {
  minQueryLength: number;
  maxQueryLength: number;
  defaultResults: number;
  maxResults: number;
  maxOffset: number;
}

// A factory, not a module-level constant, because min/max query length and
// result limits are per-instance config (createGenSearch({ limits })), not
// fixed constants the way MAX_PAGE_SIZE is in Gen_SLA.
export function makeSearchQuerySchema(limits: SearchQueryLimits) {
  return z.object({
    q: z.string().trim().min(limits.minQueryLength).max(limits.maxQueryLength),
    type: z.string().trim().min(1).max(64).optional(),
    roleId: z.string().trim().max(120).optional(),
    limit: z.coerce.number().int().positive().max(limits.maxResults).default(limits.defaultResults),
    offset: z.coerce.number().int().nonnegative().max(limits.maxOffset).default(0),
  });
}

export type SearchQueryInput = z.infer<ReturnType<typeof makeSearchQuerySchema>>;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npm test --workspace=@gen-ms/gen-search-starter -- schema.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-search-starter/src/config/env.ts packages/gen-search-starter/src/modules/query/v1/schema.ts packages/gen-search-starter/src/modules/query/v1/schema.test.ts
git commit -m "feat: add offset query param with configurable maxOffset bound"
```

---

### Task 2: Wire `maxOffset` through `createGenSearch()`

**Files:**
- Modify: `packages/gen-search-starter/src/create-gen-search.ts`

**Interfaces:**
- Consumes: `SearchQueryLimits.maxOffset` (from Task 1), `env.MAX_SEARCH_OFFSET` (from Task 1).
- Produces: `GenSearchLimits.maxOffset?: number` — a host can now pass `createGenSearch({ limits: { maxOffset: 500 } })` to tighten the default.

This task is pure config plumbing (same shape as the existing `maxResults` wiring one line above it) — verified by a typecheck rather than a new unit test, matching how the other three `SearchQueryLimits` fields are wired here today with no dedicated test of their own.

- [ ] **Step 1: Add `maxOffset` to `GenSearchLimits`**

In `packages/gen-search-starter/src/create-gen-search.ts`, find:

```typescript
export interface GenSearchLimits {
  minQueryLength?: number;
  maxQueryLength?: number;
  defaultResults?: number;
  maxResults?: number;
  timeoutMs?: number;
}
```

Replace with:

```typescript
export interface GenSearchLimits {
  minQueryLength?: number;
  maxQueryLength?: number;
  defaultResults?: number;
  maxResults?: number;
  maxOffset?: number;
  timeoutMs?: number;
}
```

- [ ] **Step 2: Wire it into the `limits` object**

Find:

```typescript
  const limits: SearchQueryLimits = {
    minQueryLength: config.limits?.minQueryLength ?? env.MIN_QUERY_LENGTH,
    maxQueryLength: config.limits?.maxQueryLength ?? env.MAX_QUERY_LENGTH,
    defaultResults: config.limits?.defaultResults ?? env.DEFAULT_RESULTS,
    maxResults: config.limits?.maxResults ?? env.MAX_RESULTS,
  };
```

Replace with:

```typescript
  const limits: SearchQueryLimits = {
    minQueryLength: config.limits?.minQueryLength ?? env.MIN_QUERY_LENGTH,
    maxQueryLength: config.limits?.maxQueryLength ?? env.MAX_QUERY_LENGTH,
    defaultResults: config.limits?.defaultResults ?? env.DEFAULT_RESULTS,
    maxResults: config.limits?.maxResults ?? env.MAX_RESULTS,
    maxOffset: config.limits?.maxOffset ?? env.MAX_SEARCH_OFFSET,
  };
```

- [ ] **Step 3: Typecheck**

Run: `npm run build --workspace=@gen-ms/gen-search-starter`
Expected: compiles clean — `SearchQueryLimits` now requires `maxOffset` (from Task 1) and this object literal supplies it, so no TS2741 "missing property" error.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-search-starter/src/create-gen-search.ts
git commit -m "feat: wire maxOffset through createGenSearch config"
```

---

### Task 3: Apply offset pagination and cross-backend dedup in `SearchQueryService`

**Files:**
- Modify: `packages/gen-search-starter/src/modules/query/v1/service.ts`
- Modify: `packages/gen-search-starter/src/modules/query/v1/service.test.ts`

**Interfaces:**
- Consumes: `SearchQueryInput.offset` (from Task 1). `ISearchQueryEngine.search({ tenantId, query, entityTypes, limit })` — unchanged signature; this task just passes a larger `limit` value into it.
- Produces: `SearchQueryService.search(tenantId, input)` now returns `input.limit` items starting after `input.offset` visible results, deduplicated by `(entityType, entityId)`. No signature change — same `Promise<SearchResultDto[]>` return type other tasks/callers already depend on.

- [ ] **Step 1: Write the failing tests**

Add to `packages/gen-search-starter/src/modules/query/v1/service.test.ts`, inside the existing `describe("SearchQueryService", ...)` block (after the last `it(...)`):

```typescript
  it("skips the first `offset` visible results, sorted by score desc", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "a", title: "A", score: 3 },
      { entityType: "lead", entityId: "b", title: "B", score: 2 },
      { entityType: "lead", entityId: "c", title: "C", score: 1 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 1, offset: 1 });
    expect(results.map((r) => r.entityId)).toEqual(["b"]);
  });

  it("requests offset + limit rows from each backend", async () => {
    const engine = makeEngine([]);
    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    await service.search("t1", { q: "acme", limit: 10, offset: 40 });
    expect(engine.search).toHaveBeenCalledWith(expect.objectContaining({ limit: 50 }));
  });

  it("deduplicates by (entityType, entityId), keeping the highest score", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "dup", title: "Old", score: 0.4 },
      { entityType: "lead", entityId: "dup", title: "New", score: 0.9 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 20, offset: 0 });
    expect(results).toHaveLength(1);
    expect(results[0].score).toBe(0.9);
  });
```

Also update every pre-existing call to `service.search("t1", { q: ..., limit: ... })` in this file to include `offset: 0` — `SearchQueryInput` will require it once Task 1 lands (no default is applied when constructing the object directly in a test; the Zod default only applies during `schema.parse`). Change each of these four existing lines:

```typescript
    await expect(service.search("t1", { q: "***", limit: 20 })).rejects.toThrow(SearchQueryTooBroadError);
```
```typescript
    await expect(service.search("t1", { q: "acme", type: "ghost", limit: 20 })).rejects.toThrow(SearchInvalidQueryError);
```
```typescript
    const results = await service.search("t1", { q: "acme", limit: 1 });
```
```typescript
    const results = await service.search("t1", { q: "acme", limit: 20 });
```
(this last pattern appears twice — in the visibility-filter test and the backend-failure test)

to include `, offset: 0` before the closing `}`, e.g. `service.search("t1", { q: "***", limit: 20, offset: 0 })`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-search-starter -- service.test.ts`
Expected: FAIL — the three new tests fail (no offset-skipping, no dedup, backend called with `limit: 10` not `limit: 50`), and the pre-existing tests fail to typecheck without `offset` in the input object once `SearchQueryInput` requires it.

- [ ] **Step 3: Implement offset + dedup in the service**

Replace the full contents of `packages/gen-search-starter/src/modules/query/v1/service.ts`:

```typescript
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

// Two rows can share (entityType, entityId) if a backend's own query
// legitimately returns the same document twice (e.g. it matched via more
// than one indexed field) — collapse to the higher-scoring copy. This is
// deliberately NOT a title-based or cross-entity-type dedup: two different
// entities that happen to share a title are two real records, not a
// duplicate, so the key stays scoped to the same entity.
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
    merged.sort((a, b) => b.score - a.score);

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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-search-starter -- service.test.ts`
Expected: PASS (all 7 tests — 4 pre-existing + 3 new)

- [ ] **Step 5: Run the full suite**

Run: `npm test --workspace=@gen-ms/gen-search-starter`
Expected: PASS (the "with a real database" suite still needs Docker/testcontainers and is unaffected by this change either way — same pass/fail status it had before this task)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-search-starter/src/modules/query/v1/service.ts packages/gen-search-starter/src/modules/query/v1/service.test.ts
git commit -m "feat: apply offset pagination and entity-key dedup to merged search results"
```

---

### Task 4: Document the new param, env var, and revised limitation

**Files:**
- Modify: `docs/integration-guide.md`

**Interfaces:**
- Consumes: nothing (docs-only).
- Produces: nothing (docs-only).

- [ ] **Step 1: Add `MAX_SEARCH_OFFSET` to the env var table**

In `docs/integration-guide.md`, find this row in the `## Configuration` table:

```markdown
| `DEFAULT_RESULTS` / `MAX_RESULTS` | never (have defaults) | `20` / `100` |
```

Add immediately after it:

```markdown
| `MAX_SEARCH_OFFSET` | never (has default) | `10000` |
```

- [ ] **Step 2: Add `offset` to the query param reference**

In the `### Query — GET /api/v1/search/:tenantId` section, find:

```markdown
| `limit` | optional, defaults to `DEFAULT_RESULTS`, capped at `MAX_RESULTS` |
```

Add immediately after it:

```markdown
| `offset` | optional, defaults to `0`, capped at `MAX_SEARCH_OFFSET` |
```

- [ ] **Step 3: Rewrite the merged-results dedup limitation**

Find this bullet under `## Known Limitations`:

```markdown
- **Merged results across backends aren't deduplicated.** Searching two
  entity types that happen to share a title won't collapse them — dedup, if
  you need it, is on you.
```

Replace with:

```markdown
- **Dedup is keyed on `(entityType, entityId)`, not title.** Merged results
  collapse to the highest-scoring copy when the same entity appears twice
  (e.g. a backend query matching one document via more than one field). Two
  *different* entities that happen to share a title — a `file` and a
  `document` row with identical text, say — are not collapsed; that's two
  real records, not a duplicate. See the "document" vs "file" naming note in
  `docs/source-audit-notes.md`.
- **Offset pagination widens the per-backend fetch, it isn't a cursor.**
  `offset` makes each backend return `offset + limit` rows before the
  visibility filter runs; if that filter rejects many of them, a page can
  come back with fewer than `limit` items even though more results exist
  further out — same caveat `limit` alone already has, just visible at depth
  now that pages go deeper.
```

- [ ] **Step 4: Commit**

```bash
git add docs/integration-guide.md
git commit -m "docs: document offset pagination and revised dedup behavior"
```

---

## Self-Review Notes

- **Spec coverage:** pagination (Tasks 1–3) ✅, cross-backend dedup (Task 3) ✅, config plumbing follows existing `maxResults` pattern (Task 1–2) ✅, docs updated to match new behavior and drop the now-inaccurate limitation (Task 4) ✅.
- **Placeholder scan:** every step has runnable code or an exact command; no TBD/"add validation"/"similar to" left in.
- **Type consistency:** `SearchQueryLimits.maxOffset` (Task 1) → `GenSearchLimits.maxOffset` / `limits.maxOffset` (Task 2) → `SearchQueryInput.offset` (Task 1's schema output, consumed in Task 3's `service.ts`) — same field names throughout. `dedupeByEntity(items: SearchResultItem[]): SearchResultItem[]` is defined and called within the same file (Task 3), no cross-file signature drift.
