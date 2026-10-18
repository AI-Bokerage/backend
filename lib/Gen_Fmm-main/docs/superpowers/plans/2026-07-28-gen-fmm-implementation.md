# Gen_FMM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_FMM, an embeddable feature-gate/entitlement engine — `createGenFmm(config)` factory with hexagonal ports, adapting CPMS-Platform's `fmm-svc` (4-tier resolution, L1+L2 cache chain, catalog/override/telemetry CRUD) minus its RabbitMQ/RBAC/JWT dependencies.

**Architecture:** npm workspace (`packages/gen-fmm-starter` + `packages/gen-fmm-demo`), TypeScript ESM/NodeNext, Express 4, Prisma 5, Postgres 15 + RLS via `withTenant()`, Redis for L2 cache. No message broker, no built-in auth — host trusts params and fronts its own authz.

**Tech Stack:** Node 20, TypeScript, Express 4, Prisma 5, Postgres 15, Redis (ioredis), Vitest 2 + supertest + Testcontainers (`@testcontainers/postgresql`, `@testcontainers/redis`), zod.

## Global Constraints

- All relative imports use explicit `.ts` extensions (rewritten to `.js` at build via `rewriteRelativeImportExtensions` — see `tsconfig.json`).
- Every Prisma call touching `tenant_feature_flag` or `feature_usage_event` MUST go through `withTenant(tenantId, fn)` — no exceptions. This is the exact bug class the Gen_USG final review found; the final whole-branch review for this plan will check for it explicitly.
- `modules`/`plan_modules`... wait, table names are `module`/`plan_module`/`feature_flag` (global, no `tenant_id`, no RLS) — confirmed correct in the design spec, do not add tenant scoping to these.
- `check()` on an unknown flag key returns `{ enabled: false, reason: "FLAG_NOT_FOUND" }` with HTTP 200 — never 404.
- `planCode` is always caller-supplied — never looked up internally, no `plans` table.
- No RabbitMQ, no Valkey/Redis pub/sub, no JWT/RBAC middleware anywhere in this codebase.
- `GenFmmConfigError` thrown synchronously at `createGenFmm()` call time for missing required config — never mid-request.
- Ports: Postgres `5441`, Redis `6385` (docker-compose), demo HTTP `3700`.
- DB role: migrations run as the docker-compose bootstrap superuser (`genfmm`); the running app connects as `genfmm_app` (non-superuser, RLS-subject) — mirrors Gen_NOTIF/Gen_USG exactly.
- `computeRolloutBucket(tenantId, flagKey)` = `parseInt(sha256(`${tenantId}:${flagKey}`).slice(0,8), 16) % 100`; enabled iff `bucket < rolloutPercentage`. Ported verbatim — do not modify.
- Override `expiresAt` MUST be checked on every read path (`resolveEntitlement`) — this is the bug fixed relative to source, verify it stays fixed.

---

## File Structure

```
Gen_FMM/
├── package.json                                   (workspace root)
├── docker-compose.yml                             (Postgres 5441, Redis 6385)
├── .env.example / .gitignore
├── docs/integration-guide.md
├── README.md
├── scripts/smoke-fmm.sh
└── packages/
    ├── gen-fmm-starter/
    │   ├── package.json / tsconfig.json / vitest.config.ts
    │   ├── prisma/schema.prisma + migrations/{init, enable_rls, create_app_role}
    │   └── src/
    │       ├── common/{errors.ts, logger.ts}
    │       ├── config/env.ts
    │       ├── domain/ports/{catalog.repository, tenant-override.repository, telemetry.repository, cache-store}.port.ts
    │       ├── infra/
    │       │   ├── persistence/{prisma-client.ts, with-tenant.ts}
    │       │   └── cache/{bounded-ttl-cache.ts, redis-cache-store.ts, get-or-set.ts, cache-keys.ts}
    │       ├── middleware/{error-handler.ts, internal-secret.ts, require-feature.ts}
    │       ├── modules/
    │       │   ├── catalog/v1/{repo,service,controller,routes}.ts
    │       │   ├── overrides/v1/{repo,service,controller,routes}.ts
    │       │   ├── telemetry/v1/{repo,service,controller,routes}.ts
    │       │   └── entitlement/v1/{types,resolve,cache,service,controller,routes}.ts
    │       ├── docs/openapi.ts
    │       ├── create-gen-fmm.ts
    │       └── index.ts
    └── gen-fmm-demo/
        ├── package.json
        └── src/index.ts
```

---

### Task 1: Workspace scaffold

**Files:**
- Create: `package.json` (root), `.gitignore`, `docker-compose.yml`, `.env.example`
- Create: `packages/gen-fmm-starter/package.json`, `packages/gen-fmm-starter/tsconfig.json`, `packages/gen-fmm-starter/vitest.config.ts`
- Create: `packages/gen-fmm-demo/package.json`

**Interfaces:**
- Produces: npm workspace with `gen-fmm-starter`/`gen-fmm-demo` packages installable via `npm install` from repo root.

- [ ] **Step 1: Root `package.json`**

```json
{
  "name": "gen-fmm",
  "private": true,
  "workspaces": ["packages/*"],
  "scripts": {
    "build": "npm run build --workspaces --if-present",
    "test": "npm run test --workspaces --if-present",
    "typecheck": "npm run typecheck --workspaces --if-present"
  }
}
```

- [ ] **Step 2: Root `.gitignore`**

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
      POSTGRES_DB: genfmm
      POSTGRES_USER: genfmm
      POSTGRES_PASSWORD: genfmm
    ports: ["5441:5432"]
    volumes: ["genfmm-pg:/var/lib/postgresql/data"]
  redis:
    image: redis:7
    ports: ["6385:6379"]
volumes:
  genfmm-pg: {}
```

- [ ] **Step 4: `.env.example`**

```
# Connect as genfmm_app (unprivileged, subject to RLS), not genfmm (the
# docker-compose bootstrap superuser, which bypasses RLS entirely). Run
# `prisma migrate deploy`/`migrate dev` as genfmm — CREATE POLICY / FORCE ROW
# LEVEL SECURITY need elevated privileges genfmm_app deliberately doesn't have.
DATABASE_URL=postgresql://genfmm_app:genfmm_app@localhost:5441/genfmm
REDIS_URL=redis://localhost:6385
GEN_FMM_INTERNAL_SECRET=change-me-dev-secret
ALLOWED_ORIGINS=http://localhost:3000
PORT=3700
CHECK_CACHE_TTL_SECS=60
BULK_CACHE_TTL_SECS=300
L1_CACHE_MAX_ENTRIES=10000
TELEMETRY_BUFFER_MAX=500
```

- [ ] **Step 5: `packages/gen-fmm-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-fmm-starter",
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
    "cors": "^2.8.5",
    "express": "^4.19.2",
    "express-async-errors": "^3.1.1",
    "helmet": "^7.1.0",
    "ioredis": "^5.4.1",
    "pino": "^9.4.0",
    "pino-pretty": "^11.2.2",
    "swagger-ui-express": "^5.0.1",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "@testcontainers/postgresql": "^10.13.2",
    "@testcontainers/redis": "^12.0.4",
    "@types/cors": "^2.8.17",
    "@types/express": "^4.17.21",
    "@types/node": "^20.16.10",
    "@types/supertest": "^6.0.2",
    "@types/swagger-ui-express": "^4.1.8",
    "prisma": "^5.20.0",
    "supertest": "^7.0.0",
    "typescript": "^5.6.2",
    "vitest": "^2.1.1"
  }
}
```

- [ ] **Step 6: `packages/gen-fmm-starter/tsconfig.json`**

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

- [ ] **Step 7: `packages/gen-fmm-starter/vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    fakeTimers: {
      toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"],
    },
  },
});
```

- [ ] **Step 8: `packages/gen-fmm-demo/package.json`**

```json
{
  "name": "@gen-ms/gen-fmm-demo",
  "version": "0.1.0",
  "type": "module",
  "private": true,
  "scripts": {
    "build": "tsc --outDir dist src/index.ts --module NodeNext --moduleResolution NodeNext --target ES2022 --esModuleInterop --skipLibCheck",
    "start": "node dist/index.js"
  },
  "dependencies": {
    "@gen-ms/gen-fmm-starter": "*"
  },
  "devDependencies": {
    "typescript": "^5.6.2"
  }
}
```

- [ ] **Step 9: Verify install**

Run: `cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && npm install`
Expected: installs cleanly, creates root `package-lock.json`, `node_modules/`.

- [ ] **Step 10: Commit**

```bash
git add package.json package-lock.json .gitignore docker-compose.yml .env.example packages/gen-fmm-starter/package.json packages/gen-fmm-starter/tsconfig.json packages/gen-fmm-starter/vitest.config.ts packages/gen-fmm-demo/package.json
git commit -m "chore: scaffold Gen_FMM npm workspace"
```

---

### Task 2: Common utils — errors, logger, env

**Files:**
- Create: `packages/gen-fmm-starter/src/common/errors.ts`
- Create: `packages/gen-fmm-starter/src/common/logger.ts`
- Create: `packages/gen-fmm-starter/src/config/env.ts`
- Test: `packages/gen-fmm-starter/src/common/errors.test.ts`
- Test: `packages/gen-fmm-starter/src/config/env.test.ts`

**Interfaces:**
- Produces: `AppError`, `GenFmmConfigError`, `ConflictError`, `NotFoundError`, `BusinessRuleError`, `InvalidTenantIdError` (all from `common/errors.ts`); `logger` (from `common/logger.ts`); `requireEnv(name)`, `optionalEnv(name, fallback)` (from `config/env.ts`).

- [ ] **Step 1: Write failing tests — `common/errors.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { AppError, ConflictError, NotFoundError, BusinessRuleError, InvalidTenantIdError, GenFmmConfigError } from "./errors.ts";

describe("errors", () => {
  it("AppError carries statusCode/code/message", () => {
    const err = new AppError(418, "TEAPOT", "I'm a teapot");
    expect(err.statusCode).toBe(418);
    expect(err.code).toBe("TEAPOT");
    expect(err.message).toBe("I'm a teapot");
  });

  it("ConflictError is a 409 AppError", () => {
    const err = new ConflictError("FLAG_ALREADY_EXISTS", "dup");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(409);
    expect(err.code).toBe("FLAG_ALREADY_EXISTS");
  });

  it("NotFoundError is a 404 AppError", () => {
    expect(new NotFoundError("FLAG_NOT_FOUND", "missing").statusCode).toBe(404);
  });

  it("BusinessRuleError is a 422 AppError", () => {
    expect(new BusinessRuleError("OVERRIDE_EXPIRY_IN_PAST", "bad").statusCode).toBe(422);
  });

  it("InvalidTenantIdError is a 400 AppError naming the bad value", () => {
    const err = new InvalidTenantIdError("not-a-uuid");
    expect(err.statusCode).toBe(400);
    expect(err.code).toBe("INVALID_TENANT_ID");
    expect(err.message).toContain("not-a-uuid");
  });

  it("GenFmmConfigError is a plain Error, not an AppError", () => {
    const err = new GenFmmConfigError("missing DATABASE_URL");
    expect(err).not.toBeInstanceOf(AppError);
    expect(err.name).toBe("GenFmmConfigError");
  });
});
```

- [ ] **Step 2: Run test, verify it fails**

Run: `cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM/packages/gen-fmm-starter" && npx vitest run src/common/errors.test.ts`
Expected: FAIL — `./errors.ts` does not exist.

- [ ] **Step 3: Implement `common/errors.ts`**

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

export class GenFmmConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenFmmConfigError";
  }
}

export class ConflictError extends AppError {
  constructor(code: string, message: string) {
    super(409, code, message);
  }
}

export class NotFoundError extends AppError {
  constructor(code: string, message: string) {
    super(404, code, message);
  }
}

export class BusinessRuleError extends AppError {
  constructor(code: string, message: string) {
    super(422, code, message);
  }
}

export class InvalidTenantIdError extends AppError {
  constructor(tenantId: string) {
    super(400, "INVALID_TENANT_ID", `Invalid tenantId format: ${tenantId}`);
  }
}
```

- [ ] **Step 4: Implement `common/logger.ts`**

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

- [ ] **Step 5: Write failing test — `config/env.test.ts`**

```ts
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "./env.ts";
import { GenFmmConfigError } from "../common/errors.ts";

describe("env", () => {
  const KEY = "GEN_FMM_TEST_VAR";

  beforeEach(() => { delete process.env[KEY]; });
  afterEach(() => { delete process.env[KEY]; });

  it("requireEnv returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("requireEnv throws GenFmmConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenFmmConfigError);
  });

  it("optionalEnv returns the fallback when unset", () => {
    expect(optionalEnv(KEY, "fallback")).toBe("fallback");
  });

  it("optionalEnv returns the value when set", () => {
    process.env[KEY] = "set";
    expect(optionalEnv(KEY, "fallback")).toBe("set");
  });
});
```

- [ ] **Step 6: Run tests, verify errors.test.ts passes and env.test.ts fails**

Run: `npx vitest run src/common/errors.test.ts src/config/env.test.ts`
Expected: `errors.test.ts` PASS; `env.test.ts` FAIL (`./env.ts` does not exist).

- [ ] **Step 7: Implement `config/env.ts`**

```ts
import { GenFmmConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenFmmConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}
```

- [ ] **Step 8: Run both test files, verify all pass**

Run: `npx vitest run src/common/errors.test.ts src/config/env.test.ts`
Expected: PASS, 10 tests.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-fmm-starter/src/common packages/gen-fmm-starter/src/config
git commit -m "feat: add common errors, logger, env helpers"
```

---

### Task 3: Domain ports

**Files:**
- Create: `packages/gen-fmm-starter/src/domain/ports/catalog.repository.port.ts`
- Create: `packages/gen-fmm-starter/src/domain/ports/tenant-override.repository.port.ts`
- Create: `packages/gen-fmm-starter/src/domain/ports/telemetry.repository.port.ts`
- Create: `packages/gen-fmm-starter/src/domain/ports/cache-store.port.ts`

**Interfaces:**
- Consumes: nothing (pure type definitions).
- Produces: `ICatalogRepo`, `ModuleRecord`, `PlanModuleRecord`, `FeatureFlagRecord`, `CreateModuleInput`, `UpdateModuleInput`, `CreateFlagInput`, `UpdateFlagInput`; `ITenantOverrideRepo`, `TenantOverrideRecord`, `UpsertOverrideInput`; `ITelemetryRepo`, `UsageEventInput`, `UsageEventRecord`, `TelemetryQueryFilter`; `ICacheStore`. Every later task imports from these files — signatures here are final.

This task has no runtime behavior to test (pure interfaces) — skip the test-first cycle, write the files directly, then verify with a typecheck.

- [ ] **Step 1: `domain/ports/catalog.repository.port.ts`**

```ts
export interface ModuleRecord {
  code: string;
  name: string;
  description: string | null;
  category: string | null;
  isActive: boolean;
  displayOrder: number;
  iconKey: string | null;
}

export interface PlanModuleRecord {
  planCode: string;
  moduleCode: string;
  entitlement: Record<string, unknown>;
}

export interface FeatureFlagRecord {
  key: string;
  moduleCode: string | null;
  defaultEnabled: boolean;
  isGradualRollout: boolean;
  rolloutPercentage: number;
}

export interface CreateModuleInput {
  code: string;
  name: string;
  description?: string;
  category?: string;
  isActive?: boolean;
  displayOrder?: number;
  iconKey?: string;
}

export interface UpdateModuleInput {
  name?: string;
  description?: string;
  category?: string;
  isActive?: boolean;
  displayOrder?: number;
  iconKey?: string;
}

export interface CreateFlagInput {
  key: string;
  moduleCode?: string;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface UpdateFlagInput {
  moduleCode?: string | null;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface ICatalogRepo {
  createModule(input: CreateModuleInput): Promise<ModuleRecord>;
  findModuleByCode(code: string): Promise<ModuleRecord | null>;
  listModules(filter: { category?: string; isActive?: boolean }): Promise<ModuleRecord[]>;
  updateModule(code: string, input: UpdateModuleInput): Promise<ModuleRecord | null>;

  createFlag(input: CreateFlagInput): Promise<FeatureFlagRecord>;
  findFlagByKey(key: string): Promise<FeatureFlagRecord | null>;
  listFlags(filter: { moduleCode?: string }): Promise<FeatureFlagRecord[]>;
  updateFlag(key: string, input: UpdateFlagInput): Promise<FeatureFlagRecord | null>;
  deleteFlag(key: string): Promise<boolean>;

  upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>): Promise<PlanModuleRecord>;
  listPlanModules(planCode: string): Promise<PlanModuleRecord[]>;
  deletePlanModule(planCode: string, moduleCode: string): Promise<boolean>;
}
```

- [ ] **Step 2: `domain/ports/tenant-override.repository.port.ts`**

```ts
export interface TenantOverrideRecord {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config: Record<string, unknown>;
  reason: string | null;
  expiresAt: Date | null;
  createdBy: string | null;
}

export interface UpsertOverrideInput {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config?: Record<string, unknown>;
  reason?: string;
  expiresAt?: Date;
  createdBy?: string;
}

export interface ITenantOverrideRepo {
  upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord>;
  findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null>;
  findAllForTenant(tenantId: string): Promise<TenantOverrideRecord[]>;
  listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]>;
  delete(tenantId: string, flagKey: string): Promise<boolean>;
}
```

- [ ] **Step 3: `domain/ports/telemetry.repository.port.ts`**

