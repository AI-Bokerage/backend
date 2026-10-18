# Gen_USG

Usage metering — per-tenant counters and resource sets, limit checks (ALLOW/SOFT_WARN/GRACE/BLOCK), daily/monthly rollups, drift reconciliation, and grace-window overage tracking. The seventh Gen_MS library.

## Gen_MS family

| Library | Stack | Status |
|---|---|---|
| Gen_AUTH | Java/Spring Boot | built |
| Gen_TNT | Java/Spring Boot | built |
| Gen_REG | TypeScript/Express/Prisma | built |
| Gen_ADM | Java/Spring Boot | built |
| Gen_TBR | TypeScript/Express/Prisma | built |
| Gen_NOTIF | TypeScript/Express/Prisma | built |
| Gen_USG | TypeScript/Express/Prisma | this repo |

## Local run

```bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-usg-starter/prisma/schema.prisma
npm install
npm run dev --workspace=@gen-ms/gen-usg-demo
```

Migrations run against the superuser role (`genusg`); the app itself connects as the unprivileged `genusg_app` role — see `.env.example`.

## Ports

| Service | Port |
|---|---|
| Gen_AUTH postgres | 5433 |
| Gen_TNT postgres | 5435 |
| Gen_REG postgres | 5436 |
| Gen_TBR postgres | 5437 |
| Gen_NOTIF postgres | 5438 |
| **Gen_USG postgres** | **5439** |
| Gen_AUTH redis | 6380 |
| Gen_TNT redis | 6381 |
| Gen_REG redis | 6382 |
| **Gen_USG redis** | **6383** |
| **Gen_USG demo HTTP** | **3600** |

## Not in scope (v1)

- No message broker, consumers, or DLQ — `increment()`/`check()` are direct in-process async calls.
- No working `ILimitProvider` — bring your own billing/plan-lookup adapter, or `createGenUsg()` throws `GenUsgConfigError` at boot whenever `modules.check`/`modules.summary` is enabled.
- No internal scheduler — `runDailyRollup()`/`runMonthlyRollup()`/`runReconciliationSweep()`/`closeExpiredGraceWindows()` are host-triggered only.
- No tenant registry — every host-triggered sweep takes its own tenant list as an argument.
- No Prometheus/OpenTelemetry — bring your own observability.

See `docs/integration-guide.md` for the full API surface, env var table, and adapter-swap guide.
