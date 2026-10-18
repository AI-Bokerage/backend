# Gen_SUP Scaffold + Dashboard Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Gen_SUP npm workspace (matching the Gen_SLA sibling's layout) and ship its first module, `dashboard` — a read-only platform KPI snapshot backed by a consumer-supplied `TenantMetricsPort`, Valkey-cached.

**Architecture:** `packages/gen-sup-starter` (`@gen-ms/gen-sup-starter`) is an Express-based library exporting `createGenSup(config)`, which wires an internal-secret-gated router at `GET /api/v1/dashboard/kpis`. The route's data comes from a host-implemented `TenantMetricsPort` (no Prisma/DB access in this module — see the spec's Gen_TNT-gap finding) rather than a local read-model. `packages/gen-sup-demo` (`@gen-ms/gen-sup-demo`) is a thin reference app supplying a sample in-memory `TenantMetricsPort` implementation.

**Tech Stack:** TypeScript (ESM, `NodeNext`), Express 4, Zod, ioredis (Valkey), Prisma (schema scaffolded now, unused until a later module), Vitest, npm workspaces.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-10-gen-sup-scaffold-and-dashboard-design.md` — every requirement below traces back to it.
- Package scope/naming: `@gen-ms/gen-sup-starter`, `@gen-ms/gen-sup-demo`, root package `gen-sup-workspace` — matches Gen_SLA (the most recently completed TS-stack sibling).
- Auth: every route gated on `X-Internal-Secret` header — no JWT verification inside Gen_SUP itself.
- Ports: Postgres `5443` (next free after Gen_SEARCH's `5442`), Valkey `6387` (next free after Gen_SEARCH's `6386`), demo app `PORT=3900` (next free after Gen_SEARCH's `3800`) — verified against every sibling's `docker-compose.yml`/`.env.example`.
- `dashboard` module has **no local Prisma table and no HTTP client to Gen_TNT** — all tenant metrics come from the host-supplied `TenantMetricsPort`, per the spec's Approach-A decision.
- Cache TTL: 5 minutes (300s), configurable via `DASHBOARD_CACHE_TTL_SEC` env var.
- Error handling: if any `TenantMetricsPort` method throws during `getKpis()`, the whole request fails (502) and nothing is cached. Valkey being unavailable (not the port — the cache itself) is separately treated as non-fatal: the KPIs are computed live instead.
- Response field names (fixed by the approved spec, do not rename): `activeTenants`, `signupsToday`, `signupsThisWeek`, `signupsThisMonth`, `mrr`, `activeTrials`, `trialsEndingIn7d`, `churnedThisMonth`, `pastDueCount`, `suspendedCount`.
- `pre-context/` is gitignored — never `git add` it, delete it once `docs/source-audit-notes.md` is written.

---

### Task 1: Source audit — copy pre-context, write audit notes

**Files:**
- Create: `Gen_SUP/.gitignore`
- Create (untracked, gitignored): `Gen_SUP/pre-context/sup-svc/` (copy of `CPMS-Platform/apps/sup-svc`)
- Create: `Gen_SUP/docs/source-audit-notes.md`

**Interfaces:** None (docs-only task, no code produced yet).

- [ ] **Step 1: Create `.gitignore` before anything else is copied in**

```
node_modules/
dist/
.env
*.log
**/__generated__/
package-lock.json
pre-context/
```

- [ ] **Step 2: Copy the original service in as local reference material**

Run (PowerShell, since the working directory is Windows):
```powershell
Copy-Item -Recurse -Force "C:\Users\naksh\Desktop\Metaupspace\CPMS-Platform\apps\sup-svc" "C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_SUP\pre-context\sup-svc"
```

- [ ] **Step 3: Write `docs/source-audit-notes.md`**

```markdown
# Gen_SUP Source Audit — sup-svc

Source: `CPMS-Platform/apps/sup-svc` (TypeScript/Express/Prisma), copied into
`pre-context/sup-svc` for this audit, deleted afterward (gitignored, never
committed).

## Modules (`src/modules/*/v1/`)

1. **analytics** — `RevenueAnalyticsService.getAnalytics` computes MRR/ARR by
   plan and region, plan distribution, trial-conversion rate, avg
   revenue/tenant. Valkey-cached 5min, no writes.
2. **dashboard** — `DashboardService.getKpis` aggregates 10 parallel repo
   queries into one KPI snapshot (active tenants, signups today/week/month,
   MRR, trials active/ending-in-7d, churn, past-due, suspended). Valkey-cached
   5min. **First Gen_SUP module — see Gen_TNT gap below.**
3. **announcements** — `BroadcasterService.broadcast` publishes a
   `sup.announcement.broadcast` event to the platform-audit exchange. No
   templating, no delivery tracking.
4. **feature-flags** — `FlagService.update` mutates flag overrides, emits
   `fmm.override.changed`. No resolution logic — Gen_FMM already owns that
   (4-tier resolution engine). Gen_SUP's version will be a thin wrapper over
   Gen_FMM, not a reimplementation.
5. **impersonate** — `ImpersonationOrchestrator`: super-admin requests a
   session against ANY tenant, tenant admin grants/denies within 24h, on
   grant mints a real cross-tenant JWT via AUTH-SVC (role via ADM-SVC),
   caches in Valkey, revokes `jti` platform-wide on end/expire. This is the
   cross-tenant case Gen_ADM's own impersonation design doc explicitly
   called out of scope ("a separate spec") — complements, doesn't duplicate.
6. **tenants** — `create.service.ts`/`suspend.service.ts`: zero local state,
   pure orchestration + audit wrapper over TNT-SVC. Gen_TNT already owns
   tenant CRUD + provisioning — Gen_SUP's version will be a thin client +
   audit wrapper, not a reimplementation.