```ts
export interface UsageEventInput {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  reason: string;
  planCode: string | null;
  occurredAt: Date;
}

export interface UsageEventRecord extends UsageEventInput {
  id: string;
}

export interface TelemetryQueryFilter {
  flagKey?: string;
  from?: Date;
  to?: Date;
  page: number;
  pageSize: number;
}

export interface ITelemetryRepo {
  insertMany(events: UsageEventInput[]): Promise<number>;
  query(tenantId: string, filter: TelemetryQueryFilter): Promise<{ events: UsageEventRecord[]; total: number }>;
}
```

- [ ] **Step 4: `domain/ports/cache-store.port.ts`**

```ts
export interface ICacheStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, ttlSeconds: number): Promise<void>;
  del(key: string): Promise<void>;
  setNx(key: string, value: string, ttlSeconds: number): Promise<boolean>;
}
```

- [ ] **Step 5: Verify typecheck passes (no consumers yet, just syntax)**

Run: `npx tsc --noEmit -p .`
Expected: no errors (these files have zero external dependencies).

- [ ] **Step 6: Commit**

```bash
git add packages/gen-fmm-starter/src/domain
git commit -m "feat: define Gen_FMM domain ports"
```

---

### Task 4: Prisma schema + init migration

**Files:**
- Create: `packages/gen-fmm-starter/prisma/schema.prisma`
- Create: `packages/gen-fmm-starter/prisma/migrations/migration_lock.toml`
- Create: `packages/gen-fmm-starter/prisma/migrations/20260728100000_init/migration.sql`

**Interfaces:**
- Produces: Prisma models `Module`, `PlanModule`, `FeatureFlag`, `TenantFeatureFlag`, `FeatureUsageEvent` — table/column names below are consumed verbatim by Task 10-12's Prisma repos.

- [ ] **Step 1: `prisma/schema.prisma`**

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model Module {
  code         String   @id @db.VarChar(32)
  name         String   @db.VarChar(120)
  description  String?
  category     String?  @db.VarChar(60)
  isActive     Boolean  @default(true) @map("is_active")
  displayOrder Int      @default(0) @map("display_order")
  iconKey      String?  @map("icon_key") @db.VarChar(60)
  createdAt    DateTime @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt    DateTime @updatedAt @map("updated_at") @db.Timestamptz(6)

  planModules PlanModule[]
  flags       FeatureFlag[]

  @@map("module")
}

model PlanModule {
  planCode    String   @map("plan_code") @db.VarChar(60)
  moduleCode  String   @map("module_code") @db.VarChar(32)
  entitlement Json     @default("{}")
  createdAt   DateTime @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt   DateTime @updatedAt @map("updated_at") @db.Timestamptz(6)

  module Module @relation(fields: [moduleCode], references: [code], onDelete: Cascade)

  @@id([planCode, moduleCode])
  @@index([moduleCode])
  @@map("plan_module")
}

model FeatureFlag {
  key               String   @id @db.VarChar(128)
  moduleCode        String?  @map("module_code") @db.VarChar(32)
  defaultEnabled    Boolean  @default(false) @map("default_enabled")
  isGradualRollout  Boolean  @default(false) @map("is_gradual_rollout")
  rolloutPercentage Int      @default(0) @map("rollout_percentage") @db.SmallInt
  createdAt         DateTime @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt         DateTime @updatedAt @map("updated_at") @db.Timestamptz(6)

  module    Module?             @relation(fields: [moduleCode], references: [code], onDelete: SetNull)
  overrides TenantFeatureFlag[]

  @@index([moduleCode])
  @@map("feature_flag")
}

model TenantFeatureFlag {
  tenantId  String    @map("tenant_id") @db.Uuid
  flagKey   String    @map("flag_key") @db.VarChar(128)
  enabled   Boolean
  config    Json      @default("{}")
  reason    String?   @db.VarChar(300)
  expiresAt DateTime? @map("expires_at") @db.Timestamptz(6)
  createdBy String?   @map("created_by") @db.VarChar(120)
  createdAt DateTime  @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt DateTime  @updatedAt @map("updated_at") @db.Timestamptz(6)

  flag FeatureFlag @relation(fields: [flagKey], references: [key], onDelete: Cascade)

  @@id([tenantId, flagKey])
  @@map("tenant_feature_flag")
}

model FeatureUsageEvent {
  id         String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId   String   @map("tenant_id") @db.Uuid
  flagKey    String   @map("flag_key") @db.VarChar(128)
  enabled    Boolean
  reason     String   @db.VarChar(30)
  planCode   String?  @map("plan_code") @db.VarChar(60)
  occurredAt DateTime @default(now()) @map("occurred_at") @db.Timestamptz(6)

  @@index([tenantId, flagKey, occurredAt(sort: Desc)])
  @@map("feature_usage_event")
}
```

- [ ] **Step 2: `prisma/migrations/migration_lock.toml`**

```toml
provider = "postgresql"
```

- [ ] **Step 3: `prisma/migrations/20260728100000_init/migration.sql`**

```sql
-- CreateTable
CREATE TABLE "module" (
    "code" VARCHAR(32) NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "description" TEXT,
    "category" VARCHAR(60),
    "is_active" BOOLEAN NOT NULL DEFAULT true,
    "display_order" INTEGER NOT NULL DEFAULT 0,
    "icon_key" VARCHAR(60),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "module_pkey" PRIMARY KEY ("code")
);

-- CreateTable
CREATE TABLE "plan_module" (
    "plan_code" VARCHAR(60) NOT NULL,
    "module_code" VARCHAR(32) NOT NULL,
    "entitlement" JSONB NOT NULL DEFAULT '{}',
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "plan_module_pkey" PRIMARY KEY ("plan_code","module_code")
);

-- CreateTable
CREATE TABLE "feature_flag" (
    "key" VARCHAR(128) NOT NULL,
    "module_code" VARCHAR(32),
    "default_enabled" BOOLEAN NOT NULL DEFAULT false,
    "is_gradual_rollout" BOOLEAN NOT NULL DEFAULT false,
    "rollout_percentage" SMALLINT NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "feature_flag_pkey" PRIMARY KEY ("key")
);

-- CreateTable
CREATE TABLE "tenant_feature_flag" (
    "tenant_id" UUID NOT NULL,
    "flag_key" VARCHAR(128) NOT NULL,
    "enabled" BOOLEAN NOT NULL,
    "config" JSONB NOT NULL DEFAULT '{}',
    "reason" VARCHAR(300),
    "expires_at" TIMESTAMPTZ(6),
    "created_by" VARCHAR(120),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "tenant_feature_flag_pkey" PRIMARY KEY ("tenant_id","flag_key")
);

-- CreateTable
CREATE TABLE "feature_usage_event" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "flag_key" VARCHAR(128) NOT NULL,
    "enabled" BOOLEAN NOT NULL,
    "reason" VARCHAR(30) NOT NULL,
    "plan_code" VARCHAR(60),
    "occurred_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "feature_usage_event_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "plan_module_module_code_idx" ON "plan_module"("module_code");

-- CreateIndex
CREATE INDEX "feature_flag_module_code_idx" ON "feature_flag"("module_code");

-- CreateIndex
CREATE INDEX "feature_usage_event_tenant_id_flag_key_occurred_at_idx" ON "feature_usage_event"("tenant_id", "flag_key", "occurred_at" DESC);

-- AddForeignKey
ALTER TABLE "plan_module" ADD CONSTRAINT "plan_module_module_code_fkey" FOREIGN KEY ("module_code") REFERENCES "module"("code") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feature_flag" ADD CONSTRAINT "feature_flag_module_code_fkey" FOREIGN KEY ("module_code") REFERENCES "module"("code") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "tenant_feature_flag" ADD CONSTRAINT "tenant_feature_flag_flag_key_fkey" FOREIGN KEY ("flag_key") REFERENCES "feature_flag"("key") ON DELETE CASCADE ON UPDATE CASCADE;
```

- [ ] **Step 4: Start docker-compose, apply migration, generate client**

Run:
```bash
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && docker compose up -d
cd packages/gen-fmm-starter && DATABASE_URL="postgresql://genfmm:genfmm@localhost:5441/genfmm" npx prisma migrate deploy && npx prisma generate
```
Expected: migration applies cleanly, Prisma Client generated with no errors.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-fmm-starter/prisma
git commit -m "feat: add Prisma schema and init migration"
```

---

### Task 5: RLS + non-superuser app-role migrations

**Files:**
- Create: `packages/gen-fmm-starter/prisma/migrations/20260728100100_enable_rls/migration.sql`
- Create: `packages/gen-fmm-starter/prisma/migrations/20260728100200_create_app_role/migration.sql`

**Interfaces:**
- Produces: RLS enforcement on `tenant_feature_flag`/`feature_usage_event`, and role `genfmm_app` — consumed by every repo test from Task 6 onward and by `.env.example`'s `DATABASE_URL`.

- [ ] **Step 1: `prisma/migrations/20260728100100_enable_rls/migration.sql`**

```sql
-- Row-level security for the two tenant-scoped tables. FORCE (not just
-- ENABLE) so the table owner is subject to policies too. NULLIF guards
-- against the Postgres "custom GUC resets to '' not NULL on pooled
-- connections" gotcha from day one (see Gen_NOTIF/Gen_USG history for why):
-- withTenant() runs `SET LOCAL app.tenant_id` inside a transaction; once
-- that transaction commits, a reused pooled connection's next query outside
-- withTenant() would otherwise hit `current_setting(...)::uuid` casting ''
-- to uuid and throwing 22P02 instead of degrading to "no tenant set -> zero
-- rows". NULLIF(..., '') turns that placeholder default into NULL first.

ALTER TABLE "tenant_feature_flag" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "tenant_feature_flag" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_feature_flag_tenant_isolation ON "tenant_feature_flag"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "feature_usage_event" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "feature_usage_event" FORCE ROW LEVEL SECURITY;
CREATE POLICY feature_usage_event_tenant_isolation ON "feature_usage_event"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- module/plan_module/feature_flag are deliberately NOT tenant-scoped (no
-- tenant_id column at all) — global catalog data, identical for every tenant.
-- Confirmed correct against fmm-svc source and the CPMS decision matrix.
```

- [ ] **Step 2: `prisma/migrations/20260728100200_create_app_role/migration.sql`**

```sql
-- Non-superuser application role, so RLS is actually enforced at runtime.
--
-- docker-compose's POSTGRES_USER=genfmm is the initdb bootstrap role, and
-- Postgres always makes that role a superuser. Superusers bypass row-level
-- security unconditionally, even with FORCE ROW LEVEL SECURITY — so every
-- connection made as genfmm/genfmm bypasses the tenant-isolation policies
-- from the enable_rls migration entirely. Create a dedicated, unprivileged
-- role for the running application to connect as instead. `genfmm` (or
-- another privileged role) still runs migrations — CREATE POLICY / ALTER
-- TABLE ... FORCE ROW LEVEL SECURITY need elevated privileges this app role
-- deliberately does not have.
--
-- Local-dev credential only (this docker-compose Postgres isn't exposed
-- beyond localhost) — reuses the genfmm/genfmm/genfmm password convention
-- already in this project's .env.example.
--
-- SECURITY: this migration ships inside the published npm package (see
-- prisma/migrations in this package's package.json "files") and creates
-- genfmm_app with a well-known default password ('genfmm_app'). Before
-- deploying against any non-local Postgres instance, rotate it:
--   ALTER ROLE genfmm_app WITH PASSWORD '<a real secret, not this one>';
-- Do NOT run this migration as-is against a shared or production database
-- without rotating the password immediately after.

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'genfmm_app') THEN
    CREATE ROLE genfmm_app LOGIN PASSWORD 'genfmm_app';
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO genfmm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
  "module",
  "plan_module",
  "feature_flag",
  "tenant_feature_flag",
  "feature_usage_event"
TO genfmm_app;

-- No sequence GRANTs: every id column uses either a natural-key varchar PK
-- or `@default(dbgenerated("gen_random_uuid()"))`, never a serial/identity
-- sequence.

-- Deliberately NOT granted: BYPASSRLS, SUPERUSER, or membership in genfmm —
-- the entire point of this role is that it stays subject to RLS.
```

- [ ] **Step 3: Apply migrations, verify role exists**

Run:
```bash
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM/packages/gen-fmm-starter" && DATABASE_URL="postgresql://genfmm:genfmm@localhost:5441/genfmm" npx prisma migrate deploy
docker exec -it $(docker ps -qf "name=gen_fmm-postgres") psql -U genfmm -d genfmm -c "\du genfmm_app"
```
Expected: migrations apply; `genfmm_app` role listed, no superuser attribute.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-fmm-starter/prisma/migrations
git commit -m "feat: enable RLS on tenant-scoped tables and create genfmm_app role"
```

---

### Task 6: Cache infra — BoundedTtlCache (L1), RedisCacheStore (L2), stampede-safe getOrSet

**Files:**
- Create: `packages/gen-fmm-starter/src/infra/cache/bounded-ttl-cache.ts`
- Create: `packages/gen-fmm-starter/src/infra/cache/redis-cache-store.ts`
- Create: `packages/gen-fmm-starter/src/infra/cache/get-or-set.ts`
- Create: `packages/gen-fmm-starter/src/infra/cache/cache-keys.ts`
- Test: `packages/gen-fmm-starter/src/infra/cache/bounded-ttl-cache.test.ts`
- Test: `packages/gen-fmm-starter/src/infra/cache/get-or-set.test.ts`

**Interfaces:**
- Consumes: `ICacheStore` (Task 3).
- Produces: `BoundedTtlCache<T>` (`get`/`set`/`delete`/`deleteByPrefix`/`size`), `RedisCacheStore` (implements `ICacheStore`), `getOrSet<T>(cache, key, fetcher, ttlSeconds, lockTtlSeconds?)`, `FmmCacheKey.check(tenantId, flagKey)` / `FmmCacheKey.bulk(tenantId)` — all consumed by Task 9's `EntitlementCache`.

- [ ] **Step 1: Write failing test — `bounded-ttl-cache.test.ts`**

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { BoundedTtlCache } from "./bounded-ttl-cache.ts";

describe("BoundedTtlCache", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("returns undefined for a missing key", () => {
    const cache = new BoundedTtlCache<number>(10);
    expect(cache.get("missing")).toBeUndefined();
  });

  it("stores and retrieves a value within TTL", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 42, 1000);
    expect(cache.get("a")).toBe(42);
  });

  it("expires a value after its TTL", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 42, 1000);
    vi.advanceTimersByTime(1001);
    expect(cache.get("a")).toBeUndefined();
  });

  it("evicts the oldest entry when maxEntries is exceeded", () => {
    const cache = new BoundedTtlCache<number>(2);
    cache.set("a", 1, 10_000);
    cache.set("b", 2, 10_000);
    cache.set("c", 3, 10_000);
    expect(cache.get("a")).toBeUndefined();
    expect(cache.get("b")).toBe(2);
    expect(cache.get("c")).toBe(3);
    expect(cache.size()).toBe(2);
  });

  it("delete removes a single key", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("a", 1, 10_000);
    cache.delete("a");
    expect(cache.get("a")).toBeUndefined();
  });

  it("deleteByPrefix removes only matching keys", () => {
    const cache = new BoundedTtlCache<number>(10);
    cache.set("fmm:flags:tenant-1", 1, 10_000);
    cache.set("fmm:flags:tenant-2", 2, 10_000);
    cache.set("fmm:check:tenant-1:x", 3, 10_000);
    cache.deleteByPrefix("fmm:flags:tenant-1");
    expect(cache.get("fmm:flags:tenant-1")).toBeUndefined();
    expect(cache.get("fmm:flags:tenant-2")).toBe(2);
    expect(cache.get("fmm:check:tenant-1:x")).toBe(3);
  });
});
```

- [ ] **Step 2: Run test, verify it fails**

Run: `npx vitest run src/infra/cache/bounded-ttl-cache.test.ts`
Expected: FAIL — `./bounded-ttl-cache.ts` does not exist.

- [ ] **Step 3: Implement `bounded-ttl-cache.ts`**

```ts
interface Entry<T> {
  value: T;
  expiresAt: number;
}

export class BoundedTtlCache<T> {
  private readonly store = new Map<string, Entry<T>>();

  constructor(private readonly maxEntries: number) {}

  get(key: string): T | undefined {
    const entry = this.store.get(key);
    if (!entry) return undefined;
    if (Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return undefined;
    }
    return entry.value;
  }

  set(key: string, value: T, ttlMs: number): void {
    if (this.store.size >= this.maxEntries && !this.store.has(key)) {
      const oldestKey = this.store.keys().next().value;
      if (oldestKey !== undefined) this.store.delete(oldestKey);
    }
    this.store.set(key, { value, expiresAt: Date.now() + ttlMs });
  }

  delete(key: string): void {
    this.store.delete(key);
  }

  deleteByPrefix(prefix: string): void {
    for (const key of this.store.keys()) {
      if (key.startsWith(prefix)) this.store.delete(key);
    }
  }

  size(): number {
    return this.store.size;
  }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `npx vitest run src/infra/cache/bounded-ttl-cache.test.ts`
Expected: PASS, 6 tests.

- [ ] **Step 5: Implement `cache-keys.ts`**

```ts
export const FmmCacheKey = {
  check: (tenantId: string, flagKey: string): string => `fmm:check:${tenantId}:${flagKey}`,
  bulk: (tenantId: string): string => `fmm:flags:${tenantId}`,
  tenantCheckPrefix: (tenantId: string): string => `fmm:check:${tenantId}:`,
  tenantBulkPrefix: (tenantId: string): string => `fmm:flags:${tenantId}`,
};
```

- [ ] **Step 6: Implement `redis-cache-store.ts`**

```ts
import Redis from "ioredis";
import type { ICacheStore } from "../../domain/ports/cache-store.port.ts";

