# Gen_SEARCH Integration Guide

## Install

    npm install @gen-ms/gen-search-starter

## Quick start

    import { createGenSearch } from "@gen-ms/gen-search-starter";

    const { app } = createGenSearch({
      entities: { lead: { backend: "pg" }, file: { backend: "opensearch" } },
      reindexSource: async function* (tenantId) { /* stream your own data */ },
      worker: {
        exchange: "my-app.events",
        queue: "my-app.search-indexer",
        routingKeys: ["*.*.created", "*.*.updated"],
        resolveDocument: (envelope) => ({
          tenantId: envelope.tenant_id,
          entityType: "lead",
          entityId: envelope.data.id,
          title: envelope.data.company_name,
        }),
      },
    });
    app.listen(3800);

## Configuration

Every field follows override-or-default-from-env, same as every Gen_MS repo.
Misconfiguration (a required env var missing for a default adapter that's
actually needed) throws `GenSearchConfigError` synchronously inside
`createGenSearch()`.

| Env var | Required when | Default |
|---|---|---|
| `DATABASE_URL` | any entity routes to `"pg"` and `pgAdapter` not overridden | none — required |
| `OPENSEARCH_URL` | any entity routes to `"opensearch"` and `openSearchAdapter` not overridden | none — required |
| `OPENSEARCH_USERNAME` / `OPENSEARCH_PASSWORD` | your OpenSearch cluster has security enabled | none — optional, unauthenticated if omitted |
| `OPENSEARCH_INDEX_PREFIX` | never (has default) | `gensearch-` |
| `VALKEY_URL` | `modules.reindex` enabled and `lock` not overridden | none — required |
| `RABBITMQ_URL` | `modules.worker` enabled and `eventBus` not overridden | none — required |
| `GEN_SEARCH_INTERNAL_SECRET` | `internalSecret` not passed to config | none — required |
| `PORT` | never (has default) | `3800` |
| `SEARCH_TIMEOUT_MS` | never (has default) | `1500` |
| `MIN_QUERY_LENGTH` / `MAX_QUERY_LENGTH` | never (have defaults) | `1` / `500` |
| `DEFAULT_RESULTS` / `MAX_RESULTS` | never (have defaults) | `20` / `100` |
| `MAX_SEARCH_OFFSET` | never (has default) | `1000` |
| `INDEXER_BATCH_SIZE` | never (has default) | `100` |
| `INDEXER_FLUSH_INTERVAL_MS` | never (has default) | `2000` |

`config.entities` is **required** — the library has no default entity types.
It's a plain map: `{ [yourEntityTypeName]: { backend: "pg" | "opensearch" } }`.

`config.reindexSource` is **required whenever `modules.reindex` is enabled**
(the default) — see "Reindex" below for why.

`config.worker` is **required whenever `modules.worker` is enabled** (the
default) — `resolveDocument` (and optionally `resolveRemoval`) are how you
map your own event vocabulary onto index writes; this library assumes none.

`modules: { query?, reindex?, erasure?, worker? }` — each defaults to `true`.
A disabled route module 404s rather than being unmounted silently.

## Auth

Every route requires an `X-Internal-Secret` header matching `internalSecret`
(config override or `GEN_SEARCH_INTERNAL_SECRET`). `tenantId` is a URL path
segment (`/api/v1/search/:tenantId/...`), validated as a UUID — not derived
from a token. There is no JWT/JWKS/RBAC layer in this library.

**Role-based visibility is a pluggable hook, not a default.** If you don't
pass `visibilityFilter`, every tenant-scoped result is returned to anyone
holding a valid internal secret for that tenant — there is no per-role
filtering unless you supply `IVisibilityFilter`. For anything with real
per-role visibility requirements, this matters — see
`docs/source-audit-notes.md`'s "Role-based ACL" section before shipping.

    import { createGenSearch, type IVisibilityFilter } from "@gen-ms/gen-search-starter";

    const visibilityFilter: IVisibilityFilter = {
      isVisible: async ({ tenantId, roleId, entityType, entityId }) =>
        myPermissionCheck(tenantId, roleId, entityType, entityId),
    };
    createGenSearch({ ...otherConfig, visibilityFilter });

## API reference

### Query — `GET /api/v1/search/:tenantId`

| Query param | Meaning |
|---|---|
| `q` | required, `MIN_QUERY_LENGTH`–`MAX_QUERY_LENGTH` chars |
| `type` | optional — restrict to one configured entity type |
| `roleId` | optional — passed through to `visibilityFilter.isVisible()` |
| `limit` | optional, defaults to `DEFAULT_RESULTS`, capped at `MAX_RESULTS` |
| `offset` | optional, defaults to `0`, capped at `MAX_SEARCH_OFFSET` |

Response: `{ "data": [{ entityType, entityId, title, snippet?, score }] }`,
sorted by score descending, deduplicated by `(entityType, entityId)` — see
Known Limitations for what that dedup does and doesn't cover (title
collisions across different entities are not collapsed).

### Reindex — `POST /api/v1/search/:tenantId/reindex`

