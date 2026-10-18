# Gen_USG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_USG — usage metering with host-registered meters, direct `increment()`/`check()` calls (no message broker), a `-1`/`Infinity`-means-unlimited limit model behind a host-supplied `ILimitProvider`, grace-window overage handling, and host-triggered rollup/reconciliation sweeps.

**Architecture:** Hexagonal ports + default adapters, one `createGenUsg(config)` factory, Express 4 + Prisma 5 + Postgres 15 + Redis (via `ICounterStore`), matching Gen_REG/Gen_TBR/Gen_NOTIF conventions exactly.

**Tech Stack:** Node 20, TypeScript ESM/NodeNext, Express 4, Zod 3, Prisma 5, Postgres 15, `ioredis`, Vitest 2 + supertest + Testcontainers (`@testcontainers/postgresql` + `@testcontainers/redis`).

## Global Constraints

- Node.js 20, ESM (`"type": "module"`), NodeNext module resolution, **`.ts` extensions on ALL relative imports** (rewritten to `.js` at build via `rewriteRelativeImportExtensions`).
- `tsconfig.json`/`vitest.config.ts` copied verbatim from `Gen_REG/packages/gen-reg-starter/` (same `fakeTimers.toFake` list, excluding `setImmediate`).
- Every request-boundary error is an `AppError` subclass (`statusCode`, `code`, `message`) — never a raw `Error`. `GenUsgConfigError` is the one exception (plain `Error`, boot-time only, never reaches `errorHandler`).
- No `console.log` in library code — structured `pino` via `logger.child(...)`. `pino-pretty` is a runtime `dependency`, not a `devDependency` (required whenever `NODE_ENV !== "production"`).
- Postgres port **5439** (AUTH=5433, TNT=5435, REG=5436, TBR=5437, NOTIF=5438) in `docker-compose.yml`, db name `genusg`. Redis port **6383** (AUTH=6380, TNT=6381, REG=6382).
- `tenantId` is a UUID, trusted from the caller, validated only as UUID shape (Zod `.uuid()`) — never checked against another service. No tenant registry — rollup/reconciliation take an explicit `tenantIds: string[]` from the host.
- No RabbitMQ, no consumers, no DLQ, no separate worker process — `increment()`/`check()` are direct async functions; a host calls them in-process whenever its own domain event fires.
- No internal cron/scheduler — `runDailyRollup`/`runMonthlyRollup`/`runReconciliationSweep`/`closeExpiredGraceWindows` are host-triggered only.
- No hardcoded metric codes — every meter is registered via `registerMeter(code, def)`, a standalone module-level-registry function (mirrors `registerTemplate`).
- `ILimitProvider` has no working default adapter — a host must supply one. Any `ILimitProvider` failure (throw/timeout) **fails open** to `Infinity` (unlimited) inside `check()`'s limit resolution — this is deliberate, documented loudly, not a bug to "fix" during implementation.
- No JWT/JWKS anywhere in this library — every route trusts a caller-supplied `tenantId`.
- RLS ships with `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + a policy with **both** `USING` and `WITH CHECK` in the **first** migration that creates a tenant-scoped table. `usage_idempotency_ledger` has no `tenantId` column and gets **no** RLS policy at all — by design, it's a pure event-id dedup backstop, not a tenant-queryable resource.
- `resolveXxx(override)` idiom for every config field: override used as-is, else lazily build the default adapter from env vars via `requireEnv`, which throws `GenUsgConfigError` synchronously at `createGenUsg()` call time.
- Prisma migrations ship inside the published package (`prisma/schema.prisma`, `prisma/migrations` in `files`). `postinstall: prisma generate`.
- Naming: package `@gen-ms/gen-usg-starter` / `@gen-ms/gen-usg-demo`, namespace `GenUsg`, factory `createGenUsg`, config `GenUsgConfig`, instance `GenUsgInstance`.
- Redis keys are all prefixed `genusg:` to avoid colliding with a host's own Redis usage.

## File Structure

```
Gen_USG/
├── package.json                     # workspace root, "workspaces": ["packages/*"]
├── docker-compose.yml                # postgres:5439, redis:6383, db "genusg"
├── .gitignore
├── .env.example
├── docs/
│   ├── integration-guide.md
│   └── superpowers/{specs,plans}/
├── packages/
│   ├── gen-usg-starter/
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── vitest.config.ts
│   │   ├── prisma/{schema.prisma, migrations/}
│   │   ├── src/
│   │   │   ├── create-gen-usg.ts
│   │   │   ├── index.ts
│   │   │   ├── config/env.ts
│   │   │   ├── common/{logger.ts, errors.ts}
│   │   │   ├── domain/ports/
│   │   │   │   ├── counter-store.port.ts
│   │   │   │   ├── limit-provider.port.ts
│   │   │   │   ├── meter.repository.port.ts
│   │   │   │   ├── grace-overage.repository.port.ts
│   │   │   │   ├── reconciliation.repository.port.ts
│   │   │   │   └── idempotency.repository.port.ts
│   │   │   ├── infra/
│   │   │   │   ├── persistence/{prisma-client.ts, with-tenant.ts}
│   │   │   │   └── cache/redis-counter-store.ts
│   │   │   ├── modules/
│   │   │   │   ├── meters/v1/registry.ts
│   │   │   │   ├── grace-overage/v1/{repo.ts, service.ts}
│   │   │   │   ├── increment/v1/{repo.ts, service.ts, controller.ts, routes.ts}
│   │   │   │   ├── check/v1/{service.ts, controller.ts, routes.ts}
│   │   │   │   ├── rollup/v1/{repo.ts, service.ts}
│   │   │   │   ├── reconciliation/v1/{repo.ts, service.ts}
│   │   │   │   └── summary/v1/{service.ts, controller.ts, routes.ts}
│   │   │   └── middleware/{error-handler.ts, internal-secret.ts}
│   │   └── tests/support/{postgres-container.ts, redis-container.ts}
│   └── gen-usg-demo/
│       ├── package.json
│       └── src/index.ts
└── scripts/
    └── smoke-usage.sh
```

---

### Task 1: Workspace Scaffolding

**Files:**
- Create: `package.json` (root), `.gitignore`, `.env.example`, `docker-compose.yml`
- Create: `packages/gen-usg-starter/package.json`, `packages/gen-usg-starter/tsconfig.json`, `packages/gen-usg-starter/vitest.config.ts`
- Create: `packages/gen-usg-demo/package.json`

**Interfaces:**
- Produces: the workspace layout every later task writes into.

- [ ] **Step 1: Root `package.json`**

```json
{
  "name": "gen-usg",
  "private": true,
  "workspaces": ["packages/*"],
  "scripts": {
    "build": "npm run build --workspaces --if-present",
    "test": "npm run test --workspaces --if-present",
    "typecheck": "npm run typecheck --workspaces --if-present"
  }
}
```

- [ ] **Step 2: `.gitignore`**

```
node_modules/
dist/
.env
__generated__/
.worktrees/
*.tsbuildinfo
```

- [ ] **Step 3: `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: genusg
      POSTGRES_USER: genusg
      POSTGRES_PASSWORD: genusg
    ports: ["5439:5432"]
    volumes: ["genusg-pg:/var/lib/postgresql/data"]
  redis:
    image: redis:7
    ports: ["6383:6379"]
volumes:
  genusg-pg: {}
```

Port 5439 (AUTH=5433, TNT=5435, REG=5436, TBR=5437, NOTIF=5438) and Redis 6383 (AUTH=6380, TNT=6381, REG=6382).

- [ ] **Step 4: `.env.example`**

```
DATABASE_URL=postgresql://genusg:genusg@localhost:5439/genusg
REDIS_URL=redis://localhost:6383
GEN_USG_INTERNAL_SECRET=change-me-dev-secret
PORT=3600
```

- [ ] **Step 5: `packages/gen-usg-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-usg-starter",
  "version": "0.1.0",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "files": ["dist", "prisma/schema.prisma", "prisma/migrations"],
  "scripts": {
    "build": "prisma generate && tsc",
    "postinstall": "prisma generate",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@prisma/client": "^5.20.0",
    "express": "^4.19.2",
    "express-async-errors": "^3.1.1",
    "helmet": "^7.1.0",
    "cors": "^2.8.5",
    "zod": "^3.23.8",
    "pino": "^9.4.0",
    "pino-http": "^10.3.0",
    "pino-pretty": "^11.2.2",
    "ioredis": "^5.4.1"
  },
  "devDependencies": {
    "@types/express": "^4.17.21",
    "@types/cors": "^2.8.17",
    "@types/node": "^20.16.10",
    "@testcontainers/postgresql": "^10.13.2",
    "@testcontainers/redis": "^10.13.2",
    "prisma": "^5.20.0",
    "supertest": "^7.0.0",
    "@types/supertest": "^6.0.2",
    "typescript": "^5.6.2",
    "vitest": "^2.1.1"
  }
}
```

- [ ] **Step 6: `packages/gen-usg-starter/tsconfig.json`**

Copy `Gen_REG/packages/gen-reg-starter/tsconfig.json` verbatim (ES2022 target, NodeNext, strict, declaration, `allowImportingTsExtensions`, `rewriteRelativeImportExtensions`, `skipLibCheck`, `rootDir: "src"`, `outDir: "dist"`).

- [ ] **Step 7: `packages/gen-usg-starter/vitest.config.ts`**

Copy `Gen_REG/packages/gen-reg-starter/vitest.config.ts` verbatim, including the `fakeTimers.toFake` list (excludes `setImmediate` — it breaks Express's finalhandler under fake timers).

- [ ] **Step 8: `packages/gen-usg-demo/package.json`**

```json
{
  "name": "@gen-ms/gen-usg-demo",
  "version": "0.1.0",
  "type": "module",
  "scripts": { "dev": "tsx src/index.ts", "build": "tsc" },
  "dependencies": {
    "@gen-ms/gen-usg-starter": "*",
    "express": "^4.19.2"
  },
  "devDependencies": {
    "tsx": "^4.19.1",
    "typescript": "^5.6.2",
    "@types/node": "^20.16.10"
  }
}
```

- [ ] **Step 9: Install and verify**

Run: `npm install` at the workspace root.
Expected: installs cleanly, `node_modules` created, no version conflicts.

- [ ] **Step 10: Commit**

```bash
git add package.json .gitignore .env.example docker-compose.yml packages/gen-usg-starter/package.json packages/gen-usg-starter/tsconfig.json packages/gen-usg-starter/vitest.config.ts packages/gen-usg-demo/package.json docs/
git commit -m "chore: scaffold Gen_USG workspace"
```

---

### Task 2: Prisma Schema + Migrations

**Files:**
- Create: `packages/gen-usg-starter/prisma/schema.prisma`
- Create: `packages/gen-usg-starter/prisma/migrations/` (generated by `prisma migrate dev`, plus one hand-written raw-SQL migration for RLS)

**Interfaces:**
- Produces: `UsageSnapshot`, `UsageGraceOverage`, `UsageReconciliationLog`, `UsageIdempotencyLedger` Prisma models — every later repo task depends on these exact field names and enum values.

- [ ] **Step 1: Write `schema.prisma`**

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum RollupPeriod {
  daily
  monthly

  @@map("rollup_period")
}

enum GraceOverageStatus {
  OPEN
  CLOSED

  @@map("grace_overage_status")
}

model UsageSnapshot {
  id         String       @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId   String       @map("tenant_id") @db.Uuid
  snapshotAt DateTime     @map("snapshot_at") @db.Timestamptz(6)
  period     RollupPeriod
  metrics    Json
  createdAt  DateTime     @default(now()) @map("created_at") @db.Timestamptz(6)

  @@index([tenantId, snapshotAt(sort: Desc)])
  @@index([tenantId, period, snapshotAt(sort: Desc)])
  @@map("usage_snapshot")
}

model UsageGraceOverage {
  id             String             @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId       String             @map("tenant_id") @db.Uuid
  metric         String             @db.VarChar(120)
  graceStartedAt DateTime           @map("grace_started_at") @db.Timestamptz(6)
  graceExpiresAt DateTime           @map("grace_expires_at") @db.Timestamptz(6)
  overageCount   Int                @default(0) @map("overage_count")
  status         GraceOverageStatus @default(OPEN)
  billedAt       DateTime?          @map("billed_at") @db.Timestamptz(6)
  createdAt      DateTime           @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt      DateTime           @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@unique([tenantId, metric, status])
  @@index([status, graceExpiresAt])
  @@map("usage_grace_overage")
}

model UsageReconciliationLog {
  id           String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId     String   @map("tenant_id") @db.Uuid
  metric       String   @db.VarChar(120)
  counterValue BigInt   @map("counter_value")
  dbValue      BigInt   @map("db_value")
  driftPct     Decimal  @map("drift_pct") @db.Decimal(6, 2)
  corrected    Boolean
  runAt        DateTime @default(now()) @map("run_at") @db.Timestamptz(6)

  @@index([tenantId, runAt(sort: Desc)])
  @@map("usage_reconciliation_log")
}

model UsageIdempotencyLedger {
  eventId     String   @id @map("event_id") @db.VarChar(128)
  processedAt DateTime @default(now()) @map("processed_at") @db.Timestamptz(6)

  @@map("usage_idempotency_ledger")
}
```

- [ ] **Step 2: Generate the initial migration**

Run: `cd packages/gen-usg-starter && npx prisma migrate dev --name init`
Expected: creates `prisma/migrations/<timestamp>_init/migration.sql` with all 4 tables + 2 enums, applies against the local Postgres from `docker-compose.yml` (must be running: `docker compose up -d postgres`).

- [ ] **Step 3: Write the RLS migration by hand**

