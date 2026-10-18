# Gen_SUP Scaffold + Dashboard Module — Design

## Why

Gen_SUP is the tenth Gen_MS library: a genericized, tenant-agnostic version of
CPMS-Platform's `sup-svc` (Super Admin Service) — the platform-operator
command centre sitting above every tenant. Like every other Gen_MS library
it's built one module at a time (spec → plan → implement → repeat), not as
one large port.

`sup-svc` has 7 modules. Three of them turn out to be thin wrappers over
capability that already exists elsewhere in Gen_MS rather than new work:

- `feature-flags` — sup-svc's version is a CRUD+audit shim with no
  resolution logic; Gen_FMM already **is** the 4-tier resolution engine.
- `tenants` — sup-svc's version has zero local state, it only orchestrates
  create/suspend/reactivate against TNT-SVC; Gen_TNT already owns tenant
  CRUD + the provisioning saga.

The other four (`dashboard`, `analytics`, `announcements`, `tickets`,
`impersonate` — five, not four) are genuinely novel. `tickets` is the richer
counterpart to what Gen_ADM's support-tickets feature explicitly deferred
("SUP-SVC owns everything past creation"), and `impersonate` is the
cross-tenant case Gen_ADM's own impersonation design doc called out as "a
separate spec" (Gen_ADM's is intra-tenant only).

Build order (easy → hard, confirmed with user): **dashboard → tenants →
feature-flags → announcements → analytics → tickets → impersonate**. This
spec covers the one-time repo scaffold plus the first module, `dashboard`.

## Scope

**In scope (this spec):**
- Gen_SUP repo scaffold: npm workspace, starter/demo package split, docker
  compose (Postgres + Valkey), Prisma wired up (empty schema for now),
  pre-context audit.
- Cross-module auth strategy (applies to every future module too).
- `dashboard` module: platform KPI snapshot, read-only.

**Out of scope (future specs):** `tenants`, `feature-flags`,
`announcements`, `analytics`, `tickets`, `impersonate`.

## A gap discovered during research

The original `sup-svc` dashboard/analytics compute KPIs from a local Prisma
read-model (`supTenantSummary`) kept in sync via a worker that pages through
TNT-SVC's tenant **search** API and enriches with BSM-SVC/PPM-SVC billing
data. None of that exists in Gen_MS today:

- Gen_TNT's HTTP surface is `POST /tenants`, `GET /tenants/{id}`, and
  suspend/reactivate/cancel only — **no list/search/count endpoint, no
  events published**. There is nothing to page through or subscribe to.
- There is no Gen_MS sibling for BSM-SVC (billing) or PPM-SVC (plan
  pricing) at all.

So a literal port of the original design is blocked on capability that
doesn't exist yet in a different library's repo. Three options were
considered:

- **(A) Consumer-supplied `TenantMetricsPort`** — Gen_SUP defines a port
  interface, the host app implements it however it wants (query Gen_TNT's
  DB directly, hit a future list endpoint, whatever), Gen_SUP ships a no-op
  default. No local Prisma table, no sync worker, no HTTP client to Gen_TNT
  in this module at all.
- **(B) Extend Gen_TNT first** — add a real search endpoint to Gen_TNT, then
  match the original design 1:1 with a synced local read-model. Correct
  long-term, but scope creep into a different library before Gen_SUP has
  its first line of code.
- **(C) Hybrid** — ship (A) now, shaped so a future `GenTntAdapter` package
  can implement the same port once Gen_TNT grows a list endpoint.

**Decision: (A).** Smallest footprint, unblocks Gen_SUP today, and doesn't
foreclose (C) later — the port's shape doesn't need to change for an
adapter to be added afterward.

## Repo scaffold

Matches Gen_SLA (the most recently completed TS-stack sibling) exactly:

