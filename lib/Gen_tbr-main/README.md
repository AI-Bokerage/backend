# Gen_TBR

Tenant branding and custom-domain ownership verification — the fifth Gen_MS library.

## Gen_MS family

| Library | Stack | Status |
|---|---|---|
| Gen_AUTH | Java/Spring Boot | built |
| Gen_TNT | Java/Spring Boot | built |
| Gen_REG | TypeScript/Express/Prisma | built |
| Gen_ADM | Java/Spring Boot | built |
| Gen_TBR | TypeScript/Express/Prisma | this repo |

## Local run

```bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-tbr-starter/prisma/schema.prisma
npm install
npm run build --workspaces --if-present
node packages/gen-tbr-demo/dist/index.js
```

## Ports

| Service | Port |
|---|---|
| Gen_AUTH postgres | 5433 |
| Gen_TNT postgres | 5435 |
| Gen_REG postgres | 5436 |
| **Gen_TBR postgres** | **5437** |
| Gen_TBR demo HTTP | 3400 |

## Not in scope (v1)

- SSL/TLS certificate provisioning or renewal.
- Edge routing / reverse-proxy configuration.
- Tenant-existence checking against Gen_TNT.
- Redis/Valkey or any background worker.