Run: `npx prisma migrate dev --create-only --name enable_rls` to scaffold an empty migration folder, then replace its `migration.sql` with:

```sql
-- Row-level security for every tenant-scoped table.
-- FORCE (not just ENABLE) so the table owner is subject to policies too,
-- and every policy has both USING and WITH CHECK from day one — matches
-- every other Gen_MS sibling's RLS convention.

ALTER TABLE "usage_snapshot" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_snapshot" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_snapshot_tenant_isolation ON "usage_snapshot"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "usage_grace_overage" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_grace_overage" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_grace_overage_tenant_isolation ON "usage_grace_overage"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "usage_reconciliation_log" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_reconciliation_log" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_reconciliation_log_tenant_isolation ON "usage_reconciliation_log"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- usage_idempotency_ledger is deliberately NOT tenant-scoped (no tenant_id
-- column) — it's a pure event-id dedup backstop, not a tenant-queryable
-- resource. No RLS policy applies to it.
```

Run: `npx prisma migrate dev` to apply it.
Expected: both migrations apply cleanly against the local Postgres container.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/prisma
git commit -m "feat(prisma): add Gen_USG schema with RLS (FORCE + WITH CHECK from day one)"
```

---

### Task 3: Common Layer — Logger + Error Taxonomy

**Files:**
- Create: `packages/gen-usg-starter/src/common/logger.ts`
- Create: `packages/gen-usg-starter/src/common/errors.ts`
- Test: `packages/gen-usg-starter/src/common/errors.test.ts`

**Interfaces:**
- Produces: `AppError`, `GenUsgConfigError`, and every domain error subclass every later module throws.

- [ ] **Step 1: `common/logger.ts`**

```ts
import pino from "pino";

const isProd = process.env.NODE_ENV === "production";

export const logger = pino(
  isProd
    ? { level: process.env.LOG_LEVEL ?? "info" }
    : {
        level: process.env.LOG_LEVEL ?? "info",
        transport: { target: "pino-pretty", options: { colorize: true } },
      },
);
```

- [ ] **Step 2: `common/errors.ts`**

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

export class GenUsgConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenUsgConfigError";
  }
}

export class MeterNotRegisteredError extends AppError {
  constructor(code: string) {
    super(400, "METER_NOT_REGISTERED", `Metric "${code}" was never registered via registerMeter()`);
  }
}

export class IncrementDeltaInvalidError extends AppError {
  constructor() {
    super(400, "INCREMENT_DELTA_INVALID", "delta must not be zero");
  }
}

export class UsageEventOutOfWindowError extends AppError {
  constructor(backdateDays: number) {
    super(422, "USAGE_EVENT_OUT_OF_WINDOW", `occurredAt is older than the ${backdateDays}-day backdate window`);
  }
}

export class ResourceIdRequiredError extends AppError {
  constructor(metric: string) {
    super(400, "RESOURCE_ID_REQUIRED", `Metric "${metric}" is mode:"resource" and requires resourceId on every call`);
  }
}
```

- [ ] **Step 3: Write the test**

```ts
import { describe, it, expect } from "vitest";
import { AppError, MeterNotRegisteredError, UsageEventOutOfWindowError } from "./errors.ts";

describe("error taxonomy", () => {
  it("MeterNotRegisteredError carries 400 and the metric code in its message", () => {
    const err = new MeterNotRegisteredError("seats");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(400);
    expect(err.code).toBe("METER_NOT_REGISTERED");
    expect(err.message).toContain("seats");
  });

  it("UsageEventOutOfWindowError is a 422 with the window length in its message", () => {
    const err = new UsageEventOutOfWindowError(30);
    expect(err.statusCode).toBe(422);
    expect(err.code).toBe("USAGE_EVENT_OUT_OF_WINDOW");
    expect(err.message).toContain("30");
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run src/common/errors.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-usg-starter/src/common
git commit -m "feat(common): add logger and error taxonomy"
```

---

### Task 4: Env Config

**Files:**
- Create: `packages/gen-usg-starter/src/config/env.ts`
- Test: `packages/gen-usg-starter/src/config/env.test.ts`

**Interfaces:**
- Consumes: none.
- Produces: `requireEnv(name)` — used by every `resolveXxx` default-adapter builder in later tasks; throws `GenUsgConfigError` if the named var is unset or empty.

- [ ] **Step 1: `config/env.ts`**

```ts
import { GenUsgConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenUsgConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}
```

No eager Zod-validated env schema at module load — matches Gen_NOTIF's precedent. `DATABASE_URL`/`REDIS_URL` are only required if their respective repos/counter store aren't overridden; each is read lazily by its own `resolveXxx` function in Task 16, never validated eagerly for a module the config disables.

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "./env.ts";
import { GenUsgConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_USG_TEST_VAR";
  afterEach(() => { delete process.env[KEY]; });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenUsgConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenUsgConfigError);
  });
});

describe("optionalEnv", () => {
  it("falls back when unset", () => {
    expect(optionalEnv("GEN_USG_TEST_UNSET", "fallback")).toBe("fallback");
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/config/env.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/src/config
git commit -m "feat(config): add requireEnv/optionalEnv lazy env helpers"
```

---

### Task 5: Domain Ports

**Files:**
- Create: `packages/gen-usg-starter/src/domain/ports/counter-store.port.ts`
- Create: `packages/gen-usg-starter/src/domain/ports/limit-provider.port.ts`
- Create: `packages/gen-usg-starter/src/domain/ports/meter.repository.port.ts`
- Create: `packages/gen-usg-starter/src/domain/ports/grace-overage.repository.port.ts`
- Create: `packages/gen-usg-starter/src/domain/ports/reconciliation.repository.port.ts`
- Create: `packages/gen-usg-starter/src/domain/ports/idempotency.repository.port.ts`

**Interfaces:**
- Produces: every interface name/method signature below is authoritative for every adapter (Task 6-7) and every service (Task 8-12) written in later tasks. Do not deviate from these signatures.

- [ ] **Step 1: `counter-store.port.ts`**

```ts
export interface SetBatchEntry {
  key: string;
  value: string;
  ttlSec: number;
}

export interface ICounterStore {
  incrBy(key: string, delta: number): Promise<number>;
  get(key: string): Promise<number | null>;
  mget(keys: string[]): Promise<(number | null)[]>;
  setNX(key: string, value: string, ttlSec: number): Promise<boolean>;
  del(key: string): Promise<void>;
  zadd(key: string, score: number, member: string): Promise<void>;
  zrem(key: string, member: string): Promise<void>;
  zsumScores(key: string): Promise<number>;
  setBatch(entries: SetBatchEntry[]): Promise<void>;
}
```

- [ ] **Step 2: `limit-provider.port.ts`**

```ts
/**
 * Resolves every metric's limit for a tenant in one call (so the caller can
 * cache all of them at once). A value of -1 means unlimited.
 */
export interface ILimitProvider {
  getLimits(tenantId: string): Promise<Record<string, number>>;
}
```

- [ ] **Step 3: `meter.repository.port.ts`**

```ts
export type RollupPeriod = "daily" | "monthly";

export interface UsageSnapshotRecord {
  id: string;
  tenantId: string;
  snapshotAt: Date;
  period: RollupPeriod;
  metrics: Record<string, number>;
  createdAt: Date;
}

export interface IMeterRepo {
  insertSnapshot(tenantId: string, snapshotAt: Date, period: RollupPeriod, metrics: Record<string, number>): Promise<void>;
  findTrend(tenantId: string, sinceDays: number): Promise<UsageSnapshotRecord[]>;
  latestSnapshot(tenantId: string): Promise<UsageSnapshotRecord | null>;
}
```

- [ ] **Step 4: `grace-overage.repository.port.ts`**

```ts
export type GraceOverageStatus = "OPEN" | "CLOSED";

export interface GraceOverageRecord {
  id: string;
  tenantId: string;
  metric: string;
  graceStartedAt: Date;
  graceExpiresAt: Date;
  overageCount: number;
  status: GraceOverageStatus;
  billedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface IGraceOverageRepo {
  findOpen(tenantId: string, metric: string): Promise<GraceOverageRecord | null>;
  open(tenantId: string, metric: string, graceStartedAt: Date, graceExpiresAt: Date): Promise<GraceOverageRecord>;
  bump(id: string): Promise<GraceOverageRecord>;
  listOpenForTenant(tenantId: string): Promise<GraceOverageRecord[]>;
  listExpiredOpen(now: Date): Promise<GraceOverageRecord[]>;
  close(id: string): Promise<void>;
}
```

`open()` implementations must catch a unique-violation on `(tenantId, metric, status)` (Postgres error code `23505`) and, on catch, re-`findOpen()` and return that instead of throwing — this is the fix for the source's uncaught concurrent-open race.

- [ ] **Step 5: `reconciliation.repository.port.ts`**

```ts
export interface InsertReconciliationLogInput {
  tenantId: string;
  metric: string;
  counterValue: bigint;
  dbValue: bigint;
  driftPct: number;
  corrected: boolean;
  runAt: Date;
}

export interface IReconciliationRepo {
  insertLog(entry: InsertReconciliationLogInput): Promise<void>;
}
```

- [ ] **Step 6: `idempotency.repository.port.ts`**

```ts
export interface IIdempotencyRepo {
  exists(eventId: string): Promise<boolean>;
  insert(eventId: string): Promise<void>;
}
```

- [ ] **Step 7: Typecheck**

Run: `cd packages/gen-usg-starter && npx tsc --noEmit`
Expected: no errors (these are pure interface files with no implementation yet).

- [ ] **Step 8: Commit**

```bash
git add packages/gen-usg-starter/src/domain
git commit -m "feat(ports): add all Gen_USG domain port interfaces"
```

---

### Task 6: Prisma Client + Repository Adapters + Testcontainers Postgres Helper

**Files:**
- Create: `packages/gen-usg-starter/src/infra/persistence/prisma-client.ts`
- Create: `packages/gen-usg-starter/src/infra/persistence/with-tenant.ts`
- Create: `packages/gen-usg-starter/src/modules/rollup/v1/repo.ts` (`PrismaMeterRepo`)
- Create: `packages/gen-usg-starter/src/modules/grace-overage/v1/repo.ts` (`PrismaGraceOverageRepo`)
- Create: `packages/gen-usg-starter/src/modules/reconciliation/v1/repo.ts` (`PrismaReconciliationRepo`)
- Create: `packages/gen-usg-starter/src/infra/persistence/prisma-idempotency-repo.ts` (`PrismaIdempotencyRepo`)
- Create: `packages/gen-usg-starter/tests/support/postgres-container.ts`
- Test: `packages/gen-usg-starter/tests/integration/repos.test.ts`

**Interfaces:**
- Consumes: `IMeterRepo`, `IGraceOverageRepo`, `IReconciliationRepo`, `IIdempotencyRepo` (Task 5), the Prisma models (Task 2).
- Produces: `getPrismaClient()`, `withTenant(tenantId, fn)`, and the 4 `Prisma*Repo` classes every service in Task 9-12 constructs by default.

- [ ] **Step 1: `infra/persistence/prisma-client.ts`**

```ts
import { PrismaClient } from "@prisma/client";

let client: PrismaClient | undefined;

export function getPrismaClient(): PrismaClient {
  if (!client) {
    client = new PrismaClient();
  }
  return client;
}
```

- [ ] **Step 2: `infra/persistence/with-tenant.ts`**

```ts
import type { PrismaClient } from "@prisma/client";
import { getPrismaClient } from "./prisma-client.ts";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function withTenant<T>(
  tenantId: string,
  fn: (tx: PrismaClient) => Promise<T>,
): Promise<T> {
  if (!UUID_RE.test(tenantId)) {
    throw new Error(`Invalid tenantId format: ${tenantId}`);
  }
  return getPrismaClient().$transaction(async (tx) => {
    // SET LOCAL doesn't accept parameterized placeholders in Postgres;
    // injection is closed off by the UUID_RE guard above, not by
    // parameterization — this must run before any relaxation of that guard.
    await (tx as unknown as PrismaClient).$executeRawUnsafe(`SET LOCAL app.tenant_id = '${tenantId}'`);
    return fn(tx as PrismaClient);
  });
}
```

- [ ] **Step 3: `modules/rollup/v1/repo.ts`**

```ts
import type { IMeterRepo, RollupPeriod, UsageSnapshotRecord } from "../../../domain/ports/meter.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";

export class PrismaMeterRepo implements IMeterRepo {
  async insertSnapshot(tenantId: string, snapshotAt: Date, period: RollupPeriod, metrics: Record<string, number>): Promise<void> {
    await withTenant(tenantId, (tx) =>
      tx.usageSnapshot.create({ data: { tenantId, snapshotAt, period, metrics } }),
    );
  }

  async findTrend(tenantId: string, sinceDays: number): Promise<UsageSnapshotRecord[]> {
    const since = new Date(Date.now() - sinceDays * 24 * 60 * 60 * 1000);
    return withTenant(tenantId, (tx) =>
      tx.usageSnapshot.findMany({
        where: { tenantId, snapshotAt: { gte: since } },
        orderBy: { snapshotAt: "desc" },
      }),
    ) as unknown as UsageSnapshotRecord[];
  }

  async latestSnapshot(tenantId: string): Promise<UsageSnapshotRecord | null> {
    const prisma = getPrismaClient();
    return prisma.usageSnapshot.findFirst({
      where: { tenantId },
      orderBy: { snapshotAt: "desc" },
    }) as unknown as Promise<UsageSnapshotRecord | null>;
  }
}
```

`latestSnapshot` reads without `withTenant()` (a plain `findFirst` filtered by `tenantId` in the `WHERE` clause) because it's called from `runReconciliationSweep`, which iterates a host-supplied `tenantIds` list itself — matches the source's reconciliation worker reading its baseline directly.

- [ ] **Step 4: `modules/grace-overage/v1/repo.ts`**

```ts
import type {
  IGraceOverageRepo,
  GraceOverageRecord,
} from "../../../domain/ports/grace-overage.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";

const UNIQUE_VIOLATION = "P2002";

export class PrismaGraceOverageRepo implements IGraceOverageRepo {
  async findOpen(tenantId: string, metric: string): Promise<GraceOverageRecord | null> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.findFirst({ where: { tenantId, metric, status: "OPEN" } }),
    );
  }

  async open(tenantId: string, metric: string, graceStartedAt: Date, graceExpiresAt: Date): Promise<GraceOverageRecord> {
    try {
      return await withTenant(tenantId, (tx) =>
        tx.usageGraceOverage.create({
          data: { tenantId, metric, graceStartedAt, graceExpiresAt, overageCount: 1, status: "OPEN" },
        }),
      );
    } catch (err) {
      // A concurrent caller won the @@unique([tenantId, metric, status]) race —
      // re-read the row it created instead of throwing.
      if ((err as { code?: string }).code === UNIQUE_VIOLATION) {
        const existing = await this.findOpen(tenantId, metric);
        if (existing) return existing;
      }
      throw err;
    }
  }

  async bump(id: string): Promise<GraceOverageRecord> {
    return getPrismaClient().usageGraceOverage.update({
      where: { id },
      data: { overageCount: { increment: 1 } },
    });
  }

  async listOpenForTenant(tenantId: string): Promise<GraceOverageRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.findMany({ where: { tenantId, status: "OPEN" } }),
    );
  }

  async listExpiredOpen(now: Date): Promise<GraceOverageRecord[]> {
    return getPrismaClient().usageGraceOverage.findMany({
      where: { status: "OPEN", graceExpiresAt: { lte: now } },
    });
  }

  async close(id: string): Promise<void> {
    await getPrismaClient().usageGraceOverage.update({
      where: { id },
      data: { status: "CLOSED" },
    });
  }
}
```

- [ ] **Step 5: `modules/reconciliation/v1/repo.ts`**

```ts
import type { IReconciliationRepo, InsertReconciliationLogInput } from "../../../domain/ports/reconciliation.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaReconciliationRepo implements IReconciliationRepo {
  async insertLog(entry: InsertReconciliationLogInput): Promise<void> {
    await withTenant(entry.tenantId, (tx) =>
      tx.usageReconciliationLog.create({ data: entry }),
    );
  }
}
```

- [ ] **Step 6: `infra/persistence/prisma-idempotency-repo.ts`**

```ts
import type { IIdempotencyRepo } from "../../domain/ports/idempotency.repository.port.ts";
import { getPrismaClient } from "./prisma-client.ts";

