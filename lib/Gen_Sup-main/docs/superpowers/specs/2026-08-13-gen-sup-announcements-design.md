# Gen_SUP announcements module — design

## Context

Fourth module in the Gen_SUP build order (`dashboard` → `tenants` → `feature-flags` → **announcements** → analytics → tickets → impersonate).

Original CPMS `sup-svc` announcements module (`SUP-06`) is a platform-wide broadcast feature: a super-admin creates a title/body/type message targeted at a coarse `targetSegment` string (`"ALL"`, `"plan:GROWTH"`, `"region:IN"`, etc.), optionally scheduled for later. On send (immediate or via a scheduled sweep), it fires an audit event onto RabbitMQ and marks itself sent. Critically, **the original feature never actually delivers anything** — segment resolution (which real tenants/users match `"plan:GROWTH"`) is nobody's job in CPMS-Platform; no consumer subscribes to the audit event; `channels: ['EMAIL','IN_APP']` is captured but never acted on. It is, in its own codebase, a stub that stores a row and fires an unconsumed event.

Gen_SUP's version mirrors this honestly rather than inventing a delivery mechanism the family doesn't support yet: store + audit-event only. This is the same category of decision as `tenants` deferring `list`/search (no Gen_TNT endpoint) and `dashboard` deferring live cross-tenant aggregation (same gap) — segment resolution requires a tenant-listing/user-directory capability that doesn't exist anywhere in Gen_MS yet.

This is also the **first Gen_SUP module with local persistence**. `dashboard` reads through `TenantMetricsPort`, `tenants` proxies Gen_TNT, `feature-flags` proxies Gen_FMM — none write to Gen_SUP's own (already-provisioned but unused) Postgres. Announcements has no sibling service that owns "an announcement" as a concept, so Gen_SUP itself becomes the source of truth, exactly as CPMS's own `sup-svc` was.

## Scope

In scope:
- Create an announcement (immediate broadcast if no `scheduledFor`, else deferred to a sweep).
- List announcements (paginated, optional `type` filter).
- Host-driven scheduled-sweep dispatch (`dispatchScheduled()`), mirroring Gen_FMM's `flushTelemetryBuffer()` host-driven pattern — no scheduler library, no internal timer.
- Fire-and-forget audit events on `platform.audit` (`sup.announcement.created` on every create, `sup.announcement.broadcast` on actual send — immediate or swept).

