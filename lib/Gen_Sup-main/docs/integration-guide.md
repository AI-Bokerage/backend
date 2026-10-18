# Gen_SUP Integration Guide

## Install

Not yet published to a registry. Install from a local build until publishing is wired up:
```bash
npm install ../Gen_SUP/packages/gen-sup-starter
```

## Upgrading

The `tenants` module now defaults to **enabled** (`modules.tenants` defaults to
`true`), matching `dashboard`. This is a breaking change for any existing
dashboard-only consumer: `createGenSup()` will now throw at construction time
if `GEN_TNT_BASE_URL`, `GEN_TNT_INTERNAL_SECRET`, or `RABBITMQ_URL` aren't
set (and no `tntClient`/`eventPublisher` override is supplied), even if you
never call the tenants routes. If you only want the dashboard module, pass
`modules: { tenants: false }` explicitly.

`featureFlags` also now defaults to `true` — an existing embedder must pass
`modules: { featureFlags: false }` or supply both `GEN_FMM_BASE_URL` and
`RABBITMQ_URL` (or their overrides: `fmmClient` and `eventPublisher`) before
upgrading. This is a breaking change for any existing embedder without these
set. Note: `RABBITMQ_URL` is shared with the `tenants` module — if `tenants`
is already enabled, this requirement is already met; if `featureFlags` is
enabled but `tenants` is disabled, `RABBITMQ_URL` becomes newly required.

`announcements` also now defaults to `true` — an existing embedder must pass
`modules: { announcements: false }` or supply both `DATABASE_URL` and
`RABBITMQ_URL` (or `prisma`/`eventPublisher` overrides) before upgrading,
the same as the `tenants`/`feature-flags` modules' own breaking-change notes
above (`RABBITMQ_URL` is shared with those modules if either is already
enabled — only newly required if both are disabled).

`analytics` also now defaults to `true` — an existing embedder must pass
`modules: { analytics: false }` or supply `GEN_USG_BASE_URL`, `DATABASE_URL`,
and `VALKEY_URL` (or `usgClient`/`prisma`/`valkeyUrl` overrides) before
upgrading, the same as prior modules' own breaking-change notes above.
`DATABASE_URL` is shared with `announcements` — only newly required if
`announcements` is disabled; `VALKEY_URL` is shared with `dashboard` — only
newly required if `dashboard` is disabled. These are independent: each is
gated on its own paired module, not on both being disabled together.

Separately — and this one applies **even if you never enable `analytics`**:
`TenantMetricsPort` gained 4 new required methods (`mrrByPlan`,
`mrrByRegion`, `planDistribution`, `trialConversion`). If you supply your
own `TenantMetricsPort` implementation (rather than relying on
`noopTenantMetricsPort`), your implementation must add these 4 methods
before your code will type-check against this version, regardless of which
modules you enable.

## Quick start

```ts
import { createGenSup } from "@gen-ms/gen-sup-starter";
import type { TenantMetricsPort } from "@gen-ms/gen-sup-starter";

const myTenantMetricsPort: TenantMetricsPort = {
  countActive: () => myDb.tenants.count({ status: "ACTIVE" }),
  countSignupsSince: (date) => myDb.tenants.count({ provisionedAt: { gte: date } }),
  sumActiveAndTrialMrr: () => myDb.tenants.sumMrr({ status: ["ACTIVE", "TRIAL"] }),
  countActiveTrials: () => myDb.tenants.count({ status: "TRIAL" }),
  countTrialsEndingBetween: (start, end) => myDb.tenants.count({ status: "TRIAL", trialEndsAt: { gte: start, lte: end } }),
  countChurnedSince: (date) => myDb.tenants.count({ status: "CANCELLED", updatedAt: { gte: date } }),
  countByStatus: (status) => myDb.tenants.count({ status }),
  mrrByPlan: (filter) => myDb.tenants.groupMrrByPlan(filter),
  mrrByRegion: (filter) => myDb.tenants.groupMrrByRegion(filter),
  planDistribution: () => myDb.tenants.groupCountByPlan(),
  trialConversion: (from, to) => myDb.tenants.trialConversion({ from, to }),
};

const { app } = createGenSup({
  tenantMetricsPort: myTenantMetricsPort,
  internalSecret: process.env.GEN_SUP_INTERNAL_SECRET,
  valkeyUrl: process.env.VALKEY_URL,
  tntBaseUrl: process.env.GEN_TNT_BASE_URL,
  tntInternalSecret: process.env.GEN_TNT_INTERNAL_SECRET,
  rabbitMqUrl: process.env.RABBITMQ_URL,
});

app.listen(3900);
```