export class RedisCacheStore implements ICacheStore {
  private readonly client: Redis;

  constructor(url: string) {
    this.client = new Redis(url);
  }

  async get(key: string): Promise<string | null> {
    return this.client.get(key);
  }

  async set(key: string, value: string, ttlSeconds: number): Promise<void> {
    await this.client.set(key, value, "EX", ttlSeconds);
  }

  async del(key: string): Promise<void> {
    await this.client.del(key);
  }

  async setNx(key: string, value: string, ttlSeconds: number): Promise<boolean> {
    const result = await this.client.set(key, value, "EX", ttlSeconds, "NX");
    return result === "OK";
  }
}
```

- [ ] **Step 7: Write failing test — `get-or-set.test.ts`** (uses a fake `ICacheStore`, no real Redis)

```ts
import { describe, it, expect, vi } from "vitest";
import { getOrSet } from "./get-or-set.ts";
import type { ICacheStore } from "../../domain/ports/cache-store.port.ts";

function fakeCache(): ICacheStore & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    async get(key) { return data.get(key) ?? null; },
    async set(key, value) { data.set(key, value); },
    async del(key) { data.delete(key); },
    async setNx(key, value) {
      if (data.has(key)) return false;
      data.set(key, value);
      return true;
    },
  };
}

describe("getOrSet", () => {
  it("returns the cached value on a hit without calling the fetcher", async () => {
    const cache = fakeCache();
    cache.data.set("k", JSON.stringify({ v: 1 }));
    const fetcher = vi.fn(async () => ({ v: 2 }));
    const result = await getOrSet(cache, "k", fetcher, 60);
    expect(result).toEqual({ v: 1 });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("calls the fetcher and populates the cache on a miss", async () => {
    const cache = fakeCache();
    const fetcher = vi.fn(async () => ({ v: 42 }));
    const result = await getOrSet(cache, "k", fetcher, 60);
    expect(result).toEqual({ v: 42 });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(JSON.parse(cache.data.get("k")!)).toEqual({ v: 42 });
  });

  it("releases the lock after fetching so a subsequent call can acquire it again", async () => {
    const cache = fakeCache();
    await getOrSet(cache, "k", async () => ({ v: 1 }), 60);
    expect(cache.data.has("k:_lock")).toBe(false);
  });

  it("a concurrent miss that loses the lock race waits and reads the winner's value", async () => {
    const cache = fakeCache();
    // Simulate: another process already holds the lock and will populate
    // the key shortly.
    await cache.setNx("k:_lock", "1", 5);
    setTimeout(() => { cache.data.set("k", JSON.stringify({ v: 99 })); cache.data.delete("k:_lock"); }, 20);
    const fetcher = vi.fn(async () => ({ v: -1 }));
    const result = await getOrSet(cache, "k", fetcher, 60, 5);
    expect(result).toEqual({ v: 99 });
    expect(fetcher).not.toHaveBeenCalled();
  }, 10_000);
});
```

- [ ] **Step 8: Run test, verify it fails**

Run: `npx vitest run src/infra/cache/get-or-set.test.ts --pool=forks`
Expected: FAIL — `./get-or-set.ts` does not exist. (Use real timers for this file: it uses `setTimeout`-based polling; run with `vi.useRealTimers()` implicitly since this test file does not call `vi.useFakeTimers()`.)

- [ ] **Step 9: Implement `get-or-set.ts`**

```ts
import type { ICacheStore } from "../../domain/ports/cache-store.port.ts";

function withJitter(ttlSeconds: number): number {
  const jitter = ttlSeconds * 0.1;
  return Math.round(ttlSeconds + (Math.random() * 2 - 1) * jitter);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getOrSet<T>(
  cache: ICacheStore,
  key: string,
  fetcher: () => Promise<T>,
  ttlSeconds: number,
  lockTtlSeconds = 5,
): Promise<T> {
  const cached = await cache.get(key);
  if (cached !== null) return JSON.parse(cached) as T;

  const lockKey = `${key}:_lock`;
  const acquired = await cache.setNx(lockKey, "1", lockTtlSeconds);

  if (acquired) {
    try {
      const value = await fetcher();
      if (value != null) await cache.set(key, JSON.stringify(value), withJitter(ttlSeconds));
      return value;
    } finally {
      await cache.del(lockKey);
    }
  }

  // Lost the race — poll every 50ms until the lock's deadline, then re-read;
  // if the lock is released without populating the value, bail early and
  // fetch directly rather than waiting out the full deadline.
  const deadlineMs = Date.now() + lockTtlSeconds * 1000;
  while (Date.now() < deadlineMs) {
    await sleep(50);
    const retried = await cache.get(key);
    if (retried !== null) return JSON.parse(retried) as T;
    const stillLocked = await cache.get(lockKey);
    if (stillLocked === null) break;
  }
  return fetcher();
}
```

- [ ] **Step 10: Run test, verify it passes**

Run: `npx vitest run src/infra/cache/get-or-set.test.ts src/infra/cache/bounded-ttl-cache.test.ts`
Expected: PASS, 10 tests total.

- [ ] **Step 11: Commit**

```bash
git add packages/gen-fmm-starter/src/infra/cache
git commit -m "feat: add L1 BoundedTtlCache, L2 RedisCacheStore, stampede-safe getOrSet"
```

---

### Task 7: Persistence — Prisma client + withTenant()

**Files:**
- Create: `packages/gen-fmm-starter/src/infra/persistence/prisma-client.ts`
- Create: `packages/gen-fmm-starter/src/infra/persistence/with-tenant.ts`
- Test: `packages/gen-fmm-starter/src/infra/persistence/with-tenant.test.ts`

**Interfaces:**
- Consumes: `InvalidTenantIdError` (Task 2).
- Produces: `getPrismaClient()`, `withTenant<T>(tenantId, fn)` — every repo in Tasks 10-12 wraps its tenant-scoped Prisma calls in this.

- [ ] **Step 1: Implement `prisma-client.ts`**

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

- [ ] **Step 2: Write failing test — `with-tenant.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { withTenant } from "./with-tenant.ts";
import { InvalidTenantIdError } from "../../common/errors.ts";

describe("withTenant", () => {
  it("rejects a non-UUID tenantId before touching the database", async () => {
    await expect(withTenant("not-a-uuid", async () => "unreachable")).rejects.toThrow(InvalidTenantIdError);
  });

  it("rejects an empty tenantId", async () => {
    await expect(withTenant("", async () => "unreachable")).rejects.toThrow(InvalidTenantIdError);
  });
});
```

- [ ] **Step 3: Run test, verify it fails**

Run: `npx vitest run src/infra/persistence/with-tenant.test.ts`
Expected: FAIL — `./with-tenant.ts` does not exist.

- [ ] **Step 4: Implement `with-tenant.ts`**

```ts
import type { PrismaClient } from "@prisma/client";
import { getPrismaClient } from "./prisma-client.ts";
import { InvalidTenantIdError } from "../../common/errors.ts";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function withTenant<T>(
  tenantId: string,
  fn: (tx: PrismaClient) => Promise<T>,
): Promise<T> {
  if (!UUID_RE.test(tenantId)) {
    throw new InvalidTenantIdError(tenantId);
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

- [ ] **Step 5: Run test, verify it passes**

Run: `npx vitest run src/infra/persistence/with-tenant.test.ts`
Expected: PASS, 2 tests (the guard rejects before any Prisma call, so no live DB is needed for this test file).

- [ ] **Step 6: Commit**

```bash
git add packages/gen-fmm-starter/src/infra/persistence
git commit -m "feat: add Prisma client singleton and withTenant() RLS wrapper"
```

---

### Task 8: Entitlement domain logic — resolveEntitlement + computeRolloutBucket

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/types.ts`
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/resolve.ts`
- Test: `packages/gen-fmm-starter/src/modules/entitlement/v1/resolve.test.ts`

**Interfaces:**
- Consumes: `FeatureFlagRecord`, `TenantOverrideRecord` (Task 3).
- Produces: `EntitlementReason`, `EntitlementCheckResult`, `BulkEntitlementResult` (types.ts); `computeRolloutBucket(tenantId, flagKey)`, `resolveEntitlement(flag, override, planModuleCodes, tenantId, flagKey, now?)` (resolve.ts) — consumed by Task 9's `EntitlementCache` and Task 13's `EntitlementService`.

- [ ] **Step 1: Implement `types.ts`**

```ts
export type EntitlementReason = "TENANT_OVERRIDE" | "PLAN_ENTITLEMENT" | "FLAG_DEFAULT" | "ROLLOUT" | "FLAG_NOT_FOUND";

export interface EntitlementCheckResult {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  reason: EntitlementReason;
  cacheHit: "l1" | "l2" | "miss";
  latencyMs: number;
}

export interface BulkEntitlementResult {
  tenantId: string;
  flags: Record<string, { enabled: boolean; reason: EntitlementReason }>;
}
```

- [ ] **Step 2: Write failing test — `resolve.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { resolveEntitlement, computeRolloutBucket } from "./resolve.ts";
import type { FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";

function flag(overrides: Partial<FeatureFlagRecord> = {}): FeatureFlagRecord {
  return {
    key: "new_dashboard",
    moduleCode: "reporting",
    defaultEnabled: false,
    isGradualRollout: false,
    rolloutPercentage: 0,
    ...overrides,
  };
}

function override(overrides: Partial<TenantOverrideRecord> = {}): TenantOverrideRecord {
  return {
    tenantId: TENANT,
    flagKey: "new_dashboard",
    enabled: true,
    config: {},
    reason: null,
    expiresAt: null,
    createdBy: null,
    ...overrides,
  };
}

describe("resolveEntitlement", () => {
  it("returns FLAG_NOT_FOUND, disabled, when the flag doesn't exist", () => {
    const result = resolveEntitlement(null, null, [], TENANT, "missing");
    expect(result).toEqual({ enabled: false, reason: "FLAG_NOT_FOUND" });
  });

  it("a live tenant override wins over everything else", () => {
    const result = resolveEntitlement(flag({ defaultEnabled: false }), override({ enabled: true }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("a disabled override wins over a true default", () => {
    const result = resolveEntitlement(flag({ defaultEnabled: true }), override({ enabled: false }), ["reporting"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "TENANT_OVERRIDE" });
  });

  it("an expired override falls through to the next tier", () => {
    const past = new Date(Date.now() - 60_000);
    const result = resolveEntitlement(flag({ defaultEnabled: true, moduleCode: null }), override({ expiresAt: past }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("an override expiring in the future still applies", () => {
    const future = new Date(Date.now() + 60_000);
    const result = resolveEntitlement(flag(), override({ enabled: true, expiresAt: future }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("grants via plan entitlement when the flag's module is in the plan", () => {
    const result = resolveEntitlement(flag({ moduleCode: "reporting" }), null, ["reporting", "billing"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "PLAN_ENTITLEMENT" });
  });

  it("falls through plan entitlement when the module isn't in the plan", () => {
    const result = resolveEntitlement(flag({ moduleCode: "reporting", defaultEnabled: false }), null, ["billing"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "FLAG_DEFAULT" });
  });

  it("falls through plan entitlement when the flag has no module", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, defaultEnabled: true }), null, ["reporting"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("returns FLAG_DEFAULT true when not gradual rollout and default is true", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, defaultEnabled: true, isGradualRollout: false }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("rollout at 100% always enables", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 100 }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "ROLLOUT" });
  });

  it("rollout at 0% always disables", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 0 }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "ROLLOUT" });
  });

  it("gradual rollout ignores defaultEnabled entirely", () => {
    const result = resolveEntitlement(
      flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 100, defaultEnabled: false }),
      null, [], TENANT, "new_dashboard",
    );
    expect(result).toEqual({ enabled: true, reason: "ROLLOUT" });
  });
});

describe("computeRolloutBucket", () => {
  it("always returns a value in [0, 99]", () => {
    for (let i = 0; i < 50; i++) {
      const bucket = computeRolloutBucket(`tenant-${i}`, "some_flag");
      expect(bucket).toBeGreaterThanOrEqual(0);
      expect(bucket).toBeLessThanOrEqual(99);
    }
  });

  it("is deterministic for the same inputs", () => {
    expect(computeRolloutBucket(TENANT, "new_dashboard")).toBe(computeRolloutBucket(TENANT, "new_dashboard"));
  });

  it("varies across different tenant/flag pairs", () => {
    const buckets = new Set<number>();
    for (let i = 0; i < 20; i++) buckets.add(computeRolloutBucket(`tenant-${i}`, "new_dashboard"));
    expect(buckets.size).toBeGreaterThan(1);
  });
});
```

- [ ] **Step 3: Run test, verify it fails**

Run: `npx vitest run src/modules/entitlement/v1/resolve.test.ts`
Expected: FAIL — `./resolve.ts` does not exist.

- [ ] **Step 4: Implement `resolve.ts`**

```ts
import { createHash } from "node:crypto";
import type { FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import type { EntitlementReason } from "./types.ts";

export interface ResolvedEntitlement {
  enabled: boolean;
  reason: EntitlementReason;
}

export function computeRolloutBucket(tenantId: string, flagKey: string): number {
  const hash = createHash("sha256").update(`${tenantId}:${flagKey}`).digest("hex");
  return parseInt(hash.substring(0, 8), 16) % 100;
}

export function resolveEntitlement(
  flag: FeatureFlagRecord | null,
  override: TenantOverrideRecord | null,
  planModuleCodes: string[],
  tenantId: string,
  flagKey: string,
  now: Date = new Date(),
): ResolvedEntitlement {
  if (!flag) return { enabled: false, reason: "FLAG_NOT_FOUND" };

  if (override && (!override.expiresAt || override.expiresAt >= now)) {
    return { enabled: override.enabled, reason: "TENANT_OVERRIDE" };
  }

  if (flag.moduleCode && planModuleCodes.includes(flag.moduleCode)) {
    return { enabled: true, reason: "PLAN_ENTITLEMENT" };
  }

  if (!flag.isGradualRollout) {
    return { enabled: flag.defaultEnabled, reason: "FLAG_DEFAULT" };
  }

  const bucket = computeRolloutBucket(tenantId, flagKey);
  return { enabled: bucket < flag.rolloutPercentage, reason: "ROLLOUT" };
}
```

- [ ] **Step 5: Run test, verify it passes**

Run: `npx vitest run src/modules/entitlement/v1/resolve.test.ts`
Expected: PASS, 15 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/entitlement
git commit -m "feat: add resolveEntitlement 4-tier resolution and computeRolloutBucket"
```

---

### Task 9: Entitlement cache chain — EntitlementCache (L1+L2)

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/cache.ts`
- Test: `packages/gen-fmm-starter/src/modules/entitlement/v1/cache.test.ts`

**Interfaces:**
- Consumes: `BoundedTtlCache` (Task 6), `getOrSet`, `FmmCacheKey` (Task 6), `ICacheStore` (Task 3), `EntitlementCheckResult`, `BulkEntitlementResult` (Task 8).
- Produces: `EntitlementCache` class with `resolveCheckCached(tenantId, flagKey, fetcher)`, `resolveBulkCached(tenantId, fetcher)`, `invalidateTenantFlag(tenantId, flagKey)`, `invalidateFlag(flagKey)` — consumed by Task 10 (`CatalogService`), Task 11 (`OverrideService`), Task 13 (`EntitlementService`).

- [ ] **Step 1: Write failing test — `cache.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import { EntitlementCache } from "./cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCache(): ICacheStore & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    async get(key) { return data.get(key) ?? null; },
    async set(key, value) { data.set(key, value); },
    async del(key) { data.delete(key); },
    async setNx(key, value) {
      if (data.has(key)) return false;
      data.set(key, value);
      return true;
    },
  };
}

const BASE = { tenantId: "t1", flagKey: "f1", enabled: true, reason: "FLAG_DEFAULT" as const };

describe("EntitlementCache", () => {
  it("a first call is a miss and calls the fetcher", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    const result = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(result.cacheHit).toBe("miss");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("a second call hits L1 without calling the fetcher again", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    await cache.resolveCheckCached("t1", "f1", fetcher);
    const second = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(second.cacheHit).toBe("l1");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("invalidateTenantFlag purges L1 so the next call misses again", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    await cache.resolveCheckCached("t1", "f1", fetcher);
    cache.invalidateTenantFlag("t1", "f1");
    const result = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(result.cacheHit).toBe("miss");
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("resolveBulkCached is a miss on first call and caches the result", async () => {
    const underlying = fakeCache();
    const cache = new EntitlementCache(underlying, 100, 60_000, 300);
    const fetcher = vi.fn(async () => ({ tenantId: "t1", flags: { f1: { enabled: true, reason: "FLAG_DEFAULT" as const } } }));
    const result = await cache.resolveBulkCached("t1", fetcher);
    expect(result.flags.f1.enabled).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(underlying.data.has("fmm:flags:t1")).toBe(true);
  });

  it("invalidateTenantFlag also purges the tenant's bulk cache entry", async () => {
    const underlying = fakeCache();
    const cache = new EntitlementCache(underlying, 100, 60_000, 300);
    await cache.resolveBulkCached("t1", async () => ({ tenantId: "t1", flags: {} }));
    cache.invalidateTenantFlag("t1", "f1");
    expect(underlying.data.has("fmm:flags:t1")).toBe(false);
  });
});
```

- [ ] **Step 2: Run test, verify it fails**

Run: `npx vitest run src/modules/entitlement/v1/cache.test.ts`
Expected: FAIL — `./cache.ts` does not exist.

- [ ] **Step 3: Implement `cache.ts`**

```ts
import { BoundedTtlCache } from "../../../infra/cache/bounded-ttl-cache.ts";
import { getOrSet } from "../../../infra/cache/get-or-set.ts";
import { FmmCacheKey } from "../../../infra/cache/cache-keys.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./types.ts";

type UncachedCheckResult = Omit<EntitlementCheckResult, "cacheHit" | "latencyMs">;

export class EntitlementCache {
  private readonly l1: BoundedTtlCache<EntitlementCheckResult>;

  constructor(
    private readonly l2: ICacheStore,
    l1MaxEntries: number,
    private readonly l1TtlMs: number,
    private readonly l2TtlSeconds: number,
  ) {
    this.l1 = new BoundedTtlCache(l1MaxEntries);
  }

  async resolveCheckCached(
    tenantId: string,
    flagKey: string,
    fetcher: () => Promise<UncachedCheckResult>,
  ): Promise<EntitlementCheckResult> {
    const start = Date.now();
    const key = FmmCacheKey.check(tenantId, flagKey);

    const l1Hit = this.l1.get(key);
    if (l1Hit) return { ...l1Hit, cacheHit: "l1", latencyMs: Date.now() - start };

    // tier starts "l2" and is only flipped to "miss" if the fetcher itself
    // actually runs — distinguishes a real L2 hit from a DB fetch inside
    // getOrSet's own cache-population path.
    let tier: "l2" | "miss" = "l2";
    const wrapped = async (): Promise<UncachedCheckResult> => {
      tier = "miss";
      return fetcher();
    };

    const resolved = (await getOrSet(this.l2, key, wrapped, this.l2TtlSeconds)) ?? (await fetcher());
    const result: EntitlementCheckResult = { ...resolved, cacheHit: tier, latencyMs: Date.now() - start };
    this.l1.set(key, result, this.l1TtlMs);
    return result;
  }

  async resolveBulkCached(
    tenantId: string,
    fetcher: () => Promise<BulkEntitlementResult>,
  ): Promise<BulkEntitlementResult> {
    const key = FmmCacheKey.bulk(tenantId);
    return getOrSet(this.l2, key, fetcher, this.l2TtlSeconds);
  }

  invalidateTenantFlag(tenantId: string, flagKey: string): void {
    this.l1.delete(FmmCacheKey.check(tenantId, flagKey));
    this.l1.deleteByPrefix(FmmCacheKey.tenantBulkPrefix(tenantId));
    void this.l2.del(FmmCacheKey.check(tenantId, flagKey));
    void this.l2.del(FmmCacheKey.bulk(tenantId));
  }

  invalidateFlag(flagKey: string): void {
    // ponytail: a catalog-level flag change (default/rollout %) can affect
    // every tenant, but this in-process L1 has no cross-tenant index to
    // scan, and the ICacheStore port has no SCAN primitive to glob-purge L2
    // safely. Correctness after a catalog change relies on the short
    // check-cache TTL (CHECK_CACHE_TTL_SECS) plus the host-supplied
    // onFlagChanged broadcast (see create-gen-fmm.ts) for other pods' L1.
    // Upgrade path: add a SCAN-capable method to ICacheStore if exact
    // immediate cross-tenant invalidation is ever required.
    void flagKey;
  }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `npx vitest run src/modules/entitlement/v1/cache.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/entitlement/v1/cache.ts packages/gen-fmm-starter/src/modules/entitlement/v1/cache.test.ts
git commit -m "feat: add EntitlementCache L1+L2 chain"
```

---

### Task 10: Catalog module — repo, service, controller, routes

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/catalog/v1/repo.ts`
- Create: `packages/gen-fmm-starter/src/modules/catalog/v1/service.ts`
- Create: `packages/gen-fmm-starter/src/modules/catalog/v1/controller.ts`
- Create: `packages/gen-fmm-starter/src/modules/catalog/v1/routes.ts`
- Test: `packages/gen-fmm-starter/src/modules/catalog/v1/service.test.ts`

**Interfaces:**
- Consumes: `ICatalogRepo` and its types (Task 3), `ConflictError`/`NotFoundError` (Task 2), `EntitlementCache` (Task 9).
- Produces: `PrismaCatalogRepo` (implements `ICatalogRepo`), `CatalogService`, `CatalogController`, `catalogRoutes(controller)` — consumed by Task 14's factory. `CatalogService`'s constructor signature `(repo: ICatalogRepo, cache: EntitlementCache, onFlagChanged: (flagKey: string, tenantId?: string) => void)` is consumed by Task 14.

- [ ] **Step 1: Implement `repo.ts`**

```ts
import { Prisma } from "@prisma/client";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";
import type {
  ICatalogRepo,
  ModuleRecord,
  PlanModuleRecord,
  FeatureFlagRecord,
  CreateModuleInput,
  UpdateModuleInput,
  CreateFlagInput,
  UpdateFlagInput,
} from "../../../domain/ports/catalog.repository.port.ts";

function isNotFound(err: unknown): boolean {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2025";
}

function toPlanModuleRecord(row: { planCode: string; moduleCode: string; entitlement: unknown }): PlanModuleRecord {
  return { planCode: row.planCode, moduleCode: row.moduleCode, entitlement: row.entitlement as Record<string, unknown> };
}

export class PrismaCatalogRepo implements ICatalogRepo {
  async createModule(input: CreateModuleInput): Promise<ModuleRecord> {
    return getPrismaClient().module.create({ data: input });
  }

  async findModuleByCode(code: string): Promise<ModuleRecord | null> {
    return getPrismaClient().module.findUnique({ where: { code } });
  }

  async listModules(filter: { category?: string; isActive?: boolean }): Promise<ModuleRecord[]> {
    return getPrismaClient().module.findMany({
      where: {
        ...(filter.category ? { category: filter.category } : {}),
        ...(filter.isActive !== undefined ? { isActive: filter.isActive } : {}),
      },
      orderBy: { displayOrder: "asc" },
    });
  }

  async updateModule(code: string, input: UpdateModuleInput): Promise<ModuleRecord | null> {
    try {
      return await getPrismaClient().module.update({ where: { code }, data: input });
    } catch (err) {
      if (isNotFound(err)) return null;
      throw err;
    }
  }

  async createFlag(input: CreateFlagInput): Promise<FeatureFlagRecord> {
    return getPrismaClient().featureFlag.create({ data: input });
  }

  async findFlagByKey(key: string): Promise<FeatureFlagRecord | null> {
    return getPrismaClient().featureFlag.findUnique({ where: { key } });
  }

  async listFlags(filter: { moduleCode?: string }): Promise<FeatureFlagRecord[]> {
    return getPrismaClient().featureFlag.findMany({
      where: filter.moduleCode ? { moduleCode: filter.moduleCode } : {},
    });
  }

  async updateFlag(key: string, input: UpdateFlagInput): Promise<FeatureFlagRecord | null> {
    try {
      return await getPrismaClient().featureFlag.update({ where: { key }, data: input });
    } catch (err) {
      if (isNotFound(err)) return null;
      throw err;
    }
  }

  async deleteFlag(key: string): Promise<boolean> {
    try {
      await getPrismaClient().featureFlag.delete({ where: { key } });
      return true;
    } catch (err) {
      if (isNotFound(err)) return false;
      throw err;
    }
  }

  async upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>): Promise<PlanModuleRecord> {
    const row = await getPrismaClient().planModule.upsert({
      where: { planCode_moduleCode: { planCode, moduleCode } },
      create: { planCode, moduleCode, entitlement },
      update: { entitlement },
    });
    return toPlanModuleRecord(row);
  }

  async listPlanModules(planCode: string): Promise<PlanModuleRecord[]> {
    const rows = await getPrismaClient().planModule.findMany({ where: { planCode } });
    return rows.map(toPlanModuleRecord);
  }

  async deletePlanModule(planCode: string, moduleCode: string): Promise<boolean> {
    try {
      await getPrismaClient().planModule.delete({ where: { planCode_moduleCode: { planCode, moduleCode } } });
      return true;
    } catch (err) {
      if (isNotFound(err)) return false;
      throw err;
    }
  }
}
```

- [ ] **Step 2: Write failing test — `service.test.ts`** (uses a fake `ICatalogRepo`, no live DB)

```ts
import { describe, it, expect, vi } from "vitest";
import { CatalogService } from "./service.ts";
import { ConflictError, NotFoundError } from "../../../common/errors.ts";
import type { ICatalogRepo, FeatureFlagRecord, ModuleRecord } from "../../../domain/ports/catalog.repository.port.ts";
import { EntitlementCache } from "../../entitlement/v1/cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(),
    findModuleByCode: vi.fn(async () => null),
    listModules: vi.fn(async () => []),
    updateModule: vi.fn(async () => null),
    createFlag: vi.fn(),
    findFlagByKey: vi.fn(async () => null),
    listFlags: vi.fn(async () => []),
    updateFlag: vi.fn(async () => null),
    deleteFlag: vi.fn(async () => false),
    upsertPlanModule: vi.fn(),
    listPlanModules: vi.fn(async () => []),
    deletePlanModule: vi.fn(async () => false),
    ...overrides,
  };
}

describe("CatalogService", () => {
  it("createModule throws ConflictError when the code already exists", async () => {
    const existing: ModuleRecord = { code: "billing", name: "Billing", description: null, category: null, isActive: true, displayOrder: 0, iconKey: null };
    const repo = fakeRepo({ findModuleByCode: vi.fn(async () => existing) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.createModule({ code: "billing", name: "Billing" })).rejects.toThrow(ConflictError);
  });

  it("updateModule throws NotFoundError when the repo returns null", async () => {
    const repo = fakeRepo();
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.updateModule("missing", { name: "x" })).rejects.toThrow(NotFoundError);
  });

  it("updateFlag invalidates the cache and fires onFlagChanged", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 };
    const repo = fakeRepo({ updateFlag: vi.fn(async () => flag) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateFlag");
    const onFlagChanged = vi.fn();
    const service = new CatalogService(repo, cache, onFlagChanged);
    await service.updateFlag("f1", { defaultEnabled: true });
    expect(invalidateSpy).toHaveBeenCalledWith("f1");
    expect(onFlagChanged).toHaveBeenCalledWith("f1");
  });

  it("deleteFlag throws NotFoundError when nothing was deleted", async () => {
    const repo = fakeRepo({ deleteFlag: vi.fn(async () => false) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.deleteFlag("missing")).rejects.toThrow(NotFoundError);
  });
});
```

- [ ] **Step 3: Run test, verify it fails**

Run: `npx vitest run src/modules/catalog/v1/service.test.ts`
Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 4: Implement `service.ts`**

```ts
import { Prisma } from "@prisma/client";
import type {
  ICatalogRepo,
  CreateModuleInput,
  UpdateModuleInput,
  CreateFlagInput,
  UpdateFlagInput,
} from "../../../domain/ports/catalog.repository.port.ts";
import { ConflictError, NotFoundError } from "../../../common/errors.ts";
import type { EntitlementCache } from "../../entitlement/v1/cache.ts";

function isUniqueViolation(err: unknown): boolean {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2002";
}

export class CatalogService {
  constructor(
    private readonly repo: ICatalogRepo,
    private readonly cache: EntitlementCache,
    private readonly onFlagChanged: (flagKey: string, tenantId?: string) => void,
  ) {}

  async createModule(input: CreateModuleInput) {
    const existing = await this.repo.findModuleByCode(input.code);
    if (existing) throw new ConflictError("MODULE_ALREADY_EXISTS", `Module "${input.code}" already exists`);
    try {
      return await this.repo.createModule(input);
    } catch (err) {
      if (isUniqueViolation(err)) throw new ConflictError("MODULE_ALREADY_EXISTS", `Module "${input.code}" already exists`);
      throw err;
    }
  }

  listModules(filter: { category?: string; isActive?: boolean }) {
    return this.repo.listModules(filter);
  }

  async updateModule(code: string, input: UpdateModuleInput) {
    const updated = await this.repo.updateModule(code, input);
    if (!updated) throw new NotFoundError("MODULE_NOT_FOUND", `Module "${code}" not found`);
    return updated;
  }

  async createFlag(input: CreateFlagInput) {
    const existing = await this.repo.findFlagByKey(input.key);
    if (existing) throw new ConflictError("FLAG_ALREADY_EXISTS", `Flag "${input.key}" already exists`);
    try {
      return await this.repo.createFlag(input);
    } catch (err) {
      if (isUniqueViolation(err)) throw new ConflictError("FLAG_ALREADY_EXISTS", `Flag "${input.key}" already exists`);
      throw err;
    }
  }

  listFlags(filter: { moduleCode?: string }) {
    return this.repo.listFlags(filter);
  }

  async updateFlag(key: string, input: UpdateFlagInput) {
    const updated = await this.repo.updateFlag(key, input);
    if (!updated) throw new NotFoundError("FLAG_NOT_FOUND", `Flag "${key}" not found`);
    this.cache.invalidateFlag(key);
    this.onFlagChanged(key);
    return updated;
  }

  async deleteFlag(key: string): Promise<void> {
    const deleted = await this.repo.deleteFlag(key);
    if (!deleted) throw new NotFoundError("FLAG_NOT_FOUND", `Flag "${key}" not found`);
    this.cache.invalidateFlag(key);
    this.onFlagChanged(key);
  }

  async upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>) {
    const result = await this.repo.upsertPlanModule(planCode, moduleCode, entitlement);
    this.onFlagChanged(`plan:${planCode}`);
    return result;
  }

  listPlanModules(planCode: string) {
    return this.repo.listPlanModules(planCode);
  }

  async deletePlanModule(planCode: string, moduleCode: string): Promise<void> {
    const deleted = await this.repo.deletePlanModule(planCode, moduleCode);
    if (!deleted) throw new NotFoundError("PLAN_MODULE_NOT_FOUND", `Plan module mapping "${planCode}"/"${moduleCode}" not found`);
    this.onFlagChanged(`plan:${planCode}`);
  }
}
```

- [ ] **Step 5: Run test, verify it passes**

Run: `npx vitest run src/modules/catalog/v1/service.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 6: Implement `controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { CatalogService } from "./service.ts";

const createModuleSchema = z.object({
  code: z.string().min(1).max(32),
  name: z.string().min(1).max(120),
  description: z.string().optional(),
  category: z.string().max(60).optional(),
  isActive: z.boolean().optional(),
  displayOrder: z.number().int().optional(),
  iconKey: z.string().max(60).optional(),
});
const updateModuleSchema = createModuleSchema.omit({ code: true }).partial();
const listModulesQuerySchema = z.object({
  category: z.string().optional(),
  isActive: z.coerce.boolean().optional(),
});
const createFlagSchema = z.object({
  key: z.string().min(1).max(128),
  moduleCode: z.string().max(32).optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
});
const updateFlagSchema = z.object({
  moduleCode: z.string().max(32).nullable().optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
});
const upsertPlanModuleSchema = z.object({
  planCode: z.string().min(1).max(60),
  moduleCode: z.string().min(1).max(32),
  entitlement: z.record(z.unknown()).optional(),
});

export class CatalogController {
  constructor(private readonly service: CatalogService) {}

  createModule = async (req: Request, res: Response): Promise<void> => {
    const input = createModuleSchema.parse(req.body);
    const module = await this.service.createModule(input);
    res.status(201).json({ module });
  };

  listModules = async (req: Request, res: Response): Promise<void> => {
    const filter = listModulesQuerySchema.parse(req.query);
    const modules = await this.service.listModules(filter);
    res.json({ modules });
  };

  updateModule = async (req: Request, res: Response): Promise<void> => {
    const input = updateModuleSchema.parse(req.body);
    const module = await this.service.updateModule(req.params.code!, input);
    res.json({ module });
  };

  createFlag = async (req: Request, res: Response): Promise<void> => {
    const input = createFlagSchema.parse(req.body);
    const flag = await this.service.createFlag(input);
    res.status(201).json({ flag });
  };

  listFlags = async (req: Request, res: Response): Promise<void> => {
    const moduleCode = typeof req.query.moduleCode === "string" ? req.query.moduleCode : undefined;
    const flags = await this.service.listFlags({ moduleCode });
    res.json({ flags });
  };

  updateFlag = async (req: Request, res: Response): Promise<void> => {
    const input = updateFlagSchema.parse(req.body);
    const flag = await this.service.updateFlag(req.params.key!, input);
    res.json({ flag });
  };

  deleteFlag = async (req: Request, res: Response): Promise<void> => {
    await this.service.deleteFlag(req.params.key!);
    res.status(204).send();
  };

  upsertPlanModule = async (req: Request, res: Response): Promise<void> => {
    const input = upsertPlanModuleSchema.parse(req.body);
    const planModule = await this.service.upsertPlanModule(input.planCode, input.moduleCode, input.entitlement ?? {});
    res.json({ planModule });
  };

  listPlanModules = async (req: Request, res: Response): Promise<void> => {
    const planModules = await this.service.listPlanModules(req.params.planCode!);
    res.json({ planModules });
  };

  deletePlanModule = async (req: Request, res: Response): Promise<void> => {
    await this.service.deletePlanModule(req.params.planCode!, req.params.moduleCode!);
    res.status(204).send();
  };
}
```

- [ ] **Step 7: Implement `routes.ts`**

```ts
import { Router } from "express";
import type { CatalogController } from "./controller.ts";

export function catalogRoutes(controller: CatalogController): Router {
  const router = Router();
  router.post("/modules", controller.createModule);
  router.get("/modules", controller.listModules);
  router.patch("/modules/:code", controller.updateModule);

  router.post("/flags", controller.createFlag);
  router.get("/flags", controller.listFlags);
  router.patch("/flags/:key", controller.updateFlag);
  router.delete("/flags/:key", controller.deleteFlag);

  router.put("/plan-modules", controller.upsertPlanModule);
  router.get("/plans/:planCode/modules", controller.listPlanModules);
  router.delete("/plans/:planCode/modules/:moduleCode", controller.deletePlanModule);

  return router;
}
```

- [ ] **Step 8: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/catalog
git commit -m "feat: add catalog module CRUD (modules/flags/plan-modules)"
```

---

### Task 11: Overrides module — repo (RLS), service (expiry enforcement), controller, routes

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/overrides/v1/repo.ts`
- Create: `packages/gen-fmm-starter/src/modules/overrides/v1/service.ts`
- Create: `packages/gen-fmm-starter/src/modules/overrides/v1/controller.ts`
- Create: `packages/gen-fmm-starter/src/modules/overrides/v1/routes.ts`
- Test: `packages/gen-fmm-starter/src/modules/overrides/v1/service.test.ts`
- Test: `packages/gen-fmm-starter/tests/support/postgres-container.ts`
- Test: `packages/gen-fmm-starter/tests/integration/repos.test.ts`

**Interfaces:**
- Consumes: `ITenantOverrideRepo` and its types (Task 3), `withTenant` (Task 7), `BusinessRuleError` (Task 2), `EntitlementCache` (Task 9).
- Produces: `PrismaTenantOverrideRepo` (implements `ITenantOverrideRepo`, every call wrapped in `withTenant`), `OverrideService`, `OverrideController`, `overridesRoutes(controller)`. `OverrideService`'s constructor `(repo, cache, onFlagChanged)` matches `CatalogService`'s shape from Task 10 — consumed by Task 14.

- [ ] **Step 1: Implement `repo.ts`**

```ts
import type {
  ITenantOverrideRepo,
  TenantOverrideRecord,
  UpsertOverrideInput,
} from "../../../domain/ports/tenant-override.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

function toRecord(row: {
  tenantId: string; flagKey: string; enabled: boolean; config: unknown;
  reason: string | null; expiresAt: Date | null; createdBy: string | null;
}): TenantOverrideRecord {
  return {
    tenantId: row.tenantId,
    flagKey: row.flagKey,
    enabled: row.enabled,
    config: row.config as Record<string, unknown>,
    reason: row.reason,
    expiresAt: row.expiresAt,
    createdBy: row.createdBy,
  };
}

export class PrismaTenantOverrideRepo implements ITenantOverrideRepo {
  async upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord> {
    return withTenant(input.tenantId, async (tx) => {
      const row = await tx.tenantFeatureFlag.upsert({
        where: { tenantId_flagKey: { tenantId: input.tenantId, flagKey: input.flagKey } },
        create: {
          tenantId: input.tenantId,
          flagKey: input.flagKey,
          enabled: input.enabled,
          config: input.config ?? {},
          reason: input.reason ?? null,
          expiresAt: input.expiresAt ?? null,
          createdBy: input.createdBy ?? null,
        },
        update: {
          enabled: input.enabled,
          config: input.config ?? {},
          reason: input.reason ?? null,
          expiresAt: input.expiresAt ?? null,
          createdBy: input.createdBy ?? null,
        },
      });
      return toRecord(row);
    });
  }

  async findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null> {
    return withTenant(tenantId, async (tx) => {
      const row = await tx.tenantFeatureFlag.findUnique({ where: { tenantId_flagKey: { tenantId, flagKey } } });
      return row ? toRecord(row) : null;
    });
  }

  async findAllForTenant(tenantId: string): Promise<TenantOverrideRecord[]> {
    return withTenant(tenantId, async (tx) => {
      const rows = await tx.tenantFeatureFlag.findMany({ where: { tenantId } });
      return rows.map(toRecord);
    });
  }

  async listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]> {
    // Cross-tenant admin listing has no single tenant to scope withTenant()
    // to; RLS is bypassed here deliberately (this method exists for a
    // host-fronted admin surface — the host is responsible for gating who
    // may call it, same as every other route in this library).
    const rows = await this.rawFindMany(filter);
    return rows.map(toRecord);
  }

  private async rawFindMany(filter: { tenantId?: string; flagKey?: string }) {
    const { getPrismaClient } = await import("../../../infra/persistence/prisma-client.ts");
    return getPrismaClient().tenantFeatureFlag.findMany({
      where: {
        ...(filter.tenantId ? { tenantId: filter.tenantId } : {}),
        ...(filter.flagKey ? { flagKey: filter.flagKey } : {}),
      },
    });
  }

  async delete(tenantId: string, flagKey: string): Promise<boolean> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.tenantFeatureFlag.deleteMany({ where: { tenantId, flagKey } });
      return result.count > 0;
    });
  }
}
```

- [ ] **Step 2: Write failing test — `service.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import { OverrideService } from "./service.ts";
import { BusinessRuleError } from "../../../common/errors.ts";
import type { ITenantOverrideRepo, TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import { EntitlementCache } from "../../entitlement/v1/cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeRepo(overrides: Partial<ITenantOverrideRepo> = {}): ITenantOverrideRepo {
  return {
    upsert: vi.fn(),
    findOne: vi.fn(async () => null),
    findAllForTenant: vi.fn(async () => []),
    listAll: vi.fn(async () => []),
    delete: vi.fn(async () => false),
    ...overrides,
  };
}

const TENANT = "11111111-1111-1111-1111-111111111111";

describe("OverrideService", () => {
  it("rejects an expiresAt already in the past", async () => {
    const service = new OverrideService(fakeRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(
      service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: true, expiresAt: new Date(Date.now() - 1000) }),
    ).rejects.toThrow(BusinessRuleError);
  });

  it("accepts an expiresAt in the future, persists, invalidates cache, fires onFlagChanged", async () => {
    const record: TenantOverrideRecord = {
      tenantId: TENANT, flagKey: "f1", enabled: true, config: {}, reason: null,
      expiresAt: new Date(Date.now() + 60_000), createdBy: null,
    };
    const repo = fakeRepo({ upsert: vi.fn(async () => record) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateTenantFlag");
    const onFlagChanged = vi.fn();
    const service = new OverrideService(repo, cache, onFlagChanged);
    const result = await service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: true, expiresAt: record.expiresAt! });
    expect(result).toEqual(record);
    expect(invalidateSpy).toHaveBeenCalledWith(TENANT, "f1");
    expect(onFlagChanged).toHaveBeenCalledWith("f1", TENANT);
  });

  it("accepts no expiresAt (permanent override)", async () => {
    const record: TenantOverrideRecord = {
      tenantId: TENANT, flagKey: "f1", enabled: false, config: {}, reason: null, expiresAt: null, createdBy: null,
    };
    const repo = fakeRepo({ upsert: vi.fn(async () => record) });
    const service = new OverrideService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    const result = await service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: false });
    expect(result.expiresAt).toBeNull();
  });

  it("delete only invalidates cache when a row was actually deleted", async () => {
    const repo = fakeRepo({ delete: vi.fn(async () => false) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateTenantFlag");
    const onFlagChanged = vi.fn();
    const service = new OverrideService(repo, cache, onFlagChanged);
    const result = await service.delete(TENANT, "f1");
    expect(result).toBe(false);
    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(onFlagChanged).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: Run test, verify it fails**

Run: `npx vitest run src/modules/overrides/v1/service.test.ts`
Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 4: Implement `service.ts`**

```ts
import type {
  ITenantOverrideRepo,
  UpsertOverrideInput,
  TenantOverrideRecord,
} from "../../../domain/ports/tenant-override.repository.port.ts";
import { BusinessRuleError } from "../../../common/errors.ts";
import type { EntitlementCache } from "../../entitlement/v1/cache.ts";

export class OverrideService {
  constructor(
    private readonly repo: ITenantOverrideRepo,
    private readonly cache: EntitlementCache,
    private readonly onFlagChanged: (flagKey: string, tenantId?: string) => void,
  ) {}

  async upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord> {
    if (input.expiresAt && input.expiresAt <= new Date()) {
      throw new BusinessRuleError("OVERRIDE_EXPIRY_IN_PAST", "expiresAt must be in the future");
    }
    const record = await this.repo.upsert(input);
    this.cache.invalidateTenantFlag(input.tenantId, input.flagKey);
    this.onFlagChanged(input.flagKey, input.tenantId);
    return record;
  }

  findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null> {
    return this.repo.findOne(tenantId, flagKey);
  }

  listForTenant(tenantId: string): Promise<TenantOverrideRecord[]> {
    return this.repo.findAllForTenant(tenantId);
  }

  listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]> {
    return this.repo.listAll(filter);
  }

  async delete(tenantId: string, flagKey: string): Promise<boolean> {
    const deleted = await this.repo.delete(tenantId, flagKey);
    if (deleted) {
      this.cache.invalidateTenantFlag(tenantId, flagKey);
      this.onFlagChanged(flagKey, tenantId);
    }
    return deleted;
  }
}
```

- [ ] **Step 5: Run test, verify it passes**

Run: `npx vitest run src/modules/overrides/v1/service.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 6: Implement `controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { OverrideService } from "./service.ts";
import { NotFoundError } from "../../../common/errors.ts";

const upsertSchema = z.object({
  tenantId: z.string().uuid(),
  flagKey: z.string().min(1).max(128),
  enabled: z.boolean(),
  config: z.record(z.unknown()).optional(),
  reason: z.string().max(300).optional(),
  expiresAt: z.coerce.date().optional(),
  createdBy: z.string().max(120).optional(),
});
const listAllQuerySchema = z.object({ tenantId: z.string().uuid().optional(), flagKey: z.string().optional() });
const tenantIdQuerySchema = z.object({ tenantId: z.string().uuid() });

export class OverrideController {
  constructor(private readonly service: OverrideService) {}

  upsert = async (req: Request, res: Response): Promise<void> => {
    const input = upsertSchema.parse(req.body);
    const override = await this.service.upsert(input);
    res.json({ override });
  };

  listAll = async (req: Request, res: Response): Promise<void> => {
    const filter = listAllQuerySchema.parse(req.query);
    const overrides = await this.service.listAll(filter);
    res.json({ overrides });
  };

  listForTenant = async (req: Request, res: Response): Promise<void> => {
    const overrides = await this.service.listForTenant(req.params.tenantId!);
    res.json({ overrides });
  };

  findOne = async (req: Request, res: Response): Promise<void> => {
    const override = await this.service.findOne(req.params.tenantId!, req.params.flagKey!);
    if (!override) throw new NotFoundError("OVERRIDE_NOT_FOUND", "No override for this tenant/flag");
    res.json({ override });
  };

  delete = async (req: Request, res: Response): Promise<void> => {
    const { tenantId } = tenantIdQuerySchema.parse(req.query);
    const deleted = await this.service.delete(tenantId, req.params.flagKey!);
    if (!deleted) throw new NotFoundError("OVERRIDE_NOT_FOUND", "No override for this tenant/flag");
    res.status(204).send();
  };
}
```

- [ ] **Step 7: Implement `routes.ts`**

```ts
import { Router } from "express";
import type { OverrideController } from "./controller.ts";

export function overridesRoutes(controller: OverrideController): Router {
  const router = Router();
  router.post("/", controller.upsert);
  router.get("/", controller.listAll);
  router.get("/:tenantId", controller.listForTenant);
  router.get("/:tenantId/:flagKey", controller.findOne);
  router.delete("/:tenantId/:flagKey", controller.delete);
  return router;
}
```

- [ ] **Step 8: `tests/support/postgres-container.ts`** (Testcontainers helper, mirrors Gen_NOTIF's — connects through `genfmm_app`, never the superuser bootstrap role, so RLS is actually live under test)

```ts
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { PrismaClient } from "@prisma/client";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { getPrismaClient } from "../../src/infra/persistence/prisma-client.ts";

export interface TestPostgres {
  prisma: PrismaClient;
  stop: () => Promise<void>;
}

const APP_ROLE = "genfmm_app";
const APP_ROLE_PASSWORD = "genfmm_app";

export async function startPostgresContainer(): Promise<TestPostgres> {
  const container: StartedPostgreSqlContainer = await new PostgreSqlContainer("postgres:15").start();
  const adminUrl = container.getConnectionUri();

  execSync("npx prisma migrate deploy", {
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    env: { ...process.env, DATABASE_URL: adminUrl },
    stdio: "inherit",
  });

  const appUrl = new URL(adminUrl);
  appUrl.username = APP_ROLE;
  appUrl.password = APP_ROLE_PASSWORD;

  process.env.DATABASE_URL = appUrl.toString();
  const prisma = getPrismaClient();

  return {
    prisma,
    stop: async () => {
      await prisma.$disconnect();
      await container.stop();
    },
  };
}
```

- [ ] **Step 9: Write integration test — `tests/integration/repos.test.ts`** (real Postgres via Testcontainers, proves RLS is actually enforced and expiry actually works)

```ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaCatalogRepo } from "../../src/modules/catalog/v1/repo.ts";
import { PrismaTenantOverrideRepo } from "../../src/modules/overrides/v1/repo.ts";
import { getPrismaClient } from "../../src/infra/persistence/prisma-client.ts";

const TENANT_A = "11111111-1111-1111-1111-111111111111";
const TENANT_B = "22222222-2222-2222-2222-222222222222";

describe("Prisma repos against real Postgres (RLS enforcement)", () => {
  let pg: TestPostgres;
  let catalogRepo: PrismaCatalogRepo;
  let overrideRepo: PrismaTenantOverrideRepo;

  beforeAll(async () => {
    pg = await startPostgresContainer();
    catalogRepo = new PrismaCatalogRepo();
    overrideRepo = new PrismaTenantOverrideRepo();
    await getPrismaClient().module.create({ data: { code: "reporting", name: "Reporting" } });
    await getPrismaClient().featureFlag.create({ data: { key: "new_dashboard", moduleCode: "reporting" } });
  }, 120_000);

  afterAll(async () => { await pg.stop(); });

  it("catalog CRUD works against the real global (non-RLS) tables", async () => {
    const module = await catalogRepo.findModuleByCode("reporting");
    expect(module?.name).toBe("Reporting");
    const flag = await catalogRepo.findFlagByKey("new_dashboard");
    expect(flag?.moduleCode).toBe("reporting");
  });

  it("an override written for tenant A is invisible when queried as tenant B (RLS isolation)", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    const asB = await overrideRepo.findOne(TENANT_B, "new_dashboard");
    expect(asB).toBeNull();
    const asA = await overrideRepo.findOne(TENANT_A, "new_dashboard");
    expect(asA?.enabled).toBe(true);
  });

  it("a second upsert for the same tenant+flag succeeds (proves RLS isn't silently blocking legitimate writes)", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    const updated = await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: false });
    expect(updated.enabled).toBe(false);
  });

  it("expiresAt round-trips correctly and is not silently discarded (the source bug this library fixes)", async () => {
    const future = new Date(Date.now() + 3_600_000);
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true, expiresAt: future });
    const found = await overrideRepo.findOne(TENANT_A, "new_dashboard");
    expect(found?.expiresAt?.getTime()).toBe(future.getTime());
  });

  it("delete only removes the row for the given tenant, not other tenants' rows", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    await overrideRepo.upsert({ tenantId: TENANT_B, flagKey: "new_dashboard", enabled: true });
    await overrideRepo.delete(TENANT_A, "new_dashboard");
    expect(await overrideRepo.findOne(TENANT_A, "new_dashboard")).toBeNull();
    expect(await overrideRepo.findOne(TENANT_B, "new_dashboard")).not.toBeNull();
  });
});
```

- [ ] **Step 10: Run the integration test (requires Docker running)**

Run: `npx vitest run tests/integration/repos.test.ts`
Expected: PASS, 5 tests (Testcontainers pulls `postgres:15` and applies all migrations from Tasks 4-5 automatically).

- [ ] **Step 11: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/overrides packages/gen-fmm-starter/tests
git commit -m "feat: add tenant override CRUD with RLS-backed repo and expiry enforcement"
```

