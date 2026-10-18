# Gen_SLA Source Audit Notes

Where the original source lives (copied read-only for reference):
`pre-context/sla-svc/` in this repo — full copy of CPMS-Platform's `apps/sla-svc`,
build artifacts stripped. Treat as reference only; do not build or run it as
part of Gen_SLA's own test suite.

## Reusable as-is (entity-agnostic domain logic)
- `prisma/schema.prisma` models: SlaPolicy, SlaInstance, SlaHistory, SlaEscalation,
  SlaOutboxEvent — already tenant-scoped and entity-agnostic (entityType/entityId/
  slaType are plain strings, not foreign keys into CPMS-specific tables).
- `modules/policies/v1/*`, `modules/instances/v1/*`, `modules/metrics/v1/*` —
  CRUD + lifecycle state machine (ACTIVE→WARNING→BREACHED→RESOLVED/CANCELLED),
  no CPMS-specific assumptions beyond auth (see below).
- `workers/sla-timer.worker.ts` — DB-poll + Valkey SET NX lock, entity-agnostic.
- `infra/messaging/outbox.service.ts` + `outbox.dispatcher.ts` — transactional
  outbox pattern, entity-agnostic (exchange/routingKey/payload are plain strings).

## Dropped / replaced
- JWT/JWKS auth (`middleware/jwt-auth.middleware.ts`) and Redis-backed RBAC
  (`middleware/require-permission.middleware.ts`) — replaced with
  `X-Internal-Secret` header + path-param tenantId, matching every other Gen_MS repo.
- `middleware/tenant-context.ts` — dead code in the ancestor (exported, never
  mounted in `app.ts`). Not ported.
- RLS migration `20260629000000_enable_rls_tenant_isolation` — enables/forces RLS
  keyed on a Postgres GUC (`app.tenant_id`) that nothing in `src/` ever sets.
  Confirmed unwired; dropped. Tenant isolation relies solely on `where: { tenantId }`.
- `workers/sla-consumer.worker.ts` business handlers (ticket/approval/invoice/KT
  event mapping) — hardcode CPMS sibling services' event shapes. Replaced with a
  generic `RabbitMqBus` + caller-supplied routing-key→handler map.
- Notif-svc mirror-publish inside `instances/v1/service.ts` — assumes CPMS
  notif-svc's fanout payload contract. Dropped; caller can subscribe to the
  generic outbox events and forward themselves.
- `@cpms/node-common` (logger, EventBus, JWT verifier, Valkey client, error
  taxonomy) does not exist outside CPMS-Platform — every module that re-exported
  from it is reimplemented locally and directly in this repo.

## Ports (local dev)
Postgres 5440, Valkey 6384, RabbitMQ AMQP 5674, RabbitMQ mgmt UI 15674 — verified
against every sibling Gen_* repo's docker-compose.yml to avoid collisions.
