# Gen_SEARCH Source Audit Notes

Where the original source lives: `apps/search-svc` in CPMS-Platform, plus the LLD
sections it's built from (§12.12, §13, §14.7.22, §14.11.3.22, §14.12.20, §17.6,
ADR-008), copied into CPMS-Platform's own
`apps/search-svc/docs/SEARCH-SVC_Developer_Reference.md`. No `pre-context/`
copy exists in this repo (unlike Gen_SLA) because there is nothing to copy —
see below.

## Nothing reusable as-is — search-svc was never implemented

Unlike every other Gen_MS ancestor (Gen_SLA, Gen_USG, etc.), `apps/search-svc`
is an **empty scaffold**: `server.ts`, `worker.ts`, stub `auth.ts`/
`tenant-context.ts` middleware (both literally `// TODO: implement`, `next()`
and nothing else), and a Prisma schema with a single comment
(`// TODO: Add models per LLD chunk 6 ER diagram`). No adapters, no indexers,
no `search_index` table, no routes exist in code. Confirmed by reading the
actual files on 2026-08-05, not inferred from the LLD.

This means Gen_SEARCH is not an extraction — there is no working CPMS logic to
port or preserve. It's a fresh implementation against the LLD's spec, informed
by the same house conventions every other Gen_MS repo uses (hexagonal ports,
`X-Internal-Secret` + path-param tenant auth, Prisma-backed defaults with
override ports, Zod validation, Vitest + testcontainers). Treat every
downstream Gen_SEARCH design decision as "first implementation," not
"generalized from something proven" — the config surface here is validated
against zero real callers until CPMS's own `apps/search-svc` becomes the first
consumer.

## Deviations from the LLD, and why

- **Auth model replaced.** The LLD assumes JWT + `visible_to_role()` derived
  from a gateway-verified token, with `tenant_id`/`role_id` extracted from
  claims. Matching every other Gen_MS repo, this library uses
  `X-Internal-Secret` header + `:tenantId` path param instead — no JWT/JWKS
  layer. `roleId` (needed for the ACL hook below) is passed as an explicit
  query param on `/search` instead of derived from a token, since the library
  has no token to derive it from.

- **Role-based ACL is a pluggable hook, not a DB-enforced default — and this
  is a real security delta from the spec, not just a style swap.** The LLD's
  `visible_to_role()` is a Postgres `SECURITY DEFINER` function, deliberately
  placed inside the database so no application code path can bypass it. This
  library exposes an `IVisibilityFilter` port instead, applied in the
  **application layer** after results come back from Postgres/OpenSearch. If
  a host doesn't supply one, the default `AllowAllVisibilityFilter` returns
  every tenant-scoped result to anyone holding a valid internal secret for
  that tenant — there is no per-role filtering by default. Any host handling
  permissioned entities (which CPMS's own leads/tickets/invoices are) must
  supply a real `IVisibilityFilter`, or push the check into their own
  `ISearchQueryEngine` override that calls a DB-side function the way the LLD
  intended. Silence here is a data-exposure bug waiting to happen, not a
  neutral default — call it out to whoever wires this in.

- **"Reindex" is host-driven, not self-contained.** The LLD's reindex
  endpoint implies SEARCH-SVC can regenerate its own index. It can't — per
  its own scope (§1), it never owns source data, only a copy. Gen_SEARCH's
  `POST /reindex` takes a per-tenant distributed lock (409 if already running,
  matching the spec's `REINDEX_ALREADY_RUNNING`), then drains an
  **caller-supplied** `reindexSource(tenantId, entityTypes?)` async iterator
  that the host implements against their own services' data. Required
  whenever `modules.reindex` is enabled (the default) — omitting it throws
  `GenSearchConfigError` at boot, same pattern `create-gen-sla.ts` uses for
  its own required adapters.

- **`REINDEX_TENANT_NOT_FOUND` and `REINDEX_AUTH_REQUIRED_SUPER_ADMIN`
  dropped.** The library has no tenant registry to validate existence
  against, and no RBAC layer to know what "Super Admin" means for a given
  host. `REINDEX_ALREADY_RUNNING` is kept (it's the library's own lock state).
  Gating *who* may call `/reindex` to a privileged role is the host's
  responsibility — front the route with your own authorization before it
  reaches this library, the same way every other Gen_MS repo punts full RBAC
  to the caller.

- **GDPR erasure kept as an HTTP endpoint, unlike Gen_SLA's precedent.**
  Gen_SLA deliberately does *not* expose instance lifecycle over HTTP,
  treating it as library-level API only. Search does the opposite for erasure
  specifically, because LLD §9 explicitly requires a callable internal
  endpoint so an orchestrating erasure saga can invoke it synchronously and
  confirm completion as one step in a larger pipeline — a plain library call
  doesn't satisfy that if the orchestrator is a separate service/process.
  `POST /erasure` is exposed *and* the underlying `ErasureService` is
  exported for in-process callers.

- **"document" vs "file" naming mismatch (LLD gap, not resolved by this
  library).** The library doesn't hardcode entity type names at all — `lead`,
  `file`, `document`, whatever a host calls things is just a key in their own
  `entities` config map. This sidesteps the LLD's own inconsistency rather
  than picking a side; CPMS's `apps/search-svc` still has to pick one when it
  wires up its own indexer→event mapping.

- **Field-mapping config dropped from what was originally proposed.** An
  earlier draft of this design (see CPMS-Platform's own
  `SEARCH-SVC_Developer_Reference.md` Part 2) sketched a per-entity `fields`
  list as library config. That's unnecessary: indexing is entirely
  host-driven (the host's own `resolveDocument` mapping function decides what
  goes into `title`/`snippet`/`content`), so the library never needs to know
  field names — only which backend (`pg` | `opensearch`) each entity type
  routes to.

## Ports (local dev)

Postgres 5442, Valkey 6386, RabbitMQ AMQP 5675, RabbitMQ mgmt UI 15675,
OpenSearch REST 9202 — verified against every sibling Gen_* repo's
`docker-compose.yml` to avoid collisions (OpenSearch is a first for Gen_MS,
no prior port to collide with).