---

### Task 12: Telemetry module — repo (RLS), service (ring buffer + host-triggered flush), controller, routes

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/telemetry/v1/repo.ts`
- Create: `packages/gen-fmm-starter/src/modules/telemetry/v1/service.ts`
- Create: `packages/gen-fmm-starter/src/modules/telemetry/v1/controller.ts`
- Create: `packages/gen-fmm-starter/src/modules/telemetry/v1/routes.ts`
- Test: `packages/gen-fmm-starter/src/modules/telemetry/v1/service.test.ts`

**Interfaces:**
- Consumes: `ITelemetryRepo` and its types (Task 3), `withTenant` (Task 7).
- Produces: `PrismaTelemetryRepo` (implements `ITelemetryRepo`), `TelemetryService` (`record(...)`, `flush()`, `query(...)`, `bufferSize()`), `TelemetryController`, `telemetryRoutes(controller)`. `TelemetryService.record`'s signature `(tenantId, flagKey, enabled, reason, planCode)` is consumed by Task 13's `EntitlementService` as its `recordUsage` callback.

- [ ] **Step 1: Implement `repo.ts`**

```ts
import type {
  ITelemetryRepo,
  UsageEventInput,
  UsageEventRecord,
  TelemetryQueryFilter,
} from "../../../domain/ports/telemetry.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaTelemetryRepo implements ITelemetryRepo {
  async insertMany(events: UsageEventInput[]): Promise<number> {
    if (events.length === 0) return 0;

    // The buffer is process-wide, not per-tenant, so a single flush can hold
    // events from several tenants. Group and insert per tenant so each
    // batch runs inside its own withTenant() transaction — RLS's WITH CHECK
    // requires app.tenant_id to match every inserted row.
    const byTenant = new Map<string, UsageEventInput[]>();
    for (const event of events) {
      const list = byTenant.get(event.tenantId) ?? [];
      list.push(event);
      byTenant.set(event.tenantId, list);
    }

    let total = 0;
    for (const [tenantId, tenantEvents] of byTenant) {
      const result = await withTenant(tenantId, (tx) =>
        tx.featureUsageEvent.createMany({
          data: tenantEvents.map((e) => ({
            tenantId: e.tenantId,
            flagKey: e.flagKey,
            enabled: e.enabled,
            reason: e.reason,
            planCode: e.planCode,
            occurredAt: e.occurredAt,
          })),
        }),
      );
      total += result.count;
    }
    return total;
  }

  async query(tenantId: string, filter: TelemetryQueryFilter): Promise<{ events: UsageEventRecord[]; total: number }> {
    return withTenant(tenantId, async (tx) => {
      const where = {
        tenantId,
        ...(filter.flagKey ? { flagKey: filter.flagKey } : {}),
        ...(filter.from || filter.to
          ? { occurredAt: { ...(filter.from ? { gte: filter.from } : {}), ...(filter.to ? { lte: filter.to } : {}) } }
          : {}),
      };
      const [rows, total] = await Promise.all([
        tx.featureUsageEvent.findMany({
          where,
          orderBy: { occurredAt: "desc" },
          skip: (filter.page - 1) * filter.pageSize,
          take: filter.pageSize,
        }),
        tx.featureUsageEvent.count({ where }),
      ]);
      return { events: rows, total };
    });
  }
}
```

- [ ] **Step 2: Write failing test — `service.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import { TelemetryService } from "./service.ts";
import type { ITelemetryRepo } from "../../../domain/ports/telemetry.repository.port.ts";

