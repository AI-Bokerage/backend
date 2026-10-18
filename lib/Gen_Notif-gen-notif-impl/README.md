# Gen_NOTIF

Notification delivery — email/webhook/in-app/SMS-port — with host-registered templates, per-user preferences, digest batching, and a self-hosted Socket.IO+Redis realtime gateway. The sixth Gen_MS library.

## Gen_MS family

| Library | Stack | Status |
|---|---|---|
| Gen_AUTH | Java/Spring Boot | built |
| Gen_TNT | Java/Spring Boot | built |
| Gen_REG | TypeScript/Express/Prisma | built |
| Gen_ADM | Java/Spring Boot | built |
| Gen_TBR | TypeScript/Express/Prisma | built |
| Gen_NOTIF | TypeScript/Express/Prisma | this repo |

## Local run

```bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-notif-starter/prisma/schema.prisma
npm install
npm run dev --workspace=@gen-ms/gen-notif-demo
```

## Ports

| Service | Port |
|---|---|
| Gen_AUTH postgres | 5433 |
| Gen_TNT postgres | 5435 |
| Gen_REG postgres | 5436 |
| Gen_TBR postgres | 5437 |
| **Gen_NOTIF postgres** | **5438** |
| Gen_NOTIF redis | 6381 |
| Gen_NOTIF demo HTTP | 3500 |

## Not in scope (v1)

- No RabbitMQ, no outbox, no saga machinery — `notify()` is a direct async function.
- No MongoDB anywhere — all persistence is Postgres/Prisma.
- No working SMS provider — configure your own `smsSender` adapter, or SMS dispatch always fails with `SMS_NOT_CONFIGURED`.
- No internal cron/scheduler — `runDigestSweep()` is host-triggered only.
- No chat/messaging features — that's a distinct concern from notification delivery.

See `docs/integration-guide.md` for the full API surface, env var table, and adapter-swap guide.
