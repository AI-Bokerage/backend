# Gen_SUP Tenants Module — Design

## Why

Second module in the confirmed build order (`dashboard` → **`tenants`** → `feature-flags` →
`announcements` → `analytics` → `tickets` → `impersonate`). In the original `sup-svc`,
`create.service.ts`/`suspend.service.ts` are a platform-operator-facing orchestration + audit
layer over TNT-SVC — zero local write-state, TNT-SVC (Gen_TNT) is the source of truth. This
spec ports that same shape onto Gen_TNT's actual (already-built) HTTP API.

Reading Gen_TNT's real source (`TenantController.java`/`CreateTenantRequest.java`/
`TenantResponse.java`) instead of assuming parity with the original CPMS source surfaced
several differences that reshape this module's scope:

- Gen_TNT's create endpoint has **no plan/billing field at all** — the original's PPM-SVC
  plan-resolution step has nothing to resolve into. There is no PPM-SVC-shaped gap to design
  around here, unlike `dashboard`'s TNT-SVC-search gap.
- Gen_TNT's `suspend`/`reactivate` endpoints take **no request body** — no `reason` field to
  forward, unlike the original's validated `reason`/`note` fields.
- Gen_TNT's `getById`/`getBySlug` **do exist and work** — only `list`/search has the gap
  `dashboard` already hit (Gen_TNT has no list/search endpoint at all).
- Gen_TNT requires the caller to supply `primaryOwnerUserId` — it does not generate one itself.
- Gen_TNT's `region` field is an unvalidated free string (its own docs use AWS-style values
  like `"us-east-1"`), not the original's `INDIA`/`EU`/`US` enum.
- Gen_TNT exposes a fuller lifecycle (`cancel`/`purge`) the original source never had.

Gen_REG already calls Gen_TNT over HTTP for its own provisioning handoff
(`packages/gen-reg-starter/src/infra/tnt-client/tnt-client.ts`) — `HttpTntClient` implementing
`ITntClient`, plain `fetch()`, `X-Internal-Secret` header, `GEN_TNT_BASE_URL`/
`GEN_TNT_INTERNAL_SECRET` env vars. This module's own Gen_TNT client mirrors that exact,
already-proven pattern rather than inventing a new one.

## Scope

**In scope:** `create`, `suspend`, `reactivate`, `getById`, `getBySlug`. Zero local
Prisma table — same "thin wrapper, no local state" shape as `dashboard`.

**Out of scope (deferred, decided during brainstorming):**
- `list`/search — same missing-capability gap `dashboard` hit (no Gen_TNT list endpoint).
  Revisit if a future module genuinely needs tenant browsing; not core to lifecycle management.
- `cancel`/`purge` — Gen_TNT supports both, but the original source this port is based on never
  had them. Deferred rather than inventing scope beyond the source material.

## Gen_TNT client

`domain/ports/tnt-client.port.ts`:

```ts
export interface TntTenantResponse {
  id: string;
  slug: string;
  name: string;
  status: string; // PROVISIONING | ACTIVE | SUSPENDED | CANCELLED | PURGED
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface CreateTenantParams {
  name: string;
  slug: string;
  region: string;
  primaryOwnerUserId: string;
  idempotencyKey?: string;
}

export interface TntClientPort {
  createTenant(params: CreateTenantParams): Promise<TntTenantResponse>;
  getTenant(id: string): Promise<TntTenantResponse | null>;
  getTenantBySlug(slug: string): Promise<TntTenantResponse | null>;
  suspendTenant(id: string): Promise<TntTenantResponse>;
  reactivateTenant(id: string): Promise<TntTenantResponse>;
}
```

`infra/external/http-tnt-client.ts`: `HttpTntClient implements TntClientPort`, constructed with
`(baseUrl, internalSecret)`, sends `X-Internal-Secret` + `Content-Type: application/json` on
every call. `getTenant`/`getTenantBySlug` return `null` on `404` (not found is a legitimate,
non-exceptional result for a lookup); every other non-2xx (including `409`) throws a typed
error the service layer maps (see Error handling). No consumer-override port needed here —
unlike `TenantMetricsPort`, this isn't papering over a missing capability, it's a real,
working client against a real, working API.

Config: `GEN_TNT_BASE_URL` and `GEN_TNT_INTERNAL_SECRET`, both via `requireEnv` (lazy,
same pattern as `VALKEY_URL`/`GEN_SUP_INTERNAL_SECRET` — only required when the `tenants`
module is enabled and no override was supplied to `createGenSup`).

## Create flow

`CreateTenantSchema` (zod): `name` (2–255 chars), `slug` (3–63 chars, lowercase/digits/hyphens,
matches Gen_TNT's own slug rules), `region` (free string, 1–100 chars, no enum — Gen_TNT itself
does zero region validation, so a Gen_SUP enum would reject values Gen_TNT would happily
accept), `ownerEmail` (valid email), `ownerFirstName`/`ownerLastName` (optional), `idempotencyKey`
(optional UUID).