function fakeRepo(overrides: Partial<ITelemetryRepo> = {}): ITelemetryRepo {
  return {
    insertMany: vi.fn(async (events) => events.length),
    query: vi.fn(async () => ({ events: [], total: 0 })),
    ...overrides,
  };
}

describe("TelemetryService", () => {
  it("record() buffers events without touching the repo", () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    expect(service.bufferSize()).toBe(1);
    expect(repo.insertMany).not.toHaveBeenCalled();
  });

  it("flush() drains the buffer and inserts via the repo", async () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    service.record("t1", "f2", false, "ROLLOUT", "pro");
    const flushed = await service.flush();
    expect(flushed).toBe(2);
    expect(service.bufferSize()).toBe(0);
    expect(repo.insertMany).toHaveBeenCalledTimes(1);
  });

  it("flush() on an empty buffer is a no-op", async () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 500);
    expect(await service.flush()).toBe(0);
    expect(repo.insertMany).not.toHaveBeenCalled();
  });

  it("drops the oldest event when the buffer overflows", () => {
    const repo = fakeRepo();
    const service = new TelemetryService(repo, 2);
    service.record("t1", "first", true, "FLAG_DEFAULT", null);
    service.record("t1", "second", true, "FLAG_DEFAULT", null);
    service.record("t1", "third", true, "FLAG_DEFAULT", null);
    expect(service.bufferSize()).toBe(2);
  });

  it("flush() swallows repo failures and returns 0 rather than throwing", async () => {
    const repo = fakeRepo({ insertMany: vi.fn(async () => { throw new Error("db down"); }) });
    const service = new TelemetryService(repo, 500);
    service.record("t1", "f1", true, "FLAG_DEFAULT", null);
    const flushed = await service.flush();
    expect(flushed).toBe(0);
  });
});
```

- [ ] **Step 3: Run test, verify it fails**

Run: `npx vitest run src/modules/telemetry/v1/service.test.ts`
Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 4: Implement `service.ts`**

```ts
import type { ITelemetryRepo, UsageEventInput, TelemetryQueryFilter } from "../../../domain/ports/telemetry.repository.port.ts";
import { logger } from "../../../common/logger.ts";