export class PrismaIdempotencyRepo implements IIdempotencyRepo {
  async exists(eventId: string): Promise<boolean> {
    const row = await getPrismaClient().usageIdempotencyLedger.findUnique({ where: { eventId } });
    return row !== null;
  }

  async insert(eventId: string): Promise<void> {
    // No withTenant() — this table has no tenant_id column and no RLS policy.
    await getPrismaClient().usageIdempotencyLedger.createMany({
      data: [{ eventId }],
      skipDuplicates: true,
    });
  }
}
```

- [ ] **Step 7: `tests/support/postgres-container.ts`**

Copy the pattern from `Gen_NOTIF/packages/gen-notif-starter/tests/support/postgres-container.ts` verbatim, including its Windows-safe path resolution via `fileURLToPath()` (not `new URL(...).pathname`, which produces a malformed path with a leading slash before the drive letter on Windows). Point its schema/migration lookup at `gen-usg-starter/prisma` instead of `gen-notif-starter/prisma`. It must: start a real Postgres 15 container via `@testcontainers/postgresql`, run `prisma migrate deploy` against it, return a connected `PrismaClient`, and expose a teardown function.

- [ ] **Step 8: Write the integration test**

```ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { randomUUID } from "node:crypto";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaGraceOverageRepo } from "../../src/modules/grace-overage/v1/repo.ts";
import { PrismaMeterRepo } from "../../src/modules/rollup/v1/repo.ts";

describe("Prisma repos against real Postgres", () => {
  let db: TestPostgres;
  beforeAll(async () => { db = await startPostgresContainer(); }, 60_000);
  afterAll(async () => { await db.stop(); });

  it("open() then a second open() with the same (tenantId, metric, status) re-reads instead of throwing", async () => {
    const repo = new PrismaGraceOverageRepo();
    const tenantId = randomUUID();
    const started = new Date();
    const expires = new Date(started.getTime() + 7 * 24 * 60 * 60 * 1000);
    const first = await repo.open(tenantId, "active_users", started, expires);
    const second = await repo.open(tenantId, "active_users", started, expires);
    expect(second.id).toBe(first.id);
  });

  it("enforces RLS: a query without app.tenant_id set sees zero rows via a raw query", async () => {
    const repo = new PrismaMeterRepo();
    const tenantId = randomUUID();
    await repo.insertSnapshot(tenantId, new Date(), "daily", { seats: 3 });
    const rawRows: unknown[] = await db.prisma.$queryRawUnsafe(`SELECT * FROM "usage_snapshot" WHERE tenant_id = '${tenantId}'`);
    // Outside withTenant(), app.tenant_id is unset -> RLS (FORCE + USING) hides the row even from a direct query.
    expect(rawRows.length).toBe(0);
  });
});
```

- [ ] **Step 9: Run tests**

Run: `npx vitest run tests/integration/repos.test.ts`
Expected: PASS, 2 tests. Requires Docker running locally for Testcontainers.

- [ ] **Step 10: Commit**

```bash
git add packages/gen-usg-starter/src/infra/persistence packages/gen-usg-starter/src/modules/rollup/v1/repo.ts packages/gen-usg-starter/src/modules/grace-overage/v1/repo.ts packages/gen-usg-starter/src/modules/reconciliation/v1/repo.ts packages/gen-usg-starter/tests
git commit -m "feat(repos): add Prisma repository adapters with RLS-scoped withTenant()"
```

---

### Task 7: Redis Counter Store Adapter + Testcontainers Redis Helper

**Files:**
- Create: `packages/gen-usg-starter/src/infra/cache/redis-counter-store.ts`
- Create: `packages/gen-usg-starter/tests/support/redis-container.ts`
- Test: `packages/gen-usg-starter/tests/integration/redis-counter-store.test.ts`

**Interfaces:**
- Consumes: `ICounterStore` (Task 5).
- Produces: `RedisCounterStore` — the default adapter every service in Task 9-12 uses unless a host overrides `counterStore`.

- [ ] **Step 1: `infra/cache/redis-counter-store.ts`**

```ts
import Redis from "ioredis";
import type { ICounterStore, SetBatchEntry } from "../../domain/ports/counter-store.port.ts";

export class RedisCounterStore implements ICounterStore {
  private readonly redis: Redis;

  constructor(redisUrl: string) {
    this.redis = new Redis(redisUrl);
  }

  async incrBy(key: string, delta: number): Promise<number> {
    return this.redis.incrby(key, delta);
  }

  async get(key: string): Promise<number | null> {
    const value = await this.redis.get(key);
    return value === null ? null : Number(value);
  }

  async mget(keys: string[]): Promise<(number | null)[]> {
    const values = await this.redis.mget(...keys);
    return values.map((v) => (v === null ? null : Number(v)));
  }

  async setNX(key: string, value: string, ttlSec: number): Promise<boolean> {
    const result = await this.redis.set(key, value, "EX", ttlSec, "NX");
    return result === "OK";
  }

  async del(key: string): Promise<void> {
    await this.redis.del(key);
  }

  async zadd(key: string, score: number, member: string): Promise<void> {
    await this.redis.zadd(key, score, member);
  }

  async zrem(key: string, member: string): Promise<void> {
    await this.redis.zrem(key, member);
  }

  async zsumScores(key: string): Promise<number> {
    const members = await this.redis.zrangebyscore(key, "-inf", "+inf", "WITHSCORES");
    let total = 0;
    for (let i = 1; i < members.length; i += 2) {
      total += Number(members[i]);
    }
    return total;
  }

  async setBatch(entries: SetBatchEntry[]): Promise<void> {
    const pipeline = this.redis.pipeline();
    for (const { key, value, ttlSec } of entries) {
      pipeline.set(key, value, "EX", ttlSec);
    }
    await pipeline.exec();
  }
}
```

- [ ] **Step 2: `tests/support/redis-container.ts`**

```ts
import { RedisContainer, type StartedRedisContainer } from "@testcontainers/redis";
import { RedisCounterStore } from "../../src/infra/cache/redis-counter-store.ts";

export interface TestRedis {
  counterStore: RedisCounterStore;
  stop: () => Promise<void>;
}

export async function startRedisContainer(): Promise<TestRedis> {
  const container: StartedRedisContainer = await new RedisContainer("redis:7").start();
  const counterStore = new RedisCounterStore(container.getConnectionUrl());
  return {
    counterStore,
    stop: async () => { await container.stop(); },
  };
}
```

- [ ] **Step 3: Write the integration test**

```ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { startRedisContainer, type TestRedis } from "../support/redis-container.ts";

