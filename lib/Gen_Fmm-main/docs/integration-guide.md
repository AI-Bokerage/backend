# Gen_FMM Integration Guide

## Embedding in-process

```ts
import { createGenFmm } from "@gen-ms/gen-fmm-starter";

const genFmm = createGenFmm({
  internalSecret: process.env.GEN_FMM_INTERNAL_SECRET,
});

hostApp.use("/fmm", genFmm.app);

// direct in-process call — no HTTP round-trip, no auth of its own
const result = await genFmm.check(tenantId, "new_dashboard", planCode);
if (result.enabled) { /* ... */ }

// gate a host route behind a feature flag
hostApp.get("/reports/new", (req, res, next) => { req.tenantId = req.user.tenantId; next(); }, genFmm.requireFeature("new_dashboard"), handler);
```

## Running standalone (HTTP)

```bash
docker compose up -d
# genfmm_app (the app role DATABASE_URL must use below) cannot run migrations —
# it has no CREATE on the public schema and the RLS-enable/role-grant
# migrations need the bootstrap superuser. Run migrate deploy against genfmm,
# then switch to genfmm_app for the app itself.
DATABASE_URL=postgresql://genfmm:genfmm@localhost:5441/genfmm npx prisma migrate deploy --schema packages/gen-fmm-starter/prisma/schema.prisma
npm run build --workspaces --if-present
DATABASE_URL=postgresql://genfmm_app:genfmm_app@localhost:5441/genfmm \
GEN_FMM_ADMIN_DATABASE_URL=postgresql://genfmm:genfmm@localhost:5441/genfmm \
REDIS_URL=redis://localhost:6385 \
GEN_FMM_INTERNAL_SECRET=change-me-dev-secret \
node packages/gen-fmm-demo/dist/index.js
```

## Environment variables

| Var | Required when | Notes |
|---|---|---|
| `DATABASE_URL` | any Prisma-backed repo (`catalogRepo`/`overrideRepo`/`telemetryRepo`) not overridden | Postgres connection string, must point at `genfmm_app`, never the bootstrap superuser |
| `GEN_FMM_ADMIN_DATABASE_URL` | `modules.overrides` enabled (default true) and no custom `overrideRepo` supplied | Postgres connection string with RLS-bypass privileges (superuser role); used only by the cross-tenant admin listing path (`listAll`) |
| `REDIS_URL` | `cacheStore` not overridden | Backs the L2 cache (`RedisCacheStore`) |
| `GEN_FMM_INTERNAL_SECRET` | `modules.check` enabled (default true) and `internalSecret` config not set | Gates `/internal/v1/fmm/*` via `X-Internal-Secret` — factory throws `GenFmmConfigError` at boot if missing while `modules.check` is on |
| `ALLOWED_ORIGINS` | never (defaults to `*`) | Comma-separated CORS origins |
| `CHECK_CACHE_TTL_SECS` | never (defaults to `60`) | L2 TTL for single-flag checks; also used as the L1 TTL (ms) |
| `L1_CACHE_MAX_ENTRIES` | never (defaults to `10000`) | Bounded in-process L1 cache size |
| `TELEMETRY_BUFFER_MAX` | never (defaults to `500`) | Ring-buffer cap; oldest event dropped on overflow |
| `PORT` | demo app only | HTTP port |

## API surface

Internal (require `X-Internal-Secret: <GEN_FMM_INTERNAL_SECRET>`):

| Method | Path | Notes |
|---|---|---|
| GET | `/internal/v1/fmm/check/:tenantId/:flagKey` | `?planCode=` optional |
| GET | `/internal/v1/fmm/bulk/:tenantId` | `?planCode=` optional |

Public (`modules.*` gated, no auth of its own — front with the host's own authn/authz):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/entitlement/:tenantId/:flagKey` | Same resolution as the internal check, no secret gate |
| GET | `/api/v1/entitlements/:tenantId` | Bulk, whole-tenant map |
| GET/POST/PATCH | `/api/v1/catalog/modules[/:code]` | |
| PUT/GET/DELETE | `/api/v1/catalog/plan-modules`, `/api/v1/catalog/plans/:planCode/modules[/:moduleCode]` | |
| GET/POST/PATCH/DELETE | `/api/v1/catalog/flags[/:key]` | |
| POST/GET | `/api/v1/overrides` | Upsert / list all (requires `GEN_FMM_ADMIN_DATABASE_URL`) |
| GET/DELETE | `/api/v1/overrides/:tenantId[/:flagKey]` | List for tenant / find one / delete |
| GET | `/api/v1/telemetry/:tenantId` | `?flagKey=&from=&to=&page=&pageSize=` |

Always available:

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | No auth |
| GET | `/docs` | Swagger UI |
| GET | `/docs.json` | Raw OpenAPI 3.0 spec |

**`check()` on an unknown flag key returns `{ enabled: false, reason: "FLAG_NOT_FOUND" }` with HTTP 200, never 404** — callers never need to special-case a missing flag.

**`planCode` is always caller-supplied.** Gen_FMM has no `plans` table and never looks up subscription state itself — the host passes the tenant's current plan code on every `check()`/`bulk()` call (or omits it, in which case only override/default/rollout tiers apply).

**Gen_FMM has no internal scheduler.** Call `flushTelemetryBuffer()` (or rely on the buffer's bounded size) on your own interval — telemetry events otherwise accumulate in-process only.

**Multi-pod cache coherence is opt-in.** `onFlagChanged?(flagKey, tenantId?)` fires on every catalog/override write; wire it to your own pub/sub if you run more than one pod. Single-pod hosts can ignore it — each pod's own L1 self-invalidates via TTL.

## Swapping adapters

- `catalogRepo` / `overrideRepo` / `telemetryRepo`: implement `ICatalogRepo` / `ITenantOverrideRepo` / `ITelemetryRepo` to swap persistence.
- `cacheStore`: implement `ICacheStore` (`get`/`set`/`del`/`setNx`) to swap Redis for another L2 store.
- `onFlagChanged`: wire to your own pub/sub for multi-pod L1 coherence.

## Known limitations (v1)

- No RBAC/JWT of any kind — the host fronts every route with its own auth.
- No message broker — `planCode` is always a parameter, never looked up via subscription events.
- No internal scheduler — telemetry flush is entirely host-driven.
- `invalidateFlag()` (catalog-level changes) does not glob-purge other tenants' L1 entries — correctness after a catalog change relies on the short check-cache TTL plus the `onFlagChanged` broadcast for other pods.
- `invalidateFlag()` also cannot purge L2/Redis cache entries across all tenants for a given flag key — `ICacheStore` has no SCAN/keys-by-pattern primitive. Correctness after a catalog-level flag change relies on the cache's short TTL plus the host-supplied `onFlagChanged` broadcast hook eventually converging every process's cache.
- Cross-tenant admin listing (`listAll`) requires a separate `GEN_FMM_ADMIN_DATABASE_URL` pointing at an RLS-bypass-capable connection (superuser role); the default Prisma-backed implementation validates this eagerly at `createGenFmm()` boot time if the overrides module is enabled and no custom `overrideRepo` is supplied.
