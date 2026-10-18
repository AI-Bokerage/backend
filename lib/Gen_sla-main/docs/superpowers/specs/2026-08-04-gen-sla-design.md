# Gen_SLA Design

## Mission

Genericize `CPMS-Platform/apps/sla-svc` into a standalone, configurable npm library (`@gen-ms/gen-sla-starter`) following the established Gen_MS pattern (Gen_REG, Gen_TBR, Gen_FMM, Gen_NOTIF, Gen_USG). Gen_SLA is currently an empty, un-initialized repo.

## Pre-context

`pre-context/sla-svc/` — one-time, gitignored, read-only copy of `CPMS-Platform/apps/sla-svc` (strip `node_modules`/`dist`/`build`). Reference material for genericization only, not a runtime dependency — no live call to CPMS-Platform ever happens. Matches the convention in `Gen_ADM/pre-context/adm-svc` and `Gen_AUTH/pre-context/auth-svc`. A `docs/source-audit-notes.md` (mirroring `Gen_ADM`'s) should capture what's reusable vs. project-specific once copied in.

Ancestor summary (`CPMS-Platform/apps/sla-svc`): Node 20, Express API + standalone worker, Prisma/PostgreSQL, Valkey (distributed lock + cache), RabbitMQ, port 3202. Domain: per-tenant SLA **policies** (duration/warning thresholds per entity type) and SLA **instances** (timer lifecycle `ACTIVE → WARNING → BREACHED → RESOLVED/CANCELLED`). 30s DB-poll timer worker holds a Valkey `SET NX` distributed lock; a transactional outbox publishes lifecycle events to RabbitMQ; 4 consumer queues ingest ticket/approval/invoice/KT events that create or update instances.

## Scope

Full port for v1: policies CRUD, instances (read + lifecycle via worker/consumers), metrics, timer worker, outbox publisher, RabbitMQ consumers, Valkey distributed lock. No feature deferred.

## Architecture

```
Gen_SLA/
├── package.json                 # npm workspace root, "workspaces": ["packages/*"]
├── docker-compose.yml            # postgres:5440, valkey:6384, rabbitmq:5674/15674
├── .gitignore                    # includes pre-context/
├── pre-context/sla-svc/          # gitignored reference copy (see above)
├── docs/
│   ├── source-audit-notes.md
│   ├── integration-guide.md
│   └── superpowers/{specs,plans}/
├── packages/
│   ├── gen-sla-starter/          # @gen-ms/gen-sla-starter — the library
│   │   ├── src/create-gen-sla.ts     # single factory entry point
│   │   ├── src/index.ts              # public barrel
│   │   ├── src/config/env.ts         # Zod env schema + lazy requireEnv()
│   │   ├── src/domain/ports/         # ISlaPolicyRepo, ISlaInstanceRepo, IDistributedLock, IEventPublisher
│   │   ├── src/infra/                # Prisma repo, Valkey lock, RabbitMQ outbox publisher (default adapters)
│   │   ├── src/modules/{policies,instances,metrics}/v1/{controller,service,repo,mapper,schema,routes,types}.ts
│   │   ├── src/workers/{sla-timer,sla-consumer}.worker.ts
│   │   ├── src/common/{errors,logger,middleware}
│   │   └── prisma/{schema.prisma,migrations/}
│   └── gen-sla-demo/              # thin app: createGenSla({}).app.listen(PORT)
└── scripts/smoke-sla.sh
```

Layer shape within each module (`controller/service/repo/mapper/schema/routes/types`) is carried over verbatim from `sla-svc`. No sibling Gen_* library is imported as a code dependency — the only cross-service talk is the `X-Internal-Secret` header on mutating/internal routes (below), never a shared DB.

## Config

Factory + `resolve*` convention, matching Gen_REG:

```ts
export interface GenSlaModulesConfig {
  policies?: boolean;
  instances?: boolean;
  metrics?: boolean;
  worker?: boolean;      // sla-timer.worker
  consumers?: boolean;   // sla-consumer.worker (RabbitMQ)
}

export interface GenSlaConfig {
  policyRepo?: ISlaPolicyRepo;         // override-or-default (Prisma)
  instanceRepo?: ISlaInstanceRepo;     // override-or-default (Prisma)
  lock?: IDistributedLock;             // override-or-default (Valkey SET NX)
  eventPublisher?: IEventPublisher;    // override-or-default (RabbitMQ + transactional outbox)
  internalSecret?: string;             // override-or-default (env GEN_SLA_INTERNAL_SECRET)
  modules?: GenSlaModulesConfig;
}

export function createGenSla(config: GenSlaConfig): { app: Express; worker?: Worker; prisma: PrismaClient };
```

Rules (identical to Gen_REG/Gen_TBR):
- Every dependency is `override ?? buildDefaultFromEnv()`.
- Misconfiguration throws `GenSlaConfigError` synchronously at `createGenSla()` call time, never mid-request.
- A disabled `modules.*` flag means its routes/worker literally don't mount/start.
- `.env` supplies defaults only; anything is overridable via the factory arg (for tests/fakes).

## Auth model

`X-Internal-Secret` header on mutating/internal routes, matching Gen_REG/Gen_TBR/Gen_AUTH. No live call to Gen_AUTH or Gen_TNT for JWT/tenant validation — this differs from the ancestor `sla-svc` (which does JWT+RBAC per request against a shared auth stack) but matches how every other genericized Gen_* library decouples from CPMS-Platform's live auth.

## Data model

Carried over from `pre-context/sla-svc/prisma/schema.prisma`:
- `SlaPolicy` — tenant-scoped: `name`, `entityType`, `slaType`, `durationMins`, `warningMins`, `isEnabled`. Validation: `warningMins < durationMins`.
- `SlaInstance` — links to a policy + external entity id, state machine `ACTIVE → WARNING → BREACHED → RESOLVED/CANCELLED`, timestamps per transition.
- `Outbox` — transactional outbox row per lifecycle event, published to RabbitMQ by a background publisher, marked sent on success.

## API surface

```
POST/GET/PATCH/DELETE /api/v1/sla/policies
GET                    /api/v1/sla/instances
GET                    /api/v1/sla/metrics
```
Zod schemas ported verbatim from `sla-svc` (e.g. `createPolicySchema` with the `warningMins < durationMins` refine).

## Worker & messaging

- `sla-timer.worker.ts` — polls DB every `TIMER_POLL_INTERVAL_MS` (default 30_000), batches `TIMER_BATCH_SIZE` (default 100) instances, holds a Valkey `SET NX` lock so only one worker replica advances timers at a time.
- `sla-consumer.worker.ts` — 4 RabbitMQ queues (ticket/approval/invoice/KT events) create/update instances on inbound domain events.
- Both gated by `modules.worker` / `modules.consumers` — disabled by default in the demo app's minimal config, enabled in the full smoke-test config.

## Ports (local dev)

| Resource | Port | Note |
|---|---|---|
| Postgres | 5440 | next free after Gen_FMM's 5441 in reverse-alloc order; verified against every sibling's docker-compose |
| Valkey/Redis | 6384 | next free after Gen_FMM's 6385 (note: Gen_TNT and Gen_NOTIF both already use 6381 — pre-existing collision in the family, not addressed here) |
| RabbitMQ AMQP | 5674 | only Gen_AUTH uses AMQP today (5673) — next free |
| RabbitMQ mgmt UI | 15674 | pairs with 5674 |

## Testing

Vitest + supertest + Testcontainers (`@testcontainers/postgresql`), matching Gen_REG/Gen_TBR. One `scripts/smoke-sla.sh` exercising policy CRUD + a simulated instance lifecycle end to end.

## Out of scope

- No live integration with CPMS-Platform at runtime, ever — `pre-context/` is input, not a dependency.
- No JWT/JWKS validation — internal-secret header only (see Auth model).
- No changes to CPMS-Platform's own `sla-svc` — it stays as the reference ancestor.