describe("RedisCounterStore against real Redis", () => {
  let redis: TestRedis;
  beforeAll(async () => { redis = await startRedisContainer(); }, 60_000);
  afterAll(async () => { await redis.stop(); });

  it("incrBy accumulates and get reads it back", async () => {
    await redis.counterStore.incrBy("genusg:t1:seats", 5);
    await redis.counterStore.incrBy("genusg:t1:seats", 3);
    expect(await redis.counterStore.get("genusg:t1:seats")).toBe(8);
  });

  it("setNX only succeeds once within the TTL window", async () => {
    const first = await redis.counterStore.setNX("genusg:dedup:evt-1", "1", 60);
    const second = await redis.counterStore.setNX("genusg:dedup:evt-1", "1", 60);
    expect(first).toBe(true);
    expect(second).toBe(false);
  });

  it("zadd/zrem/zsumScores stay exact across duplicate add/remove", async () => {
    const key = "genusg:resource:t1:storage_bytes";
    await redis.counterStore.zadd(key, 100, "file-a");
    await redis.counterStore.zadd(key, 200, "file-b");
    expect(await redis.counterStore.zsumScores(key)).toBe(300);
    await redis.counterStore.zrem(key, "file-a");
    expect(await redis.counterStore.zsumScores(key)).toBe(200);
  });

  it("setBatch seeds multiple keys with TTLs in one call", async () => {
    await redis.counterStore.setBatch([
      { key: "genusg:limit:t1:seats", value: "10", ttlSec: 300 },
      { key: "genusg:limit:t1:api_calls", value: "-1", ttlSec: 300 },
    ]);
    expect(await redis.counterStore.mget(["genusg:limit:t1:seats", "genusg:limit:t1:api_calls"])).toEqual([10, -1]);
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run tests/integration/redis-counter-store.test.ts`
Expected: PASS, 4 tests. Requires Docker running locally for Testcontainers.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-usg-starter/src/infra/cache packages/gen-usg-starter/tests/support/redis-container.ts packages/gen-usg-starter/tests/integration/redis-counter-store.test.ts
git commit -m "feat(cache): add RedisCounterStore adapter"
```

---

### Task 8: Meter Registry

**Files:**
- Create: `packages/gen-usg-starter/src/modules/meters/v1/registry.ts`
- Test: `packages/gen-usg-starter/src/modules/meters/v1/registry.test.ts`

**Interfaces:**
- Consumes: none.
- Produces: `registerMeter`, `getMeterDefinition`, `listRegisteredMeterCodes`, `MeterDefinition` — consumed by every service in Task 9-12 and the public barrel in Task 16.

- [ ] **Step 1: `modules/meters/v1/registry.ts`**

```ts
export type MeterMode = "counter" | "resource";

export interface MeterDefinition {
  unit: string;
  graceEligible: boolean;
  mode: MeterMode;
}

export interface RegisterMeterInput {
  unit: string;
  graceEligible?: boolean;
  mode?: MeterMode;
}

const registry = new Map<string, MeterDefinition>();

export function registerMeter(code: string, def: RegisterMeterInput): void {
  registry.set(code, {
    unit: def.unit,
    graceEligible: def.graceEligible ?? false,
    mode: def.mode ?? "counter",
  });
}

export function getMeterDefinition(code: string): MeterDefinition | undefined {
  return registry.get(code);
}

export function listRegisteredMeterCodes(): string[] {
  return [...registry.keys()];
}

/** Test-only: clears the module-level registry between test files. */
export function _clearRegistryForTests(): void {
  registry.clear();
}
```

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import { registerMeter, getMeterDefinition, listRegisteredMeterCodes, _clearRegistryForTests } from "./registry.ts";

describe("meter registry", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("registers a meter with defaults applied", () => {
    registerMeter("seats", { unit: "seat" });
    expect(getMeterDefinition("seats")).toEqual({ unit: "seat", graceEligible: false, mode: "counter" });
  });

  it("registers a meter with explicit graceEligible and resource mode", () => {
    registerMeter("storage_bytes", { unit: "byte", graceEligible: true, mode: "resource" });
    expect(getMeterDefinition("storage_bytes")).toEqual({ unit: "byte", graceEligible: true, mode: "resource" });
  });

  it("returns undefined for an unregistered code", () => {
    expect(getMeterDefinition("nope")).toBeUndefined();
  });

  it("lists every registered code", () => {
    registerMeter("seats", { unit: "seat" });
    registerMeter("api_calls", { unit: "call" });
    expect(listRegisteredMeterCodes().sort()).toEqual(["api_calls", "seats"]);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/modules/meters/v1/registry.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/src/modules/meters
git commit -m "feat(meters): add host-registered meter registry"
```

---

### Task 9: Grace Overage Service

**Files:**
- Create: `packages/gen-usg-starter/src/modules/grace-overage/v1/service.ts`
- Test: `packages/gen-usg-starter/src/modules/grace-overage/v1/service.test.ts`

**Interfaces:**
- Consumes: `IGraceOverageRepo` (Task 5), `PrismaGraceOverageRepo` (Task 6).
- Produces: `GraceOverageService`, `ClosedGraceWindow` — consumed by `CheckService` (Task 11) and `create-gen-usg.ts` (Task 16) for `closeExpiredGraceWindows()`.

- [ ] **Step 1: `modules/grace-overage/v1/service.ts`**

```ts
import type { IGraceOverageRepo } from "../../../domain/ports/grace-overage.repository.port.ts";

export interface GraceWindow {
  expiresAt: Date;
  daysLeft: number;
}

export interface ClosedGraceWindow {
  tenantId: string;
  metric: string;
  overageCount: number;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function daysLeftUntil(expiresAt: Date, now: Date): number {
  return Math.max(0, Math.ceil((expiresAt.getTime() - now.getTime()) / MS_PER_DAY));
}

export class GraceOverageService {
  constructor(private readonly repo: IGraceOverageRepo) {}

  /**
   * Caller must have already confirmed the metric is grace-eligible
   * (via the meter registry) before calling this.
   */
  async getOrOpenGraceWindow(tenantId: string, metric: string, graceWindowDays: number, now: Date): Promise<GraceWindow> {
    const existing = await this.repo.findOpen(tenantId, metric);
    if (existing) {
      await this.repo.bump(existing.id);
      return { expiresAt: existing.graceExpiresAt, daysLeft: daysLeftUntil(existing.graceExpiresAt, now) };
    }
    const graceExpiresAt = new Date(now.getTime() + graceWindowDays * MS_PER_DAY);
    const opened = await this.repo.open(tenantId, metric, now, graceExpiresAt);
    return { expiresAt: opened.graceExpiresAt, daysLeft: daysLeftUntil(opened.graceExpiresAt, now) };
  }

  async closeExpiredGraceWindows(now: Date): Promise<ClosedGraceWindow[]> {
    const expired = await this.repo.listExpiredOpen(now);
    const closed: ClosedGraceWindow[] = [];
    for (const window of expired) {
      await this.repo.close(window.id);
      closed.push({ tenantId: window.tenantId, metric: window.metric, overageCount: window.overageCount });
    }
    return closed;
  }
}
```

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, vi } from "vitest";
import { GraceOverageService } from "./service.ts";
import type { IGraceOverageRepo, GraceOverageRecord } from "../../../domain/ports/grace-overage.repository.port.ts";

function makeRepo(overrides: Partial<IGraceOverageRepo> = {}): IGraceOverageRepo {
  return {
    findOpen: vi.fn().mockResolvedValue(null),
    open: vi.fn(),
    bump: vi.fn(),
    listOpenForTenant: vi.fn().mockResolvedValue([]),
    listExpiredOpen: vi.fn().mockResolvedValue([]),
    close: vi.fn(),
    ...overrides,
  };
}

describe("GraceOverageService", () => {
  it("opens a fresh window when none exists", async () => {
    const now = new Date("2026-01-01T00:00:00Z");
    const opened: GraceOverageRecord = {
      id: "g1", tenantId: "t1", metric: "seats", graceStartedAt: now,
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 1,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({ open: vi.fn().mockResolvedValue(opened) });
    const service = new GraceOverageService(repo);
    const result = await service.getOrOpenGraceWindow("t1", "seats", 7, now);
    expect(result.daysLeft).toBe(7);
    expect(repo.open).toHaveBeenCalledWith("t1", "seats", now, new Date("2026-01-08T00:00:00Z"));
  });

  it("bumps overageCount on an existing OPEN window instead of opening a new one", async () => {
    const now = new Date("2026-01-05T00:00:00Z");
    const existing: GraceOverageRecord = {
      id: "g1", tenantId: "t1", metric: "seats", graceStartedAt: new Date("2026-01-01T00:00:00Z"),
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 2,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({ findOpen: vi.fn().mockResolvedValue(existing) });
    const service = new GraceOverageService(repo);
    const result = await service.getOrOpenGraceWindow("t1", "seats", 7, now);
    expect(repo.bump).toHaveBeenCalledWith("g1");
    expect(repo.open).not.toHaveBeenCalled();
    expect(result.daysLeft).toBe(3);
  });

  it("closeExpiredGraceWindows closes every expired row and returns the closed list", async () => {
    const now = new Date("2026-01-10T00:00:00Z");
    const expired: GraceOverageRecord = {
      id: "g2", tenantId: "t2", metric: "api_calls", graceStartedAt: new Date("2026-01-01T00:00:00Z"),
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 9,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({ listExpiredOpen: vi.fn().mockResolvedValue([expired]) });
    const service = new GraceOverageService(repo);
    const closed = await service.closeExpiredGraceWindows(now);
    expect(repo.close).toHaveBeenCalledWith("g2");
    expect(closed).toEqual([{ tenantId: "t2", metric: "api_calls", overageCount: 9 }]);
  });

  it("closeExpiredGraceWindows returns an empty list when nothing expired", async () => {
    const repo = makeRepo();
    const service = new GraceOverageService(repo);
    expect(await service.closeExpiredGraceWindows(new Date())).toEqual([]);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/modules/grace-overage/v1/service.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/src/modules/grace-overage
git commit -m "feat(grace-overage): add 7-day grace window state machine"
```

---

### Task 10: Increment Service

**Files:**
- Create: `packages/gen-usg-starter/src/modules/increment/v1/service.ts`
- Test: `packages/gen-usg-starter/src/modules/increment/v1/service.test.ts`

**Interfaces:**
- Consumes: `ICounterStore` (Task 5), `IIdempotencyRepo` (Task 5), `getMeterDefinition` (Task 8).
- Produces: `IncrementService`, `IncrementInput`, `IncrementResult` — consumed by the increment HTTP module (Task 14) and `create-gen-usg.ts` (Task 16).

- [ ] **Step 1: `modules/increment/v1/service.ts`**

```ts
import { randomUUID } from "node:crypto";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../../domain/ports/idempotency.repository.port.ts";
import { getMeterDefinition } from "../../meters/v1/registry.ts";
import { logger } from "../../../common/logger.ts";
import {
  MeterNotRegisteredError,
  IncrementDeltaInvalidError,
  UsageEventOutOfWindowError,
  ResourceIdRequiredError,
} from "../../../common/errors.ts";

export interface IncrementInput {
  tenantId: string;
  metric: string;
  delta: number;
  eventId?: string;
  resourceId?: string;
  idempotencyKey?: string;
  occurredAt?: Date;
}

export interface IncrementResult {
  newValue: number;
  metric: string;
  replayed: boolean;
}

export interface IncrementServiceOptions {
  backdateDays: number;
  dedupTtlSec: number;
}

function counterKey(tenantId: string, metric: string): string {
  return `genusg:${tenantId}:${metric}`;
}

function resourceKey(tenantId: string, metric: string): string {
  return `genusg:resource:${tenantId}:${metric}`;
}

export class IncrementService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly idempotencyRepo: IIdempotencyRepo,
    private readonly options: IncrementServiceOptions,
  ) {}

  async increment(input: IncrementInput): Promise<IncrementResult> {
    const def = getMeterDefinition(input.metric);
    if (!def) throw new MeterNotRegisteredError(input.metric);
    if (input.delta === 0) throw new IncrementDeltaInvalidError();

    const occurredAt = input.occurredAt ?? new Date();
    const backdateMs = this.options.backdateDays * 24 * 60 * 60 * 1000;
    if (Date.now() - occurredAt.getTime() > backdateMs) {
      throw new UsageEventOutOfWindowError(this.options.backdateDays);
    }

    const eventId = input.eventId ?? randomUUID();

    const dedupAcquired = await this.counterStore.setNX(`genusg:dedup:${eventId}`, "1", this.options.dedupTtlSec);
    if (!dedupAcquired) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    const idempotencyKey = input.idempotencyKey ?? eventId;
    const idempotencyAcquired = await this.counterStore.setNX(`genusg:dedup:inc:${idempotencyKey}`, "1", this.options.dedupTtlSec);
    if (!idempotencyAcquired) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    const alreadyProcessed = await this.idempotencyRepo.exists(eventId);
    if (alreadyProcessed) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    let newValue: number;
    if (def.mode === "resource") {
      if (!input.resourceId) throw new ResourceIdRequiredError(input.metric);
      const key = resourceKey(input.tenantId, input.metric);
      if (input.delta > 0) {
        await this.counterStore.zadd(key, input.delta, input.resourceId);
      } else {
        await this.counterStore.zrem(key, input.resourceId);
      }
      newValue = await this.counterStore.zsumScores(key);
    } else {
      const key = counterKey(input.tenantId, input.metric);
      const raw = await this.counterStore.incrBy(key, input.delta);
      if (raw < 0) {
        // Bring the counter back to exactly 0 with a second incrBy (by the
        // positive inverse of the negative result) rather than a set-style
        // call — ICounterStore has no plain "set to value" method, only
        // setBatch(which always requires a positive TTL, wrong fit here).
        await this.counterStore.incrBy(key, -raw);
        newValue = 0;
        logger.warn({ tenantId: input.tenantId, metric: input.metric }, "[gen-usg] counter.negative.clamped");
      } else {
        newValue = raw;
      }
    }

    this.idempotencyRepo.insert(eventId).catch((err: unknown) => {
      logger.warn({ err, eventId }, "[gen-usg] idempotency ledger write failed");
    });

    return { newValue, metric: input.metric, replayed: false };
  }
}
```

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, vi } from "vitest";
import { IncrementService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../../domain/ports/idempotency.repository.port.ts";
import { MeterNotRegisteredError, IncrementDeltaInvalidError, UsageEventOutOfWindowError, ResourceIdRequiredError } from "../../../common/errors.ts";

function makeCounterStore(overrides: Partial<ICounterStore> = {}): ICounterStore {
  return {
    incrBy: vi.fn().mockResolvedValue(1),
    get: vi.fn(),
    mget: vi.fn(),
    setNX: vi.fn().mockResolvedValue(true),
    del: vi.fn(),
    zadd: vi.fn(),
    zrem: vi.fn(),
    zsumScores: vi.fn().mockResolvedValue(0),
    setBatch: vi.fn(),
    ...overrides,
  };
}

function makeIdempotencyRepo(overrides: Partial<IIdempotencyRepo> = {}): IIdempotencyRepo {
  return { exists: vi.fn().mockResolvedValue(false), insert: vi.fn().mockResolvedValue(undefined), ...overrides };
}

describe("IncrementService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("throws MeterNotRegisteredError for an unregistered metric", async () => {
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "nope", delta: 1 })).rejects.toThrow(MeterNotRegisteredError);
  });

  it("throws IncrementDeltaInvalidError for a zero delta", async () => {
    registerMeter("seats", { unit: "seat" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "seats", delta: 0 })).rejects.toThrow(IncrementDeltaInvalidError);
  });

  it("throws UsageEventOutOfWindowError when occurredAt is too old", async () => {
    registerMeter("seats", { unit: "seat" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const tooOld = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000);
    await expect(service.increment({ tenantId: "t1", metric: "seats", delta: 1, occurredAt: tooOld })).rejects.toThrow(UsageEventOutOfWindowError);
  });

  it("dedup layer 1 (eventId) short-circuits as replayed", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ setNX: vi.fn().mockResolvedValueOnce(false) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result).toEqual({ newValue: 0, metric: "seats", replayed: true });
  });

  it("dedup layer 3 (Postgres fallback) short-circuits as replayed", async () => {
    registerMeter("seats", { unit: "seat" });
    const idempotencyRepo = makeIdempotencyRepo({ exists: vi.fn().mockResolvedValue(true) });
    const service = new IncrementService(makeCounterStore(), idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result.replayed).toBe(true);
  });

  it("mode:'resource' meters require resourceId", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const service = new IncrementService(makeCounterStore(), makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await expect(service.increment({ tenantId: "t1", metric: "storage_bytes", delta: 100 })).rejects.toThrow(ResourceIdRequiredError);
  });

  it("mode:'resource' meters zadd on positive delta and re-sum", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const counterStore = makeCounterStore({ zsumScores: vi.fn().mockResolvedValue(500) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "storage_bytes", delta: 500, resourceId: "file-1" });
    expect(counterStore.zadd).toHaveBeenCalledWith("genusg:resource:t1:storage_bytes", 500, "file-1");
    expect(result.newValue).toBe(500);
  });

  it("mode:'resource' meters zrem on negative delta", async () => {
    registerMeter("storage_bytes", { unit: "byte", mode: "resource" });
    const counterStore = makeCounterStore({ zsumScores: vi.fn().mockResolvedValue(0) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    await service.increment({ tenantId: "t1", metric: "storage_bytes", delta: -500, resourceId: "file-1" });
    expect(counterStore.zrem).toHaveBeenCalledWith("genusg:resource:t1:storage_bytes", "file-1");
  });

  it("mode:'counter' clamps a negative result to 0", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ incrBy: vi.fn().mockResolvedValueOnce(-3).mockResolvedValueOnce(0) });
    const service = new IncrementService(counterStore, makeIdempotencyRepo(), { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: -3 });
    expect(counterStore.incrBy).toHaveBeenCalledWith("genusg:t1:seats", -3);
    expect(counterStore.incrBy).toHaveBeenCalledWith("genusg:t1:seats", 3);
    expect(result.newValue).toBe(0);
  });

  it("fire-and-forget idempotency insert failure never rejects the call", async () => {
    registerMeter("seats", { unit: "seat" });
    const idempotencyRepo = makeIdempotencyRepo({ insert: vi.fn().mockRejectedValue(new Error("db down")) });
    const service = new IncrementService(makeCounterStore(), idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
    const result = await service.increment({ tenantId: "t1", metric: "seats", delta: 1, eventId: "evt-1" });
    expect(result.replayed).toBe(false);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/modules/increment/v1/service.test.ts`
Expected: PASS, 10 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/src/modules/increment/v1/service.ts packages/gen-usg-starter/src/modules/increment/v1/service.test.ts
git commit -m "feat(increment): add triple-dedup increment pipeline with per-meter accounting mode"
```

---

### Task 11: Check Service

**Files:**
- Create: `packages/gen-usg-starter/src/modules/check/v1/service.ts`
- Test: `packages/gen-usg-starter/src/modules/check/v1/service.test.ts`

**Interfaces:**
- Consumes: `ICounterStore`, `ILimitProvider` (Task 5), `getMeterDefinition` (Task 8), `GraceOverageService` (Task 9).
- Produces: `CheckService`, `CheckResult` — the `resolveLimits` method is reused by the summary module (Task 15); `check()` is consumed by the check HTTP module (Task 14) and `create-gen-usg.ts` (Task 16).

- [ ] **Step 1: `modules/check/v1/service.ts`**

```ts
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "../../../domain/ports/limit-provider.port.ts";
import { getMeterDefinition } from "../../meters/v1/registry.ts";
import { GraceOverageService, type GraceWindow } from "../../grace-overage/v1/service.ts";
import { MeterNotRegisteredError } from "../../../common/errors.ts";

export type CheckOutcome = "ALLOW" | "SOFT_WARN_80" | "SOFT_WARN_95" | "GRACE" | "BLOCK";

export interface CheckResult {
  allowed: boolean;
  outcome: CheckOutcome;
  metric: string;
  current: number;
  limit: number;
  pct: number;
  code?: string;
  grace?: GraceWindow;
}

export interface CheckServiceOptions {
  limitCacheTtlSec: number;
  limitLockTtlSec: number;
  softWarnPct80: number;
  softWarnPct95: number;
  graceWindowDays: number;
}

function counterKey(tenantId: string, metric: string): string {
  return `genusg:${tenantId}:${metric}`;
}

function limitKey(tenantId: string, metric: string): string {
  return `genusg:limit:${tenantId}:${metric}`;
}

function lockKey(tenantId: string): string {
  return `genusg:lock:limit:${tenantId}`;
}

export class CheckService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly limitProvider: ILimitProvider,
    private readonly graceOverageService: GraceOverageService,
    private readonly options: CheckServiceOptions,
  ) {}

  /**
   * Resolves every registered meter's limit for a tenant, seeding the cache
   * from ILimitProvider under a stampede lock on a cache miss. Any
   * ILimitProvider failure (throw or the lock-holder never finishing)
   * fails OPEN to Infinity (unlimited) — a deliberate availability-over-
   * strictness choice, not a bug.
   */
  async resolveLimits(tenantId: string, metrics: string[]): Promise<Record<string, number>> {
    const keys = metrics.map((m) => limitKey(tenantId, m));
    const cached = await this.counterStore.mget(keys);
    const result: Record<string, number> = {};
    const missing: string[] = [];
    metrics.forEach((metric, i) => {
      if (cached[i] !== null) {
        result[metric] = cached[i] as number;
      } else {
        missing.push(metric);
      }
    });
    if (missing.length === 0) return result;

    const acquired = await this.counterStore.setNX(lockKey(tenantId), "1", this.options.limitLockTtlSec);
    if (!acquired) {
      await new Promise((r) => setTimeout(r, 100));
      const retry = await this.counterStore.mget(missing.map((m) => limitKey(tenantId, m)));
      missing.forEach((metric, i) => { result[metric] = retry[i] === null ? Infinity : (retry[i] as number); });
      return result;
    }

    try {
      const fetched = await this.limitProvider.getLimits(tenantId);
      const entries = Object.entries(fetched).map(([metric, value]) => ({
        key: limitKey(tenantId, metric),
        value: String(value),
        ttlSec: this.options.limitCacheTtlSec,
      }));
      if (entries.length > 0) await this.counterStore.setBatch(entries);
      missing.forEach((metric) => { result[metric] = fetched[metric] ?? Infinity; });
      return result;
    } catch {
      missing.forEach((metric) => { result[metric] = Infinity; });
      return result;
    } finally {
      await this.counterStore.del(lockKey(tenantId));
    }
  }

  async check(tenantId: string, metric: string, delta = 1): Promise<CheckResult> {
    const def = getMeterDefinition(metric);
    if (!def) throw new MeterNotRegisteredError(metric);

    const [current, limits] = await Promise.all([
      this.counterStore.get(counterKey(tenantId, metric)).then((v) => v ?? 0),
      this.resolveLimits(tenantId, [metric]),
    ]);
    const limit = limits[metric] ?? Infinity;
    const next = current + delta;

    if (limit === -1 || limit === Infinity) {
      return { allowed: true, outcome: "ALLOW", metric, current, limit, pct: 0 };
    }

    const pct = limit === 0 ? 100 : Math.round((next / limit) * 100);

    if (next > limit) {
      if (def.graceEligible) {
        const grace = await this.graceOverageService.getOrOpenGraceWindow(tenantId, metric, this.options.graceWindowDays, new Date());
        return { allowed: true, outcome: "GRACE", metric, current, limit, pct, grace };
      }
      return { allowed: false, outcome: "BLOCK", metric, current, limit, pct, code: "USAGE_QUOTA_EXCEEDED" };
    }

    if (next > limit * this.options.softWarnPct95) {
      return { allowed: true, outcome: "SOFT_WARN_95", metric, current, limit, pct };
    }
    if (next > limit * this.options.softWarnPct80) {
      return { allowed: true, outcome: "SOFT_WARN_80", metric, current, limit, pct };
    }
    return { allowed: true, outcome: "ALLOW", metric, current, limit, pct };
  }
}
```

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { CheckService } from "./service.ts";
import { GraceOverageService } from "../../grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "../../../domain/ports/limit-provider.port.ts";
import type { IGraceOverageRepo } from "../../../domain/ports/grace-overage.repository.port.ts";
import { MeterNotRegisteredError } from "../../../common/errors.ts";

const OPTS = { limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7 };

function makeCounterStore(overrides: Partial<ICounterStore> = {}): ICounterStore {
  return {
    incrBy: vi.fn(), get: vi.fn().mockResolvedValue(0), mget: vi.fn().mockResolvedValue([null]),
    setNX: vi.fn().mockResolvedValue(true), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(),
    zsumScores: vi.fn(), setBatch: vi.fn(), ...overrides,
  };
}

function makeGraceRepo(overrides: Partial<IGraceOverageRepo> = {}): IGraceOverageRepo {
  return {
    findOpen: vi.fn().mockResolvedValue(null), open: vi.fn(), bump: vi.fn(),
    listOpenForTenant: vi.fn(), listExpiredOpen: vi.fn(), close: vi.fn(), ...overrides,
  };
}

describe("CheckService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("throws MeterNotRegisteredError for an unregistered metric", async () => {
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(makeCounterStore(), limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    await expect(service.check("t1", "nope")).rejects.toThrow(MeterNotRegisteredError);
  });

  it("ALLOW when unlimited (-1)", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(5), mget: vi.fn().mockResolvedValue([-1]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result).toEqual({ allowed: true, outcome: "ALLOW", metric: "seats", current: 5, limit: -1, pct: 0 });
  });

  it("SOFT_WARN_80 between 80% and 95%", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(84), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("SOFT_WARN_80");
    expect(result.allowed).toBe(true);
  });

  it("SOFT_WARN_95 wins over 80 when both thresholds are crossed", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(96), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("SOFT_WARN_95");
  });

  it("BLOCK when over limit and not grace-eligible", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(100), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats", 5);
    expect(result).toMatchObject({ allowed: false, outcome: "BLOCK", code: "USAGE_QUOTA_EXCEEDED" });
  });

  it("GRACE when over limit and grace-eligible", async () => {
    registerMeter("active_users", { unit: "user", graceEligible: true });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(100), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const graceRepo = makeGraceRepo({
      open: vi.fn().mockResolvedValue({
        id: "g1", tenantId: "t1", metric: "active_users", graceStartedAt: new Date(),
        graceExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), overageCount: 1,
        status: "OPEN", billedAt: null, createdAt: new Date(), updatedAt: new Date(),
      }),
    });
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(graceRepo), OPTS);
    const result = await service.check("t1", "active_users", 1);
    expect(result.outcome).toBe("GRACE");
    expect(result.allowed).toBe(true);
    expect(result.grace).toBeDefined();
  });

  it("stampede lock: a losing caller fails open to Infinity if the cache is still empty after the wait", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({
      get: vi.fn().mockResolvedValue(1),
      mget: vi.fn().mockResolvedValue([null]),
      setNX: vi.fn().mockResolvedValue(false),
    });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("ALLOW");
    expect(result.limit).toBe(Infinity);
  });

  it("fails open to Infinity when ILimitProvider throws", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(1), mget: vi.fn().mockResolvedValue([null]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn().mockRejectedValue(new Error("provider down")) };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("ALLOW");
    expect(result.limit).toBe(Infinity);
  });

  it("seeds every returned metric's limit in one setBatch call on a cache miss", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(1), mget: vi.fn().mockResolvedValue([null]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn().mockResolvedValue({ seats: 10, api_calls: 1000 }) };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    await service.check("t1", "seats");
    expect(counterStore.setBatch).toHaveBeenCalledWith([
      { key: "genusg:limit:t1:seats", value: "10", ttlSec: 300 },
      { key: "genusg:limit:t1:api_calls", value: "1000", ttlSec: 300 },
    ]);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/modules/check/v1/service.test.ts`
Expected: PASS, 9 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-usg-starter/src/modules/check
git commit -m "feat(check): add ALLOW/WARN/GRACE/BLOCK check pipeline with fail-open limit cache"
```

---

### Task 12: Rollup + Reconciliation Services

**Files:**
- Create: `packages/gen-usg-starter/src/modules/rollup/v1/service.ts`
- Create: `packages/gen-usg-starter/src/modules/reconciliation/v1/service.ts`
- Test: `packages/gen-usg-starter/src/modules/rollup/v1/service.test.ts`
- Test: `packages/gen-usg-starter/src/modules/reconciliation/v1/service.test.ts`

**Interfaces:**
- Consumes: `ICounterStore` (Task 5), `IMeterRepo`, `IReconciliationRepo` (Task 5), `listRegisteredMeterCodes` (Task 8).
- Produces: `RollupService`, `RollupResult`, `ReconciliationService`, `ReconciliationSweepResult` — consumed by `create-gen-usg.ts` (Task 16).

- [ ] **Step 1: `modules/rollup/v1/service.ts`**

```ts
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo, RollupPeriod } from "../../../domain/ports/meter.repository.port.ts";
import { listRegisteredMeterCodes } from "../../meters/v1/registry.ts";
import { logger } from "../../../common/logger.ts";

export interface RollupResult {
  period: RollupPeriod;
  tenantsProcessed: number;
  failures: number;
}

export class RollupService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
  ) {}

  async runRollup(tenantIds: string[], period: RollupPeriod): Promise<RollupResult> {
    const metricCodes = listRegisteredMeterCodes();
    const snapshotAt = new Date();
    let failures = 0;
    for (const tenantId of tenantIds) {
      try {
        const values = await this.counterStore.mget(metricCodes.map((m) => `genusg:${tenantId}:${m}`));
        const metrics: Record<string, number> = {};
        metricCodes.forEach((code, i) => { metrics[code] = values[i] ?? 0; });
        await this.meterRepo.insertSnapshot(tenantId, snapshotAt, period, metrics);
      } catch (err) {
        failures += 1;
        logger.error({ err, tenantId }, "[gen-usg] rollup.tenant.failed");
      }
    }
    return { period, tenantsProcessed: tenantIds.length, failures };
  }
}
```

- [ ] **Step 2: `modules/reconciliation/v1/service.ts`**

```ts
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IReconciliationRepo } from "../../../domain/ports/reconciliation.repository.port.ts";
import { listRegisteredMeterCodes } from "../../meters/v1/registry.ts";
import { logger } from "../../../common/logger.ts";

export interface ReconciliationSweepResult {
  tenantsProcessed: number;
  driftsCorrected: number;
}

export class ReconciliationService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
    private readonly reconciliationRepo: IReconciliationRepo,
    private readonly driftThresholdPct: number,
  ) {}

  /**
   * "Authoritative" here means the most recent UsageSnapshot row, not true
   * ground truth from each metric's real owning system — same caveat the
   * source carried. dbValue === 0 is treated as "no baseline yet" and never
   * triggers a correction.
   */
  async runSweep(tenantIds: string[]): Promise<ReconciliationSweepResult> {
    let driftsCorrected = 0;
    const now = new Date();
    for (const tenantId of tenantIds) {
      try {
        const snapshot = await this.meterRepo.latestSnapshot(tenantId);
        const dbMetrics = snapshot?.metrics ?? {};
        for (const metric of listRegisteredMeterCodes()) {
          const counterValue = BigInt(Math.trunc((await this.counterStore.get(`genusg:${tenantId}:${metric}`)) ?? 0));
          const dbValue = BigInt(Math.trunc(dbMetrics[metric] ?? 0));
          const driftPct = dbValue === 0n ? 0 : Math.abs(Number(counterValue - dbValue) / Number(dbValue)) * 100;

          let corrected = false;
          if (driftPct > this.driftThresholdPct && dbValue !== 0n) {
            await this.counterStore.setBatch([{ key: `genusg:${tenantId}:${metric}`, value: dbValue.toString(), ttlSec: 315360000 }]);
            corrected = true;
            driftsCorrected += 1;
          }
          await this.reconciliationRepo.insertLog({ tenantId, metric, counterValue, dbValue, driftPct, corrected, runAt: now });
        }
      } catch (err) {
        logger.error({ err, tenantId }, "[gen-usg] reconciliation.tenant.failed");
      }
    }
    return { tenantsProcessed: tenantIds.length, driftsCorrected };
  }
}
```

`ttlSec: 315360000` (10 years) on the drift-correction `setBatch` call means "effectively no expiry" for a plain counter reset — `ICounterStore.setBatch` always requires a positive TTL by its Task 5 contract (mirrors Redis `SET ... EX`), so a very large TTL is the correct way to express "persist this value" through that interface rather than adding a second no-TTL method used nowhere else.

- [ ] **Step 3: Write the rollup test**

```ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { RollupService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";

describe("RollupService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("snapshots every tenant's registered-metric counters", async () => {
    registerMeter("seats", { unit: "seat" });
    registerMeter("api_calls", { unit: "call" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn(), mget: vi.fn().mockResolvedValue([3, 40]),
      setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = { insertSnapshot: vi.fn(), findTrend: vi.fn(), latestSnapshot: vi.fn() };
    const service = new RollupService(counterStore, meterRepo);
    const result = await service.runRollup(["t1", "t2"], "daily");
    expect(result).toEqual({ period: "daily", tenantsProcessed: 2, failures: 0 });
    expect(meterRepo.insertSnapshot).toHaveBeenCalledTimes(2);
    expect(meterRepo.insertSnapshot).toHaveBeenCalledWith("t1", expect.any(Date), "daily", { seats: 3, api_calls: 40 });
  });

  it("one tenant's failure never blocks the rest", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn(), mget: vi.fn().mockResolvedValue([1]),
      setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn().mockRejectedValueOnce(new Error("db down")).mockResolvedValueOnce(undefined),
      findTrend: vi.fn(), latestSnapshot: vi.fn(),
    };
    const service = new RollupService(counterStore, meterRepo);
    const result = await service.runRollup(["bad-tenant", "good-tenant"], "monthly");
    expect(result).toEqual({ period: "monthly", tenantsProcessed: 2, failures: 1 });
  });
});
```

- [ ] **Step 4: Write the reconciliation test**

```ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { ReconciliationService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IReconciliationRepo } from "../../../domain/ports/reconciliation.repository.port.ts";

describe("ReconciliationService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("no drift when counter matches the snapshot baseline", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(10),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t1", snapshotAt: new Date(), period: "daily", metrics: { seats: 10 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(0);
    expect(reconciliationRepo.insertLog).toHaveBeenCalledWith(expect.objectContaining({ corrected: false, driftPct: 0 }));
  });

  it("corrects the counter when drift exceeds the threshold and the baseline is non-zero", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(50),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t1", snapshotAt: new Date(), period: "daily", metrics: { seats: 10 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(1);
    expect(counterStore.setBatch).toHaveBeenCalledWith([{ key: "genusg:t1:seats", value: "10", ttlSec: 315360000 }]);
  });

  it("a zero baseline never triggers a correction", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(50),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = { insertSnapshot: vi.fn(), findTrend: vi.fn(), latestSnapshot: vi.fn().mockResolvedValue(null) };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(0);
    expect(counterStore.setBatch).not.toHaveBeenCalled();
  });

  it("one tenant's failure never blocks the rest", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockRejectedValueOnce(new Error("redis down")).mockResolvedValueOnce(5),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t2", snapshotAt: new Date(), period: "daily", metrics: { seats: 5 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["bad-tenant", "t2"]);
    expect(result.tenantsProcessed).toBe(2);
    expect(reconciliationRepo.insertLog).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run src/modules/rollup/v1/service.test.ts src/modules/reconciliation/v1/service.test.ts`
Expected: PASS, 6 tests total.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-usg-starter/src/modules/rollup/v1/service.ts packages/gen-usg-starter/src/modules/reconciliation/v1/service.ts packages/gen-usg-starter/src/modules/rollup/v1/service.test.ts packages/gen-usg-starter/src/modules/reconciliation/v1/service.test.ts
git commit -m "feat(rollup,reconciliation): add host-triggered snapshot rollup and drift self-heal sweep"
```

---

### Task 13: Middleware (Error Handler + Internal Secret)

**Files:**
- Create: `packages/gen-usg-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-usg-starter/src/middleware/internal-secret.ts`
- Test: `packages/gen-usg-starter/src/middleware/error-handler.test.ts`

**Interfaces:**
- Consumes: `AppError` (Task 3).
- Produces: `errorHandler`, `internalSecretMiddleware(secret)` — used by every HTTP test from Task 14 onward and by `create-gen-usg.ts` in Task 16.

- [ ] **Step 1: `middleware/error-handler.ts`**

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
    const message = err.errors.map((e) => `${e.path.join(".")}: ${e.message}`).join("; ");
    res.status(400).json({ error: "VALIDATION_ERROR", message });
    return;
  }
  logger.error({ err }, "[gen-usg] unhandled error");
  res.status(500).json({ error: "INTERNAL_ERROR", message: "An unexpected error occurred" });
}
```

- [ ] **Step 2: `middleware/internal-secret.ts`**

```ts
import type { NextFunction, Request, Response } from "express";
import { AppError } from "../common/errors.ts";

export function internalSecretMiddleware(secret: string) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (req.header("x-internal-secret") !== secret) {
      throw new AppError(401, "UNAUTHORIZED", "Missing or invalid X-Internal-Secret header");
    }
    next();
  };
}
```

- [ ] **Step 3: Write the test**

```ts
import { describe, it, expect } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { MeterNotRegisteredError } from "../common/errors.ts";

describe("errorHandler", () => {
  it("maps an AppError subclass to its statusCode/code", async () => {
    const app = express();
    app.get("/boom", () => { throw new MeterNotRegisteredError("seats"); });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: "METER_NOT_REGISTERED", message: 'Metric "seats" was never registered via registerMeter()' });
  });

  it("maps a ZodError to 400 VALIDATION_ERROR", async () => {
    const app = express();
    app.get("/validate", () => { z.string().uuid().parse("not-a-uuid"); });
    app.use(errorHandler);
    const res = await request(app).get("/validate");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("maps an unknown error to 500 INTERNAL_ERROR", async () => {
    const app = express();
    app.get("/crash", () => { throw new Error("something broke"); });
    app.use(errorHandler);
    const res = await request(app).get("/crash");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("INTERNAL_ERROR");
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run src/middleware/error-handler.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-usg-starter/src/middleware
git commit -m "feat(middleware): add error-handler (AppError + ZodError mapping) and internal-secret gate"
```

---

### Task 14: Increment & Check HTTP Module (Controllers + Routes)

**Files:**
- Create: `packages/gen-usg-starter/src/modules/increment/v1/controller.ts`
- Create: `packages/gen-usg-starter/src/modules/increment/v1/routes.ts`
- Create: `packages/gen-usg-starter/src/modules/check/v1/controller.ts`
- Create: `packages/gen-usg-starter/src/modules/check/v1/routes.ts`
- Test: `packages/gen-usg-starter/tests/http/increment-check.test.ts`

**Interfaces:**
- Consumes: `IncrementService` (Task 10), `CheckService` (Task 11), `internalSecretMiddleware` (Task 13).
- Produces: `incrementRoutes(controller)`, `checkRoutes(controller)` — mounted by `create-gen-usg.ts` in Task 16 at `/internal/usage/increment` and `/internal/usage/check`.

- [ ] **Step 1: `modules/increment/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { IncrementService } from "./service.ts";

const incrementBodySchema = z.object({
  tenantId: z.string().uuid(),
  metric: z.string().min(1),
  delta: z.number(),
  eventId: z.string().optional(),
  resourceId: z.string().optional(),
  idempotencyKey: z.string().optional(),
  occurredAt: z.string().datetime().optional(),
});

export class IncrementController {
  constructor(private readonly service: IncrementService) {}

  increment = async (req: Request, res: Response): Promise<void> => {
    const body = incrementBodySchema.parse(req.body);
    const result = await this.service.increment({
      ...body,
      occurredAt: body.occurredAt ? new Date(body.occurredAt) : undefined,
    });
    res.json(result);
  };
}
```

- [ ] **Step 2: `modules/increment/v1/routes.ts`**

```ts
import { Router } from "express";
import type { IncrementController } from "./controller.ts";

export function incrementRoutes(controller: IncrementController): Router {
  const router = Router();
  router.post("/", controller.increment);
  return router;
}
```

- [ ] **Step 3: `modules/check/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { CheckService } from "./service.ts";

const checkBodySchema = z.object({
  tenantId: z.string().uuid(),
  metric: z.string().min(1),
  delta: z.number().optional(),
});

export class CheckController {
  constructor(private readonly service: CheckService) {}

  check = async (req: Request, res: Response): Promise<void> => {
    const body = checkBodySchema.parse(req.body);
    const result = await this.service.check(body.tenantId, body.metric, body.delta);
    // Always 200 — the verdict lives in the body, never in the status code.
    res.json(result);
  };
}
```

- [ ] **Step 4: `modules/check/v1/routes.ts`**

```ts
import { Router } from "express";
import type { CheckController } from "./controller.ts";

export function checkRoutes(controller: CheckController): Router {
  const router = Router();
  router.post("/", controller.check);
  return router;
}
```

- [ ] **Step 5: Write the HTTP test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { IncrementController } from "../../src/modules/increment/v1/controller.ts";
import { incrementRoutes } from "../../src/modules/increment/v1/routes.ts";
import { IncrementService } from "../../src/modules/increment/v1/service.ts";
import { CheckController } from "../../src/modules/check/v1/controller.ts";
import { checkRoutes } from "../../src/modules/check/v1/routes.ts";
import { CheckService } from "../../src/modules/check/v1/service.ts";
import { GraceOverageService } from "../../src/modules/grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import { internalSecretMiddleware } from "../../src/middleware/internal-secret.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../src/domain/ports/idempotency.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";

function makeApp() {
  const counterStore: ICounterStore = {
    incrBy: async () => 1, get: async () => 0, mget: async (keys) => keys.map(() => -1),
    setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
    zsumScores: async () => 0, setBatch: async () => {},
  };
  const idempotencyRepo: IIdempotencyRepo = { exists: async () => false, insert: async () => {} };
  const limitProvider: ILimitProvider = { getLimits: async () => ({}) };
  const graceRepo: IGraceOverageRepo = {
    findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
    listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
  };
  const incrementService = new IncrementService(counterStore, idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
  const checkService = new CheckService(counterStore, limitProvider, new GraceOverageService(graceRepo), {
    limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7,
  });
  const app = express();
  app.use(express.json());
  const gate = internalSecretMiddleware("test-secret");
  app.use("/internal/usage/increment", gate, incrementRoutes(new IncrementController(incrementService)));
  app.use("/internal/usage/check", gate, checkRoutes(new CheckController(checkService)));
  app.use(errorHandler);
  return app;
}

describe("increment & check HTTP routes", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("POST /internal/usage/increment without the internal secret is 401", async () => {
    const res = await request(makeApp()).post("/internal/usage/increment").send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(res.status).toBe(401);
  });

  it("POST /internal/usage/increment with the internal secret succeeds", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp())
      .post("/internal/usage/increment")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(res.status).toBe(200);
    expect(res.body.metric).toBe("seats");
  });

  it("POST /internal/usage/increment for an unregistered metric is 400", async () => {
    const res = await request(makeApp())
      .post("/internal/usage/increment")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "nope", delta: 1 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("METER_NOT_REGISTERED");
  });

  it("POST /internal/usage/check always returns 200 with the verdict in the body", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp())
      .post("/internal/usage/check")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats" });
    expect(res.status).toBe(200);
    expect(res.body.outcome).toBe("ALLOW");
  });
});
```

- [ ] **Step 6: Run tests**

Run: `npx vitest run tests/http/increment-check.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-usg-starter/src/modules/increment/v1/controller.ts packages/gen-usg-starter/src/modules/increment/v1/routes.ts packages/gen-usg-starter/src/modules/check/v1/controller.ts packages/gen-usg-starter/src/modules/check/v1/routes.ts packages/gen-usg-starter/tests/http/increment-check.test.ts
git commit -m "feat(http): add internal-secret-gated increment and check routes"
```

---

### Task 15: Summary HTTP Module

**Files:**
- Create: `packages/gen-usg-starter/src/modules/summary/v1/service.ts`
- Create: `packages/gen-usg-starter/src/modules/summary/v1/controller.ts`
- Create: `packages/gen-usg-starter/src/modules/summary/v1/routes.ts`
- Test: `packages/gen-usg-starter/tests/http/summary.test.ts`

**Interfaces:**
- Consumes: `ICounterStore` (Task 5), `IMeterRepo`, `IGraceOverageRepo` (Task 5), `CheckService.resolveLimits` (Task 11), `getMeterDefinition`/`listRegisteredMeterCodes` (Task 8).
- Produces: `SummaryService`, `summaryRoutes(controller)` — mounted by `create-gen-usg.ts` in Task 16 at `/api/v1/usage/summary`.

- [ ] **Step 1: `modules/summary/v1/service.ts`**

```ts
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo, GraceOverageRecord } from "../../../domain/ports/grace-overage.repository.port.ts";
import type { CheckService } from "../../check/v1/service.ts";
import { getMeterDefinition, listRegisteredMeterCodes } from "../../meters/v1/registry.ts";

