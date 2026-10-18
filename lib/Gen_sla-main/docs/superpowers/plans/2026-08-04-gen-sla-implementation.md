# Gen_SLA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `@gen-ms/gen-sla-starter` — a standalone, configurable SLA policy/instance/timer library genericized from `CPMS-Platform/apps/sla-svc`, following the Gen_REG/Gen_TBR factory+`resolve*` convention.

**Architecture:** Express app factory (`createGenSla(config)`) wired from swappable ports (policy repo, instance repo, distributed lock, event bus), all defaulting to Prisma/Valkey/RabbitMQ adapters built from env vars when no override is supplied. Domain logic (policies CRUD, instance lifecycle state machine, timer worker, transactional outbox) is ported near-verbatim from the ancestor since it is already entity-agnostic (`entityType`/`entityId`/`slaType` are plain strings). Auth is `X-Internal-Secret` header + path-param `tenantId`, not JWT — this repo has no live tie to CPMS's auth stack.

**Tech Stack:** Node 20, TypeScript (ESM, NodeNext), Express 4, Zod 3, Prisma 5 + PostgreSQL 15, ioredis (Valkey), amqplib (RabbitMQ), Vitest 2 + supertest + Testcontainers.

## Global Constraints

- Package names: `@gen-ms/gen-sla-starter`, `@gen-ms/gen-sla-demo`. Repo: `git+https://github.com/GEN-MS/Gen_SLA.git`.
- Every dependency in `GenSlaConfig` follows `override ?? buildDefaultFromEnv()`. Missing env for a default adapter throws `GenSlaConfigError` synchronously inside `createGenSla()`, never mid-request.
- Ports (local dev, verified free against every sibling's docker-compose): Postgres `5440`, Valkey `6384`, RabbitMQ AMQP `5674`, RabbitMQ mgmt UI `15674`.
- No JWT/JWKS, no Redis-backed RBAC role sets. All routes require `X-Internal-Secret` header (`internalSecret` middleware). `tenantId` is a path param (`/:tenantId/...`), validated as a UUID by `validateTenantId` middleware — not derived from a token.
- `createdBy`/`updatedBy` are optional caller-supplied strings in the request body (matches Gen_FMM's `overrides` module), not derived from an authenticated principal.
- Prisma generator output is `../__generated__/prisma` (matches Gen_REG/Gen_TBR), not the default `@prisma/client` location the ancestor uses.
- The ancestor's RLS migration (`enable_rls_tenant_isolation`) is dropped — audit found it unwired (nothing sets the Postgres GUC it depends on); tenant isolation is enforced entirely by `where: { tenantId }` filters, same as every other query in the ancestor.
- The ancestor's `tenant-context.ts` middleware is dropped (dead code — never mounted in `app.ts`).
- The ancestor's RabbitMQ consumer *business handlers* (`handleTicketEvent`/`handleApprovalEvent`/`handleInvoiceEvent`/`handleKtEvent`/`handleProvisioningEvent`) are **not** ported as fixed logic — they hardcode CPMS sibling services' event shapes (`ticket_id`, PMT's `aggregateId`, etc.) that a generic library cannot assume. Instead, Gen_SLA exposes a generic `RabbitMqBus` (connect/assertExchange/assertQueue/bindQueue/consume/publish) plus the instance lifecycle methods as its public surface; a caller wires their own routing-key→handler map. `gen-sla-demo` ships one illustrative example handler, not the full CPMS mapping.
- The ancestor's notif-svc mirror-publish (`SLA_NOTIF_WARNING`/`BREACHED` to a notif-inbound exchange) is dropped — that payload shape is CPMS's `notif-svc` contract, not assumed here. A caller can subscribe to the generic `INSTANCE_WARNING`/`INSTANCE_BREACHED` outbox events and forward to their own notification service.
- Response shape follows the Gen_REG/Gen_TBR convention (flat `res.json(record)` / `{ error: code, message }` on failure), not the ancestor's `{ success, message, data }` wrapper.
- Services take their repo/port dependencies via constructor injection (testable with hand-written fakes), not the ancestor's module-level singleton exports (`export const policyService = new PolicyService()`).
- No placeholders. Every task below contains complete, runnable code adapted from the real ancestor source at `CPMS-Platform/apps/sla-svc` and the real Gen_REG/Gen_TBR scaffolding.

---

### Task 1: Pre-context copy + source audit notes

**Files:**
- Create: `pre-context/sla-svc/` (copy of `CPMS-Platform/apps/sla-svc`, gitignored)
- Modify: `.gitignore`
- Create: `docs/source-audit-notes.md`

**Interfaces:** None — this task produces no code, only reference material and a `.gitignore` entry that later tasks rely on.

- [ ] **Step 1: Copy the ancestor source, stripping build artifacts**

```bash
mkdir -p pre-context
cp -r "/c/Users/naksh/Desktop/Metaupspace/CPMS-Platform/apps/sla-svc" pre-context/sla-svc
rm -rf pre-context/sla-svc/node_modules pre-context/sla-svc/dist pre-context/sla-svc/coverage
```

- [ ] **Step 2: Add `pre-context/` to `.gitignore`**

```
# .gitignore
node_modules/
dist/
.env
*.log
**/__generated__/
package-lock.json
pre-context/
```

- [ ] **Step 3: Write the source audit notes**

```markdown
# Gen_SLA Source Audit Notes

Where the original source lives (copied read-only for reference):
`pre-context/sla-svc/` in this repo — full copy of CPMS-Platform's `apps/sla-svc`,
build artifacts stripped. Treat as reference only; do not build or run it as
part of Gen_SLA's own test suite.

## Reusable as-is (entity-agnostic domain logic)
- `prisma/schema.prisma` models: SlaPolicy, SlaInstance, SlaHistory, SlaEscalation,
  SlaOutboxEvent — already tenant-scoped and entity-agnostic (entityType/entityId/
  slaType are plain strings, not foreign keys into CPMS-specific tables).
- `modules/policies/v1/*`, `modules/instances/v1/*`, `modules/metrics/v1/*` —
  CRUD + lifecycle state machine (ACTIVE→WARNING→BREACHED→RESOLVED/CANCELLED),
  no CPMS-specific assumptions beyond auth (see below).
- `workers/sla-timer.worker.ts` — DB-poll + Valkey SET NX lock, entity-agnostic.
- `infra/messaging/outbox.service.ts` + `outbox.dispatcher.ts` — transactional
  outbox pattern, entity-agnostic (exchange/routingKey/payload are plain strings).

## Dropped / replaced
- JWT/JWKS auth (`middleware/jwt-auth.middleware.ts`) and Redis-backed RBAC
  (`middleware/require-permission.middleware.ts`) — replaced with
  `X-Internal-Secret` header + path-param tenantId, matching every other Gen_MS repo.
- `middleware/tenant-context.ts` — dead code in the ancestor (exported, never
  mounted in `app.ts`). Not ported.
- RLS migration `20260629000000_enable_rls_tenant_isolation` — enables/forces RLS
  keyed on a Postgres GUC (`app.tenant_id`) that nothing in `src/` ever sets.
  Confirmed unwired; dropped. Tenant isolation relies solely on `where: { tenantId }`.
- `workers/sla-consumer.worker.ts` business handlers (ticket/approval/invoice/KT
  event mapping) — hardcode CPMS sibling services' event shapes. Replaced with a
  generic `RabbitMqBus` + caller-supplied routing-key→handler map.
- Notif-svc mirror-publish inside `instances/v1/service.ts` — assumes CPMS
  notif-svc's fanout payload contract. Dropped; caller can subscribe to the
  generic outbox events and forward themselves.
- `@cpms/node-common` (logger, EventBus, JWT verifier, Valkey client, error
  taxonomy) does not exist outside CPMS-Platform — every module that re-exported
  from it is reimplemented locally and directly in this repo.

## Ports (local dev)
Postgres 5440, Valkey 6384, RabbitMQ AMQP 5674, RabbitMQ mgmt UI 15674 — verified
against every sibling Gen_* repo's docker-compose.yml to avoid collisions.
```

- [ ] **Step 4: Commit**

```bash
git add .gitignore docs/source-audit-notes.md
git commit -m "docs: copy sla-svc pre-context and write source audit notes"
```

---

### Task 2: Workspace scaffolding + Prisma schema + env config

**Files:**
- Create: `package.json` (root workspace)
- Create: `docker-compose.yml`
- Create: `packages/gen-sla-starter/package.json`
- Create: `packages/gen-sla-starter/tsconfig.json`
- Create: `packages/gen-sla-starter/vitest.config.ts`
- Create: `packages/gen-sla-starter/prisma/schema.prisma`
- Create: `packages/gen-sla-starter/src/common/errors.ts` (only `GenSlaConfigError` for now — full error set in Task 3)
- Create: `packages/gen-sla-starter/src/config/env.ts`
- Create: `packages/gen-sla-starter/src/config/env.test.ts`
- Create: `packages/gen-sla-demo/package.json`
- Create: `packages/gen-sla-demo/tsconfig.json`

**Interfaces:**
- Produces: `requireEnv(key: string): string` (throws `GenSlaConfigError`), `env: Env` (parsed eager config), `GenSlaConfigError` class — every later task's default-adapter resolvers use these.

- [ ] **Step 1: Root workspace `package.json`**

```json
{
  "name": "gen-sla-workspace",
  "private": true,
  "workspaces": ["packages/*"],
  "scripts": {
    "test": "npm test --workspace=@gen-ms/gen-sla-starter",
    "build": "npm run build --workspace=@gen-ms/gen-sla-starter && npm run build --workspace=@gen-ms/gen-sla-demo",
    "dev": "npm run dev --workspace=@gen-ms/gen-sla-demo"
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
      POSTGRES_DB: gensla
      POSTGRES_PASSWORD: postgres
    ports:
      - "5440:5432"   # next free after Gen_FMM (5441) — verified against every sibling
    volumes:
      - gensla_postgres_data:/var/lib/postgresql/data

  valkey:
    image: valkey/valkey:8-alpine
    ports:
      - "6384:6379"   # next free after Gen_FMM (6385)

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5674:5672"    # AMQP — only Gen_AUTH uses AMQP today (5673), next free
      - "15674:15672"  # management UI — http://localhost:15674 (guest/guest)

volumes:
  gensla_postgres_data:
```

- [ ] **Step 3: `packages/gen-sla-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-sla-starter",
  "version": "0.1.0",
  "repository": {
    "type": "git",
    "url": "git+https://github.com/GEN-MS/Gen_SLA.git"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com",
    "access": "restricted"
  },
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": { ".": "./dist/index.js" },
  "files": ["dist", "prisma/schema.prisma", "prisma/migrations"],
  "scripts": {
    "build": "prisma generate && tsc -p tsconfig.json",
    "test": "vitest run",
    "test:watch": "vitest",
    "postinstall": "prisma generate",
    "prisma:generate": "prisma generate",
    "prisma:migrate:dev": "prisma migrate dev",
    "prisma:migrate:deploy": "prisma migrate deploy"
  },
  "dependencies": {
    "@prisma/client": "^5.19.0",
    "prisma": "^5.19.0",
    "amqplib": "^0.10.0",
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
    "@testcontainers/postgresql": "^10.13.0",
    "@types/amqplib": "^0.10.0",
    "@types/cors": "^2.8.19",
    "@types/express": "^4.17.0",
    "@types/node": "^20.19.41",
    "@types/supertest": "^6.0.0",
    "pino-pretty": "^13.1.3",
    "supertest": "^7.0.0",
    "typescript": "^5.7.3",
    "vitest": "^2.0.0"
  }
}
```

- [ ] **Step 4: `packages/gen-sla-starter/tsconfig.json`**

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

- [ ] **Step 5: `packages/gen-sla-starter/vitest.config.ts`**

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

- [ ] **Step 6: Prisma schema — ported from ancestor, RLS migration dropped, generator output relocated**

```prisma
// packages/gen-sla-starter/prisma/schema.prisma
generator client {
  provider = "prisma-client-js"
  output   = "../__generated__/prisma"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model SlaPolicy {
  id           String    @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId     String    @map("tenant_id") @db.Uuid
  name         String    @db.VarChar(128)
  entityType   String    @map("entity_type") @db.VarChar(64)
  slaType      String    @map("sla_type") @db.VarChar(64)
  durationMins Int       @map("duration_mins")
  warningMins  Int       @map("warning_mins")
  isEnabled    Boolean   @default(true) @map("is_enabled")
  description  String?   @db.Text
  createdBy    String?   @map("created_by") @db.VarChar(120)
  updatedBy    String?   @map("updated_by") @db.VarChar(120)
  createdAt    DateTime  @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt    DateTime  @updatedAt @map("updated_at") @db.Timestamptz(6)

  instances SlaInstance[]

  @@unique([tenantId, entityType, slaType])
  @@index([tenantId])
  @@index([tenantId, entityType])
  @@index([isEnabled])
  @@map("sla_policies")
}

model SlaInstance {
  id          String    @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId    String    @map("tenant_id") @db.Uuid
  policyId    String    @map("policy_id") @db.Uuid
  entityType  String    @map("entity_type") @db.VarChar(64)
  entityId    String    @map("entity_id") @db.Uuid
  slaType     String    @map("sla_type") @db.VarChar(64)
  status      String    @default("ACTIVE") @db.VarChar(16)
  startedAt   DateTime  @map("started_at") @db.Timestamptz(6)
  dueAt       DateTime  @map("due_at") @db.Timestamptz(6)
  warningAt   DateTime  @map("warning_at") @db.Timestamptz(6)
  breachedAt  DateTime? @map("breached_at") @db.Timestamptz(6)
  resolvedAt  DateTime? @map("resolved_at") @db.Timestamptz(6)
  metadata    Json      @default("{}") @db.JsonB

  policy      SlaPolicy       @relation(fields: [policyId], references: [id], onDelete: Restrict)
  history     SlaHistory[]
  escalations SlaEscalation[]

  @@unique([tenantId, entityId, slaType])
  @@index([tenantId])
  @@index([status])
  @@index([dueAt])
  @@index([warningAt])
  @@index([tenantId, entityType, status])
  @@index([tenantId, status, dueAt])
  @@map("sla_instances")
}

model SlaHistory {
  id         String    @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId   String    @map("tenant_id") @db.Uuid
  instanceId String    @map("instance_id") @db.Uuid
  fromStatus String?   @map("from_status") @db.VarChar(16)
  toStatus   String    @map("to_status") @db.VarChar(16)
  note       String?   @db.Text
  occurredAt DateTime  @default(now()) @map("occurred_at") @db.Timestamptz(6)

  instance SlaInstance @relation(fields: [instanceId], references: [id], onDelete: Cascade)

  @@index([instanceId])
  @@index([tenantId, occurredAt])
  @@map("sla_history")
}

model SlaEscalation {
  id          String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId    String   @map("tenant_id") @db.Uuid
  instanceId  String   @map("instance_id") @db.Uuid
  level       Int      @default(1)
  eventType   String   @map("event_type") @db.VarChar(64)
  notified    Boolean  @default(false)
  escalatedAt DateTime @default(now()) @map("escalated_at") @db.Timestamptz(6)

  instance SlaInstance @relation(fields: [instanceId], references: [id], onDelete: Cascade)

  @@index([instanceId])
  @@index([tenantId, escalatedAt])
  @@map("sla_escalations")
}

model SlaOutboxEvent {
  id         String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId   String   @map("tenant_id") @db.Uuid
  eventType  String   @map("event_type") @db.VarChar(128)
  exchange   String   @db.VarChar(128)
  routingKey String   @map("routing_key") @db.VarChar(128)
  payload    Json     @db.JsonB
  status     String   @default("PENDING") @db.VarChar(16)
  retryCount Int      @default(0) @map("retry_count")
  createdAt  DateTime @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt  DateTime @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([status, createdAt])
  @@index([tenantId])
  @@map("sla_outbox_events")
}
```

Note: `entityType`/`slaType`/`status` are plain `VarChar` (not Postgres enums) — the ancestor's enums (`SlaEntityType`, `SlaPolicyType`, `SlaStatus`) hardcode CPMS's TICKET/APPROVAL/INVOICE/KT vocabulary. A generic library can't fix that vocabulary at the schema level; validation of allowed status values happens in application code (Task 6), and callers define their own entity/SLA-type vocabulary via policy rows.

- [ ] **Step 7: `GenSlaConfigError` (full error set added in Task 3)**

```ts
// packages/gen-sla-starter/src/common/errors.ts
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

// Boot-time configuration error — thrown by requireEnv() when createGenSla()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: never thrown during request handling, never reaches
// errorHandler, has no HTTP status code.
export class GenSlaConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSlaConfigError";
  }
}
```

- [ ] **Step 8: `env.ts` — eager schema for values every consumer needs, `requireEnv` for adapter-specific ones**

```ts
// packages/gen-sla-starter/src/config/env.ts
import "dotenv/config";
import { z } from "zod";
import { GenSlaConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  PORT: z.coerce.number().default(3202),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),

  TIMER_POLL_INTERVAL_MS: z.coerce.number().default(30_000),
  TIMER_BATCH_SIZE: z.coerce.number().default(100),
  TIMER_LOCK_TTL_S: z.coerce.number().default(60),

  OUTBOX_POLL_INTERVAL_MS: z.coerce.number().default(5_000),
  OUTBOX_BATCH_SIZE: z.coerce.number().default(50),
  OUTBOX_MAX_RETRIES: z.coerce.number().default(3),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it. DATABASE_URL, VALKEY_URL, RABBITMQ_URL, and
// GEN_SLA_INTERNAL_SECRET are intentionally NOT in EnvSchema — they're read
// through this function instead, only when the module/adapter that needs them
// is enabled and no override was supplied (see create-gen-sla.ts).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenSlaConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
```

- [ ] **Step 9: `env.test.ts`**

```ts
// packages/gen-sla-starter/src/config/env.test.ts
import { describe, it, expect, afterEach } from "vitest";
import { requireEnv } from "./env.ts";
import { GenSlaConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const ORIGINAL_ENV = process.env.SOME_TEST_VAR;

  afterEach(() => {
    if (ORIGINAL_ENV === undefined) delete process.env.SOME_TEST_VAR;
    else process.env.SOME_TEST_VAR = ORIGINAL_ENV;
  });

  it("returns the value when the env var is set", () => {
    process.env.SOME_TEST_VAR = "hello";
    expect(requireEnv("SOME_TEST_VAR")).toBe("hello");
  });

  it("throws GenSlaConfigError with a clear message when the env var is missing", () => {
    delete process.env.SOME_TEST_VAR;
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenSlaConfigError);
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(/SOME_TEST_VAR/);
  });

  it("throws GenSlaConfigError when the env var is set to an empty string", () => {
    process.env.SOME_TEST_VAR = "";
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenSlaConfigError);
  });
});
```

- [ ] **Step 10: `packages/gen-sla-demo/package.json` + `tsconfig.json`**

```json
{
  "name": "@gen-ms/gen-sla-demo",
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
    "@gen-ms/gen-sla-starter": "*",
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

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "lib": ["ES2022"],
    "types": ["node"],
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "sourceMap": true
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **Step 11: Install deps and run the one test that exists so far**

```bash
npm install
npm test --workspace=@gen-ms/gen-sla-starter
```

Expected: `env.test.ts` — 3 tests PASS.

- [ ] **Step 12: Commit**

```bash
git add package.json docker-compose.yml packages/gen-sla-starter/package.json packages/gen-sla-starter/tsconfig.json packages/gen-sla-starter/vitest.config.ts packages/gen-sla-starter/prisma/schema.prisma packages/gen-sla-starter/src/common/errors.ts packages/gen-sla-starter/src/config packages/gen-sla-demo/package.json packages/gen-sla-demo/tsconfig.json
git commit -m "feat: scaffold Gen_SLA workspace, Prisma schema, and env config"
```

---

### Task 3: Errors + logger + middleware

**Files:**
- Modify: `packages/gen-sla-starter/src/common/errors.ts` (add full domain error set)
- Create: `packages/gen-sla-starter/src/common/logger.ts`
- Create: `packages/gen-sla-starter/src/middleware/internal-secret.ts`
- Create: `packages/gen-sla-starter/src/middleware/internal-secret.test.ts`
- Create: `packages/gen-sla-starter/src/middleware/validate-tenant-id.ts`
- Create: `packages/gen-sla-starter/src/middleware/validate-tenant-id.test.ts`
- Create: `packages/gen-sla-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-sla-starter/src/middleware/error-handler.test.ts`

**Interfaces:**
- Consumes: `AppError`, `GenSlaConfigError` from `../common/errors.ts` (Task 2).
- Produces: `internalSecret(secret: string)`, `validateTenantId`, `errorHandler` Express middlewares; domain error classes `SlaPolicyNotFoundError`, `SlaInstanceNotFoundError`, `SlaPolicyConflictError`, `SlaPolicyHasActiveInstancesError`, `SlaInvalidTimingError`, `TenantIdInvalidError` — every module task below throws these.

- [ ] **Step 1: Full error set**

```ts
// packages/gen-sla-starter/src/common/errors.ts
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

export class TenantIdInvalidError extends AppError {
  constructor(got: string) {
    super(400, "TENANT_ID_INVALID", `tenantId must be a UUID, got "${got}"`);
  }
}

export class SlaPolicyNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SLA_POLICY_NOT_FOUND", `SLA policy "${id}" not found`);
  }
}

export class SlaPolicyConflictError extends AppError {
  constructor(entityType: string, slaType: string) {
    super(409, "SLA_POLICY_CONFLICT", `An SLA policy for entityType=${entityType} slaType=${slaType} already exists`);
  }
}

export class SlaPolicyHasActiveInstancesError extends AppError {
  constructor(id: string, activeCount: number) {
    super(
      409,
      "SLA_POLICY_HAS_ACTIVE_INSTANCES",
      `Cannot delete policy ${id} — ${activeCount} active SLA instance(s) still reference it. Resolve or cancel them first.`,
    );
  }
}

export class SlaInvalidTimingError extends AppError {
  constructor() {
    super(400, "SLA_INVALID_TIMING", "warningMins must be less than durationMins");
  }
}

export class SlaInstanceNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SLA_INSTANCE_NOT_FOUND", `SLA instance "${id}" not found`);
  }
}

// Boot-time configuration error — thrown by requireEnv() when createGenSla()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: never thrown during request handling, never reaches
// errorHandler, has no HTTP status code.
export class GenSlaConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenSlaConfigError";
  }
}
```

- [ ] **Step 2: Logger**

```ts
// packages/gen-sla-starter/src/common/logger.ts
import pino from "pino";
import { env } from "../config/env.ts";

export const logger = pino({
  level: env.LOG_LEVEL,
  transport: env.NODE_ENV === "development" ? { target: "pino-pretty" } : undefined,
});
```

- [ ] **Step 3: `internal-secret.ts` — write the failing test first**

```ts
// packages/gen-sla-starter/src/middleware/internal-secret.test.ts
import { describe, it, expect, vi } from "vitest";
import { internalSecret } from "./internal-secret.ts";
import { AppError } from "../common/errors.ts";

function fakeReqRes(headerValue?: string) {
  const req = { header: vi.fn(() => headerValue) } as any;
  const res = {} as any;
  const next = vi.fn();
  return { req, res, next };
}

describe("internalSecret", () => {
  it("calls next() with no error when the header matches", () => {
    const { req, res, next } = fakeReqRes("correct-secret");
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(err) with a 401 AppError when the header is missing", () => {
    const { req, res, next } = fakeReqRes(undefined);
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(AppError));
    const err = next.mock.calls[0][0] as AppError;
    expect(err.statusCode).toBe(401);
  });

  it("calls next(err) with a 401 AppError when the header doesn't match", () => {
    const { req, res, next } = fakeReqRes("wrong-secret");
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(AppError));
  });
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- internal-secret`
Expected: FAIL — `Cannot find module './internal-secret.ts'`

- [ ] **Step 5: Implement `internal-secret.ts`**

```ts
// packages/gen-sla-starter/src/middleware/internal-secret.ts
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

- [ ] **Step 6: `validate-tenant-id.ts` — test then implementation**

```ts
// packages/gen-sla-starter/src/middleware/validate-tenant-id.test.ts
import { describe, it, expect, vi } from "vitest";
import { validateTenantId } from "./validate-tenant-id.ts";
import { TenantIdInvalidError } from "../common/errors.ts";

describe("validateTenantId", () => {
  it("calls next() when tenantId is a valid UUID", () => {
    const req = { params: { tenantId: "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11" } } as any;
    const next = vi.fn();
    validateTenantId(req, {} as any, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(err) with TenantIdInvalidError when tenantId is not a UUID", () => {
    const req = { params: { tenantId: "not-a-uuid" } } as any;
    const next = vi.fn();
    validateTenantId(req, {} as any, next);
    expect(next).toHaveBeenCalledWith(expect.any(TenantIdInvalidError));
  });
});
```

```ts
// packages/gen-sla-starter/src/middleware/validate-tenant-id.ts
import type { Request, Response, NextFunction } from "express";
import { z } from "zod";
import { TenantIdInvalidError } from "../common/errors.ts";

const uuidSchema = z.string().uuid();

export function validateTenantId(req: Request, _res: Response, next: NextFunction): void {
  const result = uuidSchema.safeParse(req.params.tenantId);
  if (!result.success) {
    next(new TenantIdInvalidError(req.params.tenantId));
    return;
  }
  next();
}
```

- [ ] **Step 7: `error-handler.ts` — test then implementation**

```ts
// packages/gen-sla-starter/src/middleware/error-handler.test.ts
import { describe, it, expect, vi } from "vitest";
import { ZodError, z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { AppError } from "../common/errors.ts";

function fakeRes() {
  const res: any = {};
  res.status = vi.fn(() => res);
  res.json = vi.fn(() => res);
  return res;
}

describe("errorHandler", () => {
  it("maps AppError to its statusCode/code/message", () => {
    const res = fakeRes();
    errorHandler(new AppError(404, "NOT_FOUND", "missing"), {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(404);
    expect(res.json).toHaveBeenCalledWith({ error: "NOT_FOUND", message: "missing" });
  });

  it("maps ZodError to 400 VALIDATION_ERROR", () => {
    const res = fakeRes();
    const zodErr = z.object({ x: z.string() }).safeParse({ x: 1 });
    errorHandler((zodErr as any).error as ZodError, {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(400);
  });

  it("maps unknown errors to 500 internal_error", () => {
    const res = fakeRes();
    errorHandler(new Error("boom"), {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(500);
    expect(res.json).toHaveBeenCalledWith({ error: "internal_error", message: "An unexpected error occurred" });
  });
});
```

```ts
// packages/gen-sla-starter/src/middleware/error-handler.ts
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

- [ ] **Step 8: Run all tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter`
Expected: all PASS.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-sla-starter/src/common packages/gen-sla-starter/src/middleware
git commit -m "feat: add domain errors, logger, and internal-secret/tenant-id/error middleware"
```

---

### Task 4: Persistence + Valkey distributed lock

**Files:**
- Create: `packages/gen-sla-starter/src/infra/persistence/prisma-client.ts`
- Create: `packages/gen-sla-starter/src/domain/ports/distributed-lock.port.ts`
- Create: `packages/gen-sla-starter/src/infra/cache/valkey-lock.ts`
- Create: `packages/gen-sla-starter/src/infra/cache/valkey-lock.test.ts`

**Interfaces:**
- Produces: `getPrismaClient(): PrismaClient`, `IDistributedLock { acquire(key: string, ttlSeconds: number): Promise<boolean> }`, `ValkeyLock implements IDistributedLock` — consumed by the timer worker (Task 9) and `create-gen-sla.ts` (Task 10).

- [ ] **Step 1: Prisma client singleton**

```ts
// packages/gen-sla-starter/src/infra/persistence/prisma-client.ts
import { PrismaClient } from "../../../__generated__/prisma/index.js";

let client: PrismaClient | undefined;

export function getPrismaClient(): PrismaClient {
  if (!client) {
    client = new PrismaClient();
  }
  return client;
}
```

- [ ] **Step 2: `IDistributedLock` port**

```ts
// packages/gen-sla-starter/src/domain/ports/distributed-lock.port.ts
export interface IDistributedLock {
  /** Returns true if `key` was NOT already locked and is now held for `ttlSeconds`; false if already held. */
  acquire(key: string, ttlSeconds: number): Promise<boolean>;
}
```

- [ ] **Step 3: Write the failing test for `ValkeyLock`**

```ts
// packages/gen-sla-starter/src/infra/cache/valkey-lock.test.ts
import { describe, it, expect, vi } from "vitest";
import { ValkeyLock } from "./valkey-lock.ts";

vi.mock("ioredis", () => {
  return {
    Redis: vi.fn().mockImplementation(() => ({
      set: vi.fn(async (_key: string, _val: string, _ex: string, _ttl: number, _nx: string) => "OK"),
    })),
  };
});

describe("ValkeyLock", () => {
  it("acquire returns true when the underlying SET NX succeeds", async () => {
    const lock = new ValkeyLock("redis://localhost:6379");
    await expect(lock.acquire("k", 60)).resolves.toBe(true);
  });
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- valkey-lock`
Expected: FAIL — `Cannot find module './valkey-lock.ts'`

- [ ] **Step 5: Implement `ValkeyLock`**

```ts
// packages/gen-sla-starter/src/infra/cache/valkey-lock.ts
import { Redis } from "ioredis";
import type { IDistributedLock } from "../../domain/ports/distributed-lock.port.ts";

export class ValkeyLock implements IDistributedLock {
  private readonly client: Redis;

  constructor(redisUrl: string) {
    // lazyConnect: constructed unconditionally by createGenSla() whenever
    // modules.worker is enabled — an eager connection would open a real socket
    // for callers who override the worker's lock with their own implementation.
    this.client = new Redis(redisUrl, { lazyConnect: true });
  }

  async acquire(key: string, ttlSeconds: number): Promise<boolean> {
    const result = await this.client.set(key, "1", "EX", ttlSeconds, "NX");
    return result === "OK";
  }

  async disconnect(): Promise<void> {
    await this.client.quit();
  }
}
```

- [ ] **Step 6: Run test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- valkey-lock`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sla-starter/src/infra/persistence packages/gen-sla-starter/src/domain/ports/distributed-lock.port.ts packages/gen-sla-starter/src/infra/cache
git commit -m "feat: add Prisma client singleton and Valkey distributed lock"
```

---

### Task 5: Policies module

**Files:**
- Create: `packages/gen-sla-starter/src/domain/ports/policy-repo.port.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/types.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/schema.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/mapper.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/repo.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/service.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/service.test.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/controller.ts`
- Create: `packages/gen-sla-starter/src/modules/policies/v1/router.ts`

**Interfaces:**
- Consumes: `getPrismaClient()` (Task 4), `AppError`/`SlaPolicyNotFoundError`/`SlaPolicyConflictError`/`SlaPolicyHasActiveInstancesError`/`SlaInvalidTimingError` (Task 3), `internalSecret`/`validateTenantId` (Task 3).
- Produces: `ISlaPolicyRepo` port, `PolicyService` class (constructor-injected), `createPoliciesRouter({ policyService, internalSecretValue }): Router` mounted at `/api/v1/sla/:tenantId/policies` by `create-gen-sla.ts` (Task 10). `PolicyService.findByEntityAndType` and `countActiveInstances` are reused by the instances module (Task 6) and timer worker — not duplicated there.

- [ ] **Step 1: `ISlaPolicyRepo` port**

```ts
// packages/gen-sla-starter/src/domain/ports/policy-repo.port.ts
export interface SlaPolicyRecord {
  id: string;
  tenantId: string;
  name: string;
  entityType: string;
  slaType: string;
  durationMins: number;
  warningMins: number;
  isEnabled: boolean;
  description: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreatePolicyParams {
  tenantId: string;
  name: string;
  entityType: string;
  slaType: string;
  durationMins: number;
  warningMins: number;
  isEnabled: boolean;
  description?: string;
  createdBy?: string;
}

export interface UpdatePolicyParams {
  name?: string;
  durationMins?: number;
  warningMins?: number;
  isEnabled?: boolean;
  description?: string | null;
  updatedBy?: string;
}

export interface ListPoliciesFilters {
  entityType?: string;
  isEnabled?: boolean;
  page: number;
  pageSize: number;
}

export interface ISlaPolicyRepo {
  create(params: CreatePolicyParams): Promise<SlaPolicyRecord>;
  findById(id: string, tenantId: string): Promise<SlaPolicyRecord | null>;
  findByEntityAndType(tenantId: string, entityType: string, slaType: string): Promise<SlaPolicyRecord | null>;
  findAll(tenantId: string, filters: ListPoliciesFilters): Promise<{ data: SlaPolicyRecord[]; total: number }>;
  update(id: string, tenantId: string, params: UpdatePolicyParams): Promise<{ count: number }>;
  countActiveInstances(policyId: string, tenantId: string): Promise<number>;
  delete(id: string, tenantId: string): Promise<{ count: number }>;
}
```

- [ ] **Step 2: Types, schema, mapper**

```ts
// packages/gen-sla-starter/src/modules/policies/v1/types.ts
export interface SlaPolicyDto {
  id: string;
  tenantId: string;
  name: string;
  entityType: string;
  slaType: string;
  durationMins: number;
  warningMins: number;
  isEnabled: boolean;
  description: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaginatedPolicies {
  data: SlaPolicyDto[];
  total: number;
  page: number;
  pageSize: number;
}
```

```ts
// packages/gen-sla-starter/src/modules/policies/v1/schema.ts
import { z } from "zod";

export const createPolicySchema = z
  .object({
    name: z.string().trim().min(1).max(128),
    entityType: z.string().trim().min(1).max(64),
    slaType: z.string().trim().min(1).max(64),
    durationMins: z.number().int().positive(),
    warningMins: z.number().int().positive(),
    isEnabled: z.boolean().default(true),
    description: z.string().trim().max(1024).optional(),
    createdBy: z.string().trim().max(120).optional(),
  })
  .refine((d) => d.warningMins < d.durationMins, {
    message: "warningMins must be less than durationMins",
    path: ["warningMins"],
  });

export type CreatePolicyInput = z.infer<typeof createPolicySchema>;

export const updatePolicySchema = z
  .object({
    name: z.string().trim().min(1).max(128).optional(),
    durationMins: z.number().int().positive().optional(),
    warningMins: z.number().int().positive().optional(),
    isEnabled: z.boolean().optional(),
    description: z.string().trim().max(1024).nullable().optional(),
    updatedBy: z.string().trim().max(120).optional(),
  })
  .refine((d) => Object.keys(d).length > 0, { message: "At least one field is required" });

export type UpdatePolicyInput = z.infer<typeof updatePolicySchema>;

export const listPoliciesQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  isEnabled: z.string().transform((v) => v === "true").optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export type ListPoliciesQuery = z.infer<typeof listPoliciesQuerySchema>;

export const policyIdSchema = z.object({ id: z.string().uuid() });
```

```ts
// packages/gen-sla-starter/src/modules/policies/v1/mapper.ts
import type { SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";
import type { SlaPolicyDto } from "./types.ts";

export function mapPolicyToDto(policy: SlaPolicyRecord): SlaPolicyDto {
  return {
    id: policy.id,
    tenantId: policy.tenantId,
    name: policy.name,
    entityType: policy.entityType,
    slaType: policy.slaType,
    durationMins: policy.durationMins,
    warningMins: policy.warningMins,
    isEnabled: policy.isEnabled,
    description: policy.description,
    createdBy: policy.createdBy,
    updatedBy: policy.updatedBy,
    createdAt: policy.createdAt.toISOString(),
    updatedAt: policy.updatedAt.toISOString(),
  };
}
```

- [ ] **Step 3: Prisma-backed repo implementation**

```ts
// packages/gen-sla-starter/src/modules/policies/v1/repo.ts
import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type {
  ISlaPolicyRepo,
  CreatePolicyParams,
  UpdatePolicyParams,
  ListPoliciesFilters,
  SlaPolicyRecord,
} from "../../../domain/ports/policy-repo.port.ts";

export class PrismaPolicyRepo implements ISlaPolicyRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(params: CreatePolicyParams): Promise<SlaPolicyRecord> {
    return this.prisma.slaPolicy.create({
      data: {
        tenantId: params.tenantId,
        name: params.name,
        entityType: params.entityType,
        slaType: params.slaType,
        durationMins: params.durationMins,
        warningMins: params.warningMins,
        isEnabled: params.isEnabled,
        description: params.description ?? null,
        createdBy: params.createdBy ?? null,
        updatedBy: params.createdBy ?? null,
      },
    });
  }

  async findById(id: string, tenantId: string): Promise<SlaPolicyRecord | null> {
    return this.prisma.slaPolicy.findFirst({ where: { id, tenantId } });
  }

  async findByEntityAndType(tenantId: string, entityType: string, slaType: string): Promise<SlaPolicyRecord | null> {
    return this.prisma.slaPolicy.findFirst({
      where: { tenantId, entityType, slaType, isEnabled: true },
    });
  }

  async findAll(
    tenantId: string,
    filters: ListPoliciesFilters,
  ): Promise<{ data: SlaPolicyRecord[]; total: number }> {
    const where: Prisma.SlaPolicyWhereInput = { tenantId };
    if (filters.entityType !== undefined) where.entityType = filters.entityType;
    if (filters.isEnabled !== undefined) where.isEnabled = filters.isEnabled;

    const [data, total] = await this.prisma.$transaction([
      this.prisma.slaPolicy.findMany({
        where,
        orderBy: [{ entityType: "asc" }, { slaType: "asc" }],
        skip: (filters.page - 1) * filters.pageSize,
        take: filters.pageSize,
      }),
      this.prisma.slaPolicy.count({ where }),
    ]);

    return { data, total };
  }

  async update(id: string, tenantId: string, params: UpdatePolicyParams): Promise<{ count: number }> {
    return this.prisma.slaPolicy.updateMany({
      where: { id, tenantId },
      data: {
        ...(params.name !== undefined && { name: params.name }),
        ...(params.durationMins !== undefined && { durationMins: params.durationMins }),
        ...(params.warningMins !== undefined && { warningMins: params.warningMins }),
        ...(params.isEnabled !== undefined && { isEnabled: params.isEnabled }),
        ...(params.description !== undefined && { description: params.description }),
        ...(params.updatedBy !== undefined && { updatedBy: params.updatedBy }),
        updatedAt: new Date(),
      },
    });
  }

  async countActiveInstances(policyId: string, tenantId: string): Promise<number> {
    return this.prisma.slaInstance.count({
      where: { policyId, tenantId, status: { in: ["ACTIVE", "WARNING"] } },
    });
  }

  async delete(id: string, tenantId: string): Promise<{ count: number }> {
    return this.prisma.slaPolicy.deleteMany({ where: { id, tenantId } });
  }
}
```

- [ ] **Step 4: Write the failing test for `PolicyService`**

```ts
// packages/gen-sla-starter/src/modules/policies/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { PolicyService } from "./service.ts";
import { SlaPolicyNotFoundError, SlaPolicyConflictError, SlaPolicyHasActiveInstancesError } from "../../../common/errors.ts";
import type { ISlaPolicyRepo, SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";

function basePolicy(overrides: Partial<SlaPolicyRecord> = {}): SlaPolicyRecord {
  return {
    id: "policy-1",
    tenantId: "tenant-1",
    name: "First Response",
    entityType: "TICKET",
    slaType: "FIRST_RESPONSE",
    durationMins: 60,
    warningMins: 45,
    isEnabled: true,
    description: null,
    createdBy: null,
    updatedBy: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISlaPolicyRepo> = {}): ISlaPolicyRepo {
  return {
    create: vi.fn(async () => basePolicy()),
    findById: vi.fn(async () => basePolicy()),
    findByEntityAndType: vi.fn(async () => null),
    findAll: vi.fn(async () => ({ data: [basePolicy()], total: 1 })),
    update: vi.fn(async () => ({ count: 1 })),
    countActiveInstances: vi.fn(async () => 0),
    delete: vi.fn(async () => ({ count: 1 })),
    ...overrides,
  };
}

describe("PolicyService", () => {
  it("throws SlaPolicyConflictError when an enabled policy for entityType+slaType already exists", async () => {
    const repo = fakeRepo({ findByEntityAndType: vi.fn(async () => basePolicy()) });
    const service = new PolicyService(repo);
    await expect(
      service.createPolicy("tenant-1", {
        name: "x", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
      }),
    ).rejects.toThrow(SlaPolicyConflictError);
  });

  it("creates a policy when no conflict exists", async () => {
    const repo = fakeRepo();
    const service = new PolicyService(repo);
    const result = await service.createPolicy("tenant-1", {
      name: "x", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
    });
    expect(repo.create).toHaveBeenCalled();
    expect(result.id).toBe("policy-1");
  });

  it("throws SlaPolicyNotFoundError from getPolicy when missing", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new PolicyService(repo);
    await expect(service.getPolicy("tenant-1", "missing")).rejects.toThrow(SlaPolicyNotFoundError);
  });

  it("throws SlaPolicyHasActiveInstancesError from deletePolicy when active instances reference it", async () => {
    const repo = fakeRepo({ countActiveInstances: vi.fn(async () => 2) });
    const service = new PolicyService(repo);
    await expect(service.deletePolicy("tenant-1", "policy-1")).rejects.toThrow(SlaPolicyHasActiveInstancesError);
  });

  it("deletePolicy succeeds when there are no active instances", async () => {
    const repo = fakeRepo();
    const service = new PolicyService(repo);
    await expect(service.deletePolicy("tenant-1", "policy-1")).resolves.toBeUndefined();
    expect(repo.delete).toHaveBeenCalledWith("policy-1", "tenant-1");
  });
});
```

- [ ] **Step 5: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/policies`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 6: Implement `PolicyService`**

```ts
// packages/gen-sla-starter/src/modules/policies/v1/service.ts
import { AppError, SlaPolicyNotFoundError, SlaPolicyHasActiveInstancesError, SlaPolicyConflictError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import { mapPolicyToDto } from "./mapper.ts";
import type { ISlaPolicyRepo } from "../../../domain/ports/policy-repo.port.ts";
import type { CreatePolicyInput, ListPoliciesQuery, UpdatePolicyInput } from "./schema.ts";
import type { SlaPolicyDto, PaginatedPolicies } from "./types.ts";

export class PolicyService {
  constructor(private readonly repo: ISlaPolicyRepo) {}

  async createPolicy(tenantId: string, input: CreatePolicyInput): Promise<SlaPolicyDto> {
    const existing = await this.repo.findByEntityAndType(tenantId, input.entityType, input.slaType);
    if (existing) {
      throw new SlaPolicyConflictError(input.entityType, input.slaType);
    }

    const policy = await this.repo.create({ tenantId, ...input });
    logger.info({ policyId: policy.id, tenantId, entityType: input.entityType }, "[policy] created");
    return mapPolicyToDto(policy);
  }

  async listPolicies(tenantId: string, query: ListPoliciesQuery): Promise<PaginatedPolicies> {
    const { data, total } = await this.repo.findAll(tenantId, {
      entityType: query.entityType,
      isEnabled: query.isEnabled,
      page: query.page,
      pageSize: query.pageSize,
    });

    return { data: data.map(mapPolicyToDto), total, page: query.page, pageSize: query.pageSize };
  }

  async getPolicy(tenantId: string, id: string): Promise<SlaPolicyDto> {
    const policy = await this.repo.findById(id, tenantId);
    if (!policy) throw new SlaPolicyNotFoundError(id);
    return mapPolicyToDto(policy);
  }

  // Exposed for the instances module (Task 6) and the timer worker's
  // createFromPolicy/createWithFixedDeadline path — not duplicated there.
  async findActivePolicy(tenantId: string, entityType: string, slaType: string) {
    return this.repo.findByEntityAndType(tenantId, entityType, slaType);
  }

  async updatePolicy(tenantId: string, id: string, input: UpdatePolicyInput): Promise<SlaPolicyDto> {
    const existing = await this.repo.findById(id, tenantId);
    if (!existing) throw new SlaPolicyNotFoundError(id);

    const effectiveDuration = input.durationMins ?? existing.durationMins;
    const effectiveWarning = input.warningMins ?? existing.warningMins;

    // durationMins=0 is a sentinel for fixed-deadline policies (deadline supplied
    // at instance creation time, not computed from policy duration) — skip the
    // ratio check for these, only warningMins is meaningful.
    if (effectiveDuration > 0 && effectiveWarning >= effectiveDuration) {
      throw new AppError(400, "SLA_INVALID_TIMING", "warningMins must be less than durationMins");
    }

    await this.repo.update(id, tenantId, input);
    const updated = await this.repo.findById(id, tenantId);
    if (!updated) throw new SlaPolicyNotFoundError(id);

    logger.info({ policyId: id, tenantId }, "[policy] updated");
    return mapPolicyToDto(updated);
  }

  async deletePolicy(tenantId: string, id: string): Promise<void> {
    const existing = await this.repo.findById(id, tenantId);
    if (!existing) throw new SlaPolicyNotFoundError(id);

    const activeCount = await this.repo.countActiveInstances(id, tenantId);
    if (activeCount > 0) {
      throw new SlaPolicyHasActiveInstancesError(id, activeCount);
    }

    const result = await this.repo.delete(id, tenantId);
    if (result.count === 0) throw new SlaPolicyNotFoundError(id);

    logger.info({ policyId: id, tenantId }, "[policy] deleted");
  }
}
```

- [ ] **Step 7: Run tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/policies`
Expected: all PASS.

- [ ] **Step 8: Controller + router**

```ts
// packages/gen-sla-starter/src/modules/policies/v1/controller.ts
import type { Request, Response } from "express";
import { createPolicySchema, updatePolicySchema, listPoliciesQuerySchema, policyIdSchema } from "./schema.ts";
import type { PolicyService } from "./service.ts";

export function makePolicyController(service: PolicyService) {
  return {
    async createPolicy(req: Request, res: Response) {
      const input = createPolicySchema.parse(req.body);
      const policy = await service.createPolicy(req.params.tenantId, input);
      res.status(201).json(policy);
    },
    async listPolicies(req: Request, res: Response) {
      const query = listPoliciesQuerySchema.parse(req.query);
      const result = await service.listPolicies(req.params.tenantId, query);
      res.json(result);
    },
    async getPolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      const policy = await service.getPolicy(req.params.tenantId, id);
      res.json(policy);
    },
    async updatePolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      const input = updatePolicySchema.parse(req.body);
      const policy = await service.updatePolicy(req.params.tenantId, id, input);
      res.json(policy);
    },
    async deletePolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      await service.deletePolicy(req.params.tenantId, id);
      res.status(204).send();
    },
  };
}
```

```ts
// packages/gen-sla-starter/src/modules/policies/v1/router.ts
import { Router } from "express";
import { makePolicyController } from "./controller.ts";
import type { PolicyService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface PoliciesRouterDeps {
  policyService: PolicyService;
  internalSecretValue: string;
}

export function createPoliciesRouter(deps: PoliciesRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makePolicyController(deps.policyService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.post("/", (req, res, next) => controller.createPolicy(req, res).catch(next));
  router.get("/", (req, res, next) => controller.listPolicies(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getPolicy(req, res).catch(next));
  router.patch("/:id", (req, res, next) => controller.updatePolicy(req, res).catch(next));
  router.delete("/:id", (req, res, next) => controller.deletePolicy(req, res).catch(next));

  return router;
}
```

- [ ] **Step 9: Commit**

```bash
git add packages/gen-sla-starter/src/domain/ports/policy-repo.port.ts packages/gen-sla-starter/src/modules/policies
git commit -m "feat: add SLA policies module (CRUD + service tests)"
```

---

### Task 6: Instances module

**Files:**
- Create: `packages/gen-sla-starter/src/domain/ports/instance-repo.port.ts`
- Create: `packages/gen-sla-starter/src/domain/ports/outbox-writer.port.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/types.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/schema.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/mapper.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/repo.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/service.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/service.test.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/controller.ts`
- Create: `packages/gen-sla-starter/src/modules/instances/v1/router.ts`
- Create: `packages/gen-sla-starter/src/config/constants.ts`

**Interfaces:**
- Consumes: `ISlaPolicyRepo`/`PolicyService.findActivePolicy` (Task 5), `SlaInstanceNotFoundError` (Task 3).
- Produces: `ISlaInstanceRepo`, `IOutboxWriter` ports; `InstanceService` with `createInstance`, `createFromPolicy`, `createWithFixedDeadline`, `transitionToWarning`, `transitionToBreached`, `resolveInstances`, `escalateInstances`, `cancelInstances` — the timer worker (Task 9) calls `transitionToWarning`/`transitionToBreached` directly; a caller's own event handler calls the `createFrom*`/`resolveInstances`/etc. methods (there is no built-in consumer wiring them up, per the Global Constraints genericization decision). `createInstancesRouter({ instanceService, internalSecretValue })` mounted at `/api/v1/sla/:tenantId/instances`.

- [ ] **Step 1: Shared constants — the routing-key vocabulary for outbox events (generic, not CPMS's)**

```ts
// packages/gen-sla-starter/src/config/constants.ts
export const SLA_EXCHANGE = "gen-sla.events";

export const SLA_ROUTING_KEYS = {
  INSTANCE_CREATED: "sla.instance.created",
  INSTANCE_WARNING: "sla.instance.warning",
  INSTANCE_BREACHED: "sla.instance.breached",
  INSTANCE_RESOLVED: "sla.instance.resolved",
  INSTANCE_CANCELLED: "sla.instance.cancelled",
} as const;

export const SLA_STATUSES = ["ACTIVE", "WARNING", "BREACHED", "RESOLVED", "CANCELLED"] as const;
export type SlaStatus = (typeof SLA_STATUSES)[number];

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
```

- [ ] **Step 2: `ISlaInstanceRepo` and `IOutboxWriter` ports**

```ts
// packages/gen-sla-starter/src/domain/ports/instance-repo.port.ts
export interface SlaInstanceRecord {
  id: string;
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  status: string;
  startedAt: Date;
  dueAt: Date;
  warningAt: Date;
  breachedAt: Date | null;
  resolvedAt: Date | null;
  metadata: Record<string, unknown>;
}

export interface CreateInstanceParams {
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  startedAt: Date;
  dueAt: Date;
  warningAt: Date;
  metadata: Record<string, unknown>;
}

export interface ListInstancesFilters {
  entityType?: string;
  entityId?: string;
  status?: string;
  page: number;
  pageSize: number;
}

export interface ISlaInstanceRepo {
  create(params: CreateInstanceParams): Promise<SlaInstanceRecord>;
  findById(id: string, tenantId: string): Promise<SlaInstanceRecord | null>;
  findByEntityAndType(tenantId: string, entityId: string, slaType: string): Promise<SlaInstanceRecord | null>;
  findAll(tenantId: string, filters: ListInstancesFilters): Promise<{ data: SlaInstanceRecord[]; total: number }>;
  updateStatus(
    id: string,
    tenantId: string,
    status: string,
    extra?: { breachedAt?: Date; resolvedAt?: Date },
  ): Promise<SlaInstanceRecord>;
  appendHistory(tenantId: string, instanceId: string, fromStatus: string | null, toStatus: string, note?: string): Promise<void>;
  findActiveInstancesDue(batchSize: number): Promise<SlaInstanceRecord[]>;
  findActiveInstancesNearWarning(batchSize: number): Promise<SlaInstanceRecord[]>;
  cancelByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }>;
  resolveByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }>;
  findActiveByEntityId(tenantId: string, entityId: string): Promise<SlaInstanceRecord[]>;
  createEscalation(tenantId: string, instanceId: string, level: number, eventType: string): Promise<void>;
}
```

```ts
// packages/gen-sla-starter/src/domain/ports/outbox-writer.port.ts
export interface OutboxEnqueueParams {
  tenantId: string;
  eventType: string;
  exchange: string;
  routingKey: string;
  payload: object;
}

export interface IOutboxWriter {
  enqueue(params: OutboxEnqueueParams): Promise<void>;
}
```

- [ ] **Step 3: Types, schema, mapper**

```ts
// packages/gen-sla-starter/src/modules/instances/v1/types.ts
export interface SlaInstanceDto {
  id: string;
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  status: string;
  startedAt: string;
  dueAt: string;
  warningAt: string;
  breachedAt: string | null;
  resolvedAt: string | null;
  metadata: Record<string, unknown>;
}

export interface PaginatedInstances {
  data: SlaInstanceDto[];
  total: number;
  page: number;
  pageSize: number;
}
```

```ts
// packages/gen-sla-starter/src/modules/instances/v1/schema.ts
import { z } from "zod";
import { SLA_STATUSES } from "../../../config/constants.ts";

export const listInstancesQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  entityId: z.string().uuid().optional(),
  status: z.enum(SLA_STATUSES).optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export type ListInstancesQuery = z.infer<typeof listInstancesQuerySchema>;

export const instanceIdSchema = z.object({ id: z.string().uuid() });
```

```ts
// packages/gen-sla-starter/src/modules/instances/v1/mapper.ts
import type { SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { SlaInstanceDto } from "./types.ts";

export function mapInstanceToDto(instance: SlaInstanceRecord): SlaInstanceDto {
  return {
    id: instance.id,
    tenantId: instance.tenantId,
    policyId: instance.policyId,
    entityType: instance.entityType,
    entityId: instance.entityId,
    slaType: instance.slaType,
    status: instance.status,
    startedAt: instance.startedAt.toISOString(),
    dueAt: instance.dueAt.toISOString(),
    warningAt: instance.warningAt.toISOString(),
    breachedAt: instance.breachedAt?.toISOString() ?? null,
    resolvedAt: instance.resolvedAt?.toISOString() ?? null,
    metadata: instance.metadata ?? {},
  };
}
```

- [ ] **Step 4: Prisma-backed repo implementation**

```ts
// packages/gen-sla-starter/src/modules/instances/v1/repo.ts
import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type {
  ISlaInstanceRepo,
  CreateInstanceParams,
  ListInstancesFilters,
  SlaInstanceRecord,
} from "../../../domain/ports/instance-repo.port.ts";

export class PrismaInstanceRepo implements ISlaInstanceRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(params: CreateInstanceParams): Promise<SlaInstanceRecord> {
    return this.prisma.slaInstance.create({
      data: {
        tenantId: params.tenantId,
        policyId: params.policyId,
        entityType: params.entityType,
        entityId: params.entityId,
        slaType: params.slaType,
        status: "ACTIVE",
        startedAt: params.startedAt,
        dueAt: params.dueAt,
        warningAt: params.warningAt,
        metadata: params.metadata as Prisma.InputJsonValue,
      },
    }) as unknown as Promise<SlaInstanceRecord>;
  }

  async findById(id: string, tenantId: string): Promise<SlaInstanceRecord | null> {
    return this.prisma.slaInstance.findFirst({ where: { id, tenantId } }) as unknown as Promise<SlaInstanceRecord | null>;
  }

  async findByEntityAndType(tenantId: string, entityId: string, slaType: string): Promise<SlaInstanceRecord | null> {
    return this.prisma.slaInstance.findFirst({
      where: { tenantId, entityId, slaType },
    }) as unknown as Promise<SlaInstanceRecord | null>;
  }

  async findAll(
    tenantId: string,
    filters: ListInstancesFilters,
  ): Promise<{ data: SlaInstanceRecord[]; total: number }> {
    const where: Prisma.SlaInstanceWhereInput = { tenantId };
    if (filters.entityType !== undefined) where.entityType = filters.entityType;
    if (filters.entityId !== undefined) where.entityId = filters.entityId;
    if (filters.status !== undefined) where.status = filters.status;

    const [data, total] = await this.prisma.$transaction([
      this.prisma.slaInstance.findMany({
        where,
        orderBy: { startedAt: "desc" },
        skip: (filters.page - 1) * filters.pageSize,
        take: filters.pageSize,
      }),
      this.prisma.slaInstance.count({ where }),
    ]);

    return { data: data as unknown as SlaInstanceRecord[], total };
  }

  async updateStatus(
    id: string,
    tenantId: string,
    status: string,
    extra?: { breachedAt?: Date; resolvedAt?: Date },
  ): Promise<SlaInstanceRecord> {
    return this.prisma.slaInstance.update({
      where: { id, tenantId },
      data: {
        status,
        ...(extra?.breachedAt && { breachedAt: extra.breachedAt }),
        ...(extra?.resolvedAt && { resolvedAt: extra.resolvedAt }),
      },
    }) as unknown as Promise<SlaInstanceRecord>;
  }

  async appendHistory(
    tenantId: string,
    instanceId: string,
    fromStatus: string | null,
    toStatus: string,
    note?: string,
  ): Promise<void> {
    await this.prisma.slaHistory.create({
      data: { tenantId, instanceId, fromStatus, toStatus, note },
    });
  }

  async findActiveInstancesDue(batchSize: number): Promise<SlaInstanceRecord[]> {
    const now = new Date();
    return this.prisma.slaInstance.findMany({
      where: { status: { in: ["ACTIVE", "WARNING"] }, dueAt: { lte: now } },
      take: batchSize,
      orderBy: { dueAt: "asc" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async findActiveInstancesNearWarning(batchSize: number): Promise<SlaInstanceRecord[]> {
    const now = new Date();
    return this.prisma.slaInstance.findMany({
      where: { status: "ACTIVE", warningAt: { lte: now } },
      take: batchSize,
      orderBy: { warningAt: "asc" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async cancelByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }> {
    return this.prisma.slaInstance.updateMany({
      where: { tenantId, entityId, slaType: { in: slaTypes }, status: { in: ["ACTIVE", "WARNING"] } },
      data: { status: "CANCELLED" },
    });
  }

  async resolveByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }> {
    return this.prisma.slaInstance.updateMany({
      where: { tenantId, entityId, slaType: { in: slaTypes }, status: { in: ["ACTIVE", "WARNING"] } },
      data: { status: "RESOLVED", resolvedAt: new Date() },
    });
  }

  async findActiveByEntityId(tenantId: string, entityId: string): Promise<SlaInstanceRecord[]> {
    return this.prisma.slaInstance.findMany({
      where: { tenantId, entityId, status: "ACTIVE" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async createEscalation(tenantId: string, instanceId: string, level: number, eventType: string): Promise<void> {
    await this.prisma.slaEscalation.create({
      data: { tenantId, instanceId, level, eventType, notified: false },
    });
  }
}
```

- [ ] **Step 5: Write the failing test for `InstanceService`**

```ts
// packages/gen-sla-starter/src/modules/instances/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { InstanceService } from "./service.ts";
import { SlaInstanceNotFoundError } from "../../../common/errors.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { ISlaPolicyRepo, SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";
import type { IOutboxWriter } from "../../../domain/ports/outbox-writer.port.ts";

function baseInstance(overrides: Partial<SlaInstanceRecord> = {}): SlaInstanceRecord {
  return {
    id: "inst-1", tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET",
    entityId: "entity-1", slaType: "FIRST_RESPONSE", status: "ACTIVE",
    startedAt: new Date(), dueAt: new Date(Date.now() + 3_600_000), warningAt: new Date(Date.now() + 2_700_000),
    breachedAt: null, resolvedAt: null, metadata: {},
    ...overrides,
  };
}

function basePolicy(overrides: Partial<SlaPolicyRecord> = {}): SlaPolicyRecord {
  return {
    id: "policy-1", tenantId: "tenant-1", name: "First Response", entityType: "TICKET",
    slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
    description: null, createdBy: null, updatedBy: null, createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeInstanceRepo(overrides: Partial<ISlaInstanceRepo> = {}): ISlaInstanceRepo {
  return {
    create: vi.fn(async () => baseInstance()),
    findById: vi.fn(async () => baseInstance()),
    findByEntityAndType: vi.fn(async () => null),
    findAll: vi.fn(async () => ({ data: [baseInstance()], total: 1 })),
    updateStatus: vi.fn(async (_id, _t, status) => baseInstance({ status })),
    appendHistory: vi.fn(async () => undefined),
    findActiveInstancesDue: vi.fn(async () => []),
    findActiveInstancesNearWarning: vi.fn(async () => []),
    cancelByEntityId: vi.fn(async () => ({ count: 1 })),
    resolveByEntityId: vi.fn(async () => ({ count: 1 })),
    findActiveByEntityId: vi.fn(async () => []),
    createEscalation: vi.fn(async () => undefined),
    ...overrides,
  };
}

function fakePolicyRepo(overrides: Partial<ISlaPolicyRepo> = {}): ISlaPolicyRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByEntityAndType: vi.fn(async () => basePolicy()),
    findAll: vi.fn(),
    update: vi.fn(),
    countActiveInstances: vi.fn(),
    delete: vi.fn(),
    ...overrides,
  };
}

function fakeOutbox(): IOutboxWriter {
  return { enqueue: vi.fn(async () => undefined) };
}

describe("InstanceService", () => {
  it("getInstance throws SlaInstanceNotFoundError when missing", async () => {
    const service = new InstanceService(fakeInstanceRepo({ findById: vi.fn(async () => null) }), fakePolicyRepo(), fakeOutbox());
    await expect(service.getInstance("tenant-1", "missing")).rejects.toThrow(SlaInstanceNotFoundError);
  });

  it("createInstance skips creating a duplicate when an ACTIVE/WARNING instance already exists", async () => {
    const instanceRepo = fakeInstanceRepo({ findByEntityAndType: vi.fn(async () => baseInstance()) });
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);

    await service.createInstance({
      tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET", entityId: "entity-1",
      slaType: "FIRST_RESPONSE", startedAt: new Date(), dueAt: new Date(), warningAt: new Date(), metadata: {},
    });

    expect(instanceRepo.create).not.toHaveBeenCalled();
    expect(outbox.enqueue).not.toHaveBeenCalled();
  });

  it("createFromPolicy returns null when no enabled policy exists for entityType+slaType", async () => {
    const policyRepo = fakePolicyRepo({ findByEntityAndType: vi.fn(async () => null) });
    const service = new InstanceService(fakeInstanceRepo(), policyRepo, fakeOutbox());
    const result = await service.createFromPolicy("tenant-1", "TICKET", "entity-1", "FIRST_RESPONSE", {}, new Date());
    expect(result).toBeNull();
  });

  it("transitionToWarning is a no-op when the instance isn't ACTIVE", async () => {
    const instanceRepo = fakeInstanceRepo({ findById: vi.fn(async () => baseInstance({ status: "BREACHED" })) });
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), fakeOutbox());
    await service.transitionToWarning("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).not.toHaveBeenCalled();
  });

  it("transitionToWarning moves ACTIVE to WARNING and enqueues an outbox event", async () => {
    const instanceRepo = fakeInstanceRepo();
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);
    await service.transitionToWarning("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).toHaveBeenCalledWith("inst-1", "tenant-1", "WARNING");
    expect(outbox.enqueue).toHaveBeenCalledTimes(1);
  });

  it("transitionToBreached moves ACTIVE/WARNING to BREACHED with breachedAt and enqueues an outbox event", async () => {
    const instanceRepo = fakeInstanceRepo();
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);
    await service.transitionToBreached("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).toHaveBeenCalledWith("inst-1", "tenant-1", "BREACHED", { breachedAt: expect.any(Date) });
    expect(outbox.enqueue).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 6: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/instances`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 7: Implement `InstanceService`**

```ts
// packages/gen-sla-starter/src/modules/instances/v1/service.ts
import { randomUUID } from "node:crypto";
import { SlaInstanceNotFoundError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import { SLA_EXCHANGE, SLA_ROUTING_KEYS } from "../../../config/constants.ts";
import { mapInstanceToDto } from "./mapper.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { ISlaPolicyRepo } from "../../../domain/ports/policy-repo.port.ts";
import type { IOutboxWriter } from "../../../domain/ports/outbox-writer.port.ts";
import type { CreateInstanceParams as CreateInstanceRepoParams } from "../../../domain/ports/instance-repo.port.ts";
import type { ListInstancesQuery } from "./schema.ts";
import type { SlaInstanceDto, PaginatedInstances } from "./types.ts";

function buildEventPayload(eventType: string, tenantId: string, data: Record<string, unknown>) {
  return {
    event_id: randomUUID(),
    event_type: eventType,
    event_version: 1,
    occurred_at: new Date().toISOString(),
    producer: { service: "gen-sla-starter", version: "dev" },
    tenant_id: tenantId,
    data,
  };
}

export class InstanceService {
  constructor(
    private readonly instanceRepo: ISlaInstanceRepo,
    private readonly policyRepo: ISlaPolicyRepo,
    private readonly outbox: IOutboxWriter,
  ) {}

  async createInstance(params: CreateInstanceRepoParams): Promise<SlaInstanceDto> {
    const existing = await this.instanceRepo.findByEntityAndType(params.tenantId, params.entityId, params.slaType);
    if (existing && ["ACTIVE", "WARNING"].includes(existing.status)) {
      logger.info({ instanceId: existing.id, entityId: params.entityId, slaType: params.slaType }, "[instance] active instance already exists — skipping");
      return mapInstanceToDto(existing);
    }

    const instance = await this.instanceRepo.create(params);
    await this.instanceRepo.appendHistory(params.tenantId, instance.id, null, "ACTIVE", "SLA instance created");

    await this.outbox.enqueue({
      tenantId: params.tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_CREATED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_CREATED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_CREATED, instance.tenantId, {
        instanceId: instance.id,
        entityType: instance.entityType,
        entityId: instance.entityId,
        slaType: instance.slaType,
        dueAt: instance.dueAt.toISOString(),
        warningAt: instance.warningAt.toISOString(),
      }),
    });

    logger.info({ instanceId: instance.id, entityId: params.entityId, slaType: params.slaType }, "[instance] created");
    return mapInstanceToDto(instance);
  }

  async listInstances(tenantId: string, query: ListInstancesQuery): Promise<PaginatedInstances> {
    const { data, total } = await this.instanceRepo.findAll(tenantId, {
      entityType: query.entityType,
      entityId: query.entityId,
      status: query.status,
      page: query.page,
      pageSize: query.pageSize,
    });
    return { data: data.map(mapInstanceToDto), total, page: query.page, pageSize: query.pageSize };
  }

  async getInstance(tenantId: string, id: string): Promise<SlaInstanceDto> {
    const instance = await this.instanceRepo.findById(id, tenantId);
    if (!instance) throw new SlaInstanceNotFoundError(id);
    return mapInstanceToDto(instance);
  }

  async transitionToWarning(instanceId: string, tenantId: string): Promise<void> {
    const instance = await this.instanceRepo.findById(instanceId, tenantId);
    if (!instance || instance.status !== "ACTIVE") return;

    await this.instanceRepo.updateStatus(instanceId, tenantId, "WARNING");
    await this.instanceRepo.appendHistory(tenantId, instanceId, "ACTIVE", "WARNING", "SLA warning threshold reached");

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_WARNING,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_WARNING,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_WARNING, tenantId, {
        instanceId, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType, dueAt: instance.dueAt.toISOString(),
      }),
    });

    logger.info({ instanceId, tenantId }, "[instance] transitioned to WARNING");
  }

  async transitionToBreached(instanceId: string, tenantId: string): Promise<void> {
    const instance = await this.instanceRepo.findById(instanceId, tenantId);
    if (!instance || !["ACTIVE", "WARNING"].includes(instance.status)) return;

    await this.instanceRepo.updateStatus(instanceId, tenantId, "BREACHED", { breachedAt: new Date() });
    await this.instanceRepo.appendHistory(tenantId, instanceId, instance.status, "BREACHED", "SLA due time exceeded");

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_BREACHED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_BREACHED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_BREACHED, tenantId, {
        instanceId, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType, breachedAt: new Date().toISOString(),
      }),
    });

    logger.warn({ instanceId, tenantId }, "[instance] BREACHED");
  }

  async resolveInstances(tenantId: string, entityId: string, slaTypes: string[]): Promise<void> {
    await this.instanceRepo.resolveByEntityId(tenantId, entityId, slaTypes);

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_RESOLVED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_RESOLVED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_RESOLVED, tenantId, { entityId, slaTypes, resolvedAt: new Date().toISOString() }),
    });

    logger.info({ tenantId, entityId, slaTypes }, "[instance] resolved");
  }

  async escalateInstances(tenantId: string, entityId: string, level: number): Promise<void> {
    const activeInstances = await this.instanceRepo.findActiveByEntityId(tenantId, entityId);
    if (activeInstances.length === 0) {
      logger.debug({ tenantId, entityId }, "[instance] escalation received but no ACTIVE instances found — skipping");
      return;
    }

    for (const instance of activeInstances) {
      await this.instanceRepo.updateStatus(instance.id, tenantId, "WARNING");
      await this.instanceRepo.appendHistory(tenantId, instance.id, "ACTIVE", "WARNING", `Escalated (level ${level})`);
      await this.instanceRepo.createEscalation(tenantId, instance.id, level, "instance.escalated");

      await this.outbox.enqueue({
        tenantId,
        eventType: SLA_ROUTING_KEYS.INSTANCE_WARNING,
        exchange: SLA_EXCHANGE,
        routingKey: SLA_ROUTING_KEYS.INSTANCE_WARNING,
        payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_WARNING, tenantId, {
          instanceId: instance.id, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType,
          dueAt: instance.dueAt.toISOString(), escalation: { level },
        }),
      });
    }

    logger.info({ tenantId, entityId, level, count: activeInstances.length }, "[instance] escalated — forced to WARNING");
  }

  async cancelInstances(tenantId: string, entityId: string, slaTypes: string[]): Promise<void> {
    await this.instanceRepo.cancelByEntityId(tenantId, entityId, slaTypes);

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_CANCELLED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_CANCELLED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_CANCELLED, tenantId, { entityId, slaTypes, cancelledAt: new Date().toISOString() }),
    });

    logger.info({ tenantId, entityId, slaTypes }, "[instance] cancelled");
  }

  async createFromPolicy(
    tenantId: string,
    entityType: string,
    entityId: string,
    slaType: string,
    metadata: Record<string, unknown>,
    startedAt: Date,
  ): Promise<SlaInstanceDto | null> {
    const policy = await this.policyRepo.findByEntityAndType(tenantId, entityType, slaType);
    if (!policy || !policy.isEnabled) return null;

    const dueAt = new Date(startedAt.getTime() + policy.durationMins * 60_000);
    const warningAt = new Date(startedAt.getTime() + policy.warningMins * 60_000);

    return this.createInstance({ tenantId, policyId: policy.id, entityType, entityId, slaType, startedAt, dueAt, warningAt, metadata });
  }

  /**
   * Creates an instance with a caller-supplied fixed deadline instead of one
   * computed from policy.durationMins — for entities whose deadline is a
   * calendar date negotiated outside this library (e.g. a contractual date).
   * warningAt = dueAt − policy.warningMins. Returns null when the policy
   * doesn't exist or is disabled.
   */
  async createWithFixedDeadline(
    tenantId: string,
    entityType: string,
    entityId: string,
    slaType: string,
    dueAt: Date,
    metadata: Record<string, unknown>,
  ): Promise<SlaInstanceDto | null> {
    const policy = await this.policyRepo.findByEntityAndType(tenantId, entityType, slaType);
    if (!policy || !policy.isEnabled) return null;

    const warningAt = new Date(dueAt.getTime() - policy.warningMins * 60_000);
    return this.createInstance({ tenantId, policyId: policy.id, entityType, entityId, slaType, startedAt: new Date(), dueAt, warningAt, metadata });
  }
}
```

- [ ] **Step 8: Run tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/instances`
Expected: all PASS.

- [ ] **Step 9: Controller + router**

```ts
// packages/gen-sla-starter/src/modules/instances/v1/controller.ts
import type { Request, Response } from "express";
import { listInstancesQuerySchema, instanceIdSchema } from "./schema.ts";
import type { InstanceService } from "./service.ts";

export function makeInstanceController(service: InstanceService) {
  return {
    async listInstances(req: Request, res: Response) {
      const query = listInstancesQuerySchema.parse(req.query);
      const result = await service.listInstances(req.params.tenantId, query);
      res.json(result);
    },
    async getInstance(req: Request, res: Response) {
      const { id } = instanceIdSchema.parse(req.params);
      const instance = await service.getInstance(req.params.tenantId, id);
      res.json(instance);
    },
  };
}
```

```ts
// packages/gen-sla-starter/src/modules/instances/v1/router.ts
import { Router } from "express";
import { makeInstanceController } from "./controller.ts";
import type { InstanceService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface InstancesRouterDeps {
  instanceService: InstanceService;
  internalSecretValue: string;
}

export function createInstancesRouter(deps: InstancesRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeInstanceController(deps.instanceService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.get("/", (req, res, next) => controller.listInstances(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getInstance(req, res).catch(next));

  return router;
}
```

- [ ] **Step 10: Commit**

```bash
git add packages/gen-sla-starter/src/domain/ports/instance-repo.port.ts packages/gen-sla-starter/src/domain/ports/outbox-writer.port.ts packages/gen-sla-starter/src/config/constants.ts packages/gen-sla-starter/src/modules/instances
git commit -m "feat: add SLA instances module (lifecycle state machine + service tests)"
```

---

### Task 7: Metrics module

**Files:**
- Create: `packages/gen-sla-starter/src/domain/ports/metrics-repo.port.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/types.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/schema.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/repo.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/service.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/service.test.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/controller.ts`
- Create: `packages/gen-sla-starter/src/modules/metrics/v1/router.ts`

**Interfaces:**
- Produces: `IMetricsRepo` port, `MetricsService` with `getSummary`/`getComplianceRates`/`getBreaches`/`getTrends`, `createMetricsRouter({ metricsService, internalSecretValue })` mounted at `/api/v1/sla/:tenantId/metrics`.

- [ ] **Step 1: Port + types + schema**

```ts
// packages/gen-sla-starter/src/domain/ports/metrics-repo.port.ts
export interface MetricsFilters {
  entityType?: string;
  from?: Date;
  to?: Date;
}

export interface StatusCounts {
  ACTIVE: number;
  WARNING: number;
  BREACHED: number;
  RESOLVED: number;
  CANCELLED: number;
}

export interface ComplianceRow {
  entityType: string;
  slaType: string;
  total: number;
  breached: number;
  resolved: number;
}

export interface BreachRow {
  id: string;
  entityType: string;
  entityId: string;
  slaType: string;
  breachedAt: Date | null;
  dueAt: Date;
}

export interface TrendRow {
  toStatus: string;
  occurredAt: Date;
}

export interface IMetricsRepo {
  getSummary(tenantId: string, filters: MetricsFilters): Promise<StatusCounts>;
  getComplianceRates(tenantId: string, filters: MetricsFilters): Promise<ComplianceRow[]>;
  getRecentBreaches(tenantId: string, filters: MetricsFilters, limit?: number): Promise<BreachRow[]>;
  getTrends(tenantId: string, filters: MetricsFilters): Promise<TrendRow[]>;
}
```

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/types.ts
export interface SlaSummary {
  totalActive: number;
  totalWarning: number;
  totalBreached: number;
  totalResolved: number;
  totalCancelled: number;
}

export interface SlaComplianceRate {
  entityType: string;
  slaType: string;
  total: number;
  breached: number;
  resolved: number;
  compliancePct: number;
}

export interface SlaBreachRecord {
  instanceId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  breachedAt: string;
  dueAt: string;
  overdueMs: number;
}

export interface SlaTrendPoint {
  date: string;
  created: number;
  breached: number;
  resolved: number;
}
```

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/schema.ts
import { z } from "zod";

export const metricsQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  from: z.string().datetime().optional(),
  to: z.string().datetime().optional(),
});

export type MetricsQuery = z.infer<typeof metricsQuerySchema>;

export const trendsQuerySchema = metricsQuerySchema.extend({
  granularity: z.enum(["day", "week", "month"]).default("day"),
});

export type TrendsQuery = z.infer<typeof trendsQuerySchema>;
```

- [ ] **Step 2: Prisma-backed repo**

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/repo.ts
import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type { IMetricsRepo, MetricsFilters, StatusCounts, ComplianceRow, BreachRow, TrendRow } from "../../../domain/ports/metrics-repo.port.ts";

function buildWhere(tenantId: string, filters: MetricsFilters): Prisma.SlaInstanceWhereInput {
  const where: Prisma.SlaInstanceWhereInput = { tenantId };
  if (filters.entityType) where.entityType = filters.entityType;
  if (filters.from || filters.to) {
    where.startedAt = { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) };
  }
  return where;
}

export class PrismaMetricsRepo implements IMetricsRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async getSummary(tenantId: string, filters: MetricsFilters): Promise<StatusCounts> {
    const where = buildWhere(tenantId, filters);
    const groups = await this.prisma.slaInstance.groupBy({ by: ["status"], where, _count: { id: true } });

    const counts: StatusCounts = { ACTIVE: 0, WARNING: 0, BREACHED: 0, RESOLVED: 0, CANCELLED: 0 };
    for (const g of groups) counts[g.status as keyof StatusCounts] = g._count.id;
    return counts;
  }

  async getComplianceRates(tenantId: string, filters: MetricsFilters): Promise<ComplianceRow[]> {
    const where = buildWhere(tenantId, filters);
    const rows = await this.prisma.slaInstance.groupBy({ by: ["entityType", "slaType", "status"], where, _count: { id: true } });

    const map = new Map<string, ComplianceRow>();
    for (const row of rows) {
      const key = `${row.entityType}|${row.slaType}`;
      if (!map.has(key)) map.set(key, { entityType: row.entityType, slaType: row.slaType, total: 0, breached: 0, resolved: 0 });
      const entry = map.get(key)!;
      entry.total += row._count.id;
      if (row.status === "BREACHED") entry.breached += row._count.id;
      if (row.status === "RESOLVED") entry.resolved += row._count.id;
    }
    return Array.from(map.values());
  }

  async getRecentBreaches(tenantId: string, filters: MetricsFilters, limit = 20): Promise<BreachRow[]> {
    const where: Prisma.SlaInstanceWhereInput = { tenantId, status: "BREACHED" };
    if (filters.entityType) where.entityType = filters.entityType;
    if (filters.from || filters.to) {
      where.breachedAt = { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) };
    }

    return this.prisma.slaInstance.findMany({
      where,
      orderBy: { breachedAt: "desc" },
      take: limit,
      select: { id: true, entityType: true, entityId: true, slaType: true, breachedAt: true, dueAt: true },
    });
  }

  async getTrends(tenantId: string, filters: MetricsFilters): Promise<TrendRow[]> {
    const where = buildWhere(tenantId, filters);
    return this.prisma.slaHistory.findMany({
      where: {
        tenantId,
        ...(filters.from || filters.to ? { occurredAt: { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) } } : {}),
        toStatus: { in: ["ACTIVE", "BREACHED", "RESOLVED"] },
        instance: where,
      },
      select: { toStatus: true, occurredAt: true },
      orderBy: { occurredAt: "asc" },
    });
  }
}
```

- [ ] **Step 3: Write the failing test for `MetricsService`**

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { MetricsService } from "./service.ts";
import type { IMetricsRepo } from "../../../domain/ports/metrics-repo.port.ts";

function fakeRepo(overrides: Partial<IMetricsRepo> = {}): IMetricsRepo {
  return {
    getSummary: vi.fn(async () => ({ ACTIVE: 1, WARNING: 0, BREACHED: 0, RESOLVED: 0, CANCELLED: 0 })),
    getComplianceRates: vi.fn(async () => [{ entityType: "TICKET", slaType: "FIRST_RESPONSE", total: 10, breached: 2, resolved: 8 }]),
    getRecentBreaches: vi.fn(async () => []),
    getTrends: vi.fn(async () => []),
    ...overrides,
  };
}

describe("MetricsService", () => {
  it("getSummary passes through repo counts", async () => {
    const service = new MetricsService(fakeRepo());
    const result = await service.getSummary("tenant-1", {});
    expect(result.totalActive).toBe(1);
  });

  it("getComplianceRates computes compliancePct as (total-breached)/total * 100", async () => {
    const service = new MetricsService(fakeRepo());
    const [rate] = await service.getComplianceRates("tenant-1", {});
    expect(rate.compliancePct).toBe(80);
  });

  it("getComplianceRates defaults compliancePct to 100 when total is 0", async () => {
    const repo = fakeRepo({ getComplianceRates: vi.fn(async () => [{ entityType: "TICKET", slaType: "X", total: 0, breached: 0, resolved: 0 }]) });
    const service = new MetricsService(repo);
    const [rate] = await service.getComplianceRates("tenant-1", {});
    expect(rate.compliancePct).toBe(100);
  });
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/metrics`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 5: Implement `MetricsService`**

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/service.ts
import type { IMetricsRepo } from "../../../domain/ports/metrics-repo.port.ts";
import type { MetricsQuery, TrendsQuery } from "./schema.ts";
import type { SlaSummary, SlaComplianceRate, SlaBreachRecord, SlaTrendPoint } from "./types.ts";