If you don't supply `tenantMetricsPort`, Gen_SUP falls back to an all-zero
no-op — the app boots and `/api/v1/dashboard/kpis` responds, but every field
is `0` until you wire up a real implementation.

## Why a port instead of a built-in Gen_TNT client?

Gen_TNT's current HTTP API (`POST /tenants`, `GET /tenants/{id}`,
suspend/reactivate/cancel) has no list/search/count endpoint to aggregate
KPIs from. `TenantMetricsPort` lets you source these numbers however you
actually can today — querying Gen_TNT's own database directly if it's
co-located, a future Gen_TNT list endpoint once one exists, or any other
system of record. See `docs/source-audit-notes.md` for the full finding.

## Auth

Every route requires an `X-Internal-Secret` header matching the
`internalSecret` you configured (or `GEN_SUP_INTERNAL_SECRET` env var).
Gen_SUP does not verify who the caller is beyond that shared secret — your
app is responsible for confirming the request actually comes from an
authenticated super-admin (e.g. via Gen_AUTH) before forwarding it here.

## API reference

### Dashboard — `GET /api/v1/dashboard/kpis`

```
GET /api/v1/dashboard/kpis?forceRefresh=true
X-Internal-Secret: <secret>
```

Response:
```json
{
  "activeTenants": 42,
  "signupsToday": 1,
  "signupsThisWeek": 5,
  "signupsThisMonth": 12,
  "mrr": 8400,
  "activeTrials": 3,
  "trialsEndingIn7d": 1,
  "churnedThisMonth": 0,
  "pastDueCount": 2,
  "suspendedCount": 1,
  "generatedAt": "2026-08-10T12:00:00.000Z"
}
```

Cached in Valkey for `DASHBOARD_CACHE_TTL_SEC` (default 300s). Pass
`forceRefresh=true` to bypass the cache. If any `TenantMetricsPort` method
throws, the request responds `502 TENANT_METRICS_UNAVAILABLE` and nothing is
cached — Valkey itself being unreachable is handled separately and falls
back to a live (uncached) computation instead of failing the request.

### Tenants — `/api/v1/tenants`

```
POST /api/v1/tenants
X-Internal-Secret: <secret>
Content-Type: application/json

{
  "name": "Acme Corp",
  "slug": "acme",
  "region": "us-east-1",
  "ownerEmail": "owner@acme.com",
  "ownerFirstName": "Ada",
  "ownerLastName": "Lovelace",
  "idempotencyKey": "optional-client-generated-uuid"
}
```

→ `201 Created`, body includes Gen_TNT's tenant fields plus `ownerEmail`/`ownerFirstName`/
`ownerLastName` (Gen_SUP-only metadata, not sent to or returned by Gen_TNT itself).
`primaryOwnerUserId` is generated server-side — Gen_TNT requires it but does not generate it.
`409 TENANT_SLUG_TAKEN` if the slug is already in use.

```
GET /api/v1/tenants/:id
GET /api/v1/tenants/by-slug/:slug
```

→ `200` with the tenant, or `404 TENANT_NOT_FOUND`. `:id` must be a UUID —
a malformed id responds `400 VALIDATION_ERROR` before any Gen_TNT call is made.

```
PATCH /api/v1/tenants/:id/suspend
Content-Type: application/json

{ "reason": "Non-payment for 90 days" }
```