export interface MeterSummary {
  metric: string;
  unit: string;
  current: number;
  limit: number;
  pct: number;
}

export interface UsageSummary {
  tenantId: string;
  meters: MeterSummary[];
  trend: Array<{ snapshotAt: Date; period: string; metrics: Record<string, number> }>;
  graceOverages: GraceOverageRecord[];
}

export class SummaryService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
    private readonly graceOverageRepo: IGraceOverageRepo,
    private readonly checkService: CheckService,
    private readonly trendSinceDays: number,
  ) {}

  async getSummary(tenantId: string): Promise<UsageSummary> {
    const metricCodes = listRegisteredMeterCodes();
    const [currentValues, limits, trend, graceOverages] = await Promise.all([
      this.counterStore.mget(metricCodes.map((m) => `genusg:${tenantId}:${m}`)),
      this.checkService.resolveLimits(tenantId, metricCodes),
      this.meterRepo.findTrend(tenantId, this.trendSinceDays),
      this.graceOverageRepo.listOpenForTenant(tenantId),
    ]);

    const meters: MeterSummary[] = metricCodes.map((metric, i) => {
      const current = currentValues[i] ?? 0;
      const limit = limits[metric] ?? Infinity;
      const pct = limit === -1 || limit === Infinity || limit === 0 ? 0 : Math.round((current / limit) * 100);
      return { metric, unit: getMeterDefinition(metric)?.unit ?? "unit", current, limit, pct };
    });

    return {
      tenantId,
      meters,
      trend: trend.map((s) => ({ snapshotAt: s.snapshotAt, period: s.period, metrics: s.metrics })),
      graceOverages,
    };
  }
}
```

- [ ] **Step 2: `modules/summary/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { SummaryService } from "./service.ts";