export class TelemetryService {
  private buffer: UsageEventInput[] = [];

  constructor(
    private readonly repo: ITelemetryRepo,
    private readonly bufferMax: number,
  ) {}

  record(tenantId: string, flagKey: string, enabled: boolean, reason: string, planCode: string | null): void {
    if (this.buffer.length >= this.bufferMax) {
      this.buffer.shift();
    }
    this.buffer.push({ tenantId, flagKey, enabled, reason, planCode, occurredAt: new Date() });
  }

  async flush(): Promise<number> {
    if (this.buffer.length === 0) return 0;
    const events = this.buffer.splice(0, this.buffer.length);
    try {
      return await this.repo.insertMany(events);
    } catch (err) {
      logger.error({ err }, "[gen-fmm] telemetry flush failed");
      return 0;
    }
  }

  query(tenantId: string, filter: TelemetryQueryFilter) {
    return this.repo.query(tenantId, filter);
  }

  bufferSize(): number {
    return this.buffer.length;
  }
}
```

- [ ] **Step 5: Run test, verify it passes**

Run: `npx vitest run src/modules/telemetry/v1/service.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 6: Implement `controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { TelemetryService } from "./service.ts";

const queryFilterSchema = z.object({
  flagKey: z.string().optional(),
  from: z.coerce.date().optional(),
  to: z.coerce.date().optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(200).default(50),
});

export class TelemetryController {
  constructor(private readonly service: TelemetryService) {}

  query = async (req: Request, res: Response): Promise<void> => {
    const filter = queryFilterSchema.parse(req.query);
    const result = await this.service.query(req.params.tenantId!, filter);
    res.json(result);
  };
}
```

- [ ] **Step 7: Implement `routes.ts`**

```ts
import { Router } from "express";
import type { TelemetryController } from "./controller.ts";

export function telemetryRoutes(controller: TelemetryController): Router {
  const router = Router();
  router.get("/:tenantId", controller.query);
  return router;
}
```

- [ ] **Step 8: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/telemetry
git commit -m "feat: add telemetry ring buffer, host-triggered flush, and query route"
```

---

### Task 13: Entitlement service (check/bulk), controller, routes, requireFeature middleware

**Files:**
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/service.ts`
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/controller.ts`
- Create: `packages/gen-fmm-starter/src/modules/entitlement/v1/routes.ts`
- Create: `packages/gen-fmm-starter/src/middleware/require-feature.ts`
- Test: `packages/gen-fmm-starter/src/modules/entitlement/v1/service.test.ts`
- Test: `packages/gen-fmm-starter/src/middleware/require-feature.test.ts`

**Interfaces:**
- Consumes: `ICatalogRepo` (Task 3), `ITenantOverrideRepo` (Task 3), `resolveEntitlement` (Task 8), `EntitlementCache` (Task 9), `AppError` (Task 2).
- Produces: `EntitlementService` (`check(tenantId, flagKey, planCode?)`, `bulk(tenantId, planCode?)`), `EntitlementController`, `internalEntitlementRoutes(controller)`, `publicEntitlementRoutes(controller)`, `requireFeatureMiddleware(featureKey, entitlementService)`. `EntitlementService`'s constructor `(catalogRepo, overrideRepo, cache, recordUsage)` where `recordUsage` has the exact signature of `TelemetryService.record` from Task 12 — consumed by Task 14's factory, which binds `recordUsage` to the real `telemetryService.record.bind(telemetryService)`.

- [ ] **Step 1: Write failing test — `service.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import { EntitlementService } from "./service.ts";
import { EntitlementCache } from "./cache.ts";
import type { ICatalogRepo, FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo, TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeCatalogRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(), findModuleByCode: vi.fn(), listModules: vi.fn(async () => []), updateModule: vi.fn(),
    createFlag: vi.fn(), findFlagByKey: vi.fn(async () => null), listFlags: vi.fn(async () => []), updateFlag: vi.fn(), deleteFlag: vi.fn(),
    upsertPlanModule: vi.fn(), listPlanModules: vi.fn(async () => []), deletePlanModule: vi.fn(),
    ...overrides,
  };
}

function fakeOverrideRepo(overrides: Partial<ITenantOverrideRepo> = {}): ITenantOverrideRepo {
  return {
    upsert: vi.fn(), findOne: vi.fn(async () => null), findAllForTenant: vi.fn(async () => []),
    listAll: vi.fn(async () => []), delete: vi.fn(),
    ...overrides,
  };
}