function toFilters(query: MetricsQuery) {
  return { entityType: query.entityType, from: query.from ? new Date(query.from) : undefined, to: query.to ? new Date(query.to) : undefined };
}

function formatDateKey(date: Date, granularity: string): string {
  const d = new Date(date);
  if (granularity === "month") return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  if (granularity === "week") {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    const monday = new Date(d.setDate(diff));
    return monday.toISOString().slice(0, 10);
  }
  return d.toISOString().slice(0, 10);
}

export class MetricsService {
  constructor(private readonly repo: IMetricsRepo) {}

  async getSummary(tenantId: string, query: MetricsQuery): Promise<SlaSummary> {
    const counts = await this.repo.getSummary(tenantId, toFilters(query));
    return {
      totalActive: counts.ACTIVE,
      totalWarning: counts.WARNING,
      totalBreached: counts.BREACHED,
      totalResolved: counts.RESOLVED,
      totalCancelled: counts.CANCELLED,
    };
  }

  async getComplianceRates(tenantId: string, query: MetricsQuery): Promise<SlaComplianceRate[]> {
    const rows = await this.repo.getComplianceRates(tenantId, toFilters(query));
    return rows.map((r) => ({
      ...r,
      compliancePct: r.total > 0 ? Math.round(((r.total - r.breached) / r.total) * 100 * 10) / 10 : 100,
    }));
  }