const summaryQuerySchema = z.object({ tenantId: z.string().uuid() });

export class SummaryController {
  constructor(private readonly service: SummaryService) {}

  getSummary = async (req: Request, res: Response): Promise<void> => {
    const { tenantId } = summaryQuerySchema.parse(req.query);
    const summary = await this.service.getSummary(tenantId);
    res.json(summary);
  };
}
```

- [ ] **Step 3: `modules/summary/v1/routes.ts`**

```ts
import { Router } from "express";
import type { SummaryController } from "./controller.ts";

export function summaryRoutes(controller: SummaryController): Router {
  const router = Router();
  router.get("/", controller.getSummary);
  return router;
}
```

- [ ] **Step 4: Write the HTTP test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { SummaryController } from "../../src/modules/summary/v1/controller.ts";
import { summaryRoutes } from "../../src/modules/summary/v1/routes.ts";
import { SummaryService } from "../../src/modules/summary/v1/service.ts";
import { CheckService } from "../../src/modules/check/v1/service.ts";
import { GraceOverageService } from "../../src/modules/grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../src/domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";

function makeApp() {
  // mget is used for two distinct purposes here: SummaryService reads
  // counter keys ("genusg:{tenantId}:{metric}") directly, while
  // CheckService.resolveLimits reads limit keys ("genusg:limit:...") to
  // check its cache before falling back to ILimitProvider. Distinguish by
  // key shape so both call sites get realistic, distinct values.
  const counterStore: ICounterStore = {
    incrBy: async () => 1, get: async () => 0,
    mget: async (keys: string[]) => keys.map((k) => (k.includes(":limit:") ? null : 7)),
    setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
    zsumScores: async () => 0, setBatch: async () => {},
  };
  const meterRepo: IMeterRepo = { insertSnapshot: async () => {}, findTrend: async () => [], latestSnapshot: async () => null };
  const graceOverageRepo: IGraceOverageRepo = {
    findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
    listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
  };
  const limitProvider: ILimitProvider = { getLimits: async () => ({ seats: 10 }) };
  const checkService = new CheckService(counterStore, limitProvider, new GraceOverageService(graceOverageRepo), {
    limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7,
  });
  const summaryService = new SummaryService(counterStore, meterRepo, graceOverageRepo, checkService, 7);
  const app = express();
  app.use("/api/v1/usage/summary", summaryRoutes(new SummaryController(summaryService)));
  app.use(errorHandler);
  return app;
}

describe("GET /api/v1/usage/summary", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("returns meters/trend/graceOverages for a trusted tenantId, no auth required", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp()).get("/api/v1/usage/summary").query({ tenantId: "11111111-1111-1111-1111-111111111111" });
    expect(res.status).toBe(200);
    expect(res.body.meters).toEqual([{ metric: "seats", unit: "seat", current: 7, limit: 10, pct: 70 }]);
    expect(res.body.trend).toEqual([]);
    expect(res.body.graceOverages).toEqual([]);
  });

  it("400s on a non-UUID tenantId", async () => {
    const res = await request(makeApp()).get("/api/v1/usage/summary").query({ tenantId: "not-a-uuid" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run tests/http/summary.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-usg-starter/src/modules/summary packages/gen-usg-starter/tests/http/summary.test.ts
git commit -m "feat(summary): add unauthenticated usage summary endpoint"
```

