# Gen_SUP

Generalized super-admin/platform-operations service, genericized from
CPMS-Platform's `sup-svc`. Two modules:

- `packages/gen-sup-starter` (`@gen-ms/gen-sup-starter`) — the reusable
  library, embeddable in any Express/Node app.
- `packages/gen-sup-demo` (`@gen-ms/gen-sup-demo`) — thin reference app.

See `docs/superpowers/specs/2026-08-10-gen-sup-scaffold-and-dashboard-design.md`
for the full design and build order.

## Gen_MS family

| Library | Stack | Status |
|---|---|---|
| Gen_AUTH | Java/Spring Boot | built |
| Gen_TNT | Java/Spring Boot | built |
| Gen_REG | TypeScript/Express/Prisma | built |
| Gen_ADM | Java/Spring Boot | built |
| Gen_TBR | TypeScript/Express/Prisma | built |
| Gen_NOTIF | TypeScript/Express/Prisma | built |
| Gen_USG | TypeScript/Express/Prisma | built |
| Gen_FMM | TypeScript/Express/Prisma | built |
| Gen_SEARCH | TypeScript/Express/Prisma | built |
| Gen_SLA | TypeScript/Express/Prisma | built |
| Gen_SUP | TypeScript/Express/Prisma | this repo — `dashboard`, `tenants`, `feature-flags`, `announcements`, and `analytics` modules built |

## Running it locally

```bash
docker compose up -d   # Postgres on 5443, Valkey on 6387, RabbitMQ on 5676 (mgmt UI on 15676)
npm install
cp .env.example packages/gen-sup-demo/.env
npm run dev             # gen-sup-demo on http://localhost:3900
```
