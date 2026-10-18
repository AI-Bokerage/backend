# Gen_TBR Integration Guide

## Embedding in-process

```ts
import { createGenTbr } from "@gen-ms/gen-tbr-starter";

const { app } = createGenTbr({
  internalSecret: process.env.GEN_TBR_INTERNAL_SECRET,
});

hostApp.use("/tbr", app);
```

## Running standalone (HTTP)

```bash
docker compose up -d postgres
npx prisma migrate deploy --schema packages/gen-tbr-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-tbr-demo/dist/index.js
```

## Environment variables

| Var | Required when | Notes |
|---|---|---|
| `DATABASE_URL` | `brandingRepo`/`domainRepo` not overridden | Postgres connection string |
| `GEN_TBR_INTERNAL_SECRET` | `internalSecret` config not set | Gates `/internal` routes and mutating `/api/v1` routes in the demo |
| `GEN_TBR_S3_BUCKET` / `_REGION` / `_ENDPOINT` / `_ACCESS_KEY_ID` / `_SECRET_ACCESS_KEY` | `modules.assets` enabled and `assetStore` not overridden | S3-compatible object storage |
| `GEN_TNT_BASE_URL` | never required | Optional future Gen_TNT webhook target |

## API surface

See design spec `docs/superpowers/specs/2026-07-25-gen-tbr-design.md` §202-231 for the full endpoint table.

**Gen_TBR performs no tenant-existence check. The host must ensure `tenantId` is valid and the caller is authorized.**

**`POST /domains/:id/verify`, `POST /domains/:id/activate`, `POST /domains/:id/detach`, and `POST /domains/:id/primary` all require `{tenantId}` in the request body.** The service validates it against the domain record's own `tenantId` and returns `404` on a mismatch — this stops a caller (past the internal-secret gate) from acting on another tenant's domain by id alone.

**Gen_TBR does not terminate TLS or provision certificates.** It verifies domain ownership only; once a domain is `ACTIVE`, the host's edge layer (Caddy, Cloudflare, nginx, a CDN) is responsible for TLS termination and routing.

## Swapping adapters

- `assetStore`: implement `IAssetStore` (`put`/`get`) for local disk, GCS, Azure Blob, etc.
- `dnsVerifier`: implement `IDnsVerifier` (`resolveTxt`/`resolveCname`) to use a DoH resolver instead of the system resolver.

## Known limitations (v1)

- ASCII hostnames only — no IDN/punycode support.
- No background re-verification scheduler — verification is on-demand only (`POST /domains/:id/verify`).