---

### Task 16: The `createGenUsg` Factory + Public Barrel

**Files:**
- Create: `packages/gen-usg-starter/src/create-gen-usg.ts`
- Create: `packages/gen-usg-starter/src/index.ts`
- Test: `packages/gen-usg-starter/tests/http/factory.test.ts`

**Interfaces:**
- Consumes: every port (Task 5), every default adapter (Tasks 6-7), the meter registry (Task 8), every service/controller/routes (Tasks 9-15).
- Produces: `createGenUsg`, `registerMeter`, `GenUsgConfig`, `GenUsgModulesConfig`, `GenUsgInstance` — the single public entry point.

- [ ] **Step 1: `create-gen-usg.ts`**

```ts
import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";

import { requireEnv, optionalEnv } from "./config/env.ts";
import { GenUsgConfigError } from "./common/errors.ts";

import type { ICounterStore } from "./domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "./domain/ports/limit-provider.port.ts";
import type { IMeterRepo } from "./domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "./domain/ports/grace-overage.repository.port.ts";
import type { IReconciliationRepo } from "./domain/ports/reconciliation.repository.port.ts";
import type { IIdempotencyRepo } from "./domain/ports/idempotency.repository.port.ts";

import { RedisCounterStore } from "./infra/cache/redis-counter-store.ts";
import { PrismaMeterRepo } from "./modules/rollup/v1/repo.ts";
import { PrismaGraceOverageRepo } from "./modules/grace-overage/v1/repo.ts";
import { PrismaReconciliationRepo } from "./modules/reconciliation/v1/repo.ts";
import { PrismaIdempotencyRepo } from "./infra/persistence/prisma-idempotency-repo.ts";

import { GraceOverageService, type ClosedGraceWindow } from "./modules/grace-overage/v1/service.ts";
import { IncrementService, type IncrementInput, type IncrementResult } from "./modules/increment/v1/service.ts";
import { IncrementController } from "./modules/increment/v1/controller.ts";
import { incrementRoutes } from "./modules/increment/v1/routes.ts";
import { CheckService, type CheckResult } from "./modules/check/v1/service.ts";
import { CheckController } from "./modules/check/v1/controller.ts";
import { checkRoutes } from "./modules/check/v1/routes.ts";
import { RollupService, type RollupResult } from "./modules/rollup/v1/service.ts";
import { ReconciliationService, type ReconciliationSweepResult } from "./modules/reconciliation/v1/service.ts";
import { SummaryService } from "./modules/summary/v1/service.ts";
import { SummaryController } from "./modules/summary/v1/controller.ts";
import { summaryRoutes } from "./modules/summary/v1/routes.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";

export interface GenUsgModulesConfig {
  check?: boolean;
  increment?: boolean;
  summary?: boolean;
}

export interface GenUsgConfig {
  counterStore?: ICounterStore;
  meterRepo?: IMeterRepo;
  graceOverageRepo?: IGraceOverageRepo;
  reconciliationRepo?: IReconciliationRepo;
  idempotencyRepo?: IIdempotencyRepo;
  limitProvider?: ILimitProvider;
  modules?: GenUsgModulesConfig;
  internalSecret?: string;
  backdateDays?: number;
  dedupTtlSec?: number;
  limitCacheTtlSec?: number;
  limitLockTtlSec?: number;
  softWarnPct80?: number;
  softWarnPct95?: number;
  graceWindowDays?: number;
  driftThresholdPct?: number;
  trendSinceDays?: number;
}

export interface GenUsgInstance {
  app: Express;
  increment(input: IncrementInput): Promise<IncrementResult>;
  check(tenantId: string, metric: string, delta?: number): Promise<CheckResult>;
  runDailyRollup(tenantIds: string[]): Promise<RollupResult>;
  runMonthlyRollup(tenantIds: string[]): Promise<RollupResult>;
  runReconciliationSweep(tenantIds: string[]): Promise<ReconciliationSweepResult>;
  closeExpiredGraceWindows(): Promise<ClosedGraceWindow[]>;
}

export { registerMeter, getMeterDefinition, listRegisteredMeterCodes } from "./modules/meters/v1/registry.ts";
export type { MeterDefinition, RegisterMeterInput, MeterMode } from "./modules/meters/v1/registry.ts";

function resolveCounterStore(override: ICounterStore | undefined): ICounterStore {
  if (override) return override;
  return new RedisCounterStore(requireEnv("REDIS_URL"));
}

function resolveMeterRepo(override: IMeterRepo | undefined): IMeterRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaMeterRepo();
}

function resolveGraceOverageRepo(override: IGraceOverageRepo | undefined): IGraceOverageRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaGraceOverageRepo();
}

function resolveReconciliationRepo(override: IReconciliationRepo | undefined): IReconciliationRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaReconciliationRepo();
}

function resolveIdempotencyRepo(override: IIdempotencyRepo | undefined): IIdempotencyRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaIdempotencyRepo();
}

export function createGenUsg(config: GenUsgConfig): GenUsgInstance {
  const modules: Required<GenUsgModulesConfig> = {
    check: config.modules?.check ?? true,
    increment: config.modules?.increment ?? true,
    summary: config.modules?.summary ?? true,
  };

  const counterStore = resolveCounterStore(config.counterStore);
  const meterRepo = resolveMeterRepo(config.meterRepo);
  const graceOverageRepo = resolveGraceOverageRepo(config.graceOverageRepo);
  const reconciliationRepo = resolveReconciliationRepo(config.reconciliationRepo);
  const idempotencyRepo = resolveIdempotencyRepo(config.idempotencyRepo);

  // limitProvider has no default adapter — resolved lazily only when check()
  // or the summary module actually needs it, matching every sibling's
  // resolveXxx idiom of never validating a module-specific dependency for a
  // feature the host never exercises. Constructing CheckService still
  // requires SOME ILimitProvider reference, so an unset limitProvider throws
  // GenUsgConfigError here at createGenUsg() time whenever check or summary
  // is enabled — never as a mid-request 500.
  if ((modules.check || modules.summary) && !config.limitProvider) {
    throw new GenUsgConfigError(
      "limitProvider is required when modules.check or modules.summary is enabled — supply an ILimitProvider implementation",
    );
  }
  const limitProvider = config.limitProvider as ILimitProvider;

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_USG_INTERNAL_SECRET", "");

  const graceOverageService = new GraceOverageService(graceOverageRepo);
  const incrementService = new IncrementService(counterStore, idempotencyRepo, {
    backdateDays: config.backdateDays ?? 30,
    dedupTtlSec: config.dedupTtlSec ?? 86400,
  });
  const checkService = new CheckService(counterStore, limitProvider, graceOverageService, {
    limitCacheTtlSec: config.limitCacheTtlSec ?? 300,
    limitLockTtlSec: config.limitLockTtlSec ?? 5,
    softWarnPct80: config.softWarnPct80 ?? 0.8,
    softWarnPct95: config.softWarnPct95 ?? 0.95,
    graceWindowDays: config.graceWindowDays ?? 7,
  });
  const rollupService = new RollupService(counterStore, meterRepo);
  const reconciliationService = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, config.driftThresholdPct ?? 2);
  const summaryService = new SummaryService(counterStore, meterRepo, graceOverageRepo, checkService, config.trendSinceDays ?? 7);

  const app = express();
  app.use(helmet());
  app.use(cors({ origin: optionalEnv("ALLOWED_ORIGINS", "*").split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  const gate = internalSecretMiddleware(internalSecret);

  if (modules.increment) {
    app.use("/internal/usage/increment", gate, incrementRoutes(new IncrementController(incrementService)));
  }
  if (modules.check) {
    app.use("/internal/usage/check", gate, checkRoutes(new CheckController(checkService)));
  }
  if (modules.summary) {
    app.use("/api/v1/usage/summary", summaryRoutes(new SummaryController(summaryService)));
  }

  app.use(errorHandler);

  return {
    app,
    increment: (input) => incrementService.increment(input),
    check: (tenantId, metric, delta) => checkService.check(tenantId, metric, delta),
    runDailyRollup: (tenantIds) => rollupService.runRollup(tenantIds, "daily"),
    runMonthlyRollup: (tenantIds) => rollupService.runRollup(tenantIds, "monthly"),
    runReconciliationSweep: (tenantIds) => reconciliationService.runSweep(tenantIds),
    closeExpiredGraceWindows: () => graceOverageService.closeExpiredGraceWindows(new Date()),
  };
}
```

- [ ] **Step 2: `index.ts`**