7. **tickets** — `TicketService`: full lifecycle (OPEN→IN_PROGRESS→RESOLVED→
   CLOSED), SLA due-dates, threaded internal/external responses, assignment,
   escalation, bulk-reassign-on-offboarding. The "other side" of what Gen_ADM's
   support-tickets feature explicitly deferred ("SUP-SVC owns everything past
   creation") — much richer than Gen_ADM's tenant-scoped self-service log.

## Gap found: Gen_TNT has no tenant-listing capability

The original dashboard/analytics compute KPIs from a local Prisma read-model
(`supTenantSummary`) kept in sync by paging through TNT-SVC's tenant
**search** API and enriching with BSM-SVC/PPM-SVC billing data. Gen_TNT's
actual HTTP surface today is `POST /tenants`, `GET /tenants/{id}`, and
suspend/reactivate/cancel only — no list/search/count endpoint, no events
published. There is also no Gen_MS sibling for BSM-SVC or PPM-SVC.
Decision (see design spec): `dashboard` uses a consumer-supplied
`TenantMetricsPort` instead of a local read-model or a Gen_TNT client.

## Build order

dashboard → tenants → feature-flags → announcements → analytics → tickets →
impersonate (easy → hard, confirmed with user during brainstorming).
```

- [ ] **Step 4: Delete the local pre-context copy**

```powershell
Remove-Item -Recurse -Force "C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_SUP\pre-context"
```

- [ ] **Step 5: Commit**

```bash
git add .gitignore docs/source-audit-notes.md
git commit -m "docs: copy sup-svc pre-context and write source audit notes"
```

---

### Task 2: Scaffold the npm workspace

**Files:**
- Create: `Gen_SUP/package.json`
- Create: `Gen_SUP/docker-compose.yml`
- Create: `Gen_SUP/.env.example`
- Create: `Gen_SUP/README.md`
- Create: `Gen_SUP/packages/gen-sup-starter/package.json`
- Create: `Gen_SUP/packages/gen-sup-starter/tsconfig.json`
- Create: `Gen_SUP/packages/gen-sup-starter/vitest.config.ts`
- Create: `Gen_SUP/packages/gen-sup-starter/prisma/schema.prisma`
- Create: `Gen_SUP/packages/gen-sup-demo/package.json`
- Create: `Gen_SUP/packages/gen-sup-demo/tsconfig.json`

**Interfaces:** None yet (no source files) — this task only produces installable, buildable-empty packages.

- [ ] **Step 1: Root `package.json`**

```json
{
  "name": "gen-sup-workspace",
  "private": true,
  "workspaces": ["packages/*"],
  "scripts": {
    "test": "npm test --workspace=@gen-ms/gen-sup-starter",
    "build": "npm run build --workspace=@gen-ms/gen-sup-starter && npm run build --workspace=@gen-ms/gen-sup-demo",
    "dev": "npm run dev --workspace=@gen-ms/gen-sup-demo"
  }
}
```

- [ ] **Step 2: `docker-compose.yml`**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: gensup
      POSTGRES_PASSWORD: postgres
    ports:
      - "5443:5432"   # next free after Gen_SEARCH (5442) — verified against every sibling
    volumes:
      - gensup_postgres_data:/var/lib/postgresql/data

  valkey:
    image: valkey/valkey:8-alpine
    ports:
      - "6387:6379"   # next free after Gen_SEARCH (6386)

volumes:
  gensup_postgres_data:
```

- [ ] **Step 3: `.env.example`**

```
DATABASE_URL=postgresql://postgres:postgres@localhost:5443/gensup
GEN_SUP_INTERNAL_SECRET=demo-secret
VALKEY_URL=redis://localhost:6387
PORT=3900
```

- [ ] **Step 4: `packages/gen-sup-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-sup-starter",
  "version": "0.1.0",
  "repository": {
    "type": "git",
    "url": "git+https://github.com/GEN-MS/Gen_SUP.git"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com",
    "access": "restricted"
  },
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": { ".": "./dist/index.js" },
  "files": ["dist", "prisma/schema.prisma"],
  "scripts": {
    "build": "prisma generate && tsc -p tsconfig.json",
    "test": "vitest run",
    "test:watch": "vitest",
    "postinstall": "prisma generate",
    "prisma:generate": "prisma generate"
  },
  "dependencies": {
    "@prisma/client": "^5.19.0",
    "prisma": "^5.19.0",
    "dotenv": "^17.4.2",
    "express": "^4.21.0",
    "express-async-errors": "^3.1.1",
    "helmet": "^8.2.0",
    "cors": "^2.8.6",
    "ioredis": "^5.4.1",
    "pino": "^9.0.0",
    "pino-http": "^10.0.0",
    "zod": "^3.23.0"
  },
  "devDependencies": {
    "@types/cors": "^2.8.19",
    "@types/express": "^4.17.0",
    "@types/node": "^20.19.41",
    "pino-pretty": "^13.1.3",
    "typescript": "^5.7.3",
    "vitest": "^2.0.0"
  }
}
```

- [ ] **Step 5: `packages/gen-sup-starter/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "lib": ["ES2022"],
    "types": ["node"],
    "allowImportingTsExtensions": true,
    "rewriteRelativeImportExtensions": true,
    "outDir": "dist",
    "rootDir": "src",
    "declaration": true,
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "sourceMap": true
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist", "tests", "src/**/*.test.ts"]
}
```

- [ ] **Step 6: `packages/gen-sup-starter/vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {},
});
```

- [ ] **Step 7: `packages/gen-sup-starter/prisma/schema.prisma`** (empty — no models until a later module needs one)

```prisma
// packages/gen-sup-starter/prisma/schema.prisma
generator client {
  provider = "prisma-client-js"
  output   = "../__generated__/prisma"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}
```

- [ ] **Step 8: `packages/gen-sup-demo/package.json`**

```json
{
  "name": "@gen-ms/gen-sup-demo",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc -p tsconfig.json",
    "start": "node dist/index.js",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@gen-ms/gen-sup-starter": "*",
    "dotenv": "^17.4.2"
  },
  "devDependencies": {
    "@types/node": "^20.19.41",
    "supertest": "^7.0.0",
    "tsx": "^4.0.0",
    "typescript": "^5.7.3",
    "vitest": "^2.0.0"
  }
}
```

- [ ] **Step 9: `packages/gen-sup-demo/tsconfig.json`** (same shape as the starter's, `rootDir`/`outDir` unchanged)

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "lib": ["ES2022"],
    "types": ["node"],
    "allowImportingTsExtensions": true,
    "rewriteRelativeImportExtensions": true,
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "sourceMap": true
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **Step 10: `README.md`**

```markdown
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
| Gen_SUP | TypeScript/Express/Prisma | this repo — `dashboard` module in progress |

## Running it locally

```bash
docker compose up -d   # Postgres on 5443, Valkey on 6387
npm install
cp .env.example .env
npm run dev             # gen-sup-demo on http://localhost:3900
```
```

- [ ] **Step 11: Install and verify the workspace resolves**

```bash
npm install
```
Expected: completes without error, `node_modules` created at root (workspaces symlinked).

- [ ] **Step 12: Commit**

```bash
git add package.json docker-compose.yml .env.example README.md packages/gen-sup-starter/package.json packages/gen-sup-starter/tsconfig.json packages/gen-sup-starter/vitest.config.ts packages/gen-sup-starter/prisma/schema.prisma packages/gen-sup-demo/package.json packages/gen-sup-demo/tsconfig.json
git commit -m "feat: scaffold Gen_SUP workspace, Prisma schema, and env config"
```

---

### Task 3: Common infra — errors, logger, env, internal-secret + error-handler middleware

**Files:**
- Create: `packages/gen-sup-starter/src/common/errors.ts`
- Create: `packages/gen-sup-starter/src/common/logger.ts`
- Create: `packages/gen-sup-starter/src/config/env.ts`
- Create: `packages/gen-sup-starter/src/config/env.test.ts`
- Create: `packages/gen-sup-starter/src/middleware/internal-secret.ts`
- Create: `packages/gen-sup-starter/src/middleware/internal-secret.test.ts`
- Create: `packages/gen-sup-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-sup-starter/src/middleware/error-handler.test.ts`

**Interfaces:**
- Produces: `AppError` (base class: `statusCode: number`, `code: string`, `message: string`), `GenSupConfigError`, `TenantMetricsUnavailableError` (502) — all from `common/errors.ts`.
- Produces: `logger` (pino instance) from `common/logger.ts`.
- Produces: `env: { NODE_ENV, LOG_LEVEL, DASHBOARD_CACHE_TTL_SEC }` and `requireEnv(key: string): string` from `config/env.ts`.
- Produces: `internalSecret(secret: string)` (Express middleware factory) from `middleware/internal-secret.ts`.
- Produces: `errorHandler(err, req, res, next): void` from `middleware/error-handler.ts`.

- [ ] **Step 1: Write `common/errors.ts`**

```ts
export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

// Boot-time configuration error — thrown by requireEnv() when createGenSup()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: never thrown during request handling, never reaches
// errorHandler, has no HTTP status code.
export class GenSupConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSupConfigError";
  }
}

export class TenantMetricsUnavailableError extends AppError {
  constructor(cause: unknown) {
    super(502, "TENANT_METRICS_UNAVAILABLE", `TenantMetricsPort failed: ${cause instanceof Error ? cause.message : String(cause)}`);
  }
}
```

- [ ] **Step 2: Write `common/logger.ts`**

```ts
import pino from "pino";
import { env } from "../config/env.ts";

export const logger = pino({
  level: env.LOG_LEVEL,
  transport: env.NODE_ENV === "development" ? { target: "pino-pretty" } : undefined,
});
```

- [ ] **Step 3: Write `config/env.ts`**

```ts
import "dotenv/config";
import { z } from "zod";
import { GenSupConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),
  DASHBOARD_CACHE_TTL_SEC: z.coerce.number().default(300),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it. VALKEY_URL and GEN_SUP_INTERNAL_SECRET are intentionally
// NOT in EnvSchema — they're read through this function instead, only when
// no override was supplied to createGenSup() (see create-gen-sup.ts).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenSupConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
```

- [ ] **Step 4: Write `config/env.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { requireEnv } from "./env.ts";
import { GenSupConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  it("returns the value when the env var is set", () => {
    process.env.SOME_TEST_VAR = "value";
    expect(requireEnv("SOME_TEST_VAR")).toBe("value");
    delete process.env.SOME_TEST_VAR;
  });

  it("throws GenSupConfigError when the env var is missing", () => {
    delete process.env.MISSING_TEST_VAR;
    expect(() => requireEnv("MISSING_TEST_VAR")).toThrow(GenSupConfigError);
  });
});
```

- [ ] **Step 5: Run env tests, verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- env.test`
Expected: 2 passed.

- [ ] **Step 6: Write `middleware/internal-secret.ts`**

```ts
import type { Request, Response, NextFunction } from "express";
import { AppError } from "../common/errors.ts";

class UnauthorizedError extends AppError {
  constructor() {
    super(401, "UNAUTHORIZED", "Missing or invalid X-Internal-Secret header");
  }
}

export function internalSecret(secret: string) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (req.header("X-Internal-Secret") !== secret) {
      next(new UnauthorizedError());
      return;
    }
    next();
  };
}
```

- [ ] **Step 7: Write `middleware/internal-secret.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import type { Request, Response } from "express";
import { internalSecret } from "./internal-secret.ts";