→ `200` with the updated tenant. `reason` is Gen_SUP-only audit metadata — Gen_TNT's suspend
endpoint takes no request body, so it's never forwarded. `404 TENANT_NOT_FOUND` if the tenant
doesn't exist, `409 TENANT_TRANSITION_CONFLICT` if suspend isn't legal from the tenant's
current status.

```
PATCH /api/v1/tenants/:id/reactivate
Content-Type: application/json

{ "note": "optional" }
```

→ Same shape as suspend, publishes `sup.tenant.reactivated`.

Every create/suspend/reactivate call publishes a fire-and-forget audit event to the
`platform.audit` RabbitMQ exchange (`sup.tenant.created`/`sup.tenant.suspended`/
`sup.tenant.reactivated`) — publish failures are logged and do not fail the request, since
the underlying Gen_TNT call already succeeded by that point.

Note: the `platform.audit` topic exchange has no bound queue or consumer in this repo.
Publishes succeed (it's fire-and-forget), but nothing consumes these events yet — that's
expected until a consuming service binds its own queue to the exchange.

`list`, `cancel`, and `purge` are not implemented in this module (Gen_TNT itself has no
list/search endpoint at all; `cancel`/`purge` are deferred pending a real use case).

### Feature flags — `/api/v1/feature-flags`

Thin proxy over Gen_FMM's catalog-flags and overrides endpoints — Gen_SUP does
not store flag state itself. Requires `GEN_FMM_BASE_URL` (no secret header —
Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` routes have no
authentication of their own).

```
GET /api/v1/feature-flags
X-Internal-Secret: <secret>
```

→ `200`, `{ "flags": [...] }` — the full Gen_FMM flag catalog.

```
PATCH /api/v1/feature-flags/:key
X-Internal-Secret: <secret>
Content-Type: application/json

{ "defaultEnabled": true, "reason": "rolling out to all plans" }
```

→ `200` with the updated flag. `reason` (3-500 chars) is **required** and
Gen_SUP-only — Gen_FMM's own update schema has no such field, so it's never
forwarded; it exists purely for the `sup.flag.updated` audit event.
`404 FLAG_NOT_FOUND` if the key doesn't exist.

```
PUT /api/v1/feature-flags/overrides/:tenantId/:flagKey
X-Internal-Secret: <secret>
Content-Type: application/json

{ "enabled": true, "expiresAt": "2026-09-01T00:00:00Z", "reason": "beta cohort" }
```

→ `200` with the override (upsert — always succeeds, no 404 case). `reason`
is required at the Gen_SUP layer and, unlike the flag-update reason, **is**
forwarded into Gen_FMM's own optional `reason` field, since Gen_FMM persists
it on the override row. Gen_SUP never sends `createdBy` on this upsert — it's
secret-gated, not user-identity-gated, so it has no per-admin identity to
attach; don't assume the field is populated on override responses.

```
GET /api/v1/feature-flags/overrides/:tenantId
```

→ `200`, `{ "overrides": [...] }` — every override for that tenant.

```
DELETE /api/v1/feature-flags/overrides/:tenantId/:flagKey
```

→ `204`. Idempotent — clearing a non-existent override still returns `204`,
not a `404`.

Every update/set/clear publishes a fire-and-forget audit event to the
`platform.audit` exchange (`sup.flag.updated`/`sup.override.set`/
`sup.override.cleared`) — publish failures are logged and do not fail the
request.

### Announcements — `/api/v1/announcements`

Gen_SUP's first module with local persistence — announcements are stored in
Gen_SUP's own Postgres database (`DATABASE_URL`), not proxied to a sibling
service. There is **no real delivery**: `channels` is stored but inert, and
`targetSegment` (e.g. `"ALL"`, `"plan:GROWTH"`, `"region:IN"`) is a free-form
string nobody in Gen_MS resolves into real tenants/users yet — this module
matches CPMS `sup-svc`'s own behavior, which never delivered anything either.

```
POST /api/v1/announcements
X-Internal-Secret: <secret>
Content-Type: application/json

{
  "title": "Scheduled maintenance",
  "body": "We will be performing maintenance this weekend.",
  "type": "MAINTENANCE",
  "targetSegment": "ALL",
  "channels": ["EMAIL", "IN_APP"],
  "scheduledFor": "2026-09-01T02:00:00Z",
  "createdBy": "11111111-1111-1111-1111-111111111111"
}
```

→ `201` with the created announcement. `createdBy` is required and
caller-supplied — Gen_SUP has no user identity of its own (only the shared
`X-Internal-Secret`). If `scheduledFor` is omitted, the announcement
broadcasts immediately (publishes `sup.announcement.broadcast` and stamps
`sentAt`); if provided, it's held until `dispatchScheduled()` picks it up.
Every create also publishes `sup.announcement.created` regardless of
scheduling. There is no update/delete/cancel — a sent announcement is
immutable history.

```
GET /api/v1/announcements?type=CRITICAL&page=1&pageSize=20
X-Internal-Secret: <secret>
```

→ `200`, `{ "announcements": [...], "page": 1, "pageSize": 20, "total": 3 }`.

**Scheduled dispatch is host-driven** — Gen_SUP adds no scheduler. Call
`instance.announcementsService?.dispatchScheduled()` on your own interval or
cron to send up to 100 due rows per call (oldest-due-first — call again to
drain a larger backlog); it returns `{ dispatched, failed }`; a single row's
failure doesn't abort the sweep, but an unreachable database rejects the
whole call — wrap your cron/interval call in a `.catch()`.

### Analytics — `/api/v1/analytics`

Revenue analytics (extends `TenantMetricsPort` — no new port, same read-only
reporting shape as `dashboard`) plus a real historical MRR trend (Gen_SUP's
second local-persistence table) and usage trends via Gen_USG.

```
GET /api/v1/analytics/revenue?planCode=&region=&from=&to=&export=csv
X-Internal-Secret: <secret>
```

→ `200` with `{ totalMrr, totalArr, averageRevenuePerTenant, byPlan, byRegion,
planDistribution, trialConversion, generatedAt }`. `planCode`/`region` are
real filters that reshape `byRegion`/`byPlan` respectively — `totalMrr`,
`totalArr`, `averageRevenuePerTenant`, `trialConversion`, and
`planDistribution` are always the whole-fleet view, unaffected by either
filter. `from`/`to` scope `trialConversion`'s date range only (default:
trailing 30 days) — they don't bucket the other numbers, which are always a
point-in-time snapshot.
Cached for `ANALYTICS_CACHE_TTL_SEC` (default 300s) — **only when unfiltered**;
any `planCode`/`region`/`from`/`to` bypasses the cache and computes fresh.
`?export=csv` renders the same snapshot (cached when unfiltered, fresh when
filtered, per the rule above) as a CSV download of the `byPlan` breakdown,
instead of the JSON response.

```
GET /api/v1/analytics/revenue/history?period=daily&from=&to=
```

→ `200` with `{ history: [...] }` — stored `RevenueSnapshot` rows, real
historical granularity (not post-hoc bucketing of live numbers). **Nothing
populates this automatically** — call `instance.analyticsService?.captureRevenueSnapshot("daily"|"weekly"|"monthly")`
on your own cron; Gen_SUP adds no scheduler, matching `announcements`'
`dispatchScheduled()` convention.

```
GET /api/v1/analytics/usage?tenantIds=id1,id2,id3
```

→ `200` with `{ tenants: [...], failures: [...], totals: {...} }` — loops
Gen_USG's per-tenant `/api/v1/usage/summary` for the tenant IDs **you
supply** and sums `current` per metric. Gen_SUP cannot discover "all
tenants" on its own (Gen_USG has no cross-tenant aggregation — every
endpoint is scoped by Postgres row-level security to one tenant per call —
and Gen_TNT has no bulk tenant-list endpoint either), so the caller must
already know which tenants to include. A single tenant's lookup failure is
recorded in `failures` and doesn't fail the request; if every tenant in the
list fails, the whole request 502s (`USG_CLIENT_ERROR`).

## Error codes

| Code | Status | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing/wrong `X-Internal-Secret` |
| `VALIDATION_ERROR` | 400 | Request failed Zod validation |
| `TENANT_METRICS_UNAVAILABLE` | 502 | `TenantMetricsPort` threw during `getKpis()` |
| `TENANT_SLUG_TAKEN` | 409 | Slug already in use (Gen_TNT create returned 409) |
| `TENANT_NOT_FOUND` | 404 | No tenant with this id/slug |
| `TENANT_TRANSITION_CONFLICT` | 409 | Suspend/reactivate isn't legal from the tenant's current status |
| `TNT_CLIENT_ERROR` | 502 | Gen_TNT returned an unexpected error |
| `FLAG_NOT_FOUND` | 404 | No flag with this key (Gen_FMM catalog) |
| `OVERRIDE_NOT_FOUND` | 404 | Reserved — not currently thrown by any route in this module |
| `FMM_CLIENT_ERROR` | 502 | Gen_FMM returned an unexpected error |
| `USG_CLIENT_ERROR` | 502 | Every tenant usage lookup in the request failed |
| `internal_error` | 500 | Unhandled error |

## Known limitations

- `dashboard`, `tenants`, `feature-flags`, `announcements`, and `analytics`
  are the only modules built so far. `tickets` and `impersonate` are planned.
- `dashboard`/`tenants`/`feature-flags` have no local persistence — they rely
  entirely on `TenantMetricsPort`/Gen_TNT/Gen_FMM respectively. `analytics` is
  the second module with local persistence (`RevenueSnapshot`, alongside
  `announcements`' `Announcement` table).
- Cross-tenant usage analytics requires the caller to supply which tenant
  IDs to include — Gen_USG has no cross-tenant aggregation and Gen_TNT has
  no bulk tenant-list endpoint, so Gen_SUP cannot discover "all tenants" on
  its own.
- `RevenueSnapshot` capture is host-driven with no built-in scheduler, same
  as `announcements`' scheduled-dispatch sweep. No retention/pruning policy
  exists for these rows yet.
- CSV export covers only the `byPlan` breakdown, not the full revenue
  snapshot or usage data.
- `announcements` never actually delivers anything — `channels` is stored
  but inert, and `targetSegment` resolution (which real tenants/users match
  a segment string) requires a capability (tenant listing, user directory)
  that doesn't exist anywhere in Gen_MS yet.
- No live-Postgres integration test for `announcements` in this pass — its
  test suite mocks `PrismaClient` entirely.
- `announcements`' scheduled dispatch publishes the broadcast event before
  persisting `sentAt`; if the persistence write fails after a successful
  publish, the next sweep re-broadcasts the same row (at-least-once, not
  exactly-once, delivery). Acceptable today since nothing consumes the event
  yet — revisit before adding a real consumer.
- `tenants` does not implement `list`/search (Gen_TNT itself has no such
  endpoint) or `cancel`/`purge` (deferred pending a real use case).
- Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` endpoints have no
  authentication of their own (Gen_FMM's own documented limitation — "the
  host fronts every route with its own auth"). Gen_SUP's `X-Internal-Secret`
  gate protects requests that go through Gen_SUP, but cannot prevent direct
  network access to Gen_FMM bypassing Gen_SUP entirely. This is Gen_FMM's
  deployment-topology responsibility, not fixable from Gen_SUP's side.

## Local development

```bash
docker compose up -d   # Postgres on 5443, Valkey on 6387, RabbitMQ on 5676 (mgmt UI on 15676)
npm install
cp .env.example packages/gen-sup-demo/.env
npm run dev            # gen-sup-demo on http://localhost:3900
./scripts/smoke-dashboard.sh
```