Body: `{ "entityTypes"?: string[] }` — omit to reindex every configured
entity type. Takes a per-tenant lock (`REINDEX_ALREADY_RUNNING`, 409, if one
is already in flight for that tenant), clears existing indexed docs for the
targeted entity types, then drains your `reindexSource(tenantId, entityTypes?)`
async iterator and bulk-writes in `INDEXER_BATCH_SIZE` batches. Responds
`200 { "indexed": <count> }` once the whole drain completes — this is
synchronous from the caller's point of view, not fire-and-forget.

**This library owns no source-of-truth data** (see
`docs/source-audit-notes.md`), so it can't regenerate its own index. You
supply `reindexSource` — typically paginating your own services' data.

**No RBAC.** The LLD gates reindex to a privileged role; this library has no
role model, so gate who can reach this route yourself (your own gateway, a
second internal secret, whatever your host already does for authz).

### Erasure — `POST /api/v1/search/:tenantId/erasure`

Body: `{ "ownerId": string }` (UUID). Removes every indexed document across
every configured backend whose `ownerId` matches, for that tenant. Responds
`200 { "removed": <count> }`. Requires `ownerId` to have been set on
`IndexDocument` at index time — if you never set it, this always returns 0.

Also exported as `ErasureService.removeUserDocuments(tenantId, ownerId)` for
in-process callers, and as an HTTP route (unlike Gen_SLA's stance of keeping
lifecycle operations library-only) because the source spec explicitly wants
an endpoint an external erasure-orchestration service can call and confirm
synchronously.

## Wiring your own events

Gen_SEARCH does not assume any business-event vocabulary. `config.worker`
drives an internal `RabbitMqBus` consumer (or your own `IEventConsumer`) that
maps your events to index writes via `resolveDocument`/`resolveRemoval`:

    worker: {
      exchange: "my-app.events",
      queue: "my-app.search-indexer",
      routingKeys: ["*.*.created", "*.*.updated", "*.*.deleted"],
      resolveRemoval: (envelope) =>
        envelope.event_type.endsWith(".deleted")
          ? { tenantId: envelope.tenant_id, entityType: "lead", entityId: envelope.data.id }
          : null,
      resolveDocument: (envelope) => ({
        tenantId: envelope.tenant_id,
        entityType: "lead",
        entityId: envelope.data.id,
        title: envelope.data.company_name,
        ownerId: envelope.data.owner_id,
      }),
    }

See `packages/gen-search-demo/src/sample-event-handler.ts` for a fuller
illustration, including a sample `reindexSource`.

## Error codes

| Code | Status | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing/wrong `X-Internal-Secret` |
| `TENANT_ID_INVALID` | 400 | `:tenantId` path segment is not a UUID |
| `VALIDATION_ERROR` | 400 | Request body/query failed Zod validation |
| `SEARCH_INVALID_QUERY` | 400 | `type` filter references an entity type not in `config.entities` |
| `SEARCH_QUERY_TOO_BROAD` | 422 | Query is wildcard-only (e.g. `*`, `**`) |
| `SEARCH_TIMEOUT` | 504 | A backend didn't respond within `SEARCH_TIMEOUT_MS` |
| `SEARCH_INDEX_UNAVAILABLE` | 503 | A backend threw (connection failure, etc.) |
| `REINDEX_ALREADY_RUNNING` | 409 | A reindex is already running for that tenant |

`REINDEX_TENANT_NOT_FOUND` and `REINDEX_AUTH_REQUIRED_SUPER_ADMIN` from the
original LLD are intentionally not implemented — see
`docs/source-audit-notes.md`.

## Known Limitations

- **Role-based ACL is a permissive default, not a safe one.** See "Auth"
  above. Any entity type with real per-role visibility requirements needs an
  explicit `visibilityFilter`.
- **The reindex lock has no early release.** `IDistributedLock` (matching
  Gen_SLA's port) only supports TTL-based expiry, not manual release — a
  reindex that finishes in 5 seconds still blocks a second one for the full
  lock TTL (3600s). Don't call `/reindex` back-to-back expecting immediate
  availability.
- **PG-backend ACL enforcement is application-layer, not database-layer.**
  The original LLD's `visible_to_role()` is a Postgres `SECURITY DEFINER`
  function so no code path can bypass it. `PgFtsAdapter` doesn't call one —
  `IVisibilityFilter` runs after rows come back from Postgres. If you need
  the stronger DB-enforced guarantee, implement your own `ISearchQueryEngine`
  that calls your own `SECURITY DEFINER` function directly, and pass it as
  `pgAdapter`.
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
  now that pages go deeper. Deduplication can shorten a page the same way —
  if `dedupeByEntity` collapses rows inside the fetched window, a page can
  come back shorter than `limit` even though more distinct results exist
  further out.
- **`entityId`/`ownerId` are UUIDs.** `search_index.entity_id` is `@db.Uuid`.
  Map non-UUID native IDs to a UUID (e.g. deterministic UUIDv5) before
  calling into this library.

## Local development

    docker compose up -d
    npm install
    npm run dev --workspace=@gen-ms/gen-search-demo
    ./scripts/smoke-search.sh

Ports: Postgres `5442`, Valkey `6386`, RabbitMQ AMQP `5675` (mgmt UI
`15675`), OpenSearch `9202`.
