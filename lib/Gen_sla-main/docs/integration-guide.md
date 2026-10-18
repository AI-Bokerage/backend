# Gen_SLA Integration Guide

## Install

npm install @gen-ms/gen-sla-starter

## Quick start

    import { createGenSla } from "@gen-ms/gen-sla-starter";
    const { app, worker } = createGenSla({});
    app.listen(3202);

## Configuration

Every field in `GenSlaConfig` follows override-or-default-from-env. Misconfiguration
(a required env var missing for a default adapter that's actually needed) throws
`GenSlaConfigError` synchronously inside `createGenSla()`.

| Env var | Required when | Default |
|---|---|---|
| `DATABASE_URL` | `policyRepo`/`instanceRepo`/`metricsRepo`/`outboxWriter` not overridden | none — required |
| `VALKEY_URL` | `modules.worker` enabled and `lock` not overridden | none — required |
| `RABBITMQ_URL` | `modules.worker` enabled and `eventPublisher` not overridden | none — required |
| `GEN_SLA_INTERNAL_SECRET` | `internalSecret` not passed to config | none — required |
| `PORT` | never (has default) | `3202` |
| `TIMER_POLL_INTERVAL_MS` | never (has default) | `30000` |
| `TIMER_BATCH_SIZE` | never (has default) | `100` |
| `TIMER_LOCK_TTL_S` | never (has default) | `60` |
| `OUTBOX_POLL_INTERVAL_MS` | never (has default) | `5000` |
| `OUTBOX_BATCH_SIZE` | never (has default) | `50` |
| `OUTBOX_MAX_RETRIES` | never (has default) | `3` |

`modules: { policies?, instances?, metrics?, worker? }` — each defaults to `true`.
A disabled module's routes 404 rather than being unmounted silently.

## Auth

Every route requires an `X-Internal-Secret` header matching `internalSecret`
(config override or `GEN_SLA_INTERNAL_SECRET`). `tenantId` is a URL path
segment (`/api/v1/sla/:tenantId/...`), validated as a UUID — not derived from
a token. There is no JWT/JWKS/RBAC layer in this library.

## API reference

### Policies — `/api/v1/sla/:tenantId/policies`

| Method | Path | Body / Query |
|---|---|---|
| POST | `/` | `{ name, entityType, slaType, durationMins, warningMins, isEnabled?, description?, createdBy? }` |
| GET | `/` | `?entityType=&isEnabled=&page=&pageSize=` |
| GET | `/:id` | — |
| PATCH | `/:id` | any subset of the create fields (except entityType/slaType) + `updatedBy?` |
| DELETE | `/:id` | — (409s if active/warning instances still reference the policy) |

### Instances — `/api/v1/sla/:tenantId/instances` (read-only via HTTP)

| Method | Path | Query |
|---|---|---|
| GET | `/` | `?entityType=&entityId=&status=&page=&pageSize=` |
| GET | `/:id` | — |

Instance *lifecycle* (`createFromPolicy`, `createWithFixedDeadline`, `resolveInstances`,
`escalateInstances`, `cancelInstances`) is a library-level API on `InstanceService`,
not exposed over HTTP — a caller wires their own event source (see below) and calls
these methods directly, or via their own thin controller if they need an HTTP path.

### Metrics — `/api/v1/sla/:tenantId/metrics`

| Method | Path | Query |
|---|---|---|
| GET | `/summary` | `?entityType=&from=&to=` |
| GET | `/compliance` | `?entityType=&from=&to=` |
| GET | `/breaches` | `?entityType=&from=&to=` |
| GET | `/trends` | `?entityType=&from=&to=&granularity=day\|week\|month` |

## Wiring your own events

Gen_SLA does not assume any business-event vocabulary. To drive SLA instances
from your own domain events, use the exported `RabbitMqBus` (or your own bus)
plus `InstanceService`:

    import { RabbitMqBus, InstanceService, PrismaInstanceRepo, PrismaPolicyRepo, PrismaOutboxWriter, getPrismaClient } from "@gen-ms/gen-sla-starter";

    const bus = new RabbitMqBus(process.env.RABBITMQ_URL!);
    await bus.connect();
    await bus.assertQueue("my-app.ticket-events", "my-app.dlx");
    await bus.bindQueue("my-app.ticket-events", "my-app.events", "ticket.created");

    const instanceService = new InstanceService(
      new PrismaInstanceRepo(getPrismaClient()),
      new PrismaPolicyRepo(getPrismaClient()),
      new PrismaOutboxWriter(getPrismaClient()),
    );

    await bus.consume("my-app.ticket-events", async (envelope) => {
      await instanceService.createFromPolicy(
        envelope.tenant_id, "TICKET", envelope.data.ticket_id as string,
        "FIRST_RESPONSE", envelope.data, new Date(envelope.occurred_at),
      );
    });

See `packages/gen-sla-demo/src/sample-event-handler.ts` for a minimal illustration.

## Error codes

| Code | Status | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing/wrong `X-Internal-Secret` |
| `TENANT_ID_INVALID` | 400 | `:tenantId` path segment is not a UUID |
| `VALIDATION_ERROR` | 400 | Request body/query failed Zod validation |
| `SLA_POLICY_NOT_FOUND` | 404 | — |
| `SLA_POLICY_CONFLICT` | 409 | An enabled policy for that entityType+slaType already exists |
| `SLA_POLICY_HAS_ACTIVE_INSTANCES` | 409 | Delete blocked — resolve/cancel instances first |
| `SLA_INVALID_TIMING` | 400 | `warningMins >= durationMins` (durationMins=0 is exempt — fixed-deadline policies) |
| `SLA_INSTANCE_NOT_FOUND` | 404 | — |

## Known Limitations

- **The outbox is not fully transactional with instance-state writes.** `transitionToWarning`/`transitionToBreached`/etc. call `updateStatus`, `appendHistory`, and `outbox.enqueue` as separate statements, not inside one DB transaction. A crash between the status update and the outbox enqueue permanently drops that lifecycle event (the instance is already in its new status, so the timer won't re-fire it). Acceptable for most use cases; if you need stronger guarantees, wrap your own transaction around instance mutations upstream of this library, or poll the `metrics`/`instances` endpoints as a reconciliation source of truth instead of relying solely on outbox events.
- **`entityId` must be a UUID.** The Prisma schema types `SlaInstance.entityId` as `@db.Uuid` and the instances list-query schema validates it as one. If your domain's entity IDs aren't UUIDs, map them to a UUID (e.g. a deterministic UUIDv5 derived from your native ID) before calling into this library.

## Local development

    docker compose up -d
    npm install
    npm run dev --workspace=@gen-ms/gen-sla-demo
    ./scripts/smoke-sla.sh

Ports: Postgres `5440`, Valkey `6384`, RabbitMQ AMQP `5674` (mgmt UI `15674`).