  async getBreaches(tenantId: string, query: MetricsQuery): Promise<SlaBreachRecord[]> {
    const rows = await this.repo.getRecentBreaches(tenantId, toFilters(query));
    return rows.map((r) => ({
      instanceId: r.id,
      entityType: r.entityType,
      entityId: r.entityId,
      slaType: r.slaType,
      breachedAt: r.breachedAt?.toISOString() ?? "",
      dueAt: r.dueAt.toISOString(),
      overdueMs: r.breachedAt ? r.breachedAt.getTime() - r.dueAt.getTime() : 0,
    }));
  }

  async getTrends(tenantId: string, query: TrendsQuery): Promise<SlaTrendPoint[]> {
    const history = await this.repo.getTrends(tenantId, toFilters(query));
    const pointMap = new Map<string, { created: number; breached: number; resolved: number }>();

    for (const row of history) {
      const dateKey = formatDateKey(row.occurredAt, query.granularity);
      if (!pointMap.has(dateKey)) pointMap.set(dateKey, { created: 0, breached: 0, resolved: 0 });
      const pt = pointMap.get(dateKey)!;
      if (row.toStatus === "ACTIVE") pt.created++;
      if (row.toStatus === "BREACHED") pt.breached++;
      if (row.toStatus === "RESOLVED") pt.resolved++;
    }

    return Array.from(pointMap.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([date, v]) => ({ date, ...v }));
  }
}
```

- [ ] **Step 6: Run tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- modules/metrics`
Expected: all PASS.