Out of scope (deferred, no current capability to build on):
- Real delivery (email/in-app) via Gen_NOTIF or any other channel — would require inventing tenant/user segment resolution that exists nowhere in the family. `channels` is stored but inert, exactly as in CPMS's own stub.
- Update, cancel, or delete — CPMS has none; a sent announcement is immutable history, and nothing in this plan needs to un-schedule one before it fires.
- A live-Postgres integration test — this pass uses a mocked `PrismaClient` at the unit level (matching every other module's mocked-dependency test convention); a real-DB smoke test is a documented known limitation, same category as other modules' deferred gaps.

## Architecture

### Persistence — new Prisma model

`packages/gen-sup-starter/prisma/schema.prisma` currently has only `generator`/`datasource` blocks (confirmed empty across all three prior modules). This adds the first model:

```prisma
model Announcement {
  id            String    @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  title         String    @db.VarChar(500)
  body          String
  type          String    @default("INFO") @db.VarChar(24)
  targetSegment String    @default("ALL") @map("target_segment") @db.VarChar(64)
  channels      String[]
  scheduledFor  DateTime? @map("scheduled_for") @db.Timestamptz(6)
  sentAt        DateTime? @map("sent_at") @db.Timestamptz(6)
  createdBy     String    @map("created_by") @db.Uuid
  createdAt     DateTime  @default(now()) @map("created_at") @db.Timestamptz(6)

  @@index([sentAt])
  @@index([scheduledFor])
  @@map("announcements")
}
```

Field shapes and defaults match CPMS's own model exactly — `targetSegment`/`channels` are free-form (no FK to a `Tenant`/`User` table anywhere in this schema, matching the family-wide absence of a resolvable directory).

New `infra/persistence/prisma-client.ts`:
```ts
import { PrismaClient } from "../../../__generated__/prisma/index.js";

export function createPrismaClient(databaseUrl: string): PrismaClient {
  return new PrismaClient({ datasources: { db: { url: databaseUrl } } });
}
```

`resolveDatabaseUrl()` follows the exact override-or-`requireEnv` shape as `resolveValkeyUrl`/`resolveTntClient`/`resolveFmmClient`: `GenSupConfig.databaseUrl?: string`, falling back to `requireEnv("DATABASE_URL")` (already in `.env.example` from the original scaffold). `GenSupConfig.prisma?: PrismaClient` lets a consumer supply their own client (e.g. to share a connection pool, or for testing). `GenSupInstance.prisma?: PrismaClient` exposes it for shutdown (`instance.prisma?.$disconnect()`), matching how `valkey`/`eventPublisher` are already exposed.

### `AnnouncementsService`

No external client port — unlike `tenants`/`feature-flags`, this data has no sibling-service owner, so the service talks to Prisma directly (same shape as `DashboardService` taking a raw `Redis` client rather than wrapping it in a port).

```ts
class AnnouncementsService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly eventPublisher: EventPublisher,
  ) {}

  async create(input: CreateAnnouncementInput): Promise<AnnouncementDto> { ... }
  async list(query: ListAnnouncementsQuery): Promise<{ announcements: AnnouncementDto[]; page: number; pageSize: number; total: number }> { ... }
  async dispatchScheduled(): Promise<{ dispatched: number; failed: number }> { ... }
}
```

- `create`: writes the row via `prisma.announcement.create`. If `scheduledFor` is absent, immediately publishes `sup.announcement.broadcast` and stamps `sentAt = now()` in the same flow (matching CPMS's immediate-send path); if `scheduledFor` is present, leaves `sentAt` null for the sweep. Always additionally publishes `sup.announcement.created` (a Gen_SUP-only audit refinement over CPMS's single event — richer traceability of the admin action itself, independent of whether/when it actually broadcasts).
- `list`: offset-paginated (`page`/`pageSize`, defaults 1/20, max 100), optional `type` filter, ordered newest-first.
- `dispatchScheduled`: queries `sentAt IS NULL AND scheduledFor <= now()`, broadcasts + stamps each; a single item's failure to persist `sentAt` is caught and logged, never aborts the sweep for the rest (same fire-and-forget resilience philosophy as `publishSafely` elsewhere in this codebase). Returns `{ dispatched, failed }` for per-row failures — but the `findMany` fetch itself sits outside that try/catch, so a database outage during the fetch rejects the whole call; a host's cron wrapper must still `.catch()` it.

Exposed on `GenSupInstance` as `announcementsService?: AnnouncementsService` (present only when the module is enabled) so a host can call `instance.announcementsService?.dispatchScheduled()` on their own interval — no scheduler dependency added to this library, mirroring Gen_FMM's own documented "no internal scheduler, call `flushTelemetryBuffer()` yourself" limitation.

### Routes (`/api/v1/announcements`, `X-Internal-Secret` gated)

```
POST /api/v1/announcements
GET  /api/v1/announcements
```

No update/delete/cancel routes — matches CPMS exactly.

### Schema (Zod)

```ts
const ANNOUNCEMENT_TYPES = ["INFO", "MAINTENANCE", "NEW_FEATURE", "PRICING_CHANGE", "CRITICAL"] as const;
const ANNOUNCEMENT_CHANNELS = ["EMAIL", "IN_APP"] as const;

createAnnouncementSchema = z.object({
  title: z.string().min(3).max(500),
  body: z.string().min(10).max(10000),
  type: z.enum(ANNOUNCEMENT_TYPES).default("INFO"),
  targetSegment: z.string().min(1).max(64).default("ALL"),
  channels: z.array(z.enum(ANNOUNCEMENT_CHANNELS)).min(1),
  scheduledFor: z.string().datetime({ offset: true }).optional(),
  createdBy: z.string().uuid(),
});

listAnnouncementsQuerySchema = z.object({
  type: z.enum(ANNOUNCEMENT_TYPES).optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});
```

`createdBy` is required and caller-supplied — Gen_SUP has no user identity of its own (only the shared `X-Internal-Secret`), the same asymmetry already documented for Gen_FMM's unpopulated override `createdBy`. `scheduledFor`'s `{ offset: true }` is a lesson carried over directly from the `feature-flags` module's own review-round fix (a bare `.datetime()` rejects valid non-UTC-offset timestamps).

## Error handling

No new `AppError` subclasses are needed — there's no get-by-id/update/delete route that could 404, and Prisma write/read failures on `create`/`list`/`dispatchScheduled` are unexpected-error cases (mapped by the existing generic `errorHandler` to `500 internal_error`), not a modeled business-error surface like `tenants`'/`feature-flags`' typed client-error mapping. If this changes in a future iteration (e.g. an update/cancel route is added), a proper `AnnouncementNotFoundError` (404) would be added then.

## Audit events

New routing keys alongside `TENANT_ROUTING_KEYS`/`FLAG_ROUTING_KEYS` (same `platform.audit` exchange, same `RabbitMqBus`, same fire-and-forget `publishSafely` pattern):

```ts
export const ANNOUNCEMENT_ROUTING_KEYS = {
  CREATED: "sup.announcement.created",
  BROADCAST: "sup.announcement.broadcast",
} as const;
```

## `createGenSup` wiring

- `GenSupModulesConfig.announcements?: boolean` (default `true`, same pattern as the other three modules).
- `GenSupConfig.databaseUrl?: string`, `prisma?: PrismaClient`.
- `resolvePrismaClient()` mirrors `resolveTntClient()`/`resolveFmmClient()`'s override-or-requireEnv shape.
- Mounts `/api/v1/announcements` when enabled.
- Every pre-existing test in `create-gen-sup.test.ts` must be audited for the same regression class hit twice already (a new module defaulting to `true` breaking tests that don't know to disable it) — by now this is a checklist item for every future module's wiring task, not a one-off risk.

## Testing

Unit-level, Vitest: `service.test.ts` with a mocked `PrismaClient` (jest/vi-style mock of `prisma.announcement.create`/`findMany`/`update`) and mocked `EventPublisher`, covering: immediate-send stamps `sentAt` + publishes both events; scheduled create leaves `sentAt` null + publishes only `CREATED`; `list` pagination/filter; `dispatchScheduled` broadcasts due items, stamps them, tolerates one failing item without aborting the rest, returns accurate counts. Route-level smoke tests in `create-gen-sup.test.ts` for `POST`/`GET` gated on `X-Internal-Secret`, plus the standard pre-existing-test audit.

## Known limitations to document

- No real delivery — `channels` is stored but inert, matching CPMS's own stub behavior. Segment resolution (which tenants/users match a `targetSegment`) requires a capability (tenant listing, user directory) that doesn't exist anywhere in Gen_MS yet.
- No live-Postgres integration test in this pass — unit tests mock `PrismaClient` entirely.
- `dispatchScheduled()` must be called by the host on its own interval/cron — Gen_SUP adds no scheduler.
- `dispatchScheduled()`'s broadcast is published before `sentAt` is persisted; a persistence failure after a successful publish causes at-least-once (not exactly-once) redelivery on the next sweep. Fine today with no real consumers of the event.