describe("EntitlementService.check", () => {
  it("returns FLAG_NOT_FOUND for an unknown flag and still records usage", async () => {
    const recordUsage = vi.fn();
    const service = new EntitlementService(
      fakeCatalogRepo(), fakeOverrideRepo(),
      new EntitlementCache(fakeCacheStore(), 10, 1000, 60), recordUsage,
    );
    const result = await service.check(TENANT, "missing");
    expect(result.enabled).toBe(false);
    expect(result.reason).toBe("FLAG_NOT_FOUND");
    expect(recordUsage).toHaveBeenCalledWith(TENANT, "missing", false, "FLAG_NOT_FOUND", null);
  });

  it("a tenant override wins over the flag default", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 };
    const override: TenantOverrideRecord = { tenantId: TENANT, flagKey: "f1", enabled: true, config: {}, reason: null, expiresAt: null, createdBy: null };
    const catalogRepo = fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) });
    const overrideRepo = fakeOverrideRepo({ findOne: vi.fn(async () => override) });
    const service = new EntitlementService(catalogRepo, overrideRepo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    const result = await service.check(TENANT, "f1");
    expect(result).toMatchObject({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("a cache hit does not call the repos again", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const findFlagByKey = vi.fn(async () => flag);
    const catalogRepo = fakeCatalogRepo({ findFlagByKey });
    const service = new EntitlementService(catalogRepo, fakeOverrideRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await service.check(TENANT, "f1");
    await service.check(TENANT, "f1");
    expect(findFlagByKey).toHaveBeenCalledTimes(1);
  });
});

describe("EntitlementService.bulk", () => {
  it("resolves every flag and records usage for each", async () => {
    const flags: FeatureFlagRecord[] = [
      { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 },
      { key: "f2", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 },
    ];
    const recordUsage = vi.fn();
    const catalogRepo = fakeCatalogRepo({ listFlags: vi.fn(async () => flags) });
    const service = new EntitlementService(catalogRepo, fakeOverrideRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), recordUsage);
    const result = await service.bulk(TENANT);
    expect(result.flags.f1.enabled).toBe(true);
    expect(result.flags.f2.enabled).toBe(false);
    expect(recordUsage).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run test, verify it fails**

Run: `npx vitest run src/modules/entitlement/v1/service.test.ts`
Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 3: Implement `service.ts`**

```ts
import type { ICatalogRepo } from "../../../domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "../../../domain/ports/tenant-override.repository.port.ts";
import { resolveEntitlement } from "./resolve.ts";
import type { EntitlementCache } from "./cache.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./types.ts";

export type RecordUsage = (
  tenantId: string,
  flagKey: string,
  enabled: boolean,
  reason: string,
  planCode: string | null,
) => void;

export class EntitlementService {
  constructor(
    private readonly catalogRepo: ICatalogRepo,
    private readonly overrideRepo: ITenantOverrideRepo,
    private readonly cache: EntitlementCache,
    private readonly recordUsage: RecordUsage,
  ) {}

  async check(tenantId: string, flagKey: string, planCode?: string): Promise<EntitlementCheckResult> {
    const result = await this.cache.resolveCheckCached(tenantId, flagKey, async () => {
      const [flag, override, planModules] = await Promise.all([
        this.catalogRepo.findFlagByKey(flagKey),
        this.overrideRepo.findOne(tenantId, flagKey),
        planCode ? this.catalogRepo.listPlanModules(planCode) : Promise.resolve([]),
      ]);
      const planModuleCodes = planModules.map((pm) => pm.moduleCode);
      const resolved = resolveEntitlement(flag, override, planModuleCodes, tenantId, flagKey);
      return { tenantId, flagKey, ...resolved };
    });

    this.recordUsage(tenantId, flagKey, result.enabled, result.reason, planCode ?? null);
    return result;
  }

  async bulk(tenantId: string, planCode?: string): Promise<BulkEntitlementResult> {
    const result = await this.cache.resolveBulkCached(tenantId, async () => {
      const [allFlags, overrides, planModules] = await Promise.all([
        this.catalogRepo.listFlags({}),
        this.overrideRepo.findAllForTenant(tenantId),
        planCode ? this.catalogRepo.listPlanModules(planCode) : Promise.resolve([]),
      ]);
      const overrideMap = new Map(overrides.map((o) => [o.flagKey, o]));
      const planModuleCodes = planModules.map((pm) => pm.moduleCode);

      const flags: BulkEntitlementResult["flags"] = {};
      for (const flag of allFlags) {
        const override = overrideMap.get(flag.key) ?? null;
        flags[flag.key] = resolveEntitlement(flag, override, planModuleCodes, tenantId, flag.key);
      }
      return { tenantId, flags };
    });

    for (const [flagKey, r] of Object.entries(result.flags)) {
      this.recordUsage(tenantId, flagKey, r.enabled, r.reason, planCode ?? null);
    }
    return result;
  }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `npx vitest run src/modules/entitlement/v1/service.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 5: Implement `controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { EntitlementService } from "./service.ts";

const paramsSchema = z.object({ tenantId: z.string().uuid(), flagKey: z.string().min(1) });
const bulkParamsSchema = z.object({ tenantId: z.string().uuid() });
const planCodeQuerySchema = z.object({ planCode: z.string().optional() });

export class EntitlementController {
  constructor(private readonly service: EntitlementService) {}

  check = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, flagKey } = paramsSchema.parse(req.params);
    const { planCode } = planCodeQuerySchema.parse(req.query);
    const result = await this.service.check(tenantId, flagKey, planCode);
    res.json(result);
  };

  bulk = async (req: Request, res: Response): Promise<void> => {
    const { tenantId } = bulkParamsSchema.parse(req.params);
    const { planCode } = planCodeQuerySchema.parse(req.query);
    const result = await this.service.bulk(tenantId, planCode);
    res.json(result);
  };
}
```

- [ ] **Step 6: Implement `routes.ts`** — two route builders, same controller: `internalEntitlementRoutes` (mounted under `/internal/v1/fmm`, behind the internal-secret gate, path shape matches source's internal API) and `publicEntitlementRoutes` (mounted under `/api/v1`, no gate, host fronts its own auth — see Task 14 for both mounts)

```ts
import { Router } from "express";
import type { EntitlementController } from "./controller.ts";

export function internalEntitlementRoutes(controller: EntitlementController): Router {
  const router = Router();
  router.get("/check/:tenantId/:flagKey", controller.check);
  router.get("/bulk/:tenantId", controller.bulk);
  return router;
}

export function publicEntitlementRoutes(controller: EntitlementController): Router {
  const router = Router();
  router.get("/entitlement/:tenantId/:flagKey", controller.check);
  router.get("/entitlements/:tenantId", controller.bulk);
  return router;
}
```

- [ ] **Step 7: Write failing test — `require-feature.test.ts`**

```ts
import { describe, it, expect, vi } from "vitest";
import type { Request, Response, NextFunction } from "express";
import { requireFeatureMiddleware } from "./require-feature.ts";
import { AppError } from "../common/errors.ts";
import type { EntitlementService } from "../modules/entitlement/v1/service.ts";

function fakeReq(tenantId?: string): Request {
  return { tenantId } as unknown as Request;
}

describe("requireFeatureMiddleware", () => {
  it("calls next(AppError 401) when req.tenantId is not set", async () => {
    const entitlementService = { check: vi.fn() } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq(undefined), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });

  it("calls next(AppError 403) when the feature resolves disabled", async () => {
    const entitlementService = {
      check: vi.fn(async () => ({ enabled: false, reason: "FLAG_DEFAULT" })),
    } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq("t1"), {} as Response, next);
    const err = next.mock.calls[0]?.[0] as AppError;
    expect(err.statusCode).toBe(403);
    expect(err.code).toBe("FEATURE_DISABLED");
  });

  it("calls next() with no error when the feature resolves enabled", async () => {
    const entitlementService = {
      check: vi.fn(async () => ({ enabled: true, reason: "FLAG_DEFAULT" })),
    } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq("t1"), {} as Response, next);
    expect(next).toHaveBeenCalledWith();
  });
});
```

- [ ] **Step 8: Run test, verify it fails**

Run: `npx vitest run src/middleware/require-feature.test.ts`
Expected: FAIL — `./require-feature.ts` does not exist.

- [ ] **Step 9: Implement `require-feature.ts`**

```ts
import type { NextFunction, Request, Response } from "express";
import { AppError } from "../common/errors.ts";
import type { EntitlementService } from "../modules/entitlement/v1/service.ts";

declare module "express-serve-static-core" {
  interface Request {
    tenantId?: string;
  }
}

export function requireFeatureMiddleware(featureKey: string, entitlementService: EntitlementService) {
  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const tenantId = req.tenantId;
    if (!tenantId) {
      next(new AppError(401, "UNAUTHORIZED", "req.tenantId must be set by the host before requireFeature runs"));
      return;
    }
    const result = await entitlementService.check(tenantId, featureKey);
    if (!result.enabled) {
      next(new AppError(403, "FEATURE_DISABLED", `Feature "${featureKey}" is not enabled for this tenant`));
      return;
    }
    next();
  };
}
```

- [ ] **Step 10: Run test, verify it passes**

Run: `npx vitest run src/modules/entitlement/v1/service.test.ts src/middleware/require-feature.test.ts`
Expected: PASS, 8 tests total.

- [ ] **Step 11: Commit**

```bash
git add packages/gen-fmm-starter/src/modules/entitlement/v1/service.ts packages/gen-fmm-starter/src/modules/entitlement/v1/service.test.ts packages/gen-fmm-starter/src/modules/entitlement/v1/controller.ts packages/gen-fmm-starter/src/modules/entitlement/v1/routes.ts packages/gen-fmm-starter/src/middleware/require-feature.ts packages/gen-fmm-starter/src/middleware/require-feature.test.ts
git commit -m "feat: add EntitlementService check/bulk, controller, routes, requireFeature middleware"
```

---

### Task 14: HTTP middleware, OpenAPI doc, factory, public barrel

**Files:**
- Create: `packages/gen-fmm-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-fmm-starter/src/middleware/internal-secret.ts`
- Create: `packages/gen-fmm-starter/src/docs/openapi.ts`
- Create: `packages/gen-fmm-starter/src/create-gen-fmm.ts`
- Create: `packages/gen-fmm-starter/src/index.ts`
- Test: `packages/gen-fmm-starter/tests/http/factory.test.ts`

**Interfaces:**
- Consumes: every module built in Tasks 2-13.
- Produces: `createGenFmm(config)`, `GenFmmConfig`, `GenFmmModulesConfig`, `GenFmmInstance` — the library's public entry point, re-exported from `index.ts` alongside every port type and default adapter.

- [ ] **Step 1: Implement `middleware/error-handler.ts`**

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
  logger.error({ err }, "[gen-fmm] unhandled error");
  res.status(500).json({ error: "INTERNAL_ERROR", message: "An unexpected error occurred" });
}
```

- [ ] **Step 2: Implement `middleware/internal-secret.ts`**

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

- [ ] **Step 3: Implement `docs/openapi.ts`**

```ts
export const openApiSpec = {
  openapi: "3.0.3",
  info: {
    title: "Gen_FMM API",
    version: "0.1.0",
    description:
      "Feature-gate/entitlement engine — 4-tier resolution (override -> plan-entitlement -> default -> rollout), catalog/override/telemetry CRUD. See docs/integration-guide.md for the full guide.",
  },
  components: {
    securitySchemes: {
      internalSecret: {
        type: "apiKey",
        in: "header",
        name: "x-internal-secret",
        description: "Value of GEN_FMM_INTERNAL_SECRET",
      },
    },
    schemas: {
      Error: {
        type: "object",
        properties: { error: { type: "string" }, message: { type: "string" } },
      },
      EntitlementCheckResult: {
        type: "object",
        properties: {
          tenantId: { type: "string", format: "uuid" },
          flagKey: { type: "string" },
          enabled: { type: "boolean" },
          reason: { type: "string", enum: ["TENANT_OVERRIDE", "PLAN_ENTITLEMENT", "FLAG_DEFAULT", "ROLLOUT", "FLAG_NOT_FOUND"] },
          cacheHit: { type: "string", enum: ["l1", "l2", "miss"] },
          latencyMs: { type: "number" },
        },
      },
      Module: {
        type: "object",
        properties: {
          code: { type: "string" },
          name: { type: "string" },
          description: { type: "string", nullable: true },
          category: { type: "string", nullable: true },
          isActive: { type: "boolean" },
          displayOrder: { type: "integer" },
          iconKey: { type: "string", nullable: true },
        },
      },
      FeatureFlag: {
        type: "object",
        properties: {
          key: { type: "string" },
          moduleCode: { type: "string", nullable: true },
          defaultEnabled: { type: "boolean" },
          isGradualRollout: { type: "boolean" },
          rolloutPercentage: { type: "integer", minimum: 0, maximum: 100 },
        },
      },
      TenantOverride: {
        type: "object",
        properties: {
          tenantId: { type: "string", format: "uuid" },
          flagKey: { type: "string" },
          enabled: { type: "boolean" },
          config: { type: "object", additionalProperties: true },
          reason: { type: "string", nullable: true },
          expiresAt: { type: "string", format: "date-time", nullable: true },
          createdBy: { type: "string", nullable: true },
        },
      },
    },
  },
  paths: {
    "/health": {
      get: { summary: "Health check", tags: ["health"], responses: { "200": { description: "OK" } } },
    },
    "/api/v1/entitlement/{tenantId}/{flagKey}": {
      get: {
        summary: "Resolve one flag for a tenant (public, host fronts auth)",
        tags: ["entitlement"],
        parameters: [
          { name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } },
          { name: "flagKey", in: "path", required: true, schema: { type: "string" } },
          { name: "planCode", in: "query", schema: { type: "string" } },
        ],
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/EntitlementCheckResult" } } } },
        },
      },
    },
    "/internal/v1/fmm/check/{tenantId}/{flagKey}": {
      get: {
        summary: "Resolve one flag for a tenant (internal, X-Internal-Secret gated)",
        tags: ["internal"],
        security: [{ internalSecret: [] }],
        parameters: [
          { name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } },
          { name: "flagKey", in: "path", required: true, schema: { type: "string" } },
          { name: "planCode", in: "query", schema: { type: "string" } },
        ],
        responses: {
          "200": { description: "OK", content: { "application/json": { schema: { $ref: "#/components/schemas/EntitlementCheckResult" } } } },
          "401": { description: "Missing/invalid X-Internal-Secret", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
        },
      },
    },
    "/api/v1/catalog/flags": {
      get: { summary: "List feature flags", tags: ["catalog"], responses: { "200": { description: "OK" } } },
      post: {
        summary: "Create a feature flag",
        tags: ["catalog"],
        requestBody: { required: true, content: { "application/json": { schema: { $ref: "#/components/schemas/FeatureFlag" } } } },
        responses: { "201": { description: "Created" }, "409": { description: "Already exists" } },
      },
    },
    "/api/v1/overrides": {
      post: {
        summary: "Upsert a tenant override",
        tags: ["overrides"],
        requestBody: { required: true, content: { "application/json": { schema: { $ref: "#/components/schemas/TenantOverride" } } } },
        responses: { "200": { description: "OK" }, "422": { description: "expiresAt in the past" } },
      },
    },
    "/api/v1/telemetry/{tenantId}": {
      get: {
        summary: "Query recorded feature-check usage events for a tenant",
        tags: ["telemetry"],
        parameters: [{ name: "tenantId", in: "path", required: true, schema: { type: "string", format: "uuid" } }],
        responses: { "200": { description: "OK" } },
      },
    },
  },
} as const;
```

- [ ] **Step 4: Write failing test — `tests/http/factory.test.ts`** (fully mocked ports, no live DB/Redis — proves the factory wires everything and the HTTP surface responds)

```ts
import { describe, it, expect, vi } from "vitest";
import request from "supertest";
import { createGenFmm } from "../../src/create-gen-fmm.ts";
import type { ICatalogRepo, FeatureFlagRecord } from "../../src/domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "../../src/domain/ports/tenant-override.repository.port.ts";
import type { ITelemetryRepo } from "../../src/domain/ports/telemetry.repository.port.ts";
import type { ICacheStore } from "../../src/domain/ports/cache-store.port.ts";

function fakeCatalogRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(), findModuleByCode: vi.fn(async () => null), listModules: vi.fn(async () => []), updateModule: vi.fn(),
    createFlag: vi.fn(), findFlagByKey: vi.fn(async () => null), listFlags: vi.fn(async () => []), updateFlag: vi.fn(), deleteFlag: vi.fn(),
    upsertPlanModule: vi.fn(), listPlanModules: vi.fn(async () => []), deletePlanModule: vi.fn(),
    ...overrides,
  };
}
function fakeOverrideRepo(): ITenantOverrideRepo {
  return { upsert: vi.fn(), findOne: vi.fn(async () => null), findAllForTenant: vi.fn(async () => []), listAll: vi.fn(async () => []), delete: vi.fn() };
}
function fakeTelemetryRepo(): ITelemetryRepo {
  return { insertMany: vi.fn(async () => 0), query: vi.fn(async () => ({ events: [], total: 0 })) };
}
function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

const TENANT = "11111111-1111-1111-1111-111111111111";

describe("createGenFmm factory", () => {
  it("throws GenFmmConfigError when modules.check is enabled with no internal secret", () => {
    expect(() =>
      createGenFmm({
        catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
        cacheStore: fakeCacheStore(), internalSecret: "",
      }),
    ).toThrow(/internalSecret/);
  });

  it("GET /health returns ok", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("GET /api/v1/entitlement/:tenantId/:flagKey resolves FLAG_NOT_FOUND with 200", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/api/v1/entitlement/${TENANT}/missing_flag`);
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ enabled: false, reason: "FLAG_NOT_FOUND" });
  });

  it("GET /internal/v1/fmm/check/:tenantId/:flagKey without the header is rejected", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/internal/v1/fmm/check/${TENANT}/f1`);
    expect(res.status).toBe(401);
  });

  it("GET /internal/v1/fmm/check/:tenantId/:flagKey with the header succeeds", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) }),
      overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/internal/v1/fmm/check/${TENANT}/f1`).set("x-internal-secret", "test-secret");
    expect(res.status).toBe(200);
    expect(res.body.enabled).toBe(true);
  });

  it("genFmm.check() works as a direct in-process call, bypassing HTTP entirely", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) }),
      overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const result = await genFmm.check(TENANT, "f1");
    expect(result.enabled).toBe(true);
  });

  it("genFmm.requireFeature() 403s a disabled feature", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    genFmm.app.get("/gated", (req, res, next) => { req.tenantId = TENANT; next(); }, genFmm.requireFeature("missing_flag"), (_req, res) => res.json({ ok: true }));
    const res = await request(genFmm.app).get("/gated");
    expect(res.status).toBe(403);
    expect(res.body.error).toBe("FEATURE_DISABLED");
  });

  it("genFmm.flushTelemetryBuffer() flushes buffered usage events", async () => {
    const insertMany = vi.fn(async (events: unknown[]) => events.length);
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(),
      telemetryRepo: { insertMany, query: vi.fn(async () => ({ events: [], total: 0 })) },
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    await genFmm.check(TENANT, "f1");
    const flushed = await genFmm.flushTelemetryBuffer();
    expect(flushed).toBe(1);
    expect(insertMany).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 5: Run test, verify it fails**

Run: `npx vitest run tests/http/factory.test.ts`
Expected: FAIL — `../../src/create-gen-fmm.ts` does not exist.

- [ ] **Step 6: Implement `create-gen-fmm.ts`**

```ts
import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import swaggerUi from "swagger-ui-express";
import type { NextFunction, Request, Response, RequestHandler } from "express";

import { requireEnv, optionalEnv } from "./config/env.ts";
import { GenFmmConfigError } from "./common/errors.ts";
import { openApiSpec } from "./docs/openapi.ts";

import type { ICatalogRepo } from "./domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "./domain/ports/tenant-override.repository.port.ts";
import type { ITelemetryRepo } from "./domain/ports/telemetry.repository.port.ts";
import type { ICacheStore } from "./domain/ports/cache-store.port.ts";

import { PrismaCatalogRepo } from "./modules/catalog/v1/repo.ts";
import { PrismaTenantOverrideRepo } from "./modules/overrides/v1/repo.ts";
import { PrismaTelemetryRepo } from "./modules/telemetry/v1/repo.ts";
import { RedisCacheStore } from "./infra/cache/redis-cache-store.ts";

import { CatalogService } from "./modules/catalog/v1/service.ts";
import { CatalogController } from "./modules/catalog/v1/controller.ts";
import { catalogRoutes } from "./modules/catalog/v1/routes.ts";
import { OverrideService } from "./modules/overrides/v1/service.ts";
import { OverrideController } from "./modules/overrides/v1/controller.ts";
import { overridesRoutes } from "./modules/overrides/v1/routes.ts";
import { TelemetryService } from "./modules/telemetry/v1/service.ts";
import { TelemetryController } from "./modules/telemetry/v1/controller.ts";
import { telemetryRoutes } from "./modules/telemetry/v1/routes.ts";
import { EntitlementCache } from "./modules/entitlement/v1/cache.ts";
import { EntitlementService } from "./modules/entitlement/v1/service.ts";
import { EntitlementController } from "./modules/entitlement/v1/controller.ts";
import { internalEntitlementRoutes, publicEntitlementRoutes } from "./modules/entitlement/v1/routes.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./modules/entitlement/v1/types.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";
import { requireFeatureMiddleware } from "./middleware/require-feature.ts";

export interface GenFmmModulesConfig {
  catalog?: boolean;
  overrides?: boolean;
  telemetry?: boolean;
  entitlement?: boolean;
  check?: boolean;
}

export interface GenFmmConfig {
  catalogRepo?: ICatalogRepo;
  overrideRepo?: ITenantOverrideRepo;
  telemetryRepo?: ITelemetryRepo;
  cacheStore?: ICacheStore;
  onFlagChanged?: (flagKey: string, tenantId?: string) => void;
  modules?: GenFmmModulesConfig;
  internalSecret?: string;
}

export interface GenFmmInstance {
  app: Express;
  check(tenantId: string, flagKey: string, planCode?: string): Promise<EntitlementCheckResult>;
  bulk(tenantId: string, planCode?: string): Promise<BulkEntitlementResult>;
  flushTelemetryBuffer(): Promise<number>;
  requireFeature(featureKey: string): RequestHandler;
}

function resolveCatalogRepo(override: ICatalogRepo | undefined): ICatalogRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaCatalogRepo();
}

function resolveOverrideRepo(override: ITenantOverrideRepo | undefined): ITenantOverrideRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantOverrideRepo();
}

function resolveTelemetryRepo(override: ITelemetryRepo | undefined): ITelemetryRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTelemetryRepo();
}

function resolveCacheStore(override: ICacheStore | undefined): ICacheStore {
  if (override) return override;
  return new RedisCacheStore(requireEnv("REDIS_URL"));
}