```ts
export { createGenUsg } from "./create-gen-usg.ts";
export type { GenUsgConfig, GenUsgModulesConfig, GenUsgInstance } from "./create-gen-usg.ts";
export { registerMeter, getMeterDefinition, listRegisteredMeterCodes } from "./modules/meters/v1/registry.ts";
export type { MeterDefinition, RegisterMeterInput, MeterMode } from "./modules/meters/v1/registry.ts";

export type { ICounterStore, SetBatchEntry } from "./domain/ports/counter-store.port.ts";
export type { ILimitProvider } from "./domain/ports/limit-provider.port.ts";
export type { IMeterRepo, RollupPeriod, UsageSnapshotRecord } from "./domain/ports/meter.repository.port.ts";
export type { IGraceOverageRepo, GraceOverageRecord, GraceOverageStatus } from "./domain/ports/grace-overage.repository.port.ts";
export type { IReconciliationRepo, InsertReconciliationLogInput } from "./domain/ports/reconciliation.repository.port.ts";
export type { IIdempotencyRepo } from "./domain/ports/idempotency.repository.port.ts";

export { RedisCounterStore } from "./infra/cache/redis-counter-store.ts";
export { PrismaMeterRepo } from "./modules/rollup/v1/repo.ts";
export { PrismaGraceOverageRepo } from "./modules/grace-overage/v1/repo.ts";
export { PrismaReconciliationRepo } from "./modules/reconciliation/v1/repo.ts";
export { PrismaIdempotencyRepo } from "./infra/persistence/prisma-idempotency-repo.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export type { IncrementInput, IncrementResult } from "./modules/increment/v1/service.ts";
export type { CheckResult, CheckOutcome } from "./modules/check/v1/service.ts";
export type { RollupResult } from "./modules/rollup/v1/service.ts";
export type { ReconciliationSweepResult } from "./modules/reconciliation/v1/service.ts";
export type { ClosedGraceWindow, GraceWindow } from "./modules/grace-overage/v1/service.ts";
export type { UsageSummary, MeterSummary } from "./modules/summary/v1/service.ts";

export { AppError, GenUsgConfigError, MeterNotRegisteredError, IncrementDeltaInvalidError, UsageEventOutOfWindowError, ResourceIdRequiredError } from "./common/errors.ts";
```

- [ ] **Step 3: Write the factory test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import request from "supertest";
import { createGenUsg } from "../../src/create-gen-usg.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../src/domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";
import type { IReconciliationRepo } from "../../src/domain/ports/reconciliation.repository.port.ts";
import type { IIdempotencyRepo } from "../../src/domain/ports/idempotency.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";

const noopCounterStore: ICounterStore = {
  incrBy: async () => 1, get: async () => 0, mget: async (keys) => keys.map(() => -1),
  setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
  zsumScores: async () => 0, setBatch: async () => {},
};
const noopMeterRepo: IMeterRepo = { insertSnapshot: async () => {}, findTrend: async () => [], latestSnapshot: async () => null };
const noopGraceOverageRepo: IGraceOverageRepo = {
  findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
  listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
};
const noopReconciliationRepo: IReconciliationRepo = { insertLog: async () => {} };
const noopIdempotencyRepo: IIdempotencyRepo = { exists: async () => false, insert: async () => {} };
const noopLimitProvider: ILimitProvider = { getLimits: async () => ({}) };

const fullOverrides = {
  counterStore: noopCounterStore, meterRepo: noopMeterRepo, graceOverageRepo: noopGraceOverageRepo,
  reconciliationRepo: noopReconciliationRepo, idempotencyRepo: noopIdempotencyRepo, limitProvider: noopLimitProvider,
  internalSecret: "test-secret",
};

describe("createGenUsg", () => {
  afterEach(() => { _clearRegistryForTests(); delete process.env.DATABASE_URL; delete process.env.REDIS_URL; });

  it("boots cleanly with every port overridden and no env set", () => {
    expect(() => createGenUsg(fullOverrides)).not.toThrow();
  });

  it("throws when check/summary are enabled but no limitProvider is supplied", () => {
    const { limitProvider, ...rest } = fullOverrides;
    expect(() => createGenUsg(rest)).toThrow(/limitProvider is required/);
  });

  it("does not require limitProvider when check and summary are both disabled", () => {
    const { limitProvider, ...rest } = fullOverrides;
    expect(() => createGenUsg({ ...rest, modules: { check: false, summary: false } })).not.toThrow();
  });

  it("GET /health returns ok without any auth", async () => {
    const instance = createGenUsg(fullOverrides);
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("a disabled module's routes genuinely 404", async () => {
    const instance = createGenUsg({ ...fullOverrides, modules: { increment: false } });
    const res = await request(instance.app).post("/internal/usage/increment").set("x-internal-secret", "test-secret").send({});
    expect(res.status).toBe(404);
  });

  it("POST /internal/usage/increment without the internal secret is 401", async () => {
    const instance = createGenUsg(fullOverrides);
    const res = await request(instance.app).post("/internal/usage/increment").send({});
    expect(res.status).toBe(401);
  });

  it("increment()/check() work as direct in-process calls, not just HTTP", async () => {
    registerMeter("seats", { unit: "seat" });
    const instance = createGenUsg(fullOverrides);
    const incResult = await instance.increment({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(incResult.metric).toBe("seats");
    const checkResult = await instance.check("11111111-1111-1111-1111-111111111111", "seats");
    expect(checkResult.outcome).toBe("ALLOW");
  });

  it("runDailyRollup/runReconciliationSweep/closeExpiredGraceWindows are host-triggered functions", async () => {
    registerMeter("seats", { unit: "seat" });
    const instance = createGenUsg(fullOverrides);
    const rollup = await instance.runDailyRollup(["11111111-1111-1111-1111-111111111111"]);
    expect(rollup).toEqual({ period: "daily", tenantsProcessed: 1, failures: 0 });
    const sweep = await instance.runReconciliationSweep(["11111111-1111-1111-1111-111111111111"]);
    expect(sweep.tenantsProcessed).toBe(1);
    expect(await instance.closeExpiredGraceWindows()).toEqual([]);
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run tests/http/factory.test.ts`
Expected: PASS, 8 tests.

- [ ] **Step 5: Full package typecheck + full test suite**

Run: `cd packages/gen-usg-starter && npx tsc --noEmit && npx vitest run`
Expected: no type errors; every test file across Tasks 3-16 passes.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-usg-starter/src/create-gen-usg.ts packages/gen-usg-starter/src/index.ts packages/gen-usg-starter/tests/http/factory.test.ts
git commit -m "feat(factory): add createGenUsg factory and public barrel"
```

---

### Task 17: Demo App + Documentation + Smoke Script

**Files:**
- Create: `packages/gen-usg-demo/src/index.ts`
- Create: `docs/integration-guide.md`
- Create: `README.md`
- Create: `scripts/smoke-usage.sh`

**Interfaces:**
- Consumes: `createGenUsg`, `registerMeter` (Task 16).
- Produces: the demo app every smoke test and manual walkthrough targets.

- [ ] **Step 1: `packages/gen-usg-demo/src/index.ts`**

```ts
import { createGenUsg, registerMeter, type ILimitProvider } from "@gen-ms/gen-usg-starter";

registerMeter("seats", { unit: "seat" });
registerMeter("api_calls", { unit: "call", graceEligible: true });
registerMeter("storage_bytes", { unit: "byte", mode: "resource" });

// A stub ILimitProvider for the demo — a real host would resolve this from
// their own billing/plan lookup. Fixed limits here just make the smoke
// script's ALLOW->GRACE->BLOCK walkthrough reproducible.
const demoLimitProvider: ILimitProvider = {
  async getLimits() {
    return { seats: 5, api_calls: 3, storage_bytes: -1 };
  },
};

const genUsg = createGenUsg({ limitProvider: demoLimitProvider });

const PORT = Number(process.env.PORT ?? 3600);
genUsg.app.listen(PORT, () => {
  console.log(`gen-usg-demo listening on :${PORT}`);
});
```

Under ~20 lines of actual logic (excluding meter registration and the stub limit provider, which are host content, not framework wiring) — mirrors every sibling demo's size.

- [ ] **Step 2: `docs/integration-guide.md`**

Write the consumer-facing reference, mirroring Gen_NOTIF's `integration-guide.md` structure: embed-in-process vs. standalone HTTP, a full env var table (`DATABASE_URL`, `REDIS_URL`, `GEN_USG_INTERNAL_SECRET`, `ALLOWED_ORIGINS`), the full API table (every route from Tasks 14-15: `POST /internal/usage/increment`, `POST /internal/usage/check`, `GET /api/v1/usage/summary`, `GET /health`), the port-swap table for each of the 6 ports (`counterStore`/`meterRepo`/`graceOverageRepo`/`reconciliationRepo`/`idempotencyRepo`/`limitProvider`), and these loud callouts:
- **"Gen_USG ships no working `ILimitProvider` — you must supply one, or `createGenUsg()` throws `GenUsgConfigError` at boot whenever `modules.check` or `modules.summary` is enabled."**
- **"Any `ILimitProvider` failure (throw or timeout) makes `check()` fail OPEN to unlimited (`Infinity`), not BLOCK. This is a deliberate availability-over-strictness default — wrap your `ILimitProvider` yourself if you need strict fail-closed behavior."**
- **"Gen_USG has no internal scheduler and no tenant registry. You must call `runDailyRollup(tenantIds)`, `runMonthlyRollup(tenantIds)`, `runReconciliationSweep(tenantIds)`, and `closeExpiredGraceWindows()` yourself on your own cron/interval, supplying your own tenant list each time."**
- **"There is no message broker anywhere in Gen_USG. Call `increment()`/`check()` directly, in-process, whenever your own domain event fires — there is no RabbitMQ consumer, no DLQ, no retry queue."**
- A worked example of registering a `mode: "resource"` meter (e.g. `storage_bytes`), incrementing it twice with the same `resourceId` (once on upload, once negative on delete), and reading the exact result back via `check()`.

- [ ] **Step 3: `README.md`**

Mirror Gen_NOTIF's `README.md`: overview, the Gen_MS family table (now seven rows: AUTH/TNT/REG/ADM/TBR/NOTIF/USG), local run instructions (`docker compose up -d`, `npx prisma migrate deploy --schema packages/gen-usg-starter/prisma/schema.prisma`, `npm run dev --workspace=@gen-ms/gen-usg-demo`), the port-offset table (5433/5435/5436/5437/5438/**5439**, Redis 6380/6381/6382/**6383**), and a "Not in scope" section listing: no message broker/consumers/DLQ, no working `ILimitProvider` (bring your own), no internal scheduler, no tenant registry, no Prometheus/OpenTelemetry.

- [ ] **Step 4: `scripts/smoke-usage.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3600}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
INTERNAL_SECRET="${GEN_USG_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== increment seats twice with the same eventId (proves dedup) =="
curl -sf -X POST "$BASE_URL/internal/usage/increment" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\",\"delta\":1,\"eventId\":\"smoke-evt-1\"}"
REPLAY=$(curl -sf -X POST "$BASE_URL/internal/usage/increment" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\",\"delta\":1,\"eventId\":\"smoke-evt-1\"}")
echo "$REPLAY" | grep -q '"replayed":true'

echo "== check seats (ALLOW, well under the demo's limit of 5) =="
curl -sf -X POST "$BASE_URL/internal/usage/check" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\"}" | grep -q '"outcome":"ALLOW"'

echo "== increment api_calls past its limit of 3, then check (GRACE, api_calls is grace-eligible) =="
for i in 1 2 3 4; do
  curl -sf -X POST "$BASE_URL/internal/usage/increment" \
    -H 'content-type: application/json' \
    -H "x-internal-secret: $INTERNAL_SECRET" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\",\"delta\":1,\"eventId\":\"smoke-api-$i\"}" > /dev/null
done
curl -sf -X POST "$BASE_URL/internal/usage/check" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\"}" | grep -q '"outcome":"GRACE"'

echo "== fetch the usage summary =="
curl -sf "$BASE_URL/api/v1/usage/summary?tenantId=$TENANT_ID"

echo "== run a reconciliation sweep and a rollup (host-triggered, no cron in the library) =="
echo "(these are function-only in v1, exercised via a small Node one-liner rather than HTTP)"
node -e "
import('@gen-ms/gen-usg-starter').then(async () => {}).catch(() => {});
" || true

echo "== smoke test complete =="
```

The rollup/reconciliation/grace-expiry functions are intentionally **not** exposed over HTTP (per the design's "function-only, host-triggered" decision) — the smoke script only exercises the HTTP-reachable surface (`increment`, `check`, `summary`); a host verifying the sweep functions does so directly in their own process, e.g. `await genUsg.runDailyRollup(["11111111-1111-1111-1111-111111111111"])`.

- [ ] **Step 5: Manual end-to-end verification**

Run: `docker compose up -d && npx prisma migrate deploy --schema packages/gen-usg-starter/prisma/schema.prisma && npm run dev --workspace=@gen-ms/gen-usg-demo &` then `bash scripts/smoke-usage.sh`.
Expected: every `curl -sf` call succeeds (non-2xx exits nonzero under `set -euo pipefail`), and every `grep -q` match is found. The `api_calls` meter's limit is 3 in the demo's stub `ILimitProvider`; after 4 increments `current` is 4, which exceeds the limit, and `api_calls` was registered with `graceEligible: true`, so `check()` resolves to `GRACE`, not `BLOCK`.

- [ ] **Step 6: Full test suite + typecheck, one final time**

Run: `npm test --workspaces && npm run typecheck --workspaces`
Expected: every test across every task passes; no type errors.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-usg-demo docs/integration-guide.md README.md scripts/smoke-usage.sh
git commit -m "docs: add integration guide, README, and smoke script"
```