function fakeReq(header?: string): Request {
  return { header: () => header } as unknown as Request;
}

describe("internalSecret", () => {
  it("calls next() with no error when the header matches", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq("s3cret"), {} as Response, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(error) when the header is missing", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq(undefined), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });

  it("calls next(error) when the header doesn't match", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq("wrong"), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });
});
```

- [ ] **Step 8: Write `middleware/error-handler.ts`**

```ts
import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({ error: err.code, message: err.message });
    return;
  }

  if (err instanceof ZodError) {
    res.status(400).json({ error: "VALIDATION_ERROR", message: err.issues.map((i) => i.message).join("; ") });
    return;
  }

  logger.error({ err }, "Unhandled error");
  res.status(500).json({ error: "internal_error", message: "An unexpected error occurred" });
}
```

- [ ] **Step 9: Write `middleware/error-handler.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import type { Response } from "express";
import { ZodError, z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { AppError } from "../common/errors.ts";

function fakeRes(): Response {
  const res = {} as Response;
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  return res;
}

describe("errorHandler", () => {
  it("responds with the AppError's statusCode and code", () => {
    const res = fakeRes();
    errorHandler(new AppError(502, "UPSTREAM_FAILED", "boom"), {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(502);
    expect(res.json).toHaveBeenCalledWith({ error: "UPSTREAM_FAILED", message: "boom" });
  });

  it("responds 400 for a ZodError", () => {
    const res = fakeRes();
    let zodError: ZodError;
    try {
      z.object({ x: z.string() }).parse({});
      throw new Error("expected parse to throw");
    } catch (e) {
      zodError = e as ZodError;
    }
    errorHandler(zodError, {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(400);
  });

  it("responds 500 for an unrecognized error", () => {
    const res = fakeRes();
    errorHandler(new Error("unexpected"), {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(500);
  });
});
```

- [ ] **Step 10: Run all tests, verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: all tests pass (env + internal-secret + error-handler).

- [ ] **Step 11: Commit**

```bash
git add packages/gen-sup-starter/src/common packages/gen-sup-starter/src/config packages/gen-sup-starter/src/middleware
git commit -m "feat: add domain errors, logger, env config, and internal-secret/error middleware"
```

---

### Task 4: Valkey client

**Files:**
- Create: `packages/gen-sup-starter/src/infra/cache/valkey-client.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `createValkeyClient(url: string): Redis` (from `ioredis`) — the raw client, used directly by `DashboardService` for `get`/`set` (mirrors the original `sup-svc` pattern exactly rather than adding an abstraction layer this single module doesn't need).

- [ ] **Step 1: Write `infra/cache/valkey-client.ts`**

```ts
import Redis from "ioredis";

export function createValkeyClient(url: string): Redis {
  return new Redis(url, {
    // Don't crash the whole process if Valkey is briefly unreachable —
    // DashboardService treats cache failures as non-fatal (computes live instead).
    maxRetriesPerRequest: 1,
    retryStrategy: (times) => Math.min(times * 200, 2000),
  });
}
```

- [ ] **Step 2: Commit**

```bash
git add packages/gen-sup-starter/src/infra/cache/valkey-client.ts
git commit -m "feat: add Valkey client"
```

---

### Task 5: Dashboard module — port, service, and tests

**Files:**
- Create: `packages/gen-sup-starter/src/domain/ports/tenant-metrics.port.ts`
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/types.ts`
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/service.ts`
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/service.test.ts`

**Interfaces:**
- Consumes: `AppError`, `TenantMetricsUnavailableError` from `common/errors.ts` (Task 3); `env` from `config/env.ts` (Task 3); `logger` from `common/logger.ts` (Task 3); `Redis` type from `ioredis`.
- Produces: `TenantMetricsPort` interface, `noopTenantMetricsPort: TenantMetricsPort` from `tenant-metrics.port.ts`.
- Produces: `DashboardKpis` type from `types.ts`.
- Produces: `DashboardService` class with `getKpis(forceRefresh?: boolean): Promise<DashboardKpis>` from `service.ts` — consumed by Task 6 (controller) and Task 7 (factory wiring).

- [ ] **Step 1: Write `domain/ports/tenant-metrics.port.ts`**

```ts
export interface TenantMetricsPort {
  countActive(): Promise<number>;
  countSignupsSince(date: Date): Promise<number>;
  sumActiveAndTrialMrr(): Promise<number>;
  countActiveTrials(): Promise<number>;
  countTrialsEndingBetween(start: Date, end: Date): Promise<number>;
  countChurnedSince(date: Date): Promise<number>;
  countByStatus(status: string): Promise<number>;
}

// Default adapter: read-only reporting, so an all-zero snapshot is a safe
// fallback (unlike a mutating flow, which should hard-fail without a real
// dependency). Lets createGenSup() boot with dashboard enabled and no
// TenantMetricsPort supplied yet.
export const noopTenantMetricsPort: TenantMetricsPort = {
  countActive: async () => 0,
  countSignupsSince: async () => 0,
  sumActiveAndTrialMrr: async () => 0,
  countActiveTrials: async () => 0,
  countTrialsEndingBetween: async () => 0,
  countChurnedSince: async () => 0,
  countByStatus: async () => 0,
};
```

- [ ] **Step 2: Write `modules/dashboard/v1/types.ts`**

```ts
export interface DashboardKpis {
  activeTenants: number;
  signupsToday: number;
  signupsThisWeek: number;
  signupsThisMonth: number;
  mrr: number;
  activeTrials: number;
  trialsEndingIn7d: number;
  churnedThisMonth: number;
  pastDueCount: number;
  suspendedCount: number;
  generatedAt: string;
}
```

- [ ] **Step 3: Write the failing test `modules/dashboard/v1/service.test.ts`**

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { DashboardService } from "./service.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";

function fakePort(overrides: Partial<TenantMetricsPort> = {}): TenantMetricsPort {
  return {
    countActive: vi.fn(async () => 10),
    countSignupsSince: vi.fn(async () => 2),
    sumActiveAndTrialMrr: vi.fn(async () => 5000),
    countActiveTrials: vi.fn(async () => 3),
    countTrialsEndingBetween: vi.fn(async () => 1),
    countChurnedSince: vi.fn(async () => 0),
    countByStatus: vi.fn(async () => 0),
    ...overrides,
  };
}

function fakeValkey(store = new Map<string, string>()) {
  return {
    get: vi.fn(async (key: string) => store.get(key) ?? null),
    set: vi.fn(async (key: string, value: string) => {
      store.set(key, value);
      return "OK";
    }),
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-08-10T12:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("DashboardService", () => {
  it("computes KPIs from the port on a cache miss", async () => {
    const port = fakePort();
    const valkey = fakeValkey();
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis.activeTenants).toBe(10);
    expect(kpis.mrr).toBe(5000);
    expect(port.countActive).toHaveBeenCalledTimes(1);
    expect(valkey.set).toHaveBeenCalledTimes(1);
  });

  it("returns the cached value without calling the port on a cache hit", async () => {
    const port = fakePort();
    const store = new Map<string, string>();
    store.set("sup:dashboard:kpi", JSON.stringify({ activeTenants: 99 }));
    const valkey = fakeValkey(store);
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis).toEqual({ activeTenants: 99 });
    expect(port.countActive).not.toHaveBeenCalled();
  });

  it("bypasses the cache when forceRefresh is true", async () => {
    const port = fakePort();
    const store = new Map<string, string>();
    store.set("sup:dashboard:kpi", JSON.stringify({ activeTenants: 99 }));
    const valkey = fakeValkey(store);
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis(true);

    expect(kpis.activeTenants).toBe(10);
    expect(port.countActive).toHaveBeenCalledTimes(1);
  });

  it("throws TenantMetricsUnavailableError and does not cache when the port fails", async () => {
    const port = fakePort({ countActive: vi.fn(async () => { throw new Error("port down"); }) });
    const valkey = fakeValkey();
    const service = new DashboardService(port, valkey as never, 300);

    await expect(service.getKpis()).rejects.toThrow(TenantMetricsUnavailableError);
    expect(valkey.set).not.toHaveBeenCalled();
  });

  it("computes live instead of failing when Valkey itself is unavailable", async () => {
    const port = fakePort();
    const valkey = {
      get: vi.fn(async () => { throw new Error("ECONNREFUSED"); }),
      set: vi.fn(async () => { throw new Error("ECONNREFUSED"); }),
    };
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis.activeTenants).toBe(10);
  });
});
```

- [ ] **Step 4: Run the test, verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- dashboard`
Expected: FAIL — `./service.ts` does not exist yet.

- [ ] **Step 5: Write `modules/dashboard/v1/service.ts`**

```ts
import type { Redis } from "ioredis";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { DashboardKpis } from "./types.ts";

const CACHE_KEY = "sup:dashboard:kpi";

export class DashboardService {
  constructor(
    private readonly port: TenantMetricsPort,
    private readonly valkey: Redis,
    private readonly cacheTtlSec: number,
  ) {}

  async getKpis(forceRefresh = false): Promise<DashboardKpis> {
    if (!forceRefresh) {
      try {
        const cached = await this.valkey.get(CACHE_KEY);
        if (cached) return JSON.parse(cached) as DashboardKpis;
      } catch (err) {
        logger.warn({ err }, "[Dashboard] Valkey read failed — computing live");
      }
    }

    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const weekStart = new Date(now.getTime() - 7 * 86_400_000);
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const in7Days = new Date(now.getTime() + 7 * 86_400_000);

    let results: [number, number, number, number, number, number, number, number, number, number];
    try {
      results = await Promise.all([
        this.port.countActive(),
        this.port.countSignupsSince(todayStart),
        this.port.countSignupsSince(weekStart),
        this.port.countSignupsSince(monthStart),
        this.port.sumActiveAndTrialMrr(),
        this.port.countActiveTrials(),
        this.port.countTrialsEndingBetween(now, in7Days),
        this.port.countChurnedSince(monthStart),
        this.port.countByStatus("PAST_DUE"),
        this.port.countByStatus("SUSPENDED"),
      ]);
    } catch (err) {
      throw new TenantMetricsUnavailableError(err);
    }

    const [
      activeTenants, signupsToday, signupsThisWeek, signupsThisMonth, mrr,
      activeTrials, trialsEndingIn7d, churnedThisMonth, pastDueCount, suspendedCount,
    ] = results;

    const kpis: DashboardKpis = {
      activeTenants,
      signupsToday,
      signupsThisWeek,
      signupsThisMonth,
      mrr,
      activeTrials,
      trialsEndingIn7d,
      churnedThisMonth,
      pastDueCount,
      suspendedCount,
      generatedAt: new Date().toISOString(),
    };

    try {
      await this.valkey.set(CACHE_KEY, JSON.stringify(kpis), "EX", this.cacheTtlSec);
    } catch (err) {
      logger.warn({ err }, "[Dashboard] Valkey write failed — KPIs not cached");
    }

    return kpis;
  }
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- dashboard`
Expected: 5 passed.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sup-starter/src/domain packages/gen-sup-starter/src/modules/dashboard
git commit -m "feat: add TenantMetricsPort and DashboardService with Valkey caching"
```

---

### Task 6: Dashboard module — controller and router

**Files:**
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/schema.ts`
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/controller.ts`
- Create: `packages/gen-sup-starter/src/modules/dashboard/v1/router.ts`

**Interfaces:**
- Consumes: `DashboardService` (Task 5); `internalSecret` from `middleware/internal-secret.ts` (Task 3).
- Produces: `createDashboardRouter(deps: { dashboardService: DashboardService; internalSecretValue: string }): Router` — consumed by Task 7's `create-gen-sup.ts`.

- [ ] **Step 1: Write `modules/dashboard/v1/schema.ts`**

```ts
import { z } from "zod";

export const dashboardKpisQuerySchema = z.object({
  forceRefresh: z
    .enum(["true", "false"])
    .optional()
    .transform((v) => v === "true"),
});
```

- [ ] **Step 2: Write `modules/dashboard/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { dashboardKpisQuerySchema } from "./schema.ts";
import type { DashboardService } from "./service.ts";

export function makeDashboardController(service: DashboardService) {
  return {
    async getKpis(req: Request, res: Response) {
      const { forceRefresh } = dashboardKpisQuerySchema.parse(req.query);
      const kpis = await service.getKpis(forceRefresh);
      res.json(kpis);
    },
  };
}
```

- [ ] **Step 3: Write `modules/dashboard/v1/router.ts`**

```ts
import { Router } from "express";
import { makeDashboardController } from "./controller.ts";
import type { DashboardService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface DashboardRouterDeps {
  dashboardService: DashboardService;
  internalSecretValue: string;
}

export function createDashboardRouter(deps: DashboardRouterDeps): Router {
  const router = Router();
  const controller = makeDashboardController(deps.dashboardService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);
  router.get("/kpis", (req, res, next) => controller.getKpis(req, res).catch(next));

  return router;
}
```

- [ ] **Step 4: Commit**

```bash
git add packages/gen-sup-starter/src/modules/dashboard/v1/schema.ts packages/gen-sup-starter/src/modules/dashboard/v1/controller.ts packages/gen-sup-starter/src/modules/dashboard/v1/router.ts
git commit -m "feat: add dashboard controller and router"
```

---

### Task 7: `createGenSup` factory and public barrel

**Files:**
- Create: `packages/gen-sup-starter/src/create-gen-sup.ts`
- Create: `packages/gen-sup-starter/src/create-gen-sup.test.ts`
- Create: `packages/gen-sup-starter/src/index.ts`

**Interfaces:**
- Consumes: everything produced by Tasks 3–6.
- Produces: `createGenSup(config: GenSupConfig): GenSupInstance` (`{ app: Express }`), `GenSupConfig`, `GenSupModulesConfig` — the public entry point `gen-sup-demo` (Task 8) imports from `@gen-ms/gen-sup-starter`.

- [ ] **Step 1: Write the failing test `create-gen-sup.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import request from "supertest";
import { createGenSup } from "./create-gen-sup.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";

describe("createGenSup", () => {
  it("exposes GET /api/v1/dashboard/kpis gated on X-Internal-Secret", async () => {
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1", // unreachable on purpose — service falls back to live compute
    });

    const unauthorized = await request(app).get("/api/v1/dashboard/kpis");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/dashboard/kpis").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.activeTenants).toBe(0);
  });

  it("exposes GET /health with no auth required, even with dashboard disabled", async () => {
    // dashboard: false means no VALKEY_URL is required to construct the app —
    // this also exercises the modules toggle itself.
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      modules: { dashboard: false },
    });
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });
});
```

- [ ] **Step 2: Add `supertest`/`@types/supertest` to `gen-sup-starter`'s devDependencies**

Edit `packages/gen-sup-starter/package.json` devDependencies to add:
```json
"supertest": "^7.0.0",
"@types/supertest": "^6.0.0",
```
Run: `npm install`

- [ ] **Step 3: Run the test, verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- create-gen-sup`
Expected: FAIL — `./create-gen-sup.ts` does not exist yet.

- [ ] **Step 4: Write `create-gen-sup.ts`**

```ts
import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { env, requireEnv } from "./config/env.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { createValkeyClient } from "./infra/cache/valkey-client.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import { DashboardService } from "./modules/dashboard/v1/service.ts";
import { createDashboardRouter } from "./modules/dashboard/v1/router.ts";

export interface GenSupModulesConfig {
  dashboard?: boolean;
}

export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkeyUrl?: string;
  internalSecret?: string;
  modules?: GenSupModulesConfig;
}

export interface GenSupInstance {
  app: Express;
}

function resolveInternalSecret(override: string | undefined): string {
  return override ?? requireEnv("GEN_SUP_INTERNAL_SECRET");
}

function resolveValkeyUrl(override: string | undefined): string {
  return override ?? requireEnv("VALKEY_URL");
}

export function createGenSup(config: GenSupConfig): GenSupInstance {
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
  };

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const tenantMetricsPort = config.tenantMetricsPort ?? noopTenantMetricsPort;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  if (modules.dashboard) {
    const valkey = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    const dashboardService = new DashboardService(tenantMetricsPort, valkey, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

  app.use(errorHandler);

  return { app };
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- create-gen-sup`
Expected: 2 passed.

- [ ] **Step 6: Write the public barrel `src/index.ts`**

```ts
export { createGenSup } from "./create-gen-sup.ts";
export type { GenSupConfig, GenSupModulesConfig, GenSupInstance } from "./create-gen-sup.ts";

export type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
export { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";

export type { DashboardKpis } from "./modules/dashboard/v1/types.ts";
export { DashboardService } from "./modules/dashboard/v1/service.ts";

export { createValkeyClient } from "./infra/cache/valkey-client.ts";
export { errorHandler } from "./middleware/error-handler.ts";
export { internalSecret } from "./middleware/internal-secret.ts";
export { AppError, GenSupConfigError, TenantMetricsUnavailableError } from "./common/errors.ts";
```

- [ ] **Step 7: Full test run for the starter package**

Run: `npm test --workspace=@gen-ms/gen-sup-starter`
Expected: all tests across every file pass.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sup-starter/src/create-gen-sup.ts packages/gen-sup-starter/src/create-gen-sup.test.ts packages/gen-sup-starter/src/index.ts packages/gen-sup-starter/package.json package-lock.json
git commit -m "feat: add createGenSup factory wiring dashboard module, and public barrel"
```

---

### Task 8: `gen-sup-demo` reference app + smoke script

**Files:**
- Create: `packages/gen-sup-demo/src/sample-tenant-metrics-port.ts`
- Create: `packages/gen-sup-demo/src/index.ts`
- Create: `Gen_SUP/scripts/smoke-dashboard.sh`

**Interfaces:**
- Consumes: `createGenSup`, `TenantMetricsPort` from `@gen-ms/gen-sup-starter` (Task 7).
- Produces: `startDemo(): { app, server }` — a runnable HTTP server for manual/smoke testing.

- [ ] **Step 1: Write `src/sample-tenant-metrics-port.ts`** — a hardcoded in-memory implementation so the demo returns plausible, non-zero numbers instead of the starter's all-zero default

```ts
import type { TenantMetricsPort } from "@gen-ms/gen-sup-starter";

const SAMPLE_TENANTS = [
  { status: "ACTIVE", mrr: 199, provisionedAt: new Date("2026-08-08"), trialEndsAt: null },
  { status: "ACTIVE", mrr: 499, provisionedAt: new Date("2026-06-01"), trialEndsAt: null },
  { status: "TRIAL", mrr: 0, provisionedAt: new Date("2026-08-09"), trialEndsAt: new Date("2026-08-15") },
  { status: "PAST_DUE", mrr: 99, provisionedAt: new Date("2026-05-01"), trialEndsAt: null },
  { status: "SUSPENDED", mrr: 0, provisionedAt: new Date("2026-01-01"), trialEndsAt: null },
];

export const sampleTenantMetricsPort: TenantMetricsPort = {
  async countActive() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE").length;
  },
  async countSignupsSince(date) {
    return SAMPLE_TENANTS.filter((t) => t.provisionedAt >= date).length;
  },
  async sumActiveAndTrialMrr() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE" || t.status === "TRIAL").reduce((sum, t) => sum + t.mrr, 0);
  },
  async countActiveTrials() {
    return SAMPLE_TENANTS.filter((t) => t.status === "TRIAL").length;
  },
  async countTrialsEndingBetween(start, end) {
    return SAMPLE_TENANTS.filter((t) => t.trialEndsAt && t.trialEndsAt >= start && t.trialEndsAt <= end).length;
  },
  async countChurnedSince() {
    return 0;
  },
  async countByStatus(status) {
    return SAMPLE_TENANTS.filter((t) => t.status === status).length;
  },
};
```

- [ ] **Step 2: Write `src/index.ts`**

```ts
import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenSup } from "@gen-ms/gen-sup-starter";
import { sampleTenantMetricsPort } from "./sample-tenant-metrics-port.ts";

const PORT = Number(process.env.PORT ?? 3900);

export function startDemo() {
  const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SUP demo listening on port ${PORT}`);
  });
  return { app, server };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
```

- [ ] **Step 3: Write `scripts/smoke-dashboard.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3900}"
SECRET="${GEN_SUP_INTERNAL_SECRET:-demo-secret}"

echo "GET /health"
curl -sf "$BASE_URL/health" | tee /dev/stderr | grep -q '"status":"ok"'

echo "GET /api/v1/dashboard/kpis without secret (expect 401)"
status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/dashboard/kpis")
[ "$status" = "401" ] || { echo "expected 401, got $status"; exit 1; }

echo "GET /api/v1/dashboard/kpis with secret (expect 200)"
curl -sf -H "X-Internal-Secret: $SECRET" "$BASE_URL/api/v1/dashboard/kpis" | tee /dev/stderr | grep -q '"activeTenants"'

echo "Smoke test passed."
```

- [ ] **Step 4: Manual verification**

```bash
docker compose up -d
cp .env.example .env
npm run dev &
sleep 3
chmod +x scripts/smoke-dashboard.sh
./scripts/smoke-dashboard.sh
```
Expected: "Smoke test passed." printed, no errors. Stop the dev server afterward.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sup-demo/src scripts/smoke-dashboard.sh
git commit -m "feat: add gen-sup-demo app, sample tenant-metrics port, and smoke script"
```

---

### Task 9: Integration guide

**Files:**
- Create: `Gen_SUP/docs/integration-guide.md`

**Interfaces:** None (docs-only).

- [ ] **Step 1: Write `docs/integration-guide.md`**

```markdown
# Gen_SUP Integration Guide

## Install

Not yet published to a registry. Install from a local build until publishing is wired up:
```bash
npm install ../Gen_SUP/packages/gen-sup-starter
```

## Quick start

```ts
import { createGenSup } from "@gen-ms/gen-sup-starter";
import type { TenantMetricsPort } from "@gen-ms/gen-sup-starter";

const myTenantMetricsPort: TenantMetricsPort = {
  countActive: () => myDb.tenants.count({ status: "ACTIVE" }),
  countSignupsSince: (date) => myDb.tenants.count({ provisionedAt: { gte: date } }),
  sumActiveAndTrialMrr: () => myDb.tenants.sumMrr({ status: ["ACTIVE", "TRIAL"] }),
  countActiveTrials: () => myDb.tenants.count({ status: "TRIAL" }),
  countTrialsEndingBetween: (start, end) => myDb.tenants.count({ status: "TRIAL", trialEndsAt: { gte: start, lte: end } }),
  countChurnedSince: (date) => myDb.tenants.count({ status: "CANCELLED", updatedAt: { gte: date } }),
  countByStatus: (status) => myDb.tenants.count({ status }),
};

const { app } = createGenSup({
  tenantMetricsPort: myTenantMetricsPort,
  internalSecret: process.env.GEN_SUP_INTERNAL_SECRET,
  valkeyUrl: process.env.VALKEY_URL,
});

app.listen(3900);
```

If you don't supply `tenantMetricsPort`, Gen_SUP falls back to an all-zero
no-op — the app boots and `/api/v1/dashboard/kpis` responds, but every field
is `0` until you wire up a real implementation.

## Why a port instead of a built-in Gen_TNT client?

Gen_TNT's current HTTP API (`POST /tenants`, `GET /tenants/{id}`,
suspend/reactivate/cancel) has no list/search/count endpoint to aggregate
KPIs from. `TenantMetricsPort` lets you source these numbers however you
actually can today — querying Gen_TNT's own database directly if it's
co-located, a future Gen_TNT list endpoint once one exists, or any other
system of record. See `docs/source-audit-notes.md` for the full finding.

## Auth

Every route requires an `X-Internal-Secret` header matching the
`internalSecret` you configured (or `GEN_SUP_INTERNAL_SECRET` env var).
Gen_SUP does not verify who the caller is beyond that shared secret — your
app is responsible for confirming the request actually comes from an
authenticated super-admin (e.g. via Gen_AUTH) before forwarding it here.

## API reference

### Dashboard — `GET /api/v1/dashboard/kpis`

```
GET /api/v1/dashboard/kpis?forceRefresh=true
X-Internal-Secret: <secret>
```

Response:
```json
{
  "activeTenants": 42,
  "signupsToday": 1,
  "signupsThisWeek": 5,
  "signupsThisMonth": 12,
  "mrr": 8400,
  "activeTrials": 3,
  "trialsEndingIn7d": 1,
  "churnedThisMonth": 0,
  "pastDueCount": 2,
  "suspendedCount": 1,
  "generatedAt": "2026-08-10T12:00:00.000Z"
}
```

Cached in Valkey for `DASHBOARD_CACHE_TTL_SEC` (default 300s). Pass
`forceRefresh=true` to bypass the cache. If any `TenantMetricsPort` method
throws, the request responds `502 TENANT_METRICS_UNAVAILABLE` and nothing is
cached — Valkey itself being unreachable is handled separately and falls
back to a live (uncached) computation instead of failing the request.

## Error codes

| Code | Status | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing/wrong `X-Internal-Secret` |
| `VALIDATION_ERROR` | 400 | Request failed Zod validation |
| `TENANT_METRICS_UNAVAILABLE` | 502 | `TenantMetricsPort` threw during `getKpis()` |
| `internal_error` | 500 | Unhandled error |

## Known limitations

- Only the `dashboard` module exists so far. `tenants`, `feature-flags`,
  `announcements`, `analytics`, `tickets`, and `impersonate` are planned
  (see the design spec for build order).
- No local persistence in this module — `TenantMetricsPort` is the only
  source of truth for tenant metrics.

## Local development

```bash
docker compose up -d   # Postgres on 5443, Valkey on 6387
npm install
cp .env.example .env
npm run dev            # gen-sup-demo on http://localhost:3900
./scripts/smoke-dashboard.sh
```
```

- [ ] **Step 2: Commit**

```bash
git add docs/integration-guide.md
git commit -m "docs: add Gen_SUP integration guide"
```

---

### Task 10: Final review pass

**Files:** Any file from Tasks 1–9, as needed to fix findings.

**Interfaces:** None new — this task only verifies and repairs existing interfaces.

- [ ] **Step 1: Full workspace build**

Run: `npm run build`
Expected: both `gen-sup-starter` and `gen-sup-demo` compile with zero TypeScript errors.

- [ ] **Step 2: Full test suite**

Run: `npm test`
Expected: all tests pass (env, internal-secret, error-handler, dashboard service, create-gen-sup).

- [ ] **Step 3: Re-read every file against the spec and this plan's Global Constraints**

Check specifically:
- Response field names match the spec exactly (`activeTenants`, `signupsToday`, etc — no drift like the original source's `totalActiveTenants`/`failedPaymentsCount` naming).
- No file imports Prisma's generated client anywhere in the `dashboard` module (per the spec's Approach-A decision — Prisma is scaffolded but unused so far).
- `pre-context/` does not exist in the working tree and is not tracked by git (`git status` shows nothing for it; `git log --all -- pre-context` returns nothing).
- Every route other than `/health` passes through `internalSecret`.
- `.env.example` port numbers (`5443`, `6387`, `3900`) match `docker-compose.yml` exactly.

- [ ] **Step 4: Run the demo + smoke script one more time end-to-end**

```bash
docker compose up -d
npm run dev &
sleep 3
./scripts/smoke-dashboard.sh
docker compose down
```
Expected: "Smoke test passed."

- [ ] **Step 5: Fix any findings from Steps 1–4, then commit**

```bash
git add -A
git commit -m "fix: final review pass for Gen_SUP scaffold and dashboard module"
```

If Steps 1–4 found nothing to fix, skip this commit — there's nothing to record.
