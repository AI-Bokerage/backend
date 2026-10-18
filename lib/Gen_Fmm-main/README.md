# Gen_FMM

Embeddable feature-gate/entitlement engine for the Gen_MS family — 4-tier resolution (tenant override -> plan entitlement -> flag default -> rollout bucket), catalog/override/telemetry CRUD, L1+L2 cache chain.

## Gen_MS family

| Library | Purpose |
|---|---|
| Gen_AUTH | Authentication |
| Gen_TNT | Tenancy |
| Gen_REG | Registration |
| Gen_ADM | Admin |
| Gen_TBR | (see its own README) |
| Gen_NOTIF | Notifications |
| Gen_USG | Usage metering |
| Gen_FMM | Feature gating / entitlements (this library) |

## Quickstart

```bash
git clone <this repo>
cd Gen_FMM
npm install
docker compose up -d
# Migrations need the bootstrap superuser (genfmm), not genfmm_app — the
# RLS-enable/role-grant migrations require privileges genfmm_app deliberately
# doesn't have. Run the app itself against genfmm_app (see .env.example).
DATABASE_URL=postgresql://genfmm:genfmm@localhost:5441/genfmm npx prisma migrate deploy --schema packages/gen-fmm-starter/prisma/schema.prisma
npm run build --workspaces --if-present
DATABASE_URL=postgresql://genfmm_app:genfmm_app@localhost:5441/genfmm \
GEN_FMM_ADMIN_DATABASE_URL=postgresql://genfmm:genfmm@localhost:5441/genfmm \
REDIS_URL=redis://localhost:6385 \
GEN_FMM_INTERNAL_SECRET=change-me-dev-secret \
node packages/gen-fmm-demo/dist/index.js
curl http://localhost:3700/health
```

See `docs/integration-guide.md` for the full API surface, environment variables, and adapter-swapping guide.