- [ ] **Step 7: Controller + router**

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/controller.ts
import type { Request, Response } from "express";
import { metricsQuerySchema, trendsQuerySchema } from "./schema.ts";
import type { MetricsService } from "./service.ts";

export function makeMetricsController(service: MetricsService) {
  return {
    async getSummary(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getSummary(req.params.tenantId, query));
    },
    async getComplianceRates(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getComplianceRates(req.params.tenantId, query));
    },
    async getBreaches(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getBreaches(req.params.tenantId, query));
    },
    async getTrends(req: Request, res: Response) {
      const query = trendsQuerySchema.parse(req.query);
      res.json(await service.getTrends(req.params.tenantId, query));
    },
  };
}
```

```ts
// packages/gen-sla-starter/src/modules/metrics/v1/router.ts
import { Router } from "express";
import { makeMetricsController } from "./controller.ts";
import type { MetricsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface MetricsRouterDeps {
  metricsService: MetricsService;
  internalSecretValue: string;
}

export function createMetricsRouter(deps: MetricsRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeMetricsController(deps.metricsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.get("/summary", (req, res, next) => controller.getSummary(req, res).catch(next));
  router.get("/compliance", (req, res, next) => controller.getComplianceRates(req, res).catch(next));
  router.get("/breaches", (req, res, next) => controller.getBreaches(req, res).catch(next));
  router.get("/trends", (req, res, next) => controller.getTrends(req, res).catch(next));

  return router;
}
```

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sla-starter/src/domain/ports/metrics-repo.port.ts packages/gen-sla-starter/src/modules/metrics
git commit -m "feat: add SLA metrics module (summary/compliance/breaches/trends)"
```

---

### Task 8: RabbitMQ bus + transactional outbox

**Files:**
- Create: `packages/gen-sla-starter/src/domain/ports/event-publisher.port.ts`
- Create: `packages/gen-sla-starter/src/infra/messaging/rabbitmq-bus.ts`
- Create: `packages/gen-sla-starter/src/infra/messaging/outbox-writer.ts`
- Create: `packages/gen-sla-starter/src/infra/messaging/outbox-dispatcher.ts`
- Create: `packages/gen-sla-starter/src/infra/messaging/outbox-writer.test.ts`

**Interfaces:**
- Produces: `IEventPublisher` port, `RabbitMqBus implements IEventPublisher` (also exposes `connect`/`close`/`assertExchange`/`assertQueue`/`bindQueue`/`consume` for a caller's own event-driven integration — see Global Constraints), `PrismaOutboxWriter implements IOutboxWriter` (consumed by `InstanceService`, Task 6), `startOutboxDispatcher(prisma, publisher, config)` (consumed by the factory, Task 10, and mirrors the worker lifecycle shape used by Task 9's timer worker: `{ close(): void }`).

- [ ] **Step 1: `IEventPublisher` port**

```ts
// packages/gen-sla-starter/src/domain/ports/event-publisher.port.ts
export interface EventEnvelope {
  event_id?: string;
  event_type: string;
  event_version?: number;
  occurred_at: string;
  producer?: { service: string; version: string };
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface IEventPublisher {
  publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>;
}
```

- [ ] **Step 2: Generic RabbitMQ bus — publish/consume infra, no baked-in business handlers**

```ts
// packages/gen-sla-starter/src/infra/messaging/rabbitmq-bus.ts
import amqp, { type ChannelModel, type Channel } from "amqplib";
import type { IEventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import { logger } from "../../common/logger.ts";

export interface ConsumeOptions {
  prefetch?: number;
}

export class RabbitMqBus implements IEventPublisher {
  private connection: ChannelModel | undefined;
  private channel: Channel | undefined;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    if (this.channel) return;
    this.connection = await amqp.connect(this.url);
    this.channel = await this.connection.createChannel();
  }

  private requireChannel(): Channel {
    if (!this.channel) throw new Error("RabbitMqBus.connect() must be called before use");
    return this.channel;
  }

  async assertExchange(name: string, type: "topic" | "direct" | "fanout" = "topic"): Promise<void> {
    await this.requireChannel().assertExchange(name, type, { durable: true });
  }

  /** `deadLetterExchange` routes rejected/nacked messages there — omit for a terminal queue (e.g. the DLQ itself). */
  async assertQueue(name: string, deadLetterExchange?: string): Promise<void> {
    await this.requireChannel().assertQueue(name, {
      durable: true,
      ...(deadLetterExchange && { arguments: { "x-dead-letter-exchange": deadLetterExchange } }),
    });
  }

  async bindQueue(queue: string, exchange: string, routingKey: string): Promise<void> {
    await this.requireChannel().bindQueue(queue, exchange, routingKey);
  }

  async publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void> {
    this.requireChannel().publish(exchange, routingKey, Buffer.from(JSON.stringify(envelope)), { contentType: "application/json", persistent: true });
  }

  async consume(queue: string, handler: (envelope: EventEnvelope) => Promise<void>, options: ConsumeOptions = {}): Promise<void> {
    const channel = this.requireChannel();
    if (options.prefetch) await channel.prefetch(options.prefetch);

    await channel.consume(queue, (msg) => {
      if (!msg) return;
      void (async () => {
        try {
          const envelope = JSON.parse(msg.content.toString("utf8")) as EventEnvelope;
          await handler(envelope);
          channel.ack(msg);
        } catch (err) {
          logger.error({ err, queue }, "[rabbitmq-bus] handler failed — nacking to DLX");
          channel.nack(msg, false, false);
        }
      })();
    });
  }

  async close(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
    this.channel = undefined;
    this.connection = undefined;
  }
}
```

- [ ] **Step 3: Outbox writer — port implementation, write the failing test first**

```ts
// packages/gen-sla-starter/src/infra/messaging/outbox-writer.test.ts
import { describe, it, expect, vi } from "vitest";
import { PrismaOutboxWriter } from "./outbox-writer.ts";

describe("PrismaOutboxWriter", () => {
  it("enqueue writes a PENDING row with retryCount 0", async () => {
    const create = vi.fn(async () => undefined);
    const prisma = { slaOutboxEvent: { create } } as any;
    const writer = new PrismaOutboxWriter(prisma);

    await writer.enqueue({ tenantId: "t1", eventType: "sla.instance.created", exchange: "gen-sla.events", routingKey: "sla.instance.created", payload: { a: 1 } });

    expect(create).toHaveBeenCalledWith({
      data: { tenantId: "t1", eventType: "sla.instance.created", exchange: "gen-sla.events", routingKey: "sla.instance.created", payload: { a: 1 }, status: "PENDING", retryCount: 0 },
    });
  });
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- outbox-writer`
Expected: FAIL — `Cannot find module './outbox-writer.ts'`

- [ ] **Step 5: Implement `PrismaOutboxWriter`**

```ts
// packages/gen-sla-starter/src/infra/messaging/outbox-writer.ts
import type { PrismaClient, Prisma } from "../../../__generated__/prisma/index.js";
import type { IOutboxWriter, OutboxEnqueueParams } from "../../domain/ports/outbox-writer.port.ts";

export class PrismaOutboxWriter implements IOutboxWriter {
  constructor(private readonly prisma: PrismaClient) {}

  async enqueue(params: OutboxEnqueueParams): Promise<void> {
    await this.prisma.slaOutboxEvent.create({
      data: {
        tenantId: params.tenantId,
        eventType: params.eventType,
        exchange: params.exchange,
        routingKey: params.routingKey,
        payload: params.payload as Prisma.InputJsonValue,
        status: "PENDING",
        retryCount: 0,
      },
    });
  }
}
```

- [ ] **Step 6: Run test, verify it passes**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- outbox-writer`
Expected: PASS.

- [ ] **Step 7: Outbox dispatcher — polls PENDING rows and publishes via the injected `IEventPublisher`**

```ts
// packages/gen-sla-starter/src/infra/messaging/outbox-dispatcher.ts
import type { PrismaClient } from "../../../__generated__/prisma/index.js";
import type { IEventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import { logger } from "../../common/logger.ts";

export interface OutboxDispatcherConfig {
  pollIntervalMs: number;
  batchSize: number;
  maxRetries: number;
}

export function startOutboxDispatcher(
  prisma: PrismaClient,
  publisher: IEventPublisher,
  config: OutboxDispatcherConfig,
): { close: () => void } {
  let running = true;

  async function pollOnce(): Promise<void> {
    if (!running) return;

    const events = await prisma.slaOutboxEvent.findMany({
      where: { status: "PENDING" },
      orderBy: { createdAt: "asc" },
      take: config.batchSize,
    });

    for (const event of events) {
      if (!running) break;

      if (event.retryCount >= config.maxRetries) {
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { status: "FAILED", updatedAt: new Date() } });
        logger.error({ eventId: event.id, eventType: event.eventType }, "[outbox] permanent failure — max retries exceeded");
        continue;
      }

      try {
        await publisher.publish(event.exchange, event.routingKey, event.payload as unknown as EventEnvelope);
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { status: "PUBLISHED", updatedAt: new Date() } });
      } catch (err) {
        const nextRetry = event.retryCount + 1;
        const newStatus = nextRetry >= config.maxRetries ? "FAILED" : "PENDING";
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { retryCount: nextRetry, status: newStatus, updatedAt: new Date() } });
        logger.warn({ eventId: event.id, eventType: event.eventType, attempt: nextRetry, err }, "[outbox] publish failure");
      }
    }
  }

  const intervalId = setInterval(() => {
    pollOnce().catch((err) => logger.error({ err }, "[outbox] unhandled error in poll"));
  }, config.pollIntervalMs);

  logger.info("[outbox] dispatcher started");

  return {
    close: () => {
      running = false;
      clearInterval(intervalId);
      logger.info("[outbox] dispatcher stopped");
    },
  };
}
```

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sla-starter/src/domain/ports/event-publisher.port.ts packages/gen-sla-starter/src/infra/messaging
git commit -m "feat: add RabbitMQ bus and transactional outbox writer/dispatcher"
```

