# Gen_SUP Source Audit — sup-svc

Source: `CPMS-Platform/apps/sup-svc` (TypeScript/Express/Prisma), copied into
`pre-context/sup-svc` for this audit, deleted afterward (gitignored, never
committed).

## Modules (`src/modules/*/v1/`)

1. **analytics** — `RevenueAnalyticsService.getAnalytics` computes MRR/ARR by
   plan and region, plan distribution, trial-conversion rate, avg
   revenue/tenant. Valkey-cached 5min, no writes.
2. **dashboard** — `DashboardService.getKpis` aggregates 10 parallel repo
   queries into one KPI snapshot (active tenants, signups today/week/month,
   MRR, trials active/ending-in-7d, churn, past-due, suspended). Valkey-cached
   5min. **First Gen_SUP module — see Gen_TNT gap below.**
3. **announcements** — `BroadcasterService.broadcast` publishes a
   `sup.announcement.broadcast` event to the platform-audit exchange. No
   templating, no delivery tracking.
4. **feature-flags** — `FlagService.update` mutates flag overrides, emits
   `fmm.override.changed`. No resolution logic — Gen_FMM already owns that
   (4-tier resolution engine). Gen_SUP's version will be a thin wrapper over
   Gen_FMM, not a reimplementation.
5. **impersonate** — `ImpersonationOrchestrator`: super-admin requests a
   session against ANY tenant, tenant admin grants/denies within 24h, on
   grant mints a real cross-tenant JWT via AUTH-SVC (role via ADM-SVC),
   caches in Valkey, revokes `jti` platform-wide on end/expire. This is the
   cross-tenant case Gen_ADM's own impersonation design doc explicitly
   called out of scope ("a separate spec") — complements, doesn't duplicate.
6. **tenants** — `create.service.ts`/`suspend.service.ts`: zero local state,
   pure orchestration + audit wrapper over TNT-SVC. Gen_TNT already owns
   tenant CRUD + provisioning — Gen_SUP's version will be a thin client +
   audit wrapper, not a reimplementation.
7. **tickets** — `TicketService`: full lifecycle (OPEN→IN_PROGRESS→RESOLVED→
   CLOSED), SLA due-dates, threaded internal/external responses, assignment,
   escalation, bulk-reassign-on-offboarding. The "other side" of what Gen_ADM's
   support-tickets feature explicitly deferred ("SUP-SVC owns everything past
   creation") — much richer than Gen_ADM's tenant-scoped self-service log.

## Gap found: Gen_TNT has no tenant-listing capability

The original dashboard/analytics compute KPIs from a local Prisma read-model
(`supTenantSummary`) kept in sync by paging through TNT-SVC's tenant
**search** API and enriching with BSM-SVC/PPM-SVC billing data. Gen_TNT's
actual HTTP surface today is `POST /tenants`, `GET /tenants/{id}`, and
suspend/reactivate/cancel only — no list/search/count endpoint, no events
published. There is also no Gen_MS sibling for BSM-SVC or PPM-SVC.
Decision (see design spec): `dashboard` uses a consumer-supplied
`TenantMetricsPort` instead of a local read-model or a Gen_TNT client.

## Build order

dashboard → tenants → feature-flags → announcements → analytics → tickets →
impersonate (easy → hard, confirmed with user during brainstorming).