```
Gen_MS/Gen_SUP/
├── package.json              # "gen-sup-workspace", workspaces: ["packages/*"]
├── package-lock.json
├── docker-compose.yml         # Postgres + Valkey
├── .env.example
├── docs/
│   ├── adr/
│   ├── source-audit-notes.md  # written from pre-context/sup-svc, then pre-context/ deleted
│   └── superpowers/{specs,plans}/
└── packages/
    ├── gen-sup-starter/       # @gen-ms/gen-sup-starter — the library
    │   ├── package.json
    │   ├── tsconfig.json
    │   ├── vitest.config.ts
    │   ├── prisma/schema.prisma   # scaffolded now, empty until `tickets`/`impersonate` need tables
    │   └── src/
    │       ├── index.ts            # public barrel
    │       ├── create-gen-sup.ts   # createGenSup(config) factory, per family convention
    │       ├── common/              # logger, errors
    │       ├── config/              # env
    │       ├── infra/                # prisma client, valkey client (added when first needed)
    │       ├── middleware/          # internal-secret gate, error-handler
    │       └── modules/
    │           └── dashboard/v1/    # controller, service, port, routes, dto, schema, types
    └── gen-sup-demo/           # @gen-ms/gen-sup-demo — thin reference app
        ├── package.json
        └── src/index.ts
```

`pre-context/sup-svc` is copied in from `CPMS-Platform/apps/sup-svc` as a
git-tracked reference during the audit step, then deleted once
`docs/source-audit-notes.md` captures what's needed — it does not persist
in the final repo, matching Gen_SLA.

## Cross-module auth strategy

Every Gen_SUP route (this module and all future ones) is gated on an
`X-Internal-Secret` header, matching Gen_TNT and Gen_SLA. The host app is
responsible for verifying the caller is an authenticated super-admin (e.g.
via Gen_AUTH) *before* it forwards the request to Gen_SUP — no JWT
verification lives inside Gen_SUP itself. This is decided once here and
applies to `tenants`, `feature-flags`, `announcements`, `analytics`,
`tickets`, and `impersonate` without needing to be re-litigated per module.

## Module: `dashboard`

**Purpose:** a single read-only platform KPI snapshot for a super-admin home
screen — active tenant count, signups today/this-week/this-month, MRR,
active trials, trials ending in 7 days, tenants churned this month, past-due
count, suspended count. Ported from `sup-svc`'s `DashboardService.getKpis`.

**`TenantMetricsPort` (host-implemented, package export):**

```ts
interface TenantMetricsPort {
  countActive(): Promise<number>;
  countSignupsSince(date: Date): Promise<number>;
  sumActiveAndTrialMrr(): Promise<number>;
  countActiveTrials(): Promise<number>;
  countTrialsEndingBetween(start: Date, end: Date): Promise<number>;
  countChurnedSince(date: Date): Promise<number>;
  countByStatus(status: string): Promise<number>;
}
```

This mirrors the original `DashboardRepo`'s methods 1:1 — same shapes, just
backed by whatever the host supplies instead of a local Prisma table. A
no-op default (every method resolves `0`) ships in the package so
`createGenSup(config)` boots without a real implementation supplied —
read-only reporting, so a harmless all-zero default is safe, unlike a
mutating flow that should hard-fail without its dependency.

**Flow:** `GET /api/v1/dashboard/kpis` → `DashboardController` →
`DashboardService.getKpis()` calls all seven port methods via
`Promise.all`, shapes the response (same field names as the original:
`activeTenants`, `signupsToday`/`signupsThisWeek`/`signupsThisMonth`, `mrr`,
`activeTrials`, `trialsEndingIn7d`, `churnedThisMonth`, `pastDueCount`,
`suspendedCount`) → cached in Valkey for 5 minutes (matches original TTL).

**Error handling:** if any port method throws, the whole request fails
(502 via the shared error-handler middleware) and nothing is written to the
cache. No partial/degraded snapshot — a half-populated KPI dashboard cached
for 5 minutes is worse than a clear error on that one request.

**Testing:** `DashboardService` unit tests against a mock `TenantMetricsPort`
(vitest) — cache-hit, cache-miss, and port-throws-partway-through cases.