`TenantsService.create(input)`:
1. Generate `primaryOwnerUserId = randomUUID()` server-side (matches the original source's
   behavior — a placeholder a real auth system binds to later; Gen_TNT has no field for owner
   email/name, so those stay Gen_SUP-side metadata, embedded only in the audit event).
2. Call `tntClient.createTenant({ name, slug, region, primaryOwnerUserId, idempotencyKey })`.
3. Publish `sup.tenant.created` (see Audit events) with the full input plus the generated
   `primaryOwnerUserId` and Gen_TNT's response.
4. Return Gen_TNT's `TntTenantResponse` plus the generated `primaryOwnerUserId` and the
   caller-supplied owner email/name (never returned by Gen_TNT itself).

## Suspend / reactivate flow

`SuspendTenantSchema`: `reason` (10–1000 chars, Gen_SUP-only metadata — Gen_TNT's suspend
endpoint takes no body, so this is never forwarded, only recorded in the audit event).
`ReactivateTenantSchema`: `note` (optional, ≤500 chars, same treatment).

`TenantsService.suspend(id, reason)` → `tntClient.suspendTenant(id)` → publish
`sup.tenant.suspended` with `{ tenant_id: id, reason }` → return Gen_TNT's response.
`TenantsService.reactivate(id, note)` → same shape for `sup.tenant.reactivated`.

## Audit events — RabbitMQ

Per your choice, real RabbitMQ infrastructure is added now (not deferred to `announcements`):

- `domain/ports/event-publisher.port.ts`: `EventPublisher.publish(exchange, routingKey,
  envelope)`, `EventEnvelope { event_id?, event_type, event_version?, occurred_at, tenant_id,
  data }` — mirrors Gen_SLA's proven `IEventPublisher`/`EventEnvelope` shape exactly (same
  field names), since it's already validated across two other Gen_MS libraries.
- `infra/messaging/rabbitmq-bus.ts`: `RabbitMqBus implements EventPublisher`, mirroring
  Gen_SLA's `RabbitMqBus` (`connect()`/`publish()`/reconnect-on-failure). Only `publish` is
  needed here — no `consume()`, this module never reads from the exchange, matching the
  original's fire-and-forget `publishEvent()` calls exactly. No transactional outbox (that
  pattern exists in Gen_SLA because it has local Prisma writes to be transactional with;
  `tenants` has none — publish happens directly after the Gen_TNT call succeeds, same as the
  original source).
- Exchange: `PLATFORM_AUDIT` (topic), routing keys `sup.tenant.created`/`sup.tenant.suspended`/
  `sup.tenant.reactivated` — same names as the original source.
- `docker-compose.yml`: new `rabbitmq` service, AMQP on `5676` (next free after Gen_SEARCH's
  `5675`), management UI on `15676` (next free after Gen_SEARCH's `15675`) — verified against
  every sibling's `docker-compose.yml`. `.env.example` gets `RABBITMQ_URL`.
- Publish failure is non-fatal: caught and logged, does not fail the request (the Gen_TNT
  call already succeeded — the audit trail is best-effort, matching the original and matching
  `dashboard`'s Valkey-failure treatment).

## Error handling

New `AppError` subclasses in `common/errors.ts`:
- `TenantSlugTakenError` (409) — Gen_TNT's create returned 409.
- `TenantNotFoundError` (404) — Gen_TNT's getById/getBySlug/suspend/reactivate returned 404.
- `TenantTransitionConflictError` (409) — Gen_TNT's suspend/reactivate returned 409 (illegal
  state transition).
- `TntClientError` (502) — any other non-2xx from Gen_TNT, or a network failure.

`HttpTntClient` throws a raw error carrying the HTTP status; `TenantsService` maps status →
the specific `AppError` subclass above before it reaches `errorHandler`. `getById`/`getBySlug`
translate Gen_TNT's 404 into a `null` return from the client (not an exception) so the service
can decide whether "not found" is an error (direct lookup) — it always is here, since these
are direct-lookup routes with no "maybe it doesn't exist" caller intent.

## Routes

All gated on `X-Internal-Secret` (same middleware as `dashboard`):

- `POST /api/v1/tenants` → 201 with the created tenant (Gen_TNT itself returns 202; Gen_SUP
  returns 201 since from Gen_SUP's own caller's perspective the call is synchronous and
  complete — Gen_TNT's own provisioning-job asynchrony is an internal detail this module
  doesn't re-expose in this pass).
- `GET /api/v1/tenants/:id` → 200 or 404.
- `GET /api/v1/tenants/by-slug/:slug` → 200 or 404.
- `PATCH /api/v1/tenants/:id/suspend` → 200, body `{ reason }`.
- `PATCH /api/v1/tenants/:id/reactivate` → 200, body `{ note? }`.

## Testing

`TenantsService` unit tests against a mock `TntClientPort` + mock `EventPublisher`:
create success (publishes `sup.tenant.created` with generated `primaryOwnerUserId`), create
slug-taken → `TenantSlugTakenError`, suspend success, suspend on missing tenant →
`TenantNotFoundError`, suspend on illegal transition → `TenantTransitionConflictError`,
reactivate success, and audit-publish throwing does **not** propagate (request still succeeds,
matching the non-fatal treatment above).