---

### Task 9: SLA timer worker

**Files:**
- Create: `packages/gen-sla-starter/src/workers/sla-timer.worker.ts`
- Create: `packages/gen-sla-starter/src/workers/sla-timer.worker.test.ts`

**Interfaces:**
- Consumes: `ISlaInstanceRepo` (Task 6), `InstanceService.transitionToWarning`/`transitionToBreached` (Task 6), `IDistributedLock` (Task 4).
- Produces: `startSlaTimerWorker(deps): { close: () => void }` — consumed by `create-gen-sla.ts` (Task 10).

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-sla-starter/src/workers/sla-timer.worker.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { startSlaTimerWorker } from "./sla-timer.worker.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../domain/ports/instance-repo.port.ts";
import type { InstanceService } from "../modules/instances/v1/service.ts";
import type { IDistributedLock } from "../domain/ports/distributed-lock.port.ts";

function instanceStub(id: string): SlaInstanceRecord {
  return {
    id, tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET", entityId: "entity-1",
    slaType: "FIRST_RESPONSE", status: "ACTIVE", startedAt: new Date(), dueAt: new Date(), warningAt: new Date(),
    breachedAt: null, resolvedAt: null, metadata: {},
  };
}

describe("startSlaTimerWorker", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("acquires a lock per due/near-warning instance and calls the matching transition", async () => {
    const instanceRepo: ISlaInstanceRepo = {
      findActiveInstancesNearWarning: vi.fn(async () => [instanceStub("warn-1")]),
      findActiveInstancesDue: vi.fn(async () => [instanceStub("due-1")]),
    } as any;
    const instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached"> = {
      transitionToWarning: vi.fn(async () => undefined),
      transitionToBreached: vi.fn(async () => undefined),
    };
    const lock: IDistributedLock = { acquire: vi.fn(async () => true) };

    const worker = startSlaTimerWorker({ instanceRepo, instanceService, lock, pollIntervalMs: 30_000, batchSize: 100, lockTtlS: 60 });
    await vi.runOnlyPendingTimersAsync();

    expect(instanceService.transitionToWarning).toHaveBeenCalledWith("warn-1", "tenant-1");
    expect(instanceService.transitionToBreached).toHaveBeenCalledWith("due-1", "tenant-1");
    worker.close();
  });

  it("skips an instance when the lock is already held", async () => {
    const instanceRepo: ISlaInstanceRepo = {
      findActiveInstancesNearWarning: vi.fn(async () => [instanceStub("warn-1")]),
      findActiveInstancesDue: vi.fn(async () => []),
    } as any;
    const instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached"> = {
      transitionToWarning: vi.fn(async () => undefined),
      transitionToBreached: vi.fn(async () => undefined),
    };
    const lock: IDistributedLock = { acquire: vi.fn(async () => false) };

    const worker = startSlaTimerWorker({ instanceRepo, instanceService, lock, pollIntervalMs: 30_000, batchSize: 100, lockTtlS: 60 });
    await vi.runOnlyPendingTimersAsync();

    expect(instanceService.transitionToWarning).not.toHaveBeenCalled();
    worker.close();
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- sla-timer.worker`
Expected: FAIL — `Cannot find module './sla-timer.worker.ts'`

- [ ] **Step 3: Implement the worker**

```ts
// packages/gen-sla-starter/src/workers/sla-timer.worker.ts
import { logger } from "../common/logger.ts";
import type { ISlaInstanceRepo } from "../domain/ports/instance-repo.port.ts";
import type { InstanceService } from "../modules/instances/v1/service.ts";
import type { IDistributedLock } from "../domain/ports/distributed-lock.port.ts";

export interface SlaTimerWorkerDeps {
  instanceRepo: ISlaInstanceRepo;
  instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached">;
  lock: IDistributedLock;
  pollIntervalMs: number;
  batchSize: number;
  lockTtlS: number;
}

export function startSlaTimerWorker(deps: SlaTimerWorkerDeps): { close: () => void } {
  async function processWarnings(): Promise<void> {
    const instances = await deps.instanceRepo.findActiveInstancesNearWarning(deps.batchSize);
    for (const instance of instances) {
      const acquired = await deps.lock.acquire(`gen-sla:lock:timer:warning:${instance.id}`, deps.lockTtlS);
      if (!acquired) continue;
      try {
        await deps.instanceService.transitionToWarning(instance.id, instance.tenantId);
      } catch (err) {
        logger.error({ err, instanceId: instance.id }, "[timer] failed to process warning transition");
      }
    }
  }

  async function processBreaches(): Promise<void> {
    const instances = await deps.instanceRepo.findActiveInstancesDue(deps.batchSize);
    for (const instance of instances) {
      const acquired = await deps.lock.acquire(`gen-sla:lock:timer:breach:${instance.id}`, deps.lockTtlS);
      if (!acquired) continue;
      try {
        await deps.instanceService.transitionToBreached(instance.id, instance.tenantId);
      } catch (err) {
        logger.error({ err, instanceId: instance.id }, "[timer] failed to process breach transition");
      }
    }
  }

  async function tick(): Promise<void> {
    try {
      await processWarnings();
      await processBreaches();
    } catch (err) {
      logger.error({ err }, "[timer] unhandled error in tick");
    }
  }

  logger.info({ intervalMs: deps.pollIntervalMs }, "[timer] SLA timer worker started");
  const intervalId = setInterval(() => void tick(), deps.pollIntervalMs);
  void tick();

  return {
    close: () => {
      clearInterval(intervalId);
      logger.info("[timer] SLA timer worker stopped");
    },
  };
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- sla-timer.worker`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sla-starter/src/workers
git commit -m "feat: add SLA timer worker (warning/breach transitions via distributed lock)"
```

---

### Task 10: Factory (`createGenSla`) + public barrel

**Files:**
- Create: `packages/gen-sla-starter/src/create-gen-sla.ts`
- Create: `packages/gen-sla-starter/src/create-gen-sla.test.ts`
- Create: `packages/gen-sla-starter/src/index.ts`
- Create: `packages/gen-sla-starter/tests/support/postgres-container.ts`

**Interfaces:**
- Consumes: every resolver-eligible port/adapter from Tasks 2–9.
- Produces: `createGenSla(config: GenSlaConfig): GenSlaInstance` — the single public entry point; `GenSlaConfig`, `GenSlaModulesConfig`, `GenSlaInstance` types exported from `index.ts` for `gen-sla-demo` (Task 11) to consume.

- [ ] **Step 1: Testcontainers helper (mirrors Gen_REG's, applies the schema via `prisma migrate deploy`)**

```ts
// packages/gen-sla-starter/tests/support/postgres-container.ts
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";

export async function startTestPostgres(): Promise<{ container: StartedPostgreSqlContainer; databaseUrl: string }> {
  const container = await new PostgreSqlContainer("postgres:15").start();
  const databaseUrl = container.getConnectionUri();

  execSync("npx prisma migrate deploy", { env: { ...process.env, DATABASE_URL: databaseUrl }, stdio: "inherit" });

  return { container, databaseUrl };
}
```

- [ ] **Step 2: Write the failing test — config-error fail-fast behavior, and a smoke request through the wired app**

```ts
// packages/gen-sla-starter/src/create-gen-sla.test.ts
import { describe, it, expect, afterAll, beforeAll } from "vitest";
import request from "supertest";
import { createGenSla } from "./create-gen-sla.ts";
import { GenSlaConfigError } from "./common/errors.ts";
import { startTestPostgres } from "../tests/support/postgres-container.ts";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";

describe("createGenSla", () => {
  it("throws GenSlaConfigError when DATABASE_URL is missing and no repo override is supplied", () => {
    const original = process.env.DATABASE_URL;
    delete process.env.DATABASE_URL;
    expect(() => createGenSla({ internalSecret: "test-secret" })).toThrow(GenSlaConfigError);
    if (original !== undefined) process.env.DATABASE_URL = original;
  });

  it("throws GenSlaConfigError when internalSecret is missing and GEN_SLA_INTERNAL_SECRET is unset", () => {
    const original = process.env.GEN_SLA_INTERNAL_SECRET;
    delete process.env.GEN_SLA_INTERNAL_SECRET;
    process.env.DATABASE_URL = "postgresql://user:pass@localhost:5440/gensla";
    expect(() => createGenSla({})).toThrow(GenSlaConfigError);
    if (original !== undefined) process.env.GEN_SLA_INTERNAL_SECRET = original;
  });

  describe("with a real database", () => {
    let container: StartedPostgreSqlContainer;

    beforeAll(async () => {
      const started = await startTestPostgres();
      container = started.container;
      process.env.DATABASE_URL = started.databaseUrl;
    }, 60_000);

    afterAll(async () => {
      await container.stop();
    });

    it("mounts policies/instances/metrics behind the internal-secret + tenantId guards", async () => {
      const { app } = createGenSla({ internalSecret: "test-secret", modules: { worker: false } });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11";

      await request(app).get(`/api/v1/sla/${tenantId}/policies`).expect(401);

      const listRes = await request(app)
        .get(`/api/v1/sla/${tenantId}/policies`)
        .set("X-Internal-Secret", "test-secret")
        .expect(200);
      expect(listRes.body.data).toEqual([]);

      const createRes = await request(app)
        .post(`/api/v1/sla/${tenantId}/policies`)
        .set("X-Internal-Secret", "test-secret")
        .send({ name: "First Response", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45 })
        .expect(201);
      expect(createRes.body.entityType).toBe("TICKET");
    });

    it("404s the worker-only routes when modules.instances is disabled", async () => {
      const { app } = createGenSla({ internalSecret: "test-secret", modules: { instances: false, worker: false } });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11";
      await request(app).get(`/api/v1/sla/${tenantId}/instances`).set("X-Internal-Secret", "test-secret").expect(404);
    });
  });
});
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- create-gen-sla`
Expected: FAIL — `Cannot find module './create-gen-sla.ts'`

- [ ] **Step 4: Implement `create-gen-sla.ts`**

```ts
// packages/gen-sla-starter/src/create-gen-sla.ts
import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { requireEnv, env } from "./config/env.ts";
import { GenSlaConfigError } from "./common/errors.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { ValkeyLock } from "./infra/cache/valkey-lock.ts";
import { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
import { PrismaOutboxWriter } from "./infra/messaging/outbox-writer.ts";
import { startOutboxDispatcher } from "./infra/messaging/outbox-dispatcher.ts";
import { PrismaPolicyRepo } from "./modules/policies/v1/repo.ts";
import { PolicyService } from "./modules/policies/v1/service.ts";
import { createPoliciesRouter } from "./modules/policies/v1/router.ts";
import { PrismaInstanceRepo } from "./modules/instances/v1/repo.ts";
import { InstanceService } from "./modules/instances/v1/service.ts";
import { createInstancesRouter } from "./modules/instances/v1/router.ts";
import { PrismaMetricsRepo } from "./modules/metrics/v1/repo.ts";
import { MetricsService } from "./modules/metrics/v1/service.ts";
import { createMetricsRouter } from "./modules/metrics/v1/router.ts";
import { startSlaTimerWorker } from "./workers/sla-timer.worker.ts";
import type { ISlaPolicyRepo } from "./domain/ports/policy-repo.port.ts";
import type { ISlaInstanceRepo } from "./domain/ports/instance-repo.port.ts";
import type { IMetricsRepo } from "./domain/ports/metrics-repo.port.ts";
import type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
import type { IEventPublisher } from "./domain/ports/event-publisher.port.ts";
import type { IOutboxWriter } from "./domain/ports/outbox-writer.port.ts";

export interface GenSlaModulesConfig {
  policies?: boolean;
  instances?: boolean;
  metrics?: boolean;
  worker?: boolean;
}

export interface GenSlaConfig {
  policyRepo?: ISlaPolicyRepo;
  instanceRepo?: ISlaInstanceRepo;
  metricsRepo?: IMetricsRepo;
  lock?: IDistributedLock;
  eventPublisher?: IEventPublisher;
  outboxWriter?: IOutboxWriter;
  internalSecret?: string;
  modules?: GenSlaModulesConfig;
}

export interface GenSlaWorker {
  close(): void;
}

export interface GenSlaInstance {
  app: Express;
  worker?: GenSlaWorker;
}

function resolvePolicyRepo(override: ISlaPolicyRepo | undefined): ISlaPolicyRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaPolicyRepo(getPrismaClient());
}

function resolveInstanceRepo(override: ISlaInstanceRepo | undefined): ISlaInstanceRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaInstanceRepo(getPrismaClient());
}

function resolveMetricsRepo(override: IMetricsRepo | undefined): IMetricsRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaMetricsRepo(getPrismaClient());
}

function resolveOutboxWriter(override: IOutboxWriter | undefined): IOutboxWriter {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaOutboxWriter(getPrismaClient());
}

function resolveLock(override: IDistributedLock | undefined): IDistributedLock {
  if (override) return override;
  const url = requireEnv("VALKEY_URL");
  return new ValkeyLock(url);
}

function resolveEventPublisher(override: IEventPublisher | undefined): IEventPublisher {
  if (override) return override;
  const url = requireEnv("RABBITMQ_URL");
  return new RabbitMqBus(url);
}

function resolveInternalSecret(override: string | undefined): string {
  return override ?? requireEnv("GEN_SLA_INTERNAL_SECRET");
}

export function createGenSla(config: GenSlaConfig): GenSlaInstance {
  const modules: Required<GenSlaModulesConfig> = {
    policies: config.modules?.policies ?? true,
    instances: config.modules?.instances ?? true,
    metrics: config.modules?.metrics ?? true,
    worker: config.modules?.worker ?? true,
  };

  if (!modules.policies && !modules.instances && !modules.metrics) {
    throw new GenSlaConfigError("At least one of modules.policies, modules.instances, or modules.metrics must be enabled");
  }

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const policyRepo = resolvePolicyRepo(config.policyRepo);
  const instanceRepo = resolveInstanceRepo(config.instanceRepo);
  const outboxWriter = resolveOutboxWriter(config.outboxWriter);

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  const instanceService = new InstanceService(instanceRepo, policyRepo, outboxWriter);

  if (modules.policies) {
    const policyService = new PolicyService(policyRepo);
    app.use("/api/v1/sla/:tenantId/policies", createPoliciesRouter({ policyService, internalSecretValue }));
  }

  if (modules.instances) {
    app.use("/api/v1/sla/:tenantId/instances", createInstancesRouter({ instanceService, internalSecretValue }));
  }

  if (modules.metrics) {
    const metricsRepo = resolveMetricsRepo(config.metricsRepo);
    const metricsService = new MetricsService(metricsRepo);
    app.use("/api/v1/sla/:tenantId/metrics", createMetricsRouter({ metricsService, internalSecretValue }));
  }

  app.use(errorHandler);

  let worker: GenSlaWorker | undefined;
  if (modules.worker) {
    const lock = resolveLock(config.lock);
    const publisher = resolveEventPublisher(config.eventPublisher);
    if (publisher instanceof RabbitMqBus) void publisher.connect();

    const timer = startSlaTimerWorker({
      instanceRepo,
      instanceService,
      lock,
      pollIntervalMs: env.TIMER_POLL_INTERVAL_MS,
      batchSize: env.TIMER_BATCH_SIZE,
      lockTtlS: env.TIMER_LOCK_TTL_S,
    });
    const outboxDispatcher = startOutboxDispatcher(getPrismaClient(), publisher, {
      pollIntervalMs: env.OUTBOX_POLL_INTERVAL_MS,
      batchSize: env.OUTBOX_BATCH_SIZE,
      maxRetries: env.OUTBOX_MAX_RETRIES,
    });

    worker = {
      close: () => {
        timer.close();
        outboxDispatcher.close();
      },
    };
  }

  return { app, worker };
}
```

Note: env vars used here — `TIMER_POLL_INTERVAL_MS`/`TIMER_BATCH_SIZE`/`TIMER_LOCK_TTL_S`/`OUTBOX_POLL_INTERVAL_MS`/`OUTBOX_BATCH_SIZE`/`OUTBOX_MAX_RETRIES` — come from the eager `EnvSchema` in Task 2 (they have defaults, so they're always present on `env`), while `DATABASE_URL`/`VALKEY_URL`/`RABBITMQ_URL`/`GEN_SLA_INTERNAL_SECRET` are read lazily via `requireEnv` only when the module needing them is enabled and no override was supplied — same split as Gen_REG's `env.ts`.

- [ ] **Step 5: Run tests, verify pass**

Run: `npm test --workspace=@gen-ms/gen-sla-starter -- create-gen-sla`
Expected: all PASS. (The Testcontainers-backed tests require Docker running locally/in CI.)

- [ ] **Step 6: Public barrel**

```ts
// packages/gen-sla-starter/src/index.ts
export { createGenSla } from "./create-gen-sla.ts";
export type { GenSlaConfig, GenSlaModulesConfig, GenSlaWorker, GenSlaInstance } from "./create-gen-sla.ts";

export type { ISlaPolicyRepo, SlaPolicyRecord, CreatePolicyParams, UpdatePolicyParams } from "./domain/ports/policy-repo.port.ts";
export type { ISlaInstanceRepo, SlaInstanceRecord, CreateInstanceParams } from "./domain/ports/instance-repo.port.ts";
export type { IMetricsRepo } from "./domain/ports/metrics-repo.port.ts";
export type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
export type { IEventPublisher, EventEnvelope } from "./domain/ports/event-publisher.port.ts";
export type { IOutboxWriter, OutboxEnqueueParams } from "./domain/ports/outbox-writer.port.ts";

export { PrismaPolicyRepo } from "./modules/policies/v1/repo.ts";
export { PolicyService } from "./modules/policies/v1/service.ts";
export { PrismaInstanceRepo } from "./modules/instances/v1/repo.ts";
export { InstanceService } from "./modules/instances/v1/service.ts";
export { PrismaMetricsRepo } from "./modules/metrics/v1/repo.ts";
export { MetricsService } from "./modules/metrics/v1/service.ts";

export { ValkeyLock } from "./infra/cache/valkey-lock.ts";
export { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
export { PrismaOutboxWriter } from "./infra/messaging/outbox-writer.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { errorHandler } from "./middleware/error-handler.ts";
export {
  AppError,
  GenSlaConfigError,
  TenantIdInvalidError,
  SlaPolicyNotFoundError,
  SlaPolicyConflictError,
  SlaPolicyHasActiveInstancesError,
  SlaInvalidTimingError,
  SlaInstanceNotFoundError,
} from "./common/errors.ts";

export { SLA_EXCHANGE, SLA_ROUTING_KEYS, SLA_STATUSES } from "./config/constants.ts";
```

- [ ] **Step 7: Build to confirm the package compiles end to end**

Run: `npm run build --workspace=@gen-ms/gen-sla-starter`
Expected: exits 0, `dist/` populated.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sla-starter/src/create-gen-sla.ts packages/gen-sla-starter/src/create-gen-sla.test.ts packages/gen-sla-starter/src/index.ts packages/gen-sla-starter/tests
git commit -m "feat: add createGenSla factory wiring policies/instances/metrics/worker, and public barrel"
```

---

### Task 11: Demo app + smoke script + sample event handler

**Files:**
- Create: `packages/gen-sla-demo/src/index.ts`
- Create: `packages/gen-sla-demo/src/index.test.ts`
- Create: `packages/gen-sla-demo/src/sample-event-handler.ts`
- Create: `scripts/smoke-sla.sh`

**Interfaces:**
- Consumes: `createGenSla` (Task 10).
- Produces: a runnable demo app on `PORT` (default 3202) and a smoke script exercising policy CRUD + instance lifecycle end to end.

- [ ] **Step 1: Demo entrypoint**

```ts
// packages/gen-sla-demo/src/index.ts
import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenSla } from "@gen-ms/gen-sla-starter";

const PORT = Number(process.env.PORT ?? 3202);

export function startDemo() {
  const { app, worker } = createGenSla({});
  const server = app.listen(PORT, () => {
    console.log(`Gen_SLA demo listening on port ${PORT}`);
  });
  return { app, server, worker };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
```

- [ ] **Step 2: Demo smoke test**

```ts
// packages/gen-sla-demo/src/index.test.ts
import { describe, it, expect } from "vitest";
import request from "supertest";
import { createGenSla } from "@gen-ms/gen-sla-starter";

describe("gen-sla-demo wiring", () => {
  it("createGenSla({}) mounts a working /health endpoint with no config", () => {
    process.env.DATABASE_URL ??= "postgresql://user:pass@localhost:5440/gensla";
    process.env.GEN_SLA_INTERNAL_SECRET ??= "demo-secret";
    process.env.VALKEY_URL ??= "redis://localhost:6384";
    process.env.RABBITMQ_URL ??= "amqp://localhost:5674";

    const { app } = createGenSla({ modules: { worker: false } });
    return request(app).get("/health").expect(200, { status: "ok" });
  });
});
```

- [ ] **Step 3: Illustrative sample event handler — shows the intended integration shape, not a CPMS mapping**

```ts
// packages/gen-sla-demo/src/sample-event-handler.ts
// Illustrative only. A real deployment wires its own routing keys and handler
// logic against its own event bus — Gen_SLA doesn't assume any business event
// vocabulary (see docs/source-audit-notes.md for why the ancestor's ticket/
// approval/invoice/KT consumer handlers were not ported as fixed logic).
import type { InstanceService, EventEnvelope } from "@gen-ms/gen-sla-starter";

export function makeSampleEntityCreatedHandler(instanceService: InstanceService) {
  return async (envelope: EventEnvelope): Promise<void> => {
    const entityId = envelope.data.entity_id as string;
    const startedAt = new Date(envelope.occurred_at);
    await instanceService.createFromPolicy(envelope.tenant_id, "GENERIC_ENTITY", entityId, "DEFAULT_SLA", envelope.data, startedAt);
  };
}
```

- [ ] **Step 4: Smoke script — policy CRUD + instance lifecycle against a running demo**

```bash
#!/usr/bin/env bash
# scripts/smoke-sla.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3202}"
SECRET="${GEN_SLA_INTERNAL_SECRET:-demo-secret}"
TENANT_ID="${TENANT_ID:-8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11}"

echo "== health =="
curl -sf "$BASE_URL/health"; echo

echo "== create policy =="
POLICY=$(curl -sf -X POST "$BASE_URL/api/v1/sla/$TENANT_ID/policies" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d '{"name":"First Response","entityType":"TICKET","slaType":"FIRST_RESPONSE","durationMins":60,"warningMins":45}')
echo "$POLICY"
POLICY_ID=$(echo "$POLICY" | node -pe 'JSON.parse(require("fs").readFileSync(0)).id')

echo "== list policies =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/policies" -H "X-Internal-Secret: $SECRET"; echo

echo "== get policy =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/policies/$POLICY_ID" -H "X-Internal-Secret: $SECRET"; echo

echo "== list instances (expect empty) =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/instances" -H "X-Internal-Secret: $SECRET"; echo

echo "== metrics summary =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/metrics/summary" -H "X-Internal-Secret: $SECRET"; echo

echo "== delete policy =="
curl -sf -X DELETE "$BASE_URL/api/v1/sla/$TENANT_ID/policies/$POLICY_ID" -H "X-Internal-Secret: $SECRET" -o /dev/null -w "%{http_code}\n"

echo "Smoke test passed."
```

```bash
chmod +x scripts/smoke-sla.sh
```

- [ ] **Step 5: Run the demo test**

Run: `npm test --workspace=@gen-ms/gen-sla-demo`
Expected: PASS (requires the workspace built first: `npm run build --workspace=@gen-ms/gen-sla-starter`).

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sla-demo scripts/smoke-sla.sh
git commit -m "feat: add gen-sla-demo app, sample event handler, and smoke script"
```

---

### Task 12: Integration guide

**Files:**
- Create: `docs/integration-guide.md`

**Interfaces:** None — documentation only, describing the public surface built in Tasks 1–11.

- [ ] **Step 1: Write the integration guide**

```markdown
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

## Local development

    docker compose up -d
    npm install
    npm run dev --workspace=@gen-ms/gen-sla-demo
    ./scripts/smoke-sla.sh

Ports: Postgres `5440`, Valkey `6384`, RabbitMQ AMQP `5674` (mgmt UI `15674`).
```

- [ ] **Step 2: Commit**

```bash
git add docs/integration-guide.md
git commit -m "docs: add Gen_SLA integration guide"
```