export function createGenFmm(config: GenFmmConfig): GenFmmInstance {
  const modules: Required<GenFmmModulesConfig> = {
    catalog: config.modules?.catalog ?? true,
    overrides: config.modules?.overrides ?? true,
    telemetry: config.modules?.telemetry ?? true,
    entitlement: config.modules?.entitlement ?? true,
    check: config.modules?.check ?? true,
  };

  const catalogRepo = resolveCatalogRepo(config.catalogRepo);
  const overrideRepo = resolveOverrideRepo(config.overrideRepo);
  const telemetryRepo = resolveTelemetryRepo(config.telemetryRepo);
  const cacheStore = resolveCacheStore(config.cacheStore);
  const onFlagChanged = config.onFlagChanged ?? (() => {});

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_FMM_INTERNAL_SECRET", "");
  if (modules.check && internalSecret === "") {
    throw new GenFmmConfigError(
      "modules.check is enabled but no internal secret was configured — set GEN_FMM_INTERNAL_SECRET or pass config.internalSecret",
    );
  }

  const checkTtlSeconds = Number(optionalEnv("CHECK_CACHE_TTL_SECS", "60"));
  const cache = new EntitlementCache(
    cacheStore,
    Number(optionalEnv("L1_CACHE_MAX_ENTRIES", "10000")),
    checkTtlSeconds * 1000,
    checkTtlSeconds,
  );

  const telemetryService = new TelemetryService(telemetryRepo, Number(optionalEnv("TELEMETRY_BUFFER_MAX", "500")));
  const catalogService = new CatalogService(catalogRepo, cache, onFlagChanged);
  const overrideService = new OverrideService(overrideRepo, cache, onFlagChanged);
  const entitlementService = new EntitlementService(
    catalogRepo,
    overrideRepo,
    cache,
    (tenantId, flagKey, enabled, reason, planCode) => telemetryService.record(tenantId, flagKey, enabled, reason, planCode),
  );

  const app = express();
  app.use(helmet());
  const allowedOrigins = optionalEnv("ALLOWED_ORIGINS", "*");
  app.use(cors({ origin: allowedOrigins === "*" ? true : allowedOrigins.split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));
  app.get("/docs.json", (_req, res) => res.json(openApiSpec));
  app.use(
    "/docs",
    (_req: Request, res: Response, next: NextFunction) => { res.removeHeader("Content-Security-Policy"); next(); },
    swaggerUi.serve,
    swaggerUi.setup(openApiSpec),
  );

  if (modules.catalog) {
    app.use("/api/v1/catalog", catalogRoutes(new CatalogController(catalogService)));
  }
  if (modules.overrides) {
    app.use("/api/v1/overrides", overridesRoutes(new OverrideController(overrideService)));
  }
  if (modules.telemetry) {
    app.use("/api/v1/telemetry", telemetryRoutes(new TelemetryController(telemetryService)));
  }
  if (modules.entitlement) {
    app.use("/api/v1", publicEntitlementRoutes(new EntitlementController(entitlementService)));
  }
  if (modules.check) {
    const gate = internalSecretMiddleware(internalSecret);
    app.use("/internal/v1/fmm", gate, internalEntitlementRoutes(new EntitlementController(entitlementService)));
  }

  app.use(errorHandler);

  return {
    app,
    check: (tenantId, flagKey, planCode) => entitlementService.check(tenantId, flagKey, planCode),
    bulk: (tenantId, planCode) => entitlementService.bulk(tenantId, planCode),
    flushTelemetryBuffer: () => telemetryService.flush(),
    requireFeature: (featureKey: string) => requireFeatureMiddleware(featureKey, entitlementService),
  };
}
```

- [ ] **Step 7: Implement `index.ts`** (public barrel)

```ts
export { createGenFmm } from "./create-gen-fmm.ts";
export type { GenFmmConfig, GenFmmModulesConfig, GenFmmInstance } from "./create-gen-fmm.ts";

export type { EntitlementCheckResult, BulkEntitlementResult, EntitlementReason } from "./modules/entitlement/v1/types.ts";
export { resolveEntitlement, computeRolloutBucket } from "./modules/entitlement/v1/resolve.ts";

export type {
  ICatalogRepo, ModuleRecord, PlanModuleRecord, FeatureFlagRecord,
  CreateModuleInput, UpdateModuleInput, CreateFlagInput, UpdateFlagInput,
} from "./domain/ports/catalog.repository.port.ts";
export type { ITenantOverrideRepo, TenantOverrideRecord, UpsertOverrideInput } from "./domain/ports/tenant-override.repository.port.ts";
export type { ITelemetryRepo, UsageEventInput, UsageEventRecord, TelemetryQueryFilter } from "./domain/ports/telemetry.repository.port.ts";
export type { ICacheStore } from "./domain/ports/cache-store.port.ts";

export { PrismaCatalogRepo } from "./modules/catalog/v1/repo.ts";
export { PrismaTenantOverrideRepo } from "./modules/overrides/v1/repo.ts";
export { PrismaTelemetryRepo } from "./modules/telemetry/v1/repo.ts";
export { RedisCacheStore } from "./infra/cache/redis-cache-store.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { AppError, GenFmmConfigError, ConflictError, NotFoundError, BusinessRuleError, InvalidTenantIdError } from "./common/errors.ts";
```

- [ ] **Step 8: Run test, verify it passes**

Run: `npx vitest run tests/http/factory.test.ts`
Expected: PASS, 8 tests.

- [ ] **Step 9: Run the full test suite for a regression check**

Run: `npx vitest run`
Expected: every test file from Tasks 2-14 passes (integration test from Task 11 requires Docker running).

- [ ] **Step 10: Commit**

```bash
git add packages/gen-fmm-starter/src/middleware/error-handler.ts packages/gen-fmm-starter/src/middleware/internal-secret.ts packages/gen-fmm-starter/src/docs packages/gen-fmm-starter/src/create-gen-fmm.ts packages/gen-fmm-starter/src/index.ts packages/gen-fmm-starter/tests/http
git commit -m "feat: add createGenFmm factory, HTTP middleware, OpenAPI docs, public barrel"
```

---

### Task 15: Demo app

**Files:**
- Create: `packages/gen-fmm-demo/src/index.ts`

**Interfaces:**
- Consumes: `createGenFmm` and its config type from `@gen-ms/gen-fmm-starter` (Task 14).

- [ ] **Step 1: Implement `packages/gen-fmm-demo/src/index.ts`**

```ts
import { createGenFmm } from "@gen-ms/gen-fmm-starter";
import { getPrismaClient } from "@gen-ms/gen-fmm-starter";

const PORT = Number(process.env.PORT ?? "3700");

const genFmm = createGenFmm({
  // onFlagChanged is a no-op by default; a multi-pod host would wire this to
  // its own pub/sub (Redis, SNS, whatever it already runs) to broadcast
  // cache invalidation to every other pod's in-process L1.
  onFlagChanged: (flagKey, tenantId) => {
    console.log(`[demo] flag changed: ${flagKey}${tenantId ? ` (tenant ${tenantId})` : " (global)"}`);
  },
});

async function seed(): Promise<void> {
  const prisma = getPrismaClient();
  await prisma.module.upsert({
    where: { code: "reporting" },
    create: { code: "reporting", name: "Reporting", category: "analytics" },
    update: {},
  });
  await prisma.featureFlag.upsert({
    where: { key: "new_dashboard" },
    create: { key: "new_dashboard", moduleCode: "reporting", defaultEnabled: false },
    update: {},
  });
  await prisma.featureFlag.upsert({
    where: { key: "beta_export" },
    create: { key: "beta_export", moduleCode: null, isGradualRollout: true, rolloutPercentage: 50 },
    update: {},
  });
}

// Host owns telemetry-flush scheduling — no internal timer, matching the
// rest of the Gen_MS family's "host supplies the cron" convention.
setInterval(() => {
  void genFmm.flushTelemetryBuffer().then((count) => {
    if (count > 0) console.log(`[demo] flushed ${count} telemetry events`);
  });
}, 30_000);

seed()
  .then(() => {
    genFmm.app.listen(PORT, () => {
      console.log(`[demo] Gen_FMM demo listening on http://localhost:${PORT}`);
      console.log(`[demo] docs at http://localhost:${PORT}/docs`);
    });
  })
  .catch((err) => {
    console.error("[demo] seed failed", err);
    process.exit(1);
  });
```

- [ ] **Step 2: Build and run manually**

Run:
```bash
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && npm run build --workspaces --if-present
cd packages/gen-fmm-demo && node --env-file=../../.env dist/index.js
```
Expected: logs `Gen_FMM demo listening on http://localhost:3700`; `curl http://localhost:3700/health` returns `{"status":"ok"}`.

- [ ] **Step 3: Commit**

```bash
git add packages/gen-fmm-demo/src
git commit -m "feat: add Gen_FMM demo app"
```

---

### Task 16: Documentation — integration guide, README

**Files:**
- Create: `docs/integration-guide.md`
- Create: `README.md`

**Interfaces:**
- Consumes: nothing (documentation only) — must accurately describe every route/env var/port finalized in Tasks 1-15.

- [ ] **Step 1: Write `docs/integration-guide.md`**

```markdown
# Gen_FMM Integration Guide

## Embedding in-process

\`\`\`ts
import { createGenFmm } from "@gen-ms/gen-fmm-starter";

const genFmm = createGenFmm({
  internalSecret: process.env.GEN_FMM_INTERNAL_SECRET,
});

hostApp.use("/fmm", genFmm.app);

// direct in-process call — no HTTP round-trip, no auth of its own
const result = await genFmm.check(tenantId, "new_dashboard", planCode);
if (result.enabled) { /* ... */ }

// gate a host route behind a feature flag
hostApp.get("/reports/new", (req, res, next) => { req.tenantId = req.user.tenantId; next(); }, genFmm.requireFeature("new_dashboard"), handler);
\`\`\`

## Running standalone (HTTP)

\`\`\`bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-fmm-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-fmm-demo/dist/index.js
\`\`\`

## Environment variables

| Var | Required when | Notes |
|---|---|---|
| \`DATABASE_URL\` | any Prisma-backed repo (\`catalogRepo\`/\`overrideRepo\`/\`telemetryRepo\`) not overridden | Postgres connection string, must point at \`genfmm_app\`, never the bootstrap superuser |
| \`REDIS_URL\` | \`cacheStore\` not overridden | Backs the L2 cache (\`RedisCacheStore\`) |
| \`GEN_FMM_INTERNAL_SECRET\` | \`modules.check\` enabled (default true) and \`internalSecret\` config not set | Gates \`/internal/v1/fmm/*\` via \`X-Internal-Secret\` — factory throws \`GenFmmConfigError\` at boot if missing while \`modules.check\` is on |
| \`ALLOWED_ORIGINS\` | never (defaults to \`*\`) | Comma-separated CORS origins |
| \`CHECK_CACHE_TTL_SECS\` | never (defaults to \`60\`) | L2 TTL for single-flag checks; also used as the L1 TTL (ms) |
| \`L1_CACHE_MAX_ENTRIES\` | never (defaults to \`10000\`) | Bounded in-process L1 cache size |
| \`TELEMETRY_BUFFER_MAX\` | never (defaults to \`500\`) | Ring-buffer cap; oldest event dropped on overflow |
| \`PORT\` | demo app only | HTTP port |

## API surface

Internal (require \`X-Internal-Secret: <GEN_FMM_INTERNAL_SECRET>\`):

| Method | Path | Notes |
|---|---|---|
| GET | \`/internal/v1/fmm/check/:tenantId/:flagKey\` | \`?planCode=\` optional |
| GET | \`/internal/v1/fmm/bulk/:tenantId\` | \`?planCode=\` optional |

Public (\`modules.*\` gated, no auth of its own — front with the host's own authn/authz):

| Method | Path | Notes |
|---|---|---|
| GET | \`/api/v1/entitlement/:tenantId/:flagKey\` | Same resolution as the internal check, no secret gate |
| GET | \`/api/v1/entitlements/:tenantId\` | Bulk, whole-tenant map |
| GET/POST/PATCH | \`/api/v1/catalog/modules[/:code]\` | |
| PUT/GET/DELETE | \`/api/v1/catalog/plan-modules\`, \`/api/v1/catalog/plans/:planCode/modules[/:moduleCode]\` | |
| GET/POST/PATCH/DELETE | \`/api/v1/catalog/flags[/:key]\` | |
| POST/GET/DELETE | \`/api/v1/overrides\`, \`/api/v1/overrides/:tenantId[/:flagKey]\` | |
| GET | \`/api/v1/telemetry/:tenantId\` | \`?flagKey=&from=&to=&page=&pageSize=\` |

Always available:

| Method | Path | Notes |
|---|---|---|
| GET | \`/health\` | No auth |
| GET | \`/docs\` | Swagger UI |
| GET | \`/docs.json\` | Raw OpenAPI 3.0 spec |

**\`check()\` on an unknown flag key returns \`{ enabled: false, reason: "FLAG_NOT_FOUND" }\` with HTTP 200, never 404** — callers never need to special-case a missing flag.

**\`planCode\` is always caller-supplied.** Gen_FMM has no \`plans\` table and never looks up subscription state itself — the host passes the tenant's current plan code on every \`check()\`/\`bulk()\` call (or omits it, in which case only override/default/rollout tiers apply).

**Gen_FMM has no internal scheduler.** Call \`flushTelemetryBuffer()\` (or rely on the buffer's bounded size) on your own interval — telemetry events otherwise accumulate in-process only.

**Multi-pod cache coherence is opt-in.** \`onFlagChanged?(flagKey, tenantId?)\` fires on every catalog/override write; wire it to your own pub/sub if you run more than one pod. Single-pod hosts can ignore it — each pod's own L1 self-invalidates via TTL.

## Swapping adapters

- \`catalogRepo\` / \`overrideRepo\` / \`telemetryRepo\`: implement \`ICatalogRepo\` / \`ITenantOverrideRepo\` / \`ITelemetryRepo\` to swap persistence.
- \`cacheStore\`: implement \`ICacheStore\` (\`get\`/\`set\`/\`del\`/\`setNx\`) to swap Redis for another L2 store.
- \`onFlagChanged\`: wire to your own pub/sub for multi-pod L1 coherence.

## Known limitations (v1)

- No RBAC/JWT of any kind — the host fronts every route with its own auth.
- No message broker — \`planCode\` is always a parameter, never looked up via subscription events.
- No internal scheduler — telemetry flush is entirely host-driven.
- \`invalidateFlag()\` (catalog-level changes) does not glob-purge other tenants' L1 entries — correctness after a catalog change relies on the short check-cache TTL plus the \`onFlagChanged\` broadcast for other pods.
```

- [ ] **Step 2: Write `README.md`** (Gen_MS family table + quickstart)

```markdown
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

\`\`\`bash
git clone <this repo>
cd Gen_FMM
npm install
docker compose up -d
npx prisma migrate deploy --schema packages/gen-fmm-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-fmm-demo/dist/index.js
curl http://localhost:3700/health
\`\`\`

See \`docs/integration-guide.md\` for the full API surface, environment variables, and adapter-swapping guide.
```

- [ ] **Step 3: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: add Gen_FMM integration guide and README"
```

---

### Task 17: Smoke test script + live verification

**Files:**
- Create: `scripts/smoke-fmm.sh`

**Interfaces:**
- Consumes: the running demo app from Task 15 (`http://localhost:3700` by default).

- [ ] **Step 1: Implement `scripts/smoke-fmm.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3700}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
INTERNAL_SECRET="${GEN_FMM_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== list catalog flags (seeded by the demo) =="
curl -sf "$BASE_URL/api/v1/catalog/flags"

echo "== resolve new_dashboard (default false, no override) =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/new_dashboard")
echo "$RESULT"
echo "$RESULT" | grep -q '"reason":"FLAG_DEFAULT"'

echo "== set a tenant override enabling it =="
curl -sf -X POST "$BASE_URL/api/v1/overrides" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"flagKey\":\"new_dashboard\",\"enabled\":true,\"reason\":\"smoke test\"}"

echo "== resolve again — override should now win =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/new_dashboard")
echo "$RESULT"
echo "$RESULT" | grep -q '"reason":"TENANT_OVERRIDE"'
echo "$RESULT" | grep -q '"enabled":true'

echo "== resolve an unknown flag — 200 with FLAG_NOT_FOUND, never 404 =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/does_not_exist")
echo "$RESULT" | grep -q '"reason":"FLAG_NOT_FOUND"'

echo "== internal check route rejects a missing secret =="
if curl -sf "$BASE_URL/internal/v1/fmm/check/$TENANT_ID/new_dashboard" >/dev/null 2>&1; then
  echo "expected 401, got success" >&2
  exit 1
fi

echo "== internal check route succeeds with the secret =="
curl -sf "$BASE_URL/internal/v1/fmm/check/$TENANT_ID/new_dashboard" -H "x-internal-secret: $INTERNAL_SECRET"

echo "== bulk resolve for the tenant =="
curl -sf "$BASE_URL/api/v1/entitlements/$TENANT_ID"

echo "== query telemetry (populated by the checks above once flushed) =="
curl -sf "$BASE_URL/api/v1/telemetry/$TENANT_ID"

echo "== remove the override =="
curl -sf -X DELETE "$BASE_URL/api/v1/overrides/$TENANT_ID/new_dashboard"

echo "== smoke test complete =="
```

- [ ] **Step 2: Run docker compose, apply migrations, build, start the demo, run the smoke script live**

Run:
```bash
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && docker compose up -d
cd packages/gen-fmm-starter && DATABASE_URL="postgresql://genfmm:genfmm@localhost:5441/genfmm" npx prisma migrate deploy
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && npm run build --workspaces --if-present
node --env-file=.env packages/gen-fmm-demo/dist/index.js &
sleep 2
chmod +x scripts/smoke-fmm.sh && ./scripts/smoke-fmm.sh
```
Expected: every step in `smoke-fmm.sh` prints its section header and no `curl -f` call fails; the two `grep -q` assertions after override-set and unknown-flag both match.

- [ ] **Step 3: Run the full workspace test suite one final time**

Run: `cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_FMM" && npm test`
Expected: every test file across `gen-fmm-starter` passes, including the Testcontainers integration test from Task 11.

- [ ] **Step 4: Commit**

```bash
git add scripts/smoke-fmm.sh
git commit -m "test: add live smoke test script for Gen_FMM"
```

---

## Plan Self-Review Notes

- **Spec coverage:** every design-spec section has a task — factory/ports (Tasks 3, 14), Prisma+RLS+app-role (Tasks 4-5), resolve()/rollout bucket (Task 8), L1+L2 cache+stampede lock (Tasks 6, 9), catalog CRUD (Task 10), override CRUD+expiry fix (Task 11), telemetry+host-triggered flush (Task 12), check()/bulk()+requireFeature (Task 13), HTTP surface+config errors (Task 14), demo (Task 15), docs (Task 16), smoke test (Task 17).
- **RLS liveness:** Task 11's integration test explicitly proves cross-tenant isolation AND that a repeat write on the same tenant+flag succeeds — the two failure modes the Gen_USG final review found in that family's prior library. Task 7's `withTenant()` throws `InvalidTenantIdError` from day one (a fix Gen_USG only added in its final-review fix wave) rather than a bare `Error`.
- **Override-expiry bug fix:** Task 8's `resolveEntitlement` checks `expiresAt` on every call (tests cover both expired-falls-through and future-still-applies); Task 11's integration test additionally proves the value round-trips through the real repo rather than being hardcoded, closing the exact gap found in `fmm-svc`'s source.
- **Type consistency check:** `EntitlementCheckResult`/`BulkEntitlementResult` (Task 8) are used identically in `EntitlementCache` (Task 9), `EntitlementService` (Task 13), and the factory/barrel (Task 14) with no renames. `CatalogService`/`OverrideService` share the identical `(repo, cache, onFlagChanged)` constructor shape (Tasks 10-11), consumed uniformly by Task 14. `TelemetryService.record`'s 5-argument signature (Task 12) matches `RecordUsage` in Task 13 exactly.
- **No placeholders:** every step above contains complete, runnable code — no TBD/TODO markers, no "similar to Task N" references requiring cross-referencing during implementation.

