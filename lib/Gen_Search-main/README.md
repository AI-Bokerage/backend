# Gen_SEARCH

Generic, host-agnostic search library for the Gen_MS family — hexagonal
ports over Postgres full-text search and OpenSearch, with pluggable
reindex, erasure, and event-driven indexing modules.

## Packages

- `@gen-ms/gen-search-starter` — the library. `createGenSearch(config)`
  returns an Express app (query/reindex/erasure routes) plus an optional
  background indexer worker.
- `@gen-ms/gen-search-demo` — minimal host app wiring the library up
  end-to-end, used by `scripts/smoke-search.sh`.

## Quick start

```
npm install
docker compose up -d
npm run dev --workspace=@gen-ms/gen-search-demo
./scripts/smoke-search.sh
```

## Docs

- [`docs/integration-guide.md`](docs/integration-guide.md) — install, config,
  auth, API reference, error codes, known limitations.
- [`docs/source-audit-notes.md`](docs/source-audit-notes.md) — how this
  library's design deviates from the original LLD spec, and why.

## Status

Fresh implementation, not an extraction — see "Nothing reusable as-is" in
`docs/source-audit-notes.md`. Validated against zero real callers until
CPMS's own `apps/search-svc` becomes the first consumer.
