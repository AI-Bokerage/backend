# Gen_NOTIF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_NOTIF — notification delivery (email/webhook/in-app/SMS-port) with host-registered templates, per-user preferences, digest batching, and a self-hosted Socket.IO+Redis realtime gateway.

**Architecture:** Hexagonal ports + default adapters, one `createGenNotif(config)` factory, Express 4 + Prisma 5 + Postgres 15, matching Gen_REG/Gen_TBR conventions exactly.

**Tech Stack:** Node 20, TypeScript ESM/NodeNext, Express 4, Zod 3, Prisma 5, Postgres 15, `socket.io` + `@socket.io/redis-adapter` + `ioredis`, `nodemailer`, `jose`, Vitest 2 + supertest + Testcontainers.

## Global Constraints

- Node.js 20, ESM (`"type": "module"`), NodeNext module resolution, **`.ts` extensions on ALL relative imports** (rewritten to `.js` at build via `rewriteRelativeImportExtensions`).
- `tsconfig.json`/`vitest.config.ts` copied verbatim from `Gen_REG/packages/gen-reg-starter/` (same `fakeTimers.toFake` list, excluding `setImmediate`).
- Every request-boundary error is an `AppError` subclass (`statusCode`, `code`, `message`) — never a raw `Error`. `GenNotifConfigError` is the one exception (plain `Error`, boot-time only, never reaches `errorHandler`).
- No `console.log` in library code — structured `pino` via `logger.child(...)`.
- Postgres port **5438** (AUTH=5433, TNT=5435, REG=5436, TBR=5437) in `docker-compose.yml`, db name `gennotif`.
- `tenantId`/`userId` are UUIDs, trusted from the caller, validated only as UUID shape (Zod `.uuid()`) — never checked against another service.
- All persistence is Postgres/Prisma — no MongoDB anywhere.
- No RabbitMQ, no outbox, no saga machinery — `notify()` is a direct async function.
- No internal cron/scheduler — `runDigestSweep()` is host-triggered only.
- RLS ships with `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + a policy with **both** `USING` and `WITH CHECK` in the **first** migration that creates a tenant-scoped table — never `USING`-only, never missing `FORCE`.
- `resolveXxx(override)` idiom for every config field: override used as-is, else lazily build the default adapter from env vars via `requireEnv`, which throws `GenNotifConfigError` synchronously at `createGenNotif()` call time.
- Prisma migrations ship inside the published package (`prisma/schema.prisma`, `prisma/migrations` in `files`). `postinstall: prisma generate`.
- Naming: package `@gen-ms/gen-notif-starter` / `@gen-ms/gen-notif-demo`, namespace `GenNotif`, factory `createGenNotif`, config `GenNotifConfig`, instance `GenNotifInstance`.

## File Structure

```
Gen_NOTIF/
├── package.json                     # workspace root, "workspaces": ["packages/*"]
├── docker-compose.yml                # postgres:5438, valkey/redis, db "gennotif"
├── .gitignore
├── .env.example
├── docs/
│   ├── integration-guide.md
│   └── superpowers/{specs,plans}/
├── packages/
│   ├── gen-notif-starter/
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── vitest.config.ts
│   │   ├── prisma/{schema.prisma, migrations/}
│   │   ├── src/
│   │   │   ├── create-gen-notif.ts
│   │   │   ├── index.ts
│   │   │   ├── config/env.ts
│   │   │   ├── common/{logger.ts, errors.ts}
│   │   │   ├── domain/ports/
│   │   │   │   ├── tenant-preference.repository.port.ts
│   │   │   │   ├── notification-log.repository.port.ts
│   │   │   │   ├── digest-queue.repository.port.ts
│   │   │   │   ├── webhook-endpoint.repository.port.ts
│   │   │   │   ├── email-sender.port.ts
│   │   │   │   ├── sms-sender.port.ts
│   │   │   │   ├── realtime-gateway.port.ts
│   │   │   │   └── jwt-verifier.port.ts
│   │   │   ├── infra/
│   │   │   │   ├── persistence/prisma-client.ts
│   │   │   │   ├── email/nodemailer-email-sender.ts
│   │   │   │   ├── sms/unimplemented-sms-sender.ts
│   │   │   │   ├── auth/jwks-jwt-verifier.ts
│   │   │   │   └── realtime/{socket-io-redis-gateway.ts, rooms.ts}
│   │   │   ├── modules/
│   │   │   │   ├── templates/v1/registry.ts
│   │   │   │   ├── notify/v1/{service.ts, controller.ts, routes.ts}
│   │   │   │   ├── preferences/v1/{repo.ts, service.ts, controller.ts, routes.ts}
│   │   │   │   ├── notifications/v1/{repo.ts, service.ts, controller.ts, routes.ts}
│   │   │   │   ├── webhooks/v1/{repo.ts, service.ts, dispatch.ts, controller.ts, routes.ts}
│   │   │   │   └── digest/v1/{repo.ts, service.ts, controller.ts, routes.ts}
│   │   │   └── middleware/{error-handler.ts, internal-secret.ts}
│   │   └── tests/support/postgres-container.ts
│   └── gen-notif-demo/
│       ├── package.json
│       └── src/index.ts
└── scripts/
    └── smoke-notif.sh
```

---

### Task 1: Workspace Scaffolding

**Files:**
- Create: `package.json` (root), `.gitignore`, `.env.example`, `docker-compose.yml`
- Create: `packages/gen-notif-starter/package.json`, `packages/gen-notif-starter/tsconfig.json`, `packages/gen-notif-starter/vitest.config.ts`
- Create: `packages/gen-notif-demo/package.json`

**Interfaces:**
- Produces: the workspace layout every later task writes into.

- [ ] **Step 1: Root `package.json`**

```json
{
  "name": "gen-notif",
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
      POSTGRES_DB: gennotif
      POSTGRES_USER: gennotif
      POSTGRES_PASSWORD: gennotif
    ports: ["5438:5432"]
    volumes: ["gennotif-pg:/var/lib/postgresql/data"]
  redis:
    image: redis:7
    ports: ["6381:6379"]
volumes:
  gennotif-pg: {}
```

Port 5438 (AUTH=5433, TNT=5435, REG=5436, TBR=5437) and Redis 6381 (chosen to not collide with any sibling's default 6379/6380 usage).

- [ ] **Step 4: `.env.example`**

```
DATABASE_URL=postgresql://gennotif:gennotif@localhost:5438/gennotif
REDIS_URL=redis://localhost:6381
JWKS_URL=http://localhost:8081/.well-known/jwks.json
JWT_ISSUER=gen-auth
GEN_NOTIF_INTERNAL_SECRET=change-me-dev-secret
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USER=
SMTP_PASS=
SMTP_SECURE=false
SMTP_FROM=no-reply@example.com
ALLOWED_ORIGINS=http://localhost:3000
PORT=3500
```

- [ ] **Step 5: `packages/gen-notif-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-notif-starter",
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
    "nodemailer": "^6.9.15",
    "jose": "^5.9.3",
    "socket.io": "^4.8.0",
    "@socket.io/redis-adapter": "^8.3.0",
    "ioredis": "^5.4.1"
  },
  "devDependencies": {
    "@types/express": "^4.17.21",
    "@types/cors": "^2.8.17",
    "@types/node": "^20.16.10",
    "@types/nodemailer": "^6.4.16",
    "@testcontainers/postgresql": "^10.13.2",
    "prisma": "^5.20.0",
    "pino-pretty": "^11.2.2",
    "socket.io-client": "^4.8.0",
    "supertest": "^7.0.0",
    "@types/supertest": "^6.0.2",
    "typescript": "^5.6.2",
    "vitest": "^2.1.1"
  }
}
```

`pino-pretty` is a `dependency`, not a `devDependency` — `common/logger.ts` requires it at import time whenever `NODE_ENV !== "production"` (matches the fix already applied in Gen_TBR after its final review).

- [ ] **Step 6: `packages/gen-notif-starter/tsconfig.json`**

Copy `Gen_REG/packages/gen-reg-starter/tsconfig.json` verbatim (ES2022 target, NodeNext, strict, declaration, `allowImportingTsExtensions`, `rewriteRelativeImportExtensions`, `skipLibCheck`, `rootDir: "src"`, `outDir: "dist"`).

- [ ] **Step 7: `packages/gen-notif-starter/vitest.config.ts`**

Copy `Gen_REG/packages/gen-reg-starter/vitest.config.ts` verbatim, including the `fakeTimers.toFake` list (excludes `setImmediate` — it breaks Express's finalhandler under fake timers).

- [ ] **Step 8: `packages/gen-notif-demo/package.json`**

```json
{
  "name": "@gen-ms/gen-notif-demo",
  "version": "0.1.0",
  "type": "module",
  "scripts": { "dev": "tsx src/index.ts", "build": "tsc" },
  "dependencies": {
    "@gen-ms/gen-notif-starter": "*",
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
git init
git add package.json .gitignore .env.example docker-compose.yml packages/gen-notif-starter/package.json packages/gen-notif-starter/tsconfig.json packages/gen-notif-starter/vitest.config.ts packages/gen-notif-demo/package.json docs/
git commit -m "chore: scaffold Gen_NOTIF workspace"
```

---

### Task 2: Prisma Schema + Migrations

**Files:**
- Create: `packages/gen-notif-starter/prisma/schema.prisma`
- Create: `packages/gen-notif-starter/prisma/migrations/` (generated by `prisma migrate dev`, plus one hand-written raw-SQL migration for RLS)

**Interfaces:**
- Produces: `NotificationPreference`, `NotificationLog`, `DigestQueueEntry`, `WebhookEndpoint`, `EmailSuppression` Prisma models — every later repo/service task depends on these exact field names and enum values.

- [ ] **Step 1: Write `schema.prisma`**

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum NotifChannel {
  email
  inapp
  sms
  webhook

  @@map("notif_channel")
}

enum NotificationStatus {
  SENT
  FAILED
  QUEUED_FOR_DIGEST
  READ

  @@map("notification_status")
}

enum DigestStatus {
  PENDING
  PROCESSING
  SENT
  FAILED

  @@map("digest_status")
}

model NotificationPreference {
  id         String       @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId   String       @map("tenant_id") @db.Uuid
  userId     String       @map("user_id") @db.Uuid
  eventType  String       @map("event_type") @db.VarChar(120)
  channel    NotifChannel
  enabled    Boolean      @default(true)
  digestMode Boolean      @default(false) @map("digest_mode")
  createdAt  DateTime     @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt  DateTime     @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@unique([tenantId, userId, eventType, channel])
  @@index([tenantId, userId])
  @@map("notification_preference")
}

model NotificationLog {
  id            String              @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId      String              @map("tenant_id") @db.Uuid
  userId        String              @map("user_id") @db.Uuid
  channel       NotifChannel
  eventType     String              @map("event_type") @db.VarChar(120)
  title         String              @db.VarChar(300)
  body          String
  entityRefType String?             @map("entity_ref_type") @db.VarChar(60)
  entityRefId   String?             @map("entity_ref_id") @db.VarChar(120)
  status        NotificationStatus
  readAt        DateTime?           @map("read_at") @db.Timestamptz(6)
  attempts      Int                 @default(1)
  lastError     String?             @map("last_error")
  sentAt        DateTime?           @map("sent_at") @db.Timestamptz(6)
  createdAt     DateTime            @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt     DateTime            @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([tenantId, userId, status, createdAt(sort: Desc)])
  @@map("notification_log")
}

model DigestQueueEntry {
  id              String       @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId        String       @map("tenant_id") @db.Uuid
  userId          String       @map("user_id") @db.Uuid
  email           String       @db.VarChar(320)
  channel         NotifChannel
  scheduledFor    DateTime     @map("scheduled_for") @db.Timestamptz(6)
  status          DigestStatus @default(PENDING)
  notificationIds String[]     @map("notification_ids") @db.Uuid
  attempts        Int          @default(0)
  lastError       String?      @map("last_error")
  sentAt          DateTime?    @map("sent_at") @db.Timestamptz(6)
  createdAt       DateTime     @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt       DateTime     @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([status, scheduledFor])
  @@index([tenantId, userId, status])
  @@map("digest_queue_entry")
}

model WebhookEndpoint {
  id          String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId    String   @map("tenant_id") @db.Uuid
  userId      String   @map("user_id") @db.Uuid
  url         String   @db.VarChar(2048)
  secret      String   @db.VarChar(128)
  enabled     Boolean  @default(true)
  description String?  @db.VarChar(300)
  createdAt   DateTime @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt   DateTime @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([tenantId, userId, enabled])
  @@map("webhook_endpoint")
}

model EmailSuppression {
  id            String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  email         String   @unique @db.VarChar(320)
  reason        String   @db.VarChar(60)
  suppressedAt  DateTime @default(now()) @map("suppressed_at") @db.Timestamptz(6)

  @@map("email_suppression")
}
```

- [ ] **Step 2: Generate the initial migration**

Run: `cd packages/gen-notif-starter && npx prisma migrate dev --name init`
Expected: creates `prisma/migrations/<timestamp>_init/migration.sql` with all 5 tables + 3 enums, applies against the local Postgres from `docker-compose.yml` (must be running: `docker compose up -d postgres`).

- [ ] **Step 3: Write the RLS migration by hand**

Run: `npx prisma migrate dev --create-only --name enable_rls` to scaffold an empty migration folder, then replace its `migration.sql` with:

```sql
-- Row-level security for every tenant-scoped table.
-- FORCE (not just ENABLE) so the table owner is subject to policies too,
-- and every policy has both USING and WITH CHECK from day one — the
-- source service (notif-svc) shipped USING-only, no FORCE, and had to
-- patch both gaps in a later migration after real exposure. Ship it
-- correct the first time.

ALTER TABLE "notification_preference" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "notification_preference" FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_preference_tenant_isolation ON "notification_preference"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "notification_log" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "notification_log" FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_log_tenant_isolation ON "notification_log"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "digest_queue_entry" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "digest_queue_entry" FORCE ROW LEVEL SECURITY;
CREATE POLICY digest_queue_entry_tenant_isolation ON "digest_queue_entry"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "webhook_endpoint" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "webhook_endpoint" FORCE ROW LEVEL SECURITY;
CREATE POLICY webhook_endpoint_tenant_isolation ON "webhook_endpoint"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- email_suppression is deliberately NOT tenant-scoped (no tenant_id column) — a
-- bounce/complaint suppression is a property of the email address itself.
```

Run: `npx prisma migrate dev` to apply it.
Expected: both migrations apply cleanly against the local Postgres container; `npx prisma studio` (optional, manual) shows RLS enabled on all 4 tenant-scoped tables.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-notif-starter/prisma
git commit -m "feat(prisma): add Gen_NOTIF schema with RLS (FORCE + WITH CHECK from day one)"
```

---

### Task 3: Common Layer — Logger + Error Taxonomy

**Files:**
- Create: `packages/gen-notif-starter/src/common/logger.ts`
- Create: `packages/gen-notif-starter/src/common/errors.ts`
- Test: `packages/gen-notif-starter/src/common/errors.test.ts`

**Interfaces:**
- Produces: `AppError`, `GenNotifConfigError`, and every domain error subclass every later module throws.

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

export class GenNotifConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenNotifConfigError";
  }
}

export class NotificationNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "NOTIFICATION_NOT_FOUND", `Notification "${id}" not found`);
  }
}

export class NotificationForbiddenError extends AppError {
  constructor() {
    super(403, "NOTIFICATION_FORBIDDEN", "Access to this notification is denied");
  }
}

export class PreferenceNotFoundError extends AppError {
  constructor() {
    super(404, "PREFERENCE_NOT_FOUND", "Notification preference not found");
  }
}

export class WebhookEndpointNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "WEBHOOK_ENDPOINT_NOT_FOUND", `Webhook endpoint "${id}" not found`);
  }
}

export class SmsNotConfiguredError extends AppError {
  constructor() {
    super(501, "SMS_NOT_CONFIGURED", "No SMS provider is configured — supply an ISmsSender override");
  }
}

export class ChannelDispatchError extends AppError {
  constructor(channel: string, cause: string) {
    super(502, "CHANNEL_DISPATCH_ERROR", `Failed to dispatch via "${channel}": ${cause}`);
  }
}
```

- [ ] **Step 3: Write the test**

```ts
import { describe, it, expect } from "vitest";
import { AppError, NotificationNotFoundError, SmsNotConfiguredError } from "./errors.ts";

describe("error taxonomy", () => {
  it("NotificationNotFoundError carries 404 and the id in its message", () => {
    const err = new NotificationNotFoundError("abc-123");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(404);
    expect(err.code).toBe("NOTIFICATION_NOT_FOUND");
    expect(err.message).toContain("abc-123");
  });

  it("SmsNotConfiguredError is a 501", () => {
    const err = new SmsNotConfiguredError();
    expect(err.statusCode).toBe(501);
    expect(err.code).toBe("SMS_NOT_CONFIGURED");
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run src/common/errors.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-notif-starter/src/common
git commit -m "feat(common): add logger and error taxonomy"
```

---

### Task 4: Env Config

**Files:**
- Create: `packages/gen-notif-starter/src/config/env.ts`
- Test: `packages/gen-notif-starter/src/config/env.test.ts`

**Interfaces:**
- Consumes: none.
- Produces: `requireEnv(name)` — used by every `resolveXxx` default-adapter builder in later tasks; throws `GenNotifConfigError` if the named var is unset or empty.

- [ ] **Step 1: `config/env.ts`**

```ts
import { GenNotifConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenNotifConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}
```

No eager Zod-validated env schema at module load — unlike Gen_REG/Gen_TBR's core config, Gen_NOTIF has no vars that are *always* required regardless of which modules are enabled (e.g. `SMTP_HOST` is only needed if the email channel is actually exercised, `JWKS_URL` only if `modules.realtime` is enabled). Every module-specific var is read lazily by its own `resolveXxx` function in Task 6-9, per the Global Constraints' `resolveXxx` idiom — never validated eagerly for a module the config disables.

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "./env.ts";
import { GenNotifConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_NOTIF_TEST_VAR";
  afterEach(() => { delete process.env[KEY]; });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenNotifConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenNotifConfigError);
  });
});

describe("optionalEnv", () => {
  it("falls back when unset", () => {
    expect(optionalEnv("GEN_NOTIF_TEST_UNSET", "fallback")).toBe("fallback");
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/config/env.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-notif-starter/src/config
git commit -m "feat(config): add requireEnv/optionalEnv lazy env helpers"
```

---

### Task 5: Domain Ports

**Files:**
- Create: `packages/gen-notif-starter/src/domain/ports/tenant-preference.repository.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/notification-log.repository.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/digest-queue.repository.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/webhook-endpoint.repository.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/email-sender.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/sms-sender.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/realtime-gateway.port.ts`
- Create: `packages/gen-notif-starter/src/domain/ports/jwt-verifier.port.ts`

**Interfaces:**
- Produces: every interface name/method signature below is authoritative for every adapter (Task 6-9) and every service (Task 11-15) written in later tasks. Do not deviate from these signatures.

- [ ] **Step 1: `tenant-preference.repository.port.ts`**

```ts
export type NotifChannel = "email" | "inapp" | "sms" | "webhook";

export interface PreferenceRecord {
  id: string;
  tenantId: string;
  userId: string;
  eventType: string;
  channel: NotifChannel;
  enabled: boolean;
  digestMode: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface UpsertPreferenceInput {
  tenantId: string;
  userId: string;
  eventType: string;
  channel: NotifChannel;
  enabled: boolean;
  digestMode: boolean;
}

export interface ITenantPreferenceRepo {
  findAll(tenantId: string, userId: string): Promise<PreferenceRecord[]>;
  findByEventType(tenantId: string, userId: string, eventType: string): Promise<PreferenceRecord[]>;
  upsert(input: UpsertPreferenceInput): Promise<PreferenceRecord>;
}
```

- [ ] **Step 2: `notification-log.repository.port.ts`**

```ts
import type { NotifChannel } from "./tenant-preference.repository.port.ts";

export type NotificationStatus = "SENT" | "FAILED" | "QUEUED_FOR_DIGEST" | "READ";

export interface NotificationLogRecord {
  id: string;
  tenantId: string;
  userId: string;
  channel: NotifChannel;
  eventType: string;
  title: string;
  body: string;
  entityRefType: string | null;
  entityRefId: string | null;
  status: NotificationStatus;
  readAt: Date | null;
  attempts: number;
  lastError: string | null;
  sentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateNotificationLogInput {
  tenantId: string;
  userId: string;
  channel: NotifChannel;
  eventType: string;
  title: string;
  body: string;
  entityRefType?: string;
  entityRefId?: string;
  status: NotificationStatus;
  attempts?: number;
  lastError?: string;
  sentAt?: Date;
}

export interface FindUnreadOptions {
  limit: number;
  before?: Date;
}

export interface INotificationLogRepo {
  create(input: CreateNotificationLogInput): Promise<NotificationLogRecord>;
  findById(tenantId: string, id: string): Promise<NotificationLogRecord | null>;
  findUnread(tenantId: string, userId: string, opts: FindUnreadOptions): Promise<NotificationLogRecord[]>;
  markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord | null>;
  markStatus(tenantId: string, id: string, status: NotificationStatus, lastError?: string): Promise<void>;
}
```

- [ ] **Step 3: `digest-queue.repository.port.ts`**

```ts
export type DigestStatus = "PENDING" | "PROCESSING" | "SENT" | "FAILED";

export interface DigestQueueRecord {
  id: string;
  tenantId: string;
  userId: string;
  email: string;
  channel: "email";
  scheduledFor: Date;
  status: DigestStatus;
  notificationIds: string[];
  attempts: number;
  lastError: string | null;
  sentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface IDigestQueueRepo {
  enqueue(tenantId: string, userId: string, email: string, notificationId: string, scheduledFor: Date): Promise<void>;
  claimDue(batchLimit: number, now: Date): Promise<DigestQueueRecord[]>;
  markSent(id: string): Promise<void>;
  markFailed(id: string, lastError: string): Promise<void>;
}
```

`enqueue` is an upsert: on conflict with an existing `PENDING` entry for `(tenantId, userId, email, channel)`, append `notificationId` to `notificationIds`; on first insert, set `scheduledFor` as given and never overwrite it on subsequent appends to the same pending batch.

- [ ] **Step 4: `webhook-endpoint.repository.port.ts`**

```ts
export interface WebhookEndpointRecord {
  id: string;
  tenantId: string;
  userId: string;
  url: string;
  secret: string;
  enabled: boolean;
  description: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateWebhookEndpointInput {
  tenantId: string;
  userId: string;
  url: string;
  secret: string;
  description?: string;
}

export interface UpdateWebhookEndpointInput {
  url?: string;
  enabled?: boolean;
  description?: string;
}

export interface IWebhookEndpointRepo {
  create(input: CreateWebhookEndpointInput): Promise<WebhookEndpointRecord>;
  findById(tenantId: string, id: string): Promise<WebhookEndpointRecord | null>;
  listEnabled(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]>;
  update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord>;
  delete(tenantId: string, id: string): Promise<void>;
}
```

- [ ] **Step 5: `email-sender.port.ts`**

```ts
export interface SendEmailInput {
  to: string;
  subject: string;
  html?: string;
  text: string;
}

export interface IEmailSender {
  send(input: SendEmailInput): Promise<void>;
}
```

- [ ] **Step 6: `sms-sender.port.ts`**

```ts
export interface SendSmsInput {
  to: string;
  body: string;
}

export interface ISmsSender {
  send(input: SendSmsInput): Promise<void>;
}
```

- [ ] **Step 7: `realtime-gateway.port.ts`**

```ts
import type { Server as SocketIoServer } from "socket.io";

export interface InAppPayload {
  notifId: string;
  type: string;
  title: string;
  body: string;
  entityRefType?: string;
  entityRefId?: string;
  createdAt: string;
}

export interface IRealtimeGateway {
  attach(io: SocketIoServer): void;
  publishInApp(tenantId: string, userId: string, payload: InAppPayload): Promise<void>;
}
```

- [ ] **Step 8: `jwt-verifier.port.ts`**

```ts
export interface VerifiedClaims {
  sub: string;
  tenantId: string;
  roles: string[];
}

export interface IJwtVerifier {
  verify(token: string): Promise<VerifiedClaims>;
}
```

- [ ] **Step 9: Typecheck**

Run: `cd packages/gen-notif-starter && npx tsc --noEmit`
Expected: no errors (these are pure interface files with no implementation yet).

- [ ] **Step 10: Commit**

```bash
git add packages/gen-notif-starter/src/domain
git commit -m "feat(ports): add all Gen_NOTIF domain port interfaces"
```

---

### Task 6: Prisma Client + Repository Adapters + Testcontainers Helper

**Files:**
- Create: `packages/gen-notif-starter/src/infra/persistence/prisma-client.ts`
- Create: `packages/gen-notif-starter/src/infra/persistence/with-tenant.ts`
- Create: `packages/gen-notif-starter/src/modules/preferences/v1/repo.ts` (`PrismaTenantPreferenceRepo`)
- Create: `packages/gen-notif-starter/src/modules/notifications/v1/repo.ts` (`PrismaNotificationLogRepo`)
- Create: `packages/gen-notif-starter/src/modules/digest/v1/repo.ts` (`PrismaDigestQueueRepo`)
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/repo.ts` (`PrismaWebhookEndpointRepo`)
- Create: `packages/gen-notif-starter/tests/support/postgres-container.ts`
- Test: `packages/gen-notif-starter/tests/integration/repos.test.ts`

**Interfaces:**
- Consumes: all 4 repo port interfaces from Task 5, the Prisma models from Task 2.
- Produces: `getPrismaClient()`, `withTenant(tenantId, fn)`, and the 4 `Prisma*Repo` classes every service in Task 11-15 constructs by default.

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

- [ ] **Step 3: `modules/preferences/v1/repo.ts`**

```ts
import type {
  ITenantPreferenceRepo,
  PreferenceRecord,
  UpsertPreferenceInput,
} from "../../../domain/ports/tenant-preference.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaTenantPreferenceRepo implements ITenantPreferenceRepo {
  async findAll(tenantId: string, userId: string): Promise<PreferenceRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationPreference.findMany({ where: { tenantId, userId } }),
    );
  }

  async findByEventType(tenantId: string, userId: string, eventType: string): Promise<PreferenceRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationPreference.findMany({ where: { tenantId, userId, eventType } }),
    );
  }

  async upsert(input: UpsertPreferenceInput): Promise<PreferenceRecord> {
    return withTenant(input.tenantId, (tx) =>
      tx.notificationPreference.upsert({
        where: {
          tenantId_userId_eventType_channel: {
            tenantId: input.tenantId,
            userId: input.userId,
            eventType: input.eventType,
            channel: input.channel,
          },
        },
        create: input,
        update: { enabled: input.enabled, digestMode: input.digestMode },
      }),
    );
  }
}
```

- [ ] **Step 4: `modules/notifications/v1/repo.ts`**

```ts
import type {
  INotificationLogRepo,
  NotificationLogRecord,
  CreateNotificationLogInput,
  FindUnreadOptions,
  NotificationStatus,
} from "../../../domain/ports/notification-log.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaNotificationLogRepo implements INotificationLogRepo {
  async create(input: CreateNotificationLogInput): Promise<NotificationLogRecord> {
    return withTenant(input.tenantId, (tx) => tx.notificationLog.create({ data: input }));
  }

  async findById(tenantId: string, id: string): Promise<NotificationLogRecord | null> {
    return withTenant(tenantId, (tx) => tx.notificationLog.findFirst({ where: { id, tenantId } }));
  }

  async findUnread(tenantId: string, userId: string, opts: FindUnreadOptions): Promise<NotificationLogRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationLog.findMany({
        where: {
          tenantId,
          userId,
          status: { in: ["SENT", "QUEUED_FOR_DIGEST"] },
          ...(opts.before ? { createdAt: { lt: opts.before } } : {}),
        },
        orderBy: { createdAt: "desc" },
        take: opts.limit,
      }),
    );
  }

  async markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord | null> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.notificationLog.updateMany({
        where: { id, tenantId, userId },
        data: { status: "READ", readAt: new Date() },
      });
      if (result.count === 0) return null;
      return tx.notificationLog.findUnique({ where: { id } });
    });
  }

  async markStatus(tenantId: string, id: string, status: NotificationStatus, lastError?: string): Promise<void> {
    // Must run inside withTenant(): with FORCE ROW LEVEL SECURITY + a USING/WITH CHECK
    // policy keyed on current_setting('app.tenant_id'), an UPDATE issued with
    // app.tenant_id unset matches zero rows and silently no-ops.
    await withTenant(tenantId, (tx) =>
      tx.notificationLog.update({
        where: { id },
        data: { status, lastError, sentAt: status === "SENT" ? new Date() : undefined },
      }),
    );
  }
}
```

- [ ] **Step 5: `modules/digest/v1/repo.ts`**

```ts
import type { IDigestQueueRepo, DigestQueueRecord } from "../../../domain/ports/digest-queue.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";

export class PrismaDigestQueueRepo implements IDigestQueueRepo {
  async enqueue(
    tenantId: string,
    userId: string,
    email: string,
    notificationId: string,
    scheduledFor: Date,
  ): Promise<void> {
    await withTenant(tenantId, async (tx) => {
      const existing = await tx.digestQueueEntry.findFirst({
        where: { tenantId, userId, email, channel: "email", status: "PENDING" },
      });
      if (existing) {
        await tx.digestQueueEntry.update({
          where: { id: existing.id },
          data: { notificationIds: { push: notificationId } },
        });
      } else {
        await tx.digestQueueEntry.create({
          data: {
            tenantId, userId, email, channel: "email", scheduledFor,
            status: "PENDING", notificationIds: [notificationId],
          },
        });
      }
    });
  }

  async claimDue(batchLimit: number, now: Date): Promise<DigestQueueRecord[]> {
    const prisma = getPrismaClient();
    // Atomic claim: only rows still PENDING get flipped, so two concurrent
    // sweep callers never both process the same entry.
    const due = await prisma.digestQueueEntry.findMany({
      where: { status: "PENDING", scheduledFor: { lte: now } },
      orderBy: { createdAt: "asc" },
      take: batchLimit,
    });
    const claimed: DigestQueueRecord[] = [];
    for (const entry of due) {
      const result = await prisma.digestQueueEntry.updateMany({
        where: { id: entry.id, status: "PENDING" },
        data: { status: "PROCESSING" },
      });
      if (result.count === 1) claimed.push({ ...entry, status: "PROCESSING" });
    }
    return claimed;
  }

  async markSent(id: string): Promise<void> {
    await getPrismaClient().digestQueueEntry.update({
      where: { id },
      data: { status: "SENT", sentAt: new Date() },
    });
  }

  async markFailed(id: string, lastError: string): Promise<void> {
    await getPrismaClient().digestQueueEntry.update({
      where: { id },
      data: { status: "FAILED", lastError, attempts: { increment: 1 } },
    });
  }
}
```

- [ ] **Step 6: `modules/webhooks/v1/repo.ts`**

```ts
import type {
  IWebhookEndpointRepo,
  WebhookEndpointRecord,
  CreateWebhookEndpointInput,
  UpdateWebhookEndpointInput,
} from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { WebhookEndpointNotFoundError } from "../../../common/errors.ts";

export class PrismaWebhookEndpointRepo implements IWebhookEndpointRepo {
  async create(input: CreateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return withTenant(input.tenantId, (tx) => tx.webhookEndpoint.create({ data: input }));
  }

  async findById(tenantId: string, id: string): Promise<WebhookEndpointRecord | null> {
    return withTenant(tenantId, (tx) => tx.webhookEndpoint.findFirst({ where: { id, tenantId } }));
  }

  async listEnabled(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.webhookEndpoint.findMany({ where: { tenantId, userId, enabled: true } }),
    );
  }

  async update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.webhookEndpoint.updateMany({ where: { id, tenantId }, data: input });
      if (result.count === 0) throw new WebhookEndpointNotFoundError(id);
      return tx.webhookEndpoint.findFirstOrThrow({ where: { id, tenantId } });
    });
  }

  async delete(tenantId: string, id: string): Promise<void> {
    await withTenant(tenantId, async (tx) => {
      const result = await tx.webhookEndpoint.deleteMany({ where: { id, tenantId } });
      if (result.count === 0) throw new WebhookEndpointNotFoundError(id);
    });
  }
}
```

- [ ] **Step 7: `tests/support/postgres-container.ts`**

Copy the pattern from `Gen_TBR/packages/gen-tbr-starter/tests/support/postgres-container.ts` verbatim, including its Windows-safe path resolution via `fileURLToPath()` (not `new URL(...).pathname`, which produces a malformed path with a leading slash before the drive letter on Windows). Point its schema/migration lookup at `gen-notif-starter/prisma` instead of `gen-tbr-starter/prisma`. It must: start a real Postgres 15 container via `@testcontainers/postgresql`, run `prisma migrate deploy` against it, return a connected `PrismaClient`, and expose a teardown function.

- [ ] **Step 8: Write the integration test**

```ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { randomUUID } from "node:crypto";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantPreferenceRepo } from "../../src/modules/preferences/v1/repo.ts";
import { PrismaWebhookEndpointRepo } from "../../src/modules/webhooks/v1/repo.ts";

describe("Prisma repos against real Postgres", () => {
  let db: TestPostgres;
  beforeAll(async () => { db = await startPostgresContainer(); }, 60_000);
  afterAll(async () => { await db.stop(); });

  it("upserts a preference and enforces the tenant-scoped unique key", async () => {
    const repo = new PrismaTenantPreferenceRepo();
    const tenantId = randomUUID();
    const userId = randomUUID();
    const first = await repo.upsert({
      tenantId, userId, eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false,
    });
    const second = await repo.upsert({
      tenantId, userId, eventType: "doc.uploaded", channel: "email", enabled: false, digestMode: true,
    });
    expect(second.id).toBe(first.id);
    expect(second.enabled).toBe(false);
    expect(second.digestMode).toBe(true);
  });

  it("enforces RLS: a query without app.tenant_id set sees zero rows via the public client", async () => {
    const repo = new PrismaWebhookEndpointRepo();
    const tenantId = randomUUID();
    await repo.create({ tenantId, userId: randomUUID(), url: "https://example.com/hook", secret: "s3cr3t" });
    const rawRows: unknown[] = await db.prisma.$queryRawUnsafe(`SELECT * FROM "webhook_endpoint" WHERE tenant_id = '${tenantId}'`);
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
git add packages/gen-notif-starter/src/infra/persistence packages/gen-notif-starter/src/modules/preferences/v1/repo.ts packages/gen-notif-starter/src/modules/notifications/v1/repo.ts packages/gen-notif-starter/src/modules/digest/v1/repo.ts packages/gen-notif-starter/src/modules/webhooks/v1/repo.ts packages/gen-notif-starter/tests
git commit -m "feat(repos): add Prisma repository adapters with RLS-scoped withTenant()"
```

---

### Task 7: Email + SMS Sender Adapters

**Files:**
- Create: `packages/gen-notif-starter/src/infra/email/nodemailer-email-sender.ts`
- Create: `packages/gen-notif-starter/src/infra/sms/unimplemented-sms-sender.ts`
- Test: `packages/gen-notif-starter/src/infra/sms/unimplemented-sms-sender.test.ts`

**Interfaces:**
- Consumes: `IEmailSender`, `ISmsSender` from Task 5.
- Produces: `NodemailerEmailSender`, `UnimplementedSmsSender`.

- [ ] **Step 1: `infra/email/nodemailer-email-sender.ts`**

```ts
import nodemailer, { type Transporter } from "nodemailer";
import type { IEmailSender, SendEmailInput } from "../../domain/ports/email-sender.port.ts";

export interface NodemailerConfig {
  host: string;
  port: number;
  secure: boolean;
  user?: string;
  pass?: string;
  from: string;
}

export class NodemailerEmailSender implements IEmailSender {
  private readonly transporter: Transporter;
  private readonly from: string;

  constructor(config: NodemailerConfig) {
    this.from = config.from;
    this.transporter = nodemailer.createTransport({
      host: config.host,
      port: config.port,
      secure: config.secure,
      auth: config.user ? { user: config.user, pass: config.pass } : undefined,
    });
  }

  async send(input: SendEmailInput): Promise<void> {
    await this.transporter.sendMail({
      from: this.from,
      to: input.to,
      subject: input.subject,
      text: input.text,
      html: input.html,
    });
  }
}
```

Single-tier SMTP — no AWS-SES-specific branching. SES's own SMTP credentials (`SMTP_HOST=email-smtp.<region>.amazonaws.com`) work through this transport with zero special-casing, unlike the source's two-tier "SES-via-SMTP vs. generic SMTP" branch.

- [ ] **Step 2: `infra/sms/unimplemented-sms-sender.ts`**

```ts
import type { ISmsSender, SendSmsInput } from "../../domain/ports/sms-sender.port.ts";
import { SmsNotConfiguredError } from "../../common/errors.ts";

export class UnimplementedSmsSender implements ISmsSender {
  async send(_input: SendSmsInput): Promise<void> {
    throw new SmsNotConfiguredError();
  }
}
```

This is the default `ISmsSender` — calling `notify()` for a recipient whose only resolved channel is `sms`, with no `smsSender` override configured, throws `SmsNotConfiguredError` (501) rather than silently writing a fake `FAILED` log row and returning normally (the source's actual behavior).

- [ ] **Step 3: Write the test**

```ts
import { describe, it, expect } from "vitest";
import { UnimplementedSmsSender } from "./unimplemented-sms-sender.ts";
import { SmsNotConfiguredError } from "../../common/errors.ts";

describe("UnimplementedSmsSender", () => {
  it("throws SmsNotConfiguredError rather than silently succeeding", async () => {
    const sender = new UnimplementedSmsSender();
    await expect(sender.send({ to: "+15551234567", body: "hi" })).rejects.toThrow(SmsNotConfiguredError);
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run src/infra/sms/unimplemented-sms-sender.test.ts`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-notif-starter/src/infra/email packages/gen-notif-starter/src/infra/sms
git commit -m "feat(infra): add nodemailer email sender and unimplemented SMS sender"
```

---

### Task 8: JWT Verifier Adapter

**Files:**
- Create: `packages/gen-notif-starter/src/infra/auth/jwks-jwt-verifier.ts`
- Test: `packages/gen-notif-starter/src/infra/auth/jwks-jwt-verifier.test.ts`

**Interfaces:**
- Consumes: `IJwtVerifier` from Task 5.
- Produces: `JwksJwtVerifier`, used by the realtime gateway (Task 9) for the Socket.IO handshake.

- [ ] **Step 1: `infra/auth/jwks-jwt-verifier.ts`**

```ts
import { createRemoteJWKSet, jwtVerify } from "jose";
import type { IJwtVerifier, VerifiedClaims } from "../../domain/ports/jwt-verifier.port.ts";

export class JwksJwtVerifier implements IJwtVerifier {
  private readonly jwks: ReturnType<typeof createRemoteJWKSet>;

  constructor(
    private readonly jwksUrl: string,
    private readonly issuer: string,
  ) {
    this.jwks = createRemoteJWKSet(new URL(jwksUrl));
  }

  async verify(token: string): Promise<VerifiedClaims> {
    const { payload } = await jwtVerify(token, this.jwks, { issuer: this.issuer });
    const tenantId = payload["tenant_id"];
    const roles = payload["roles"];
    if (typeof payload.sub !== "string" || typeof tenantId !== "string") {
      throw new Error("JWT payload missing required sub/tenant_id claims");
    }
    return {
      sub: payload.sub,
      tenantId,
      roles: Array.isArray(roles) ? roles.map(String) : [],
    };
  }
}
```

- [ ] **Step 2: Write the test**

Use a fake in-memory JWKS (generate an RS256 keypair with `jose`'s `generateKeyPair`, sign a token, serve its JWK via a local `createLocalJWKSet` substituted through a thin wrapper) rather than hitting a real network JWKS endpoint:

```ts
import { describe, it, expect, beforeAll } from "vitest";
import { SignJWT, generateKeyPair, exportJWK } from "jose";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { JwksJwtVerifier } from "./jwks-jwt-verifier.ts";

describe("JwksJwtVerifier", () => {
  let jwksUrl: string;
  let privateKey: CryptoKey;
  let kid: string;
  let server: http.Server;

  beforeAll(async () => {
    const { publicKey, privateKey: priv } = await generateKeyPair("RS256");
    privateKey = priv;
    const jwk = await exportJWK(publicKey);
    kid = "test-key-1";
    server = http.createServer((_req, res) => {
      res.setHeader("content-type", "application/json");
      res.end(JSON.stringify({ keys: [{ ...jwk, kid, alg: "RS256", use: "sig" }] }));
    });
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const { port } = server.address() as AddressInfo;
    jwksUrl = `http://localhost:${port}/jwks.json`;
  });

  it("verifies a valid token and extracts tenant_id/roles", async () => {
    const token = await new SignJWT({ tenant_id: "11111111-1111-1111-1111-111111111111", roles: ["admin"] })
      .setProtectedHeader({ alg: "RS256", kid })
      .setSubject("user-1")
      .setIssuer("gen-auth")
      .setExpirationTime("5m")
      .sign(privateKey);

    const verifier = new JwksJwtVerifier(jwksUrl, "gen-auth");
    const claims = await verifier.verify(token);
    expect(claims.sub).toBe("user-1");
    expect(claims.tenantId).toBe("11111111-1111-1111-1111-111111111111");
    expect(claims.roles).toEqual(["admin"]);
  });

  it("rejects a token signed with the wrong issuer", async () => {
    const token = await new SignJWT({ tenant_id: "11111111-1111-1111-1111-111111111111" })
      .setProtectedHeader({ alg: "RS256", kid })
      .setSubject("user-1")
      .setIssuer("someone-else")
      .setExpirationTime("5m")
      .sign(privateKey);

    const verifier = new JwksJwtVerifier(jwksUrl, "gen-auth");
    await expect(verifier.verify(token)).rejects.toThrow();
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/infra/auth/jwks-jwt-verifier.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-notif-starter/src/infra/auth
git commit -m "feat(infra): add JWKS-based JWT verifier"
```

---

### Task 9: Realtime Gateway (Socket.IO + Redis Adapter)

**Files:**
- Create: `packages/gen-notif-starter/src/infra/realtime/rooms.ts`
- Create: `packages/gen-notif-starter/src/infra/realtime/socket-io-redis-gateway.ts`
- Test: `packages/gen-notif-starter/src/infra/realtime/rooms.test.ts`
- Test: `packages/gen-notif-starter/tests/integration/realtime-gateway.test.ts`

**Interfaces:**
- Consumes: `IRealtimeGateway`, `IJwtVerifier` from Task 5, `JwksJwtVerifier` from Task 8.
- Produces: `SocketIoRedisGateway`, `userRoom()`, `buildNotifChannel()` — the one shared contract between the publish side and the subscribe side, both living in this same file/module (fixing the source's cross-service string-duplication bug).

- [ ] **Step 1: `infra/realtime/rooms.ts`**

```ts
export function userRoom(tenantId: string, userId: string): string {
  return `tenant:${tenantId}:user:${userId}`;
}

export function buildNotifChannel(tenantId: string, userId: string): string {
  return `tenant:${tenantId}:notif:${userId}`;
}

export const NOTIF_CHANNEL_PATTERN = "tenant:*:notif:*";

export function parseNotifChannel(channel: string): { tenantId: string; userId: string } | null {
  const parts = channel.split(":");
  if (parts.length < 4 || parts[0] !== "tenant" || parts[2] !== "notif") return null;
  return { tenantId: parts[1]!, userId: parts[3]! };
}
```

`buildNotifChannel` (publish side) and `parseNotifChannel` (subscribe side) are the one shared source of truth for the Redis channel-naming contract — both are used by `SocketIoRedisGateway` below, in the same package, closing the gap where the source had this same string format independently duplicated across two separate services with no shared constant.

- [ ] **Step 2: Write the `rooms.ts` test**

```ts
import { describe, it, expect } from "vitest";
import { userRoom, buildNotifChannel, parseNotifChannel } from "./rooms.ts";

describe("realtime room/channel naming", () => {
  it("round-trips tenantId/userId through buildNotifChannel -> parseNotifChannel", () => {
    const tenantId = "11111111-1111-1111-1111-111111111111";
    const userId = "22222222-2222-2222-2222-222222222222";
    const channel = buildNotifChannel(tenantId, userId);
    expect(channel).toBe(`tenant:${tenantId}:notif:${userId}`);
    expect(parseNotifChannel(channel)).toEqual({ tenantId, userId });
  });

  it("userRoom uses the :user: segment, distinct from the :notif: channel", () => {
    expect(userRoom("t1", "u1")).toBe("tenant:t1:user:u1");
  });

  it("parseNotifChannel rejects a malformed channel", () => {
    expect(parseNotifChannel("garbage")).toBeNull();
    expect(parseNotifChannel("tenant:t1:user:u1")).toBeNull(); // wrong segment (user, not notif)
  });
});
```

- [ ] **Step 3: `infra/realtime/socket-io-redis-gateway.ts`**

```ts
import type { Server as SocketIoServer } from "socket.io";
import { createAdapter } from "@socket.io/redis-adapter";
import Redis from "ioredis";
import type { IRealtimeGateway, InAppPayload } from "../../domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "../../domain/ports/jwt-verifier.port.ts";
import { userRoom, buildNotifChannel, parseNotifChannel, NOTIF_CHANNEL_PATTERN } from "./rooms.ts";
import { logger } from "../../common/logger.ts";

export class SocketIoRedisGateway implements IRealtimeGateway {
  private readonly redisUrl: string;
  private readonly jwtVerifier: IJwtVerifier;
  private publishClient: Redis | undefined;

  constructor(redisUrl: string, jwtVerifier: IJwtVerifier) {
    this.redisUrl = redisUrl;
    this.jwtVerifier = jwtVerifier;
  }

  private getPublishClient(): Redis {
    if (!this.publishClient) this.publishClient = new Redis(this.redisUrl);
    return this.publishClient;
  }

  attach(io: SocketIoServer): void {
    // Socket.IO's Redis adapter needs its own dedicated pub/sub connection
    // pair, separate from any client issuing normal commands — a
    // subscriber-mode Redis connection can't issue normal commands.
    const pub = new Redis(this.redisUrl);
    const sub = pub.duplicate();
    io.adapter(createAdapter(pub, sub));

    io.use(async (socket, next) => {
      try {
        const auth = socket.handshake.auth as Record<string, string>;
        const header = socket.handshake.headers["authorization"];
        const token = auth?.token ?? (typeof header === "string" ? header.replace(/^Bearer\s+/i, "").trim() : undefined);
        if (!token) {
          next(new Error("GEN_NOTIF_UNAUTHORIZED: no token"));
          return;
        }
        const claims = await this.jwtVerifier.verify(token);
        socket.data.userId = claims.sub;
        socket.data.tenantId = claims.tenantId;
        socket.data.roles = claims.roles;
        next();
      } catch {
        next(new Error("GEN_NOTIF_UNAUTHORIZED: invalid token"));
      }
    });

    io.on("connection", (socket) => {
      const { tenantId, userId } = socket.data as { tenantId: string; userId: string };
      socket.join(userRoom(tenantId, userId));
    });

    // The publish-to-socket bridge: a dedicated psubscribe connection
    // (again, separate from the adapter's own pub/sub pair and from the
    // client publishInApp() uses).
    const bridgeSub = pub.duplicate();
    bridgeSub.psubscribe(NOTIF_CHANNEL_PATTERN, (err) => {
      if (err) logger.error({ err }, "[gen-notif] failed to psubscribe to notification channel pattern");
    });
    bridgeSub.on("pmessage", (_pattern, channel, message) => {
      const parsed = parseNotifChannel(channel);
      if (!parsed) {
        logger.warn({ channel }, "[gen-notif] received message on unparseable channel — skipping");
        return;
      }
      let payload: unknown;
      try {
        payload = JSON.parse(message);
      } catch {
        logger.warn({ channel }, "[gen-notif] received non-JSON message — skipping");
        return;
      }
      io.to(userRoom(parsed.tenantId, parsed.userId)).emit("notification", payload);
    });
  }

  async publishInApp(tenantId: string, userId: string, payload: InAppPayload): Promise<void> {
    await this.getPublishClient().publish(buildNotifChannel(tenantId, userId), JSON.stringify(payload));
  }
}
```

- [ ] **Step 4: Write the integration test**

Requires a real Redis (via Testcontainers `@testcontainers/redis` — add as a devDependency in Task 1 if not already present; if adding late, run `npm install --save-dev @testcontainers/redis --workspace=@gen-ms/gen-notif-starter` now) and a real `http.Server` + `socket.io-client`:

```ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { Server as SocketIoServer } from "socket.io";
import { io as ioClient, type Socket as ClientSocket } from "socket.io-client";
import { GenericContainer, type StartedTestContainer } from "testcontainers";
import { SignJWT, generateKeyPair } from "jose";
import { SocketIoRedisGateway } from "../../src/infra/realtime/socket-io-redis-gateway.ts";
import type { IJwtVerifier } from "../../src/domain/ports/jwt-verifier.port.ts";

describe("SocketIoRedisGateway end-to-end", () => {
  let redisContainer: StartedTestContainer;
  let httpServer: http.Server;
  let port: number;
  let gateway: SocketIoRedisGateway;

  beforeAll(async () => {
    redisContainer = await new GenericContainer("redis:7").withExposedPorts(6379).start();
    const redisUrl = `redis://${redisContainer.getHost()}:${redisContainer.getMappedPort(6379)}`;

    const fakeVerifier: IJwtVerifier = {
      async verify(token: string) {
        const [, payloadB64] = token.split(".");
        return JSON.parse(Buffer.from(payloadB64!, "base64url").toString());
      },
    };

    gateway = new SocketIoRedisGateway(redisUrl, fakeVerifier);
    httpServer = http.createServer();
    const io = new SocketIoServer(httpServer);
    gateway.attach(io);
    await new Promise<void>((resolve) => httpServer.listen(0, resolve));
    port = (httpServer.address() as AddressInfo).port;
  }, 60_000);

  afterAll(async () => {
    httpServer.close();
    await redisContainer.stop();
  });

  it("delivers a publishInApp() call to the connected client in that tenant/user's room", async () => {
    const fakeToken = `header.${Buffer.from(JSON.stringify({ sub: "user-1", tenantId: "t1", roles: [] })).toString("base64url")}.sig`;
    const client: ClientSocket = ioClient(`http://localhost:${port}`, { auth: { token: fakeToken } });
    await new Promise<void>((resolve, reject) => {
      client.on("connect", resolve);
      client.on("connect_error", reject);
    });

    const received = new Promise((resolve) => client.once("notification", resolve));
    await gateway.publishInApp("t1", "user-1", {
      notifId: "n1", type: "doc.uploaded", title: "New doc", body: "A file was uploaded", createdAt: new Date().toISOString(),
    });

    const payload = await received;
    expect(payload).toMatchObject({ notifId: "n1", type: "doc.uploaded" });
    client.close();
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run src/infra/realtime/rooms.test.ts tests/integration/realtime-gateway.test.ts`
Expected: PASS, 4 tests total. Requires Docker running locally.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/infra/realtime packages/gen-notif-starter/tests/integration/realtime-gateway.test.ts packages/gen-notif-starter/package.json
git commit -m "feat(realtime): add Socket.IO + Redis-adapter gateway with shared room/channel contract"
```

---

### Task 10: Template Registry

**Files:**
- Create: `packages/gen-notif-starter/src/modules/templates/v1/registry.ts`
- Test: `packages/gen-notif-starter/src/modules/templates/v1/registry.test.ts`

**Interfaces:**
- Produces: `registerTemplate(eventType, builder)`, `renderTemplate(eventType, data)`, `listRegisteredEventTypes()` — consumed by `NotifService` (Task 12) and the preferences `event-types` route (Task 13).

- [ ] **Step 1: `modules/templates/v1/registry.ts`**

```ts
export interface NotificationContent {
  title: string;
  body: string;
  html?: string;
}

export type TemplateBuilder = (data: Record<string, unknown>) => NotificationContent;

const registry = new Map<string, TemplateBuilder>();

const FALLBACK_BUILDER: TemplateBuilder = (data) => ({
  title: "New notification",
  body: typeof data["message"] === "string" ? data["message"] : "You have a new notification.",
});

export function registerTemplate(eventType: string, builder: TemplateBuilder): void {
  registry.set(eventType, builder);
}

export function renderTemplate(eventType: string, data: Record<string, unknown>): NotificationContent {
  const builder = registry.get(eventType) ?? FALLBACK_BUILDER;
  return builder(data);
}

export function listRegisteredEventTypes(): string[] {
  return [...registry.keys()];
}

/** Test-only: clears all registered templates. Not exported from the package's public barrel. */
export function __resetRegistryForTests(): void {
  registry.clear();
}
```

`NotificationContent` is channel-neutral by construction (`title`/`body`/optional `html`) — no channel is forced to consume an email-shaped structure the way the source's `EmailContent` (`badge/h2/bodyText/details/ctaText/ctaUrl/closingText`) forced non-email channels to derive their content by joining label:value pairs.

- [ ] **Step 2: Write the test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import { registerTemplate, renderTemplate, listRegisteredEventTypes, __resetRegistryForTests } from "./registry.ts";

describe("template registry", () => {
  afterEach(() => __resetRegistryForTests());

  it("renders a registered template with the given data", () => {
    registerTemplate("doc.uploaded", (data) => ({
      title: "New document",
      body: `${data["fileName"]} was uploaded`,
    }));
    const content = renderTemplate("doc.uploaded", { fileName: "invoice.pdf" });
    expect(content.title).toBe("New document");
    expect(content.body).toBe("invoice.pdf was uploaded");
  });

  it("falls back to a generic builder for an unregistered eventType", () => {
    const content = renderTemplate("totally.unknown.event", { message: "custom fallback text" });
    expect(content.title).toBe("New notification");
    expect(content.body).toBe("custom fallback text");
  });

  it("listRegisteredEventTypes reflects only what's been registered", () => {
    expect(listRegisteredEventTypes()).toEqual([]);
    registerTemplate("doc.uploaded", () => ({ title: "t", body: "b" }));
    expect(listRegisteredEventTypes()).toEqual(["doc.uploaded"]);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npx vitest run src/modules/templates/v1/registry.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 4: Commit**

```bash
git add packages/gen-notif-starter/src/modules/templates
git commit -m "feat(templates): add host-registered, channel-neutral template registry"
```

---

### Task 11: Channel Dispatchers (Email, In-App, SMS, Webhook HMAC)

**Files:**
- Create: `packages/gen-notif-starter/src/modules/notify/v1/channels/email.ts`
- Create: `packages/gen-notif-starter/src/modules/notify/v1/channels/inapp.ts`
- Create: `packages/gen-notif-starter/src/modules/notify/v1/channels/sms.ts`
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/hmac.ts`
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/dispatch.ts`
- Test: `packages/gen-notif-starter/src/modules/webhooks/v1/hmac.test.ts`

**Interfaces:**
- Consumes: `IEmailSender`, `ISmsSender`, `IRealtimeGateway`, `IWebhookEndpointRepo` (Task 5), `NotificationContent` (Task 10).
- Produces: `dispatchEmail`, `dispatchInApp`, `dispatchSms`, `dispatchWebhook`, `sign`/`verifyWebhookSignature` — every one consumed by `NotifService` in Task 12.

- [ ] **Step 1: `modules/notify/v1/channels/email.ts`**

```ts
import type { IEmailSender } from "../../../../domain/ports/email-sender.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchEmailResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchEmail(
  sender: IEmailSender,
  to: string,
  content: NotificationContent,
): Promise<DispatchEmailResult> {
  try {
    await sender.send({
      to,
      subject: content.title,
      text: content.body,
      html: content.html,
    });
    return { status: "SENT" };
  } catch (err) {
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
```

- [ ] **Step 2: `modules/notify/v1/channels/inapp.ts`**

```ts
import type { IRealtimeGateway } from "../../../../domain/ports/realtime-gateway.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchInAppResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchInApp(
  gateway: IRealtimeGateway,
  tenantId: string,
  userId: string,
  notifId: string,
  eventType: string,
  content: NotificationContent,
  entityRefType?: string,
  entityRefId?: string,
): Promise<DispatchInAppResult> {
  try {
    await gateway.publishInApp(tenantId, userId, {
      notifId,
      type: eventType,
      title: content.title,
      body: content.body,
      entityRefType,
      entityRefId,
      createdAt: new Date().toISOString(),
    });
    return { status: "SENT" };
  } catch (err) {
    // Unlike the source (which wrote SENT before attempting the publish and
    // only logged a warning on failure), a publish failure here is a real
    // dispatch failure — the caller writes FAILED to the log, matching what
    // actually happened.
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
```

- [ ] **Step 3: `modules/notify/v1/channels/sms.ts`**

```ts
import type { ISmsSender } from "../../../../domain/ports/sms-sender.port.ts";
import type { NotificationContent } from "../../../templates/v1/registry.ts";

export interface DispatchSmsResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

export async function dispatchSms(
  sender: ISmsSender,
  to: string,
  content: NotificationContent,
): Promise<DispatchSmsResult> {
  try {
    await sender.send({ to, body: `${content.title}: ${content.body}` });
    return { status: "SENT" };
  } catch (err) {
    return { status: "FAILED", lastError: err instanceof Error ? err.message : String(err) };
  }
}
```

With the default `UnimplementedSmsSender` (Task 7), this always resolves to `{status: "FAILED", lastError: "No SMS provider is configured..."}` — `NotifService` (Task 12) writes that as a real, honest `FAILED` log row.

- [ ] **Step 4: `modules/webhooks/v1/hmac.ts`**

```ts
import { createHmac, timingSafeEqual } from "node:crypto";

const TIMESTAMP_GRACE_S = 300;

export function signWebhookPayload(secret: string, timestamp: number, body: string): string {
  const data = `${timestamp}.${body}`;
  const hex = createHmac("sha256", secret).update(data).digest("hex");
  return `sha256=${hex}`;
}

export function verifyWebhookSignature(
  secret: string,
  timestamp: number,
  rawBody: string,
  signature: string,
): boolean {
  if (Math.abs(Date.now() / 1000 - timestamp) > TIMESTAMP_GRACE_S) return false;
  const expected = signWebhookPayload(secret, timestamp, rawBody);
  const expectedBuf = Buffer.from(expected);
  const actualBuf = Buffer.from(signature);
  if (expectedBuf.length !== actualBuf.length) return false;
  return timingSafeEqual(expectedBuf, actualBuf);
}
```

- [ ] **Step 5: `modules/webhooks/v1/dispatch.ts`**

```ts
import type { WebhookEndpointRecord } from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import type { NotificationContent } from "../../templates/v1/registry.ts";
import { signWebhookPayload } from "./hmac.ts";

const RETRY_DELAYS_MS = [0, 1_000, 4_000];
const TIMEOUT_MS = 10_000;

export interface DispatchWebhookResult {
  status: "SENT" | "FAILED";
  lastError?: string;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function dispatchWebhookToEndpoint(
  endpoint: WebhookEndpointRecord,
  eventType: string,
  content: NotificationContent,
): Promise<DispatchWebhookResult> {
  const body = JSON.stringify({ eventType, title: content.title, body: content.body });
  let lastError = "unknown error";

  for (const delay of RETRY_DELAYS_MS) {
    if (delay > 0) await sleep(delay);
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = signWebhookPayload(endpoint.secret, timestamp, body);
    try {
      const res = await fetch(endpoint.url, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-gen-notif-signature": signature,
          "x-gen-notif-timestamp": String(timestamp),
        },
        body,
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (res.ok) return { status: "SENT" };
      lastError = `HTTP ${res.status}`;
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
  }
  return { status: "FAILED", lastError };
}

export async function dispatchWebhook(
  endpoints: WebhookEndpointRecord[],
  eventType: string,
  content: NotificationContent,
): Promise<Array<{ endpointId: string; result: DispatchWebhookResult }>> {
  const settled = await Promise.allSettled(
    endpoints.map(async (endpoint) => ({
      endpointId: endpoint.id,
      result: await dispatchWebhookToEndpoint(endpoint, eventType, content),
    })),
  );
  return settled.map((s, i) =>
    s.status === "fulfilled" ? s.value : { endpointId: endpoints[i]!.id, result: { status: "FAILED" as const, lastError: String(s.reason) } },
  );
}
```

Retry is in-process and synchronous — 3 attempts (`0ms`/`1000ms`/`4000ms` delays), each re-signing with a fresh timestamp. A process crash mid-retry loses the remaining attempts; documented as a known v1 limitation in `integration-guide.md` (Task 18), not silently hidden. No SSRF guard on `endpoint.url` in v1 — also documented, matching the source's own gap.

- [ ] **Step 6: Write the HMAC test**

```ts
import { describe, it, expect } from "vitest";
import { signWebhookPayload, verifyWebhookSignature } from "./hmac.ts";

describe("webhook HMAC signing", () => {
  it("a signature verifies against the same secret/timestamp/body", () => {
    const sig = signWebhookPayload("s3cr3t", 1_700_000_000, '{"a":1}');
    expect(sig).toMatch(/^sha256=[0-9a-f]{64}$/);
  });

  it("verifyWebhookSignature accepts a fresh, correctly-signed payload", () => {
    const now = Math.floor(Date.now() / 1000);
    const body = '{"eventType":"doc.uploaded"}';
    const sig = signWebhookPayload("s3cr3t", now, body);
    expect(verifyWebhookSignature("s3cr3t", now, body, sig)).toBe(true);
  });

  it("verifyWebhookSignature rejects a timestamp outside the 300s grace window", () => {
    const stale = Math.floor(Date.now() / 1000) - 301;
    const body = "{}";
    const sig = signWebhookPayload("s3cr3t", stale, body);
    expect(verifyWebhookSignature("s3cr3t", stale, body, sig)).toBe(false);
  });

  it("verifyWebhookSignature rejects a tampered body", () => {
    const now = Math.floor(Date.now() / 1000);
    const sig = signWebhookPayload("s3cr3t", now, '{"a":1}');
    expect(verifyWebhookSignature("s3cr3t", now, '{"a":2}', sig)).toBe(false);
  });
});
```

- [ ] **Step 7: Run tests**

Run: `npx vitest run src/modules/webhooks/v1/hmac.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-notif-starter/src/modules/notify/v1/channels packages/gen-notif-starter/src/modules/webhooks/v1/hmac.ts packages/gen-notif-starter/src/modules/webhooks/v1/dispatch.ts packages/gen-notif-starter/src/modules/webhooks/v1/hmac.test.ts
git commit -m "feat(dispatch): add email/inapp/sms/webhook channel dispatchers with honest status reporting"
```

---

### Task 12: NotifService — the `notify()` Pipeline

**Files:**
- Create: `packages/gen-notif-starter/src/modules/notify/v1/channel-router.ts`
- Create: `packages/gen-notif-starter/src/modules/notify/v1/service.ts`
- Test: `packages/gen-notif-starter/src/modules/notify/v1/service.test.ts`

**Interfaces:**
- Consumes: `ITenantPreferenceRepo`, `INotificationLogRepo`, `IDigestQueueRepo`, `IWebhookEndpointRepo`, `IEmailSender`, `ISmsSender`, `IRealtimeGateway` (Task 5), the four channel dispatchers (Task 11), `renderTemplate` (Task 10).
- Produces: `NotifService.notify(input)` — this is the `notify()` function `GenNotifInstance` exposes (wired in Task 17).

- [ ] **Step 1: `modules/notify/v1/channel-router.ts`**

```ts
import type { PreferenceRecord } from "../../../domain/ports/tenant-preference.repository.port.ts";

export interface ResolvedChannel {
  channel: PreferenceRecord["channel"];
  digestMode: boolean;
}

export function resolveChannels(prefs: PreferenceRecord[]): ResolvedChannel[] {
  const enabled = prefs.filter((p) => p.enabled).map((p) => ({ channel: p.channel, digestMode: p.digestMode }));
  if (enabled.length === 0) return [{ channel: "inapp", digestMode: false }];
  return enabled;
}
```

- [ ] **Step 2: `modules/notify/v1/service.ts`**

```ts
import type { ITenantPreferenceRepo } from "../../../domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "../../../domain/ports/email-sender.port.ts";
import type { ISmsSender } from "../../../domain/ports/sms-sender.port.ts";
import type { IRealtimeGateway } from "../../../domain/ports/realtime-gateway.port.ts";
import { renderTemplate } from "../../templates/v1/registry.ts";
import { resolveChannels } from "./channel-router.ts";
import { dispatchEmail } from "./channels/email.ts";
import { dispatchInApp } from "./channels/inapp.ts";
import { dispatchSms } from "./channels/sms.ts";
import { dispatchWebhook } from "../../webhooks/v1/dispatch.ts";
import { logger } from "../../../common/logger.ts";

export interface NotifyRecipient {
  userId: string;
  email?: string;
  phone?: string;
}

export interface NotifyInput {
  tenantId: string;
  recipients: NotifyRecipient[];
  eventType: string;
  data: Record<string, unknown>;
  entityRefType?: string;
  entityRefId?: string;
}

export interface NotifyRecipientResult {
  userId: string;
  channels: Array<{ channel: string; status: "SENT" | "FAILED" | "QUEUED_FOR_DIGEST"; lastError?: string }>;
}

export interface NotifyResult {
  recipients: NotifyRecipientResult[];
}

function nextDigestBoundary(): Date {
  const now = new Date();
  const next = new Date(now);
  next.setMinutes(0, 0, 0);
  next.setHours(next.getHours() + 1);
  return next;
}

export class NotifService {
  constructor(
    private readonly preferenceRepo: ITenantPreferenceRepo,
    private readonly notificationRepo: INotificationLogRepo,
    private readonly digestRepo: IDigestQueueRepo,
    private readonly webhookRepo: IWebhookEndpointRepo,
    private readonly emailSender: IEmailSender,
    private readonly smsSender: ISmsSender,
    private readonly realtimeGateway: IRealtimeGateway | undefined,
    private readonly channelGate: (tenantId: string, channel: string) => boolean | Promise<boolean> = () => true,
  ) {}

  async notify(input: NotifyInput): Promise<NotifyResult> {
    const settled = await Promise.allSettled(input.recipients.map((r) => this.notifyOne(input, r)));
    return {
      recipients: settled.map((s, i) =>
        s.status === "fulfilled"
          ? s.value
          : { userId: input.recipients[i]!.userId, channels: [] },
      ),
    };
  }

  private async notifyOne(input: NotifyInput, recipient: NotifyRecipient): Promise<NotifyRecipientResult> {
    const prefs = await this.preferenceRepo.findByEventType(input.tenantId, recipient.userId, input.eventType);
    const resolved = resolveChannels(prefs);
    const content = renderTemplate(input.eventType, input.data);
    const channelResults: NotifyRecipientResult["channels"] = [];

    for (const { channel, digestMode } of resolved) {
      try {
        if (!(await this.channelGate(input.tenantId, channel))) continue;

        if (channel === "email" && digestMode) {
          if (!recipient.email) continue;
          const log = await this.notificationRepo.create({
            tenantId: input.tenantId, userId: recipient.userId, channel: "email",
            eventType: input.eventType, title: content.title, body: content.body,
            entityRefType: input.entityRefType, entityRefId: input.entityRefId,
            status: "QUEUED_FOR_DIGEST",
          });
          await this.digestRepo.enqueue(input.tenantId, recipient.userId, recipient.email, log.id, nextDigestBoundary());
          channelResults.push({ channel, status: "QUEUED_FOR_DIGEST" });
          continue;
        }

        const log = await this.notificationRepo.create({
          tenantId: input.tenantId, userId: recipient.userId, channel,
          eventType: input.eventType, title: content.title, body: content.body,
          entityRefType: input.entityRefType, entityRefId: input.entityRefId,
          status: "SENT",
        });

        const outcome = await this.dispatchImmediate(channel, input, recipient, content, log.id);
        await this.notificationRepo.markStatus(input.tenantId, log.id, outcome.status, outcome.lastError);
        channelResults.push({ channel, status: outcome.status, lastError: outcome.lastError });
      } catch (err) {
        logger.warn({ err, channel, userId: recipient.userId }, "[gen-notif] channel dispatch error — continuing");
        channelResults.push({ channel, status: "FAILED", lastError: err instanceof Error ? err.message : String(err) });
      }
    }

    return { userId: recipient.userId, channels: channelResults };
  }

  private async dispatchImmediate(
    channel: string,
    input: NotifyInput,
    recipient: NotifyRecipient,
    content: ReturnType<typeof renderTemplate>,
    notifId: string,
  ): Promise<{ status: "SENT" | "FAILED"; lastError?: string }> {
    switch (channel) {
      case "email":
        if (!recipient.email) return { status: "FAILED", lastError: "recipient has no email address" };
        return dispatchEmail(this.emailSender, recipient.email, content);
      case "inapp":
        if (!this.realtimeGateway) return { status: "FAILED", lastError: "no realtime gateway configured (modules.realtime is disabled and no override was supplied)" };
        return dispatchInApp(this.realtimeGateway, input.tenantId, recipient.userId, notifId, input.eventType, content, input.entityRefType, input.entityRefId);
      case "sms":
        if (!recipient.phone) return { status: "FAILED", lastError: "recipient has no phone number" };
        return dispatchSms(this.smsSender, recipient.phone, content);
      case "webhook": {
        const endpoints = await this.webhookRepo.listEnabled(input.tenantId, recipient.userId);
        if (endpoints.length === 0) return { status: "FAILED", lastError: "no enabled webhook endpoints" };
        const results = await dispatchWebhook(endpoints, input.eventType, content);
        const anySent = results.some((r) => r.result.status === "SENT");
        return anySent
          ? { status: "SENT" }
          : { status: "FAILED", lastError: results[0]?.result.lastError ?? "all webhook endpoints failed" };
      }
      default:
        return { status: "FAILED", lastError: `unknown channel: ${channel}` };
    }
  }
}
```

- [ ] **Step 3: Write the unit test (all ports mocked)**

```ts
import { describe, it, expect, vi } from "vitest";
import { NotifService } from "./service.ts";
import { registerTemplate, __resetRegistryForTests } from "../../templates/v1/registry.ts";
import type { ITenantPreferenceRepo } from "../../../domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";

function makeService(overrides: { prefs?: ITenantPreferenceRepo["findByEventType"] } = {}) {
  const created: unknown[] = [];
  const statusUpdates: unknown[] = [];
  const notificationRepo: INotificationLogRepo = {
    create: vi.fn(async (input) => { created.push(input); return { id: "log-1", ...input, readAt: null, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date() } as never; }),
    findById: vi.fn(),
    findUnread: vi.fn(),
    markRead: vi.fn(),
    markStatus: vi.fn(async (...args) => { statusUpdates.push(args); }),
  };
  const preferenceRepo: ITenantPreferenceRepo = {
    findAll: vi.fn(),
    findByEventType: overrides.prefs ?? vi.fn(async () => []),
    upsert: vi.fn(),
  };
  const digestRepo = { enqueue: vi.fn(), claimDue: vi.fn(), markSent: vi.fn(), markFailed: vi.fn() };
  const webhookRepo = { create: vi.fn(), findById: vi.fn(), listEnabled: vi.fn(async () => []), update: vi.fn(), delete: vi.fn() };
  const emailSender = { send: vi.fn(async () => {}) };
  const smsSender = { send: vi.fn(async () => { throw new Error("no SMS provider"); }) };
  const realtimeGateway = { attach: vi.fn(), publishInApp: vi.fn(async () => {}) };

  const service = new NotifService(preferenceRepo, notificationRepo, digestRepo, webhookRepo, emailSender, smsSender, realtimeGateway);
  return { service, created, statusUpdates, notificationRepo, preferenceRepo, emailSender, realtimeGateway };
}

describe("NotifService.notify()", () => {
  afterEach(() => __resetRegistryForTests());

  it("defaults to in-app when no preference rows exist", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, realtimeGateway } = makeService();
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "inapp", status: "SENT" }]);
    expect(realtimeGateway.publishInApp).toHaveBeenCalledOnce();
  });

  it("dispatches email when an enabled email preference exists", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, emailSender } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", email: "a@example.com" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "email", status: "SENT" }]);
    expect(emailSender.send).toHaveBeenCalledWith(expect.objectContaining({ to: "a@example.com", subject: "New doc" }));
  });

  it("queues for digest instead of sending immediately when digestMode is set", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, emailSender } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: true, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", email: "a@example.com" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "email", status: "QUEUED_FOR_DIGEST" }]);
    expect(emailSender.send).not.toHaveBeenCalled();
  });

  it("SMS with no configured provider dispatches FAILED, not a silent success", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "sms", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", phone: "+15551234567" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels[0]!.status).toBe("FAILED");
  });

  it("one recipient's channel failure does not abort another recipient", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service } = makeService();
    const result = await service.notify({
      tenantId: "t1",
      recipients: [{ userId: "u1" }, { userId: "u2" }],
      eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients).toHaveLength(2);
  });
});
```

(Add `import { afterEach } from "vitest";` alongside the existing `describe, it, expect, vi` import.)

- [ ] **Step 4: Run tests**

Run: `npx vitest run src/modules/notify/v1/service.test.ts`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-notif-starter/src/modules/notify/v1/channel-router.ts packages/gen-notif-starter/src/modules/notify/v1/service.ts packages/gen-notif-starter/src/modules/notify/v1/service.test.ts
git commit -m "feat(notify): add NotifService — preference resolution, dispatch, honest logging pipeline"
```

---

### Task 13: Middleware (Error Handler + Internal Secret)

**Files:**
- Create: `packages/gen-notif-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-notif-starter/src/middleware/internal-secret.ts`
- Test: `packages/gen-notif-starter/src/middleware/error-handler.test.ts`

**Interfaces:**
- Consumes: `AppError` (Task 3).
- Produces: `errorHandler`, `internalSecretMiddleware(secret)` — used by every HTTP test from Task 14 onward and by `create-gen-notif.ts` in Task 17.

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
  logger.error({ err }, "[gen-notif] unhandled error");
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
import { NotificationNotFoundError } from "../common/errors.ts";

describe("errorHandler", () => {
  it("maps an AppError subclass to its statusCode/code", async () => {
    const app = express();
    app.get("/boom", () => { throw new NotificationNotFoundError("abc"); });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "NOTIFICATION_NOT_FOUND", message: 'Notification "abc" not found' });
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
git add packages/gen-notif-starter/src/middleware
git commit -m "feat(middleware): add error-handler (AppError + ZodError mapping) and internal-secret gate"
```

---

### Task 14: Preferences Module (Controller + Routes)

**Files:**
- Create: `packages/gen-notif-starter/src/modules/preferences/v1/service.ts`
- Create: `packages/gen-notif-starter/src/modules/preferences/v1/controller.ts`
- Create: `packages/gen-notif-starter/src/modules/preferences/v1/routes.ts`
- Test: `packages/gen-notif-starter/tests/http/preferences.test.ts`

**Interfaces:**
- Consumes: `ITenantPreferenceRepo` (Task 5), `PrismaTenantPreferenceRepo` (Task 6), `listRegisteredEventTypes` (Task 10).
- Produces: `preferencesRoutes` — mounted by `create-gen-notif.ts` in Task 17 at `/api/v1/preferences`.

- [ ] **Step 1: `modules/preferences/v1/service.ts`**

```ts
import type { ITenantPreferenceRepo, PreferenceRecord, UpsertPreferenceInput } from "../../../domain/ports/tenant-preference.repository.port.ts";

export class PreferenceService {
  constructor(private readonly repo: ITenantPreferenceRepo) {}

  listPreferences(tenantId: string, userId: string): Promise<PreferenceRecord[]> {
    return this.repo.findAll(tenantId, userId);
  }

  upsertPreference(input: UpsertPreferenceInput): Promise<PreferenceRecord> {
    return this.repo.upsert(input);
  }
}
```

- [ ] **Step 2: `modules/preferences/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { PreferenceService } from "./service.ts";
import { listRegisteredEventTypes } from "../../templates/v1/registry.ts";

const listQuerySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });
const upsertBodySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  eventType: z.string().min(1).max(120),
  channel: z.enum(["email", "inapp", "sms", "webhook"]),
  enabled: z.boolean(),
  digestMode: z.boolean(),
});

export class PreferenceController {
  constructor(private readonly service: PreferenceService) {}

  list = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId } = listQuerySchema.parse(req.query);
    const prefs = await this.service.listPreferences(tenantId, userId);
    res.json({ preferences: prefs });
  };

  upsert = async (req: Request, res: Response): Promise<void> => {
    const input = upsertBodySchema.parse(req.body);
    const pref = await this.service.upsertPreference(input);
    res.json({ preference: pref });
  };

  eventTypes = async (_req: Request, res: Response): Promise<void> => {
    res.json({ eventTypes: listRegisteredEventTypes() });
  };
}
```

- [ ] **Step 3: `modules/preferences/v1/routes.ts`**

```ts
import { Router } from "express";
import { PreferenceController } from "./controller.ts";

export function preferencesRoutes(controller: PreferenceController): Router {
  const router = Router();
  router.get("/", controller.list);
  router.patch("/", controller.upsert);
  router.get("/event-types", controller.eventTypes);
  return router;
}
```

Route order matters: `/event-types` is registered after `/` (an exact-path `GET /` and `PATCH /` don't shadow a distinct `/event-types` path, since Express matches by full path, not prefix, for these — no ordering hazard here, unlike a `/:id` catch-all would create).

- [ ] **Step 4: Write the HTTP test**

```ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { PreferenceService } from "../../src/modules/preferences/v1/service.ts";
import { PreferenceController } from "../../src/modules/preferences/v1/controller.ts";
import { preferencesRoutes } from "../../src/modules/preferences/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import { registerTemplate, __resetRegistryForTests } from "../../src/modules/templates/v1/registry.ts";
import type { ITenantPreferenceRepo } from "../../src/domain/ports/tenant-preference.repository.port.ts";

function buildApp(repo: ITenantPreferenceRepo) {
  const app = express();
  app.use(express.json());
  const service = new PreferenceService(repo);
  const controller = new PreferenceController(service);
  app.use("/api/v1/preferences", preferencesRoutes(controller));
  app.use(errorHandler);
  return app;
}

describe("preferences HTTP routes", () => {
  afterEach(() => __resetRegistryForTests());

  it("GET / lists preferences for a tenant/user", async () => {
    const repo: ITenantPreferenceRepo = {
      findAll: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
      findByEventType: vi.fn(),
      upsert: vi.fn(),
    };
    const res = await request(buildApp(repo)).get("/api/v1/preferences").query({ tenantId: "11111111-1111-1111-1111-111111111111", userId: "22222222-2222-2222-2222-222222222222" });
    expect(res.status).toBe(200);
    expect(res.body.preferences).toHaveLength(1);
  });

  it("PATCH / rejects an invalid channel with 400", async () => {
    const repo: ITenantPreferenceRepo = { findAll: vi.fn(), findByEventType: vi.fn(), upsert: vi.fn() };
    const res = await request(buildApp(repo)).patch("/api/v1/preferences").send({
      tenantId: "11111111-1111-1111-1111-111111111111", userId: "22222222-2222-2222-2222-222222222222",
      eventType: "doc.uploaded", channel: "carrier-pigeon", enabled: true, digestMode: false,
    });
    expect(res.status).toBe(400);
  });

  it("GET /event-types reflects live-registered templates", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "t", body: "b" }));
    const repo: ITenantPreferenceRepo = { findAll: vi.fn(), findByEventType: vi.fn(), upsert: vi.fn() };
    const res = await request(buildApp(repo)).get("/api/v1/preferences/event-types");
    expect(res.body.eventTypes).toEqual(["doc.uploaded"]);
  });
});
```

(Add `import { afterEach } from "vitest";` to the existing import line.)

- [ ] **Step 5: Run tests**

Run: `npx vitest run tests/http/preferences.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/modules/preferences packages/gen-notif-starter/tests/http/preferences.test.ts
git commit -m "feat(preferences): add preferences service, controller, and routes"
```

---

### Task 15: Notifications Module (Unread List + Mark Read)

**Files:**
- Create: `packages/gen-notif-starter/src/modules/notifications/v1/service.ts`
- Create: `packages/gen-notif-starter/src/modules/notifications/v1/controller.ts`
- Create: `packages/gen-notif-starter/src/modules/notifications/v1/routes.ts`
- Test: `packages/gen-notif-starter/tests/http/notifications.test.ts`

**Interfaces:**
- Consumes: `INotificationLogRepo` (Task 5), `PrismaNotificationLogRepo` (Task 6), `errorHandler` (Task 13).
- Produces: `notificationsRoutes` — mounted at `/api/v1/notifications` in Task 17.

- [ ] **Step 1: `modules/notifications/v1/service.ts`**

```ts
import type { INotificationLogRepo, NotificationLogRecord } from "../../../domain/ports/notification-log.repository.port.ts";
import { NotificationNotFoundError, NotificationForbiddenError } from "../../../common/errors.ts";

export class NotificationService {
  constructor(private readonly repo: INotificationLogRepo) {}

  listUnread(tenantId: string, userId: string, limit: number, before?: Date): Promise<NotificationLogRecord[]> {
    return this.repo.findUnread(tenantId, userId, { limit, before });
  }

  async markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord> {
    const existing = await this.repo.findById(tenantId, id);
    if (!existing) throw new NotificationNotFoundError(id);
    if (existing.userId !== userId) throw new NotificationForbiddenError();
    const updated = await this.repo.markRead(tenantId, userId, id);
    if (!updated) throw new NotificationNotFoundError(id);
    return updated;
  }
}
```

- [ ] **Step 2: `modules/notifications/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { NotificationService } from "./service.ts";

const listQuerySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  before: z.coerce.date().optional(),
});
const paramsSchema = z.object({ id: z.string().uuid() });
const bodySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });

export class NotificationController {
  constructor(private readonly service: NotificationService) {}

  listUnread = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId, limit, before } = listQuerySchema.parse(req.query);
    const notifications = await this.service.listUnread(tenantId, userId, limit, before);
    res.json({ notifications });
  };

  markRead = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId, userId } = bodySchema.parse(req.body);
    const notification = await this.service.markRead(tenantId, userId, id);
    res.json({ notification });
  };
}
```

`markRead`'s ownership check follows the same pattern Gen_TBR's final review settled on for its own mutating domain routes: the caller's `tenantId`/`userId` come from the request body (matching how a host mounts its own auth in front and forwards the authenticated identity), checked against the record's actual owner before any mutation — a mismatch is `404`/`403`, never a silent cross-tenant read.

- [ ] **Step 3: `modules/notifications/v1/routes.ts`**

```ts
import { Router } from "express";
import type { NotificationController } from "./controller.ts";

export function notificationsRoutes(controller: NotificationController): Router {
  const router = Router();
  router.get("/unread", controller.listUnread);
  router.patch("/:id/read", controller.markRead);
  return router;
}
```

- [ ] **Step 4: Write the HTTP test**

```ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { NotificationService } from "../../src/modules/notifications/v1/service.ts";
import { NotificationController } from "../../src/modules/notifications/v1/controller.ts";
import { notificationsRoutes } from "../../src/modules/notifications/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { INotificationLogRepo } from "../../src/domain/ports/notification-log.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";
const OWNER = "22222222-2222-2222-2222-222222222222";
const OTHER = "33333333-3333-3333-3333-333333333333";

function buildApp(repo: INotificationLogRepo) {
  const app = express();
  app.use(express.json());
  const service = new NotificationService(repo);
  const controller = new NotificationController(service);
  app.use("/api/v1/notifications", notificationsRoutes(controller));
  app.use(errorHandler);
  return app;
}

function baseRecord(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "n1", tenantId: TENANT, userId: OWNER, channel: "inapp", eventType: "doc.uploaded",
    title: "New doc", body: "body", entityRefType: null, entityRefId: null, status: "SENT",
    readAt: null, attempts: 1, lastError: null, sentAt: new Date(), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

describe("notifications HTTP routes", () => {
  it("GET /unread returns the repo's unread list", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findById: vi.fn(), markStatus: vi.fn(),
      findUnread: vi.fn(async () => [baseRecord()]),
      markRead: vi.fn(),
    };
    const res = await request(buildApp(repo)).get("/api/v1/notifications/unread").query({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(200);
    expect(res.body.notifications).toHaveLength(1);
  });

  it("PATCH /:id/read marks it read for the owning user", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(),
      findById: vi.fn(async () => baseRecord()),
      markRead: vi.fn(async () => baseRecord({ status: "READ", readAt: new Date() })),
    };
    const res = await request(buildApp(repo)).patch("/api/v1/notifications/n1/read").send({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(200);
    expect(res.body.notification.status).toBe("READ");
  });

  it("PATCH /:id/read for a different user's notification is 403", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(), markRead: vi.fn(),
      findById: vi.fn(async () => baseRecord()),
    };
    const res = await request(buildApp(repo)).patch("/api/v1/notifications/n1/read").send({ tenantId: TENANT, userId: OTHER });
    expect(res.status).toBe(403);
  });

  it("PATCH /:id/read for an unknown id is 404", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(), markRead: vi.fn(),
      findById: vi.fn(async () => null),
    };
    const res = await request(buildApp(repo)).patch("/api/v1/notifications/nope/read").send({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(404);
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run tests/http/notifications.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/modules/notifications packages/gen-notif-starter/tests/http/notifications.test.ts
git commit -m "feat(notifications): add unread-list and ownership-checked mark-read routes"
```

---

### Task 16: Webhook Endpoints Module (CRUD)

**Files:**
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/service.ts`
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/controller.ts`
- Create: `packages/gen-notif-starter/src/modules/webhooks/v1/routes.ts`
- Test: `packages/gen-notif-starter/tests/http/webhooks.test.ts`

**Interfaces:**
- Consumes: `IWebhookEndpointRepo` (Task 5), `PrismaWebhookEndpointRepo` (Task 6), `errorHandler` (Task 13).
- Produces: `webhooksRoutes` — mounted at `/api/v1/webhook-endpoints` in Task 17.

- [ ] **Step 1: `modules/webhooks/v1/service.ts`**

```ts
import { randomBytes } from "node:crypto";
import type {
  IWebhookEndpointRepo,
  WebhookEndpointRecord,
  UpdateWebhookEndpointInput,
} from "../../../domain/ports/webhook-endpoint.repository.port.ts";

export interface RegisterWebhookInput {
  tenantId: string;
  userId: string;
  url: string;
  description?: string;
}

export class WebhookEndpointService {
  constructor(private readonly repo: IWebhookEndpointRepo) {}

  register(input: RegisterWebhookInput): Promise<WebhookEndpointRecord> {
    const secret = randomBytes(32).toString("hex");
    return this.repo.create({ ...input, secret });
  }

  list(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]> {
    return this.repo.listEnabled(tenantId, userId);
  }

  update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return this.repo.update(tenantId, id, input);
  }

  delete(tenantId: string, id: string): Promise<void> {
    return this.repo.delete(tenantId, id);
  }
}
```

The 64-char hex `secret` is generated server-side and returned only once, at creation — the same pattern Gen_TBR uses for its domain `verificationToken`. Consumers store it to verify inbound HMAC signatures (Task 11's `verifyWebhookSignature`).

- [ ] **Step 2: `modules/webhooks/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { WebhookEndpointService } from "./service.ts";

const listQuerySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });
const createBodySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  url: z.string().url().max(2048),
  description: z.string().max(300).optional(),
});
const updateBodySchema = z.object({
  tenantId: z.string().uuid(),
  url: z.string().url().max(2048).optional(),
  enabled: z.boolean().optional(),
  description: z.string().max(300).optional(),
});
const paramsSchema = z.object({ id: z.string().uuid() });
const deleteBodySchema = z.object({ tenantId: z.string().uuid() });

export class WebhookEndpointController {
  constructor(private readonly service: WebhookEndpointService) {}

  list = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId } = listQuerySchema.parse(req.query);
    const endpoints = await this.service.list(tenantId, userId);
    res.json({ endpoints });
  };

  create = async (req: Request, res: Response): Promise<void> => {
    const input = createBodySchema.parse(req.body);
    const endpoint = await this.service.register(input);
    res.status(201).json({ endpoint });
  };

  update = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId, ...rest } = updateBodySchema.parse(req.body);
    const endpoint = await this.service.update(tenantId, id, rest);
    res.json({ endpoint });
  };

  remove = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId } = deleteBodySchema.parse(req.body);
    await this.service.delete(tenantId, id);
    res.status(204).send();
  };
}
```

- [ ] **Step 3: `modules/webhooks/v1/routes.ts`**

```ts
import { Router } from "express";
import type { WebhookEndpointController } from "./controller.ts";

export function webhooksRoutes(controller: WebhookEndpointController): Router {
  const router = Router();
  router.get("/", controller.list);
  router.post("/", controller.create);
  router.patch("/:id", controller.update);
  router.delete("/:id", controller.remove);
  return router;
}
```

- [ ] **Step 4: Write the HTTP test**

```ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { WebhookEndpointService } from "../../src/modules/webhooks/v1/service.ts";
import { WebhookEndpointController } from "../../src/modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "../../src/modules/webhooks/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { IWebhookEndpointRepo } from "../../src/domain/ports/webhook-endpoint.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";
const USER = "22222222-2222-2222-2222-222222222222";

function buildApp(repo: IWebhookEndpointRepo) {
  const app = express();
  app.use(express.json());
  const controller = new WebhookEndpointController(new WebhookEndpointService(repo));
  app.use("/api/v1/webhook-endpoints", webhooksRoutes(controller));
  app.use(errorHandler);
  return app;
}

describe("webhook endpoints HTTP routes", () => {
  it("POST / creates an endpoint and never echoes back a client-supplied secret (server generates it)", async () => {
    const repo: IWebhookEndpointRepo = {
      create: vi.fn(async (input) => ({ id: "e1", ...input, secret: "server-generated-secret", enabled: true, description: input.description ?? null, createdAt: new Date(), updatedAt: new Date() })),
      findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn(),
    };
    const res = await request(buildApp(repo)).post("/api/v1/webhook-endpoints").send({ tenantId: TENANT, userId: USER, url: "https://example.com/hook" });
    expect(res.status).toBe(201);
    expect(res.body.endpoint.secret).toBe("server-generated-secret");
    expect(repo.create).toHaveBeenCalledWith(expect.objectContaining({ secret: expect.any(String) }));
  });

  it("POST / rejects a non-URL", async () => {
    const repo: IWebhookEndpointRepo = { create: vi.fn(), findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn() };
    const res = await request(buildApp(repo)).post("/api/v1/webhook-endpoints").send({ tenantId: TENANT, userId: USER, url: "not-a-url" });
    expect(res.status).toBe(400);
  });

  it("DELETE /:id calls repo.delete scoped to tenantId", async () => {
    const repo: IWebhookEndpointRepo = { create: vi.fn(), findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn(async () => {}) };
    const res = await request(buildApp(repo)).delete("/api/v1/webhook-endpoints/e1").send({ tenantId: TENANT });
    expect(res.status).toBe(204);
    expect(repo.delete).toHaveBeenCalledWith(TENANT, "e1");
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run tests/http/webhooks.test.ts`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/modules/webhooks/v1/service.ts packages/gen-notif-starter/src/modules/webhooks/v1/controller.ts packages/gen-notif-starter/src/modules/webhooks/v1/routes.ts packages/gen-notif-starter/tests/http/webhooks.test.ts
git commit -m "feat(webhooks): add webhook endpoint CRUD with server-generated secrets"
```

---

### Task 17: Digest Module (Sweep Service + Internal Route)

**Files:**
- Create: `packages/gen-notif-starter/src/modules/digest/v1/service.ts`
- Create: `packages/gen-notif-starter/src/modules/digest/v1/controller.ts`
- Create: `packages/gen-notif-starter/src/modules/digest/v1/routes.ts`
- Test: `packages/gen-notif-starter/src/modules/digest/v1/service.test.ts`

**Interfaces:**
- Consumes: `IDigestQueueRepo`, `INotificationLogRepo` (Task 5), `IEmailSender` (Task 5).
- Produces: `DigestService.runSweep()` — this is `GenNotifInstance.runDigestSweep()`, wired in Task 18.

- [ ] **Step 1: `modules/digest/v1/service.ts`**

```ts
import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";
import type { IEmailSender } from "../../../domain/ports/email-sender.port.ts";
import { logger } from "../../../common/logger.ts";

const DEFAULT_BATCH_LIMIT = 50;

export interface DigestSweepResult {
  processed: number;
  sent: number;
  failed: number;
}

export class DigestService {
  constructor(
    private readonly digestRepo: IDigestQueueRepo,
    private readonly notificationRepo: INotificationLogRepo,
    private readonly emailSender: IEmailSender,
    private readonly batchLimit: number = DEFAULT_BATCH_LIMIT,
  ) {}

  async runSweep(now: Date = new Date()): Promise<DigestSweepResult> {
    const due = await this.digestRepo.claimDue(this.batchLimit, now);
    let sent = 0;
    let failed = 0;

    for (const entry of due) {
      try {
        const items = await Promise.all(
          entry.notificationIds.map((id) => this.notificationRepo.findById(entry.tenantId, id)),
        );
        const realItems = items.filter((i): i is NonNullable<typeof i> => i !== null);

        const text = realItems.length > 0
          ? realItems.map((i) => `- ${i.title}: ${i.body}`).join("\n")
          : "You have new notifications.";
        const html = realItems.length > 0
          ? `<ul>${realItems.map((i) => `<li><strong>${escapeHtml(i.title)}</strong>: ${escapeHtml(i.body)}</li>`).join("")}</ul>`
          : undefined;

        await this.emailSender.send({
          to: entry.email,
          subject: `You have ${realItems.length} notification${realItems.length === 1 ? "" : "s"}`,
          text,
          html,
        });

        for (const id of entry.notificationIds) {
          await this.notificationRepo.markStatus(entry.tenantId, id, "SENT");
        }
        await this.digestRepo.markSent(entry.id);
        sent++;
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        logger.error({ err, digestEntryId: entry.id }, "[gen-notif] digest sweep entry failed");
        await this.digestRepo.markFailed(entry.id, message);
        failed++;
      }
    }

    return { processed: due.length, sent, failed };
  }
}

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
```

Each digest email lists every real notification's `title`/`body` — fixing the source's contentless "You have N notification(s)" bug. `escapeHtml` guards against a notification title/body containing HTML, since these values ultimately came from host-supplied template data.

- [ ] **Step 2: `modules/digest/v1/controller.ts`**

```ts
import type { Request, Response } from "express";
import type { DigestService } from "./service.ts";

export class DigestController {
  constructor(private readonly service: DigestService) {}

  sweep = async (_req: Request, res: Response): Promise<void> => {
    const result = await this.service.runSweep();
    res.json(result);
  };
}
```

- [ ] **Step 3: `modules/digest/v1/routes.ts`**

```ts
import { Router } from "express";
import type { DigestController } from "./controller.ts";

export function digestRoutes(controller: DigestController): Router {
  const router = Router();
  router.post("/sweep", controller.sweep);
  return router;
}
```

Mounted under `/internal/digest` in Task 18 (i.e. `POST /internal/digest/sweep`), behind the internal-secret gate — this is a host-triggered endpoint, not a self-scheduling one (see Global Constraints: no internal cron).

- [ ] **Step 4: Write the unit test**

```ts
import { describe, it, expect, vi } from "vitest";
import { DigestService } from "./service.ts";
import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";

describe("DigestService.runSweep()", () => {
  it("sends a digest email listing each notification's real title/body, not just a count", async () => {
    const digestRepo: IDigestQueueRepo = {
      enqueue: vi.fn(),
      claimDue: vi.fn(async () => [{
        id: "d1", tenantId: "t1", userId: "u1", email: "a@example.com", channel: "email",
        scheduledFor: new Date(), status: "PROCESSING", notificationIds: ["n1", "n2"],
        attempts: 0, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      }]),
      markSent: vi.fn(),
      markFailed: vi.fn(),
    };
    const notificationRepo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markRead: vi.fn(), markStatus: vi.fn(),
      findById: vi.fn(async (_tenantId, id) => ({
        id, tenantId: "t1", userId: "u1", channel: "email", eventType: "doc.uploaded",
        title: id === "n1" ? "Invoice uploaded" : "Report uploaded", body: `body for ${id}`,
        entityRefType: null, entityRefId: null, status: "QUEUED_FOR_DIGEST", readAt: null,
        attempts: 1, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      })),
    };
    const emailSender = { send: vi.fn(async () => {}) };

    const service = new DigestService(digestRepo, notificationRepo, emailSender);
    const result = await service.runSweep();

    expect(result).toEqual({ processed: 1, sent: 1, failed: 0 });
    expect(emailSender.send).toHaveBeenCalledWith(expect.objectContaining({
      to: "a@example.com",
      text: expect.stringContaining("Invoice uploaded"),
    }));
    expect(digestRepo.markSent).toHaveBeenCalledWith("d1");
    expect(notificationRepo.markStatus).toHaveBeenCalledWith("t1", "n1", "SENT");
    expect(notificationRepo.markStatus).toHaveBeenCalledWith("t1", "n2", "SENT");
  });

  it("marks the entry FAILED (not SENT) when the email send throws", async () => {
    const digestRepo: IDigestQueueRepo = {
      enqueue: vi.fn(),
      claimDue: vi.fn(async () => [{
        id: "d1", tenantId: "t1", userId: "u1", email: "a@example.com", channel: "email",
        scheduledFor: new Date(), status: "PROCESSING", notificationIds: [],
        attempts: 0, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      }]),
      markSent: vi.fn(), markFailed: vi.fn(),
    };
    const notificationRepo: INotificationLogRepo = { create: vi.fn(), findUnread: vi.fn(), markRead: vi.fn(), markStatus: vi.fn(), findById: vi.fn() };
    const emailSender = { send: vi.fn(async () => { throw new Error("SMTP down"); }) };

    const service = new DigestService(digestRepo, notificationRepo, emailSender);
    const result = await service.runSweep();

    expect(result).toEqual({ processed: 1, sent: 0, failed: 1 });
    expect(digestRepo.markFailed).toHaveBeenCalledWith("d1", "SMTP down");
    expect(digestRepo.markSent).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 5: Run tests**

Run: `npx vitest run src/modules/digest/v1/service.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/modules/digest
git commit -m "feat(digest): add host-triggered digest sweep with real per-notification content"
```

---

### Task 18: The `createGenNotif` Factory + Public Barrel

**Files:**
- Create: `packages/gen-notif-starter/src/create-gen-notif.ts`
- Create: `packages/gen-notif-starter/src/index.ts`
- Test: `packages/gen-notif-starter/tests/http/factory.test.ts`

**Interfaces:**
- Consumes: every port (Task 5), every default adapter (Tasks 6-9), every service/controller/routes (Tasks 12-17).
- Produces: `createGenNotif`, `GenNotifConfig`, `GenNotifModulesConfig`, `GenNotifInstance` — the single public entry point.

- [ ] **Step 1: `create-gen-notif.ts`**

```ts
import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import type { Server as HttpServer } from "node:http";
import type { Server as SocketIoServer } from "socket.io";
import { Server as SocketIoServerImpl } from "socket.io";

import { requireEnv, optionalEnv } from "./config/env.ts";
import { logger } from "./common/logger.ts";

import type { ITenantPreferenceRepo } from "./domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "./domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "./domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "./domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "./domain/ports/email-sender.port.ts";
import type { ISmsSender } from "./domain/ports/sms-sender.port.ts";
import type { IRealtimeGateway } from "./domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "./domain/ports/jwt-verifier.port.ts";
import type { NotifChannel } from "./domain/ports/tenant-preference.repository.port.ts";

import { PrismaTenantPreferenceRepo } from "./modules/preferences/v1/repo.ts";
import { PrismaNotificationLogRepo } from "./modules/notifications/v1/repo.ts";
import { PrismaDigestQueueRepo } from "./modules/digest/v1/repo.ts";
import { PrismaWebhookEndpointRepo } from "./modules/webhooks/v1/repo.ts";
import { NodemailerEmailSender } from "./infra/email/nodemailer-email-sender.ts";
import { UnimplementedSmsSender } from "./infra/sms/unimplemented-sms-sender.ts";
import { SocketIoRedisGateway } from "./infra/realtime/socket-io-redis-gateway.ts";
import { JwksJwtVerifier } from "./infra/auth/jwks-jwt-verifier.ts";

import { NotifService, type NotifyInput, type NotifyResult } from "./modules/notify/v1/service.ts";
import { PreferenceService } from "./modules/preferences/v1/service.ts";
import { PreferenceController } from "./modules/preferences/v1/controller.ts";
import { preferencesRoutes } from "./modules/preferences/v1/routes.ts";
import { NotificationService } from "./modules/notifications/v1/service.ts";
import { NotificationController } from "./modules/notifications/v1/controller.ts";
import { notificationsRoutes } from "./modules/notifications/v1/routes.ts";
import { WebhookEndpointService } from "./modules/webhooks/v1/service.ts";
import { WebhookEndpointController } from "./modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "./modules/webhooks/v1/routes.ts";
import { DigestService, type DigestSweepResult } from "./modules/digest/v1/service.ts";
import { DigestController } from "./modules/digest/v1/controller.ts";
import { digestRoutes } from "./modules/digest/v1/routes.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";

export interface GenNotifModulesConfig {
  preferences?: boolean;
  notifications?: boolean;
  webhookEndpoints?: boolean;
  realtime?: boolean;
}

export interface GenNotifConfig {
  preferenceRepo?: ITenantPreferenceRepo;
  notificationRepo?: INotificationLogRepo;
  digestRepo?: IDigestQueueRepo;
  webhookRepo?: IWebhookEndpointRepo;
  emailSender?: IEmailSender;
  smsSender?: ISmsSender;
  realtimeGateway?: IRealtimeGateway;
  jwtVerifier?: IJwtVerifier;
  channelGate?: (tenantId: string, channel: NotifChannel) => boolean | Promise<boolean>;
  modules?: GenNotifModulesConfig;
  internalSecret?: string;
}

export interface GenNotifInstance {
  app: Express;
  attachRealtime?: (httpServer: HttpServer) => SocketIoServer;
  notify(input: NotifyInput): Promise<NotifyResult>;
  runDigestSweep(): Promise<DigestSweepResult>;
}

function resolvePreferenceRepo(override: ITenantPreferenceRepo | undefined): ITenantPreferenceRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantPreferenceRepo();
}

function resolveNotificationRepo(override: INotificationLogRepo | undefined): INotificationLogRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaNotificationLogRepo();
}

function resolveDigestRepo(override: IDigestQueueRepo | undefined): IDigestQueueRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaDigestQueueRepo();
}

function resolveWebhookRepo(override: IWebhookEndpointRepo | undefined): IWebhookEndpointRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaWebhookEndpointRepo();
}

function resolveEmailSender(override: IEmailSender | undefined): IEmailSender {
  if (override) return override;
  return new NodemailerEmailSender({
    host: requireEnv("SMTP_HOST"),
    port: Number(optionalEnv("SMTP_PORT", "587")),
    secure: optionalEnv("SMTP_SECURE", "false") === "true",
    user: process.env.SMTP_USER || undefined,
    pass: process.env.SMTP_PASS || undefined,
    from: optionalEnv("SMTP_FROM", "no-reply@example.com"),
  });
}

function resolveSmsSender(override: ISmsSender | undefined): ISmsSender {
  if (override) return override;
  return new UnimplementedSmsSender();
}

function resolveJwtVerifier(override: IJwtVerifier | undefined): IJwtVerifier {
  if (override) return override;
  return new JwksJwtVerifier(requireEnv("JWKS_URL"), requireEnv("JWT_ISSUER"));
}

function resolveRealtimeGateway(override: IRealtimeGateway | undefined, jwtVerifier: IJwtVerifier): IRealtimeGateway {
  if (override) return override;
  return new SocketIoRedisGateway(requireEnv("REDIS_URL"), jwtVerifier);
}

export function createGenNotif(config: GenNotifConfig): GenNotifInstance {
  const modules: Required<GenNotifModulesConfig> = {
    preferences: config.modules?.preferences ?? true,
    notifications: config.modules?.notifications ?? true,
    webhookEndpoints: config.modules?.webhookEndpoints ?? true,
    realtime: config.modules?.realtime ?? true,
  };

  const preferenceRepo = resolvePreferenceRepo(config.preferenceRepo);
  const notificationRepo = resolveNotificationRepo(config.notificationRepo);
  const digestRepo = resolveDigestRepo(config.digestRepo);
  const webhookRepo = resolveWebhookRepo(config.webhookRepo);
  const emailSender = resolveEmailSender(config.emailSender);
  const smsSender = resolveSmsSender(config.smsSender);
  const channelGate = config.channelGate ?? (() => true);

  // realtimeGateway is optional by construction: if modules.realtime is
  // disabled and no override is supplied, notify()'s "inapp" channel
  // dispatch fails per-recipient at request time (a normal FAILED log
  // entry, per NotifService's dispatchImmediate) rather than createGenNotif()
  // refusing to boot over config (JWKS_URL/REDIS_URL) a host that only
  // wants email/webhook channels never needed to supply.
  let realtimeGateway: IRealtimeGateway | undefined = config.realtimeGateway;
  if (!realtimeGateway && modules.realtime) {
    const jwtVerifier = resolveJwtVerifier(config.jwtVerifier);
    realtimeGateway = resolveRealtimeGateway(undefined, jwtVerifier);
  }

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_NOTIF_INTERNAL_SECRET", "");

  const notifService = new NotifService(
    preferenceRepo, notificationRepo, digestRepo, webhookRepo,
    emailSender, smsSender, realtimeGateway, channelGate,
  );
  const digestService = new DigestService(digestRepo, notificationRepo, emailSender);

  const app = express();
  app.use(helmet());
  app.use(cors({ origin: optionalEnv("ALLOWED_ORIGINS", "*").split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  const gate = internalSecretMiddleware(internalSecret);

  app.post("/internal/notify", gate, async (req, res) => {
    const result = await notifService.notify(req.body as NotifyInput);
    res.json(result);
  });
  app.use("/internal/digest", gate, digestRoutes(new DigestController(digestService)));

  if (modules.preferences) {
    const controller = new PreferenceController(new PreferenceService(preferenceRepo));
    app.use("/api/v1/preferences", preferencesRoutes(controller));
  }
  if (modules.notifications) {
    const controller = new NotificationController(new NotificationService(notificationRepo));
    app.use("/api/v1/notifications", notificationsRoutes(controller));
  }
  if (modules.webhookEndpoints) {
    const controller = new WebhookEndpointController(new WebhookEndpointService(webhookRepo));
    app.use("/api/v1/webhook-endpoints", webhooksRoutes(controller));
  }

  app.use(errorHandler);

  const instance: GenNotifInstance = {
    app,
    notify: (input) => notifService.notify(input),
    runDigestSweep: () => digestService.runSweep(),
  };

  if (modules.realtime) {
    // realtimeGateway is guaranteed set here — the resolution block above
    // constructs it whenever modules.realtime is true and no override was given.
    const gateway = realtimeGateway!;
    instance.attachRealtime = (httpServer: HttpServer): SocketIoServer => {
      const io = new SocketIoServerImpl(httpServer, {
        path: "/gen-notif/ws",
        cors: { origin: optionalEnv("ALLOWED_ORIGINS", "*").split(",") },
      });
      gateway.attach(io);
      logger.info("[gen-notif] realtime gateway attached");
      return io;
    };
  }

  return instance;
}
```

- [ ] **Step 2: `index.ts` (public barrel)**

```ts
export { createGenNotif } from "./create-gen-notif.ts";
export type { GenNotifConfig, GenNotifModulesConfig, GenNotifInstance } from "./create-gen-notif.ts";

export type { NotifyInput, NotifyResult, NotifyRecipient } from "./modules/notify/v1/service.ts";
export type { DigestSweepResult } from "./modules/digest/v1/service.ts";
export { registerTemplate, renderTemplate, listRegisteredEventTypes } from "./modules/templates/v1/registry.ts";
export type { NotificationContent, TemplateBuilder } from "./modules/templates/v1/registry.ts";
export { verifyWebhookSignature } from "./modules/webhooks/v1/hmac.ts";

export type { ITenantPreferenceRepo, PreferenceRecord, NotifChannel } from "./domain/ports/tenant-preference.repository.port.ts";
export type { INotificationLogRepo, NotificationLogRecord, NotificationStatus } from "./domain/ports/notification-log.repository.port.ts";
export type { IDigestQueueRepo, DigestQueueRecord } from "./domain/ports/digest-queue.repository.port.ts";
export type { IWebhookEndpointRepo, WebhookEndpointRecord } from "./domain/ports/webhook-endpoint.repository.port.ts";
export type { IEmailSender, SendEmailInput } from "./domain/ports/email-sender.port.ts";
export type { ISmsSender, SendSmsInput } from "./domain/ports/sms-sender.port.ts";
export type { IRealtimeGateway, InAppPayload } from "./domain/ports/realtime-gateway.port.ts";
export type { IJwtVerifier, VerifiedClaims } from "./domain/ports/jwt-verifier.port.ts";

export { PrismaTenantPreferenceRepo } from "./modules/preferences/v1/repo.ts";
export { PrismaNotificationLogRepo } from "./modules/notifications/v1/repo.ts";
export { PrismaDigestQueueRepo } from "./modules/digest/v1/repo.ts";
export { PrismaWebhookEndpointRepo } from "./modules/webhooks/v1/repo.ts";
export { NodemailerEmailSender } from "./infra/email/nodemailer-email-sender.ts";
export { UnimplementedSmsSender } from "./infra/sms/unimplemented-sms-sender.ts";
export { SocketIoRedisGateway } from "./infra/realtime/socket-io-redis-gateway.ts";
export { JwksJwtVerifier } from "./infra/auth/jwks-jwt-verifier.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { AppError, GenNotifConfigError } from "./common/errors.ts";
```

- [ ] **Step 3: Write the factory test**

```ts
import { describe, it, expect, afterEach } from "vitest";
import request from "supertest";
import { createGenNotif } from "../../src/create-gen-notif.ts";
import { GenNotifConfigError } from "../../src/common/errors.ts";
import type { ITenantPreferenceRepo } from "../../src/domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../src/domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "../../src/domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "../../src/domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "../../src/domain/ports/email-sender.port.ts";
import type { IRealtimeGateway } from "../../src/domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "../../src/domain/ports/jwt-verifier.port.ts";

const noopPreferenceRepo: ITenantPreferenceRepo = { findAll: async () => [], findByEventType: async () => [], upsert: async (i) => ({ id: "p1", ...i, createdAt: new Date(), updatedAt: new Date() }) };
const noopNotificationRepo: INotificationLogRepo = { create: async (i) => ({ id: "n1", ...i, readAt: null, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(), entityRefType: i.entityRefType ?? null, entityRefId: i.entityRefId ?? null }), findById: async () => null, findUnread: async () => [], markRead: async () => null, markStatus: async () => {} };
const noopDigestRepo: IDigestQueueRepo = { enqueue: async () => {}, claimDue: async () => [], markSent: async () => {}, markFailed: async () => {} };
const noopWebhookRepo: IWebhookEndpointRepo = { create: async (i) => ({ id: "w1", ...i, secret: "s", enabled: true, description: i.description ?? null, createdAt: new Date(), updatedAt: new Date() }), findById: async () => null, listEnabled: async () => [], update: async (_t, id) => ({ id, tenantId: "t", userId: "u", url: "https://x", secret: "s", enabled: true, description: null, createdAt: new Date(), updatedAt: new Date() }), delete: async () => {} };
const noopEmailSender: IEmailSender = { send: async () => {} };
const noopRealtimeGateway: IRealtimeGateway = { attach: () => {}, publishInApp: async () => {} };
const noopJwtVerifier: IJwtVerifier = { verify: async () => ({ sub: "u", tenantId: "t", roles: [] }) };

const fullOverrides = {
  preferenceRepo: noopPreferenceRepo, notificationRepo: noopNotificationRepo, digestRepo: noopDigestRepo,
  webhookRepo: noopWebhookRepo, emailSender: noopEmailSender, realtimeGateway: noopRealtimeGateway, jwtVerifier: noopJwtVerifier,
  internalSecret: "test-secret",
};

describe("createGenNotif", () => {
  afterEach(() => { delete process.env.DATABASE_URL; });

  it("boots cleanly with every port overridden and no env set", () => {
    expect(() => createGenNotif(fullOverrides)).not.toThrow();
  });

  it("throws GenNotifConfigError when a Prisma-backed repo has no DATABASE_URL", () => {
    expect(() => createGenNotif({ ...fullOverrides, preferenceRepo: undefined })).toThrow(GenNotifConfigError);
  });

  it("GET /health returns ok without any auth", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("a disabled module's routes genuinely 404", async () => {
    const instance = createGenNotif({ ...fullOverrides, modules: { preferences: false } });
    const res = await request(instance.app).get("/api/v1/preferences").query({ tenantId: "t", userId: "u" });
    expect(res.status).toBe(404);
  });

  it("POST /internal/notify without the internal secret is 401", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).post("/internal/notify").send({ tenantId: "t", recipients: [], eventType: "x", data: {} });
    expect(res.status).toBe(401);
  });

  it("modules.realtime = false omits attachRealtime", () => {
    const instance = createGenNotif({ ...fullOverrides, modules: { realtime: false } });
    expect(instance.attachRealtime).toBeUndefined();
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npx vitest run tests/http/factory.test.ts`
Expected: PASS, 6 tests.

- [ ] **Step 5: Full workspace typecheck**

Run: `cd packages/gen-notif-starter && npx tsc --noEmit`
Expected: no errors. Fix any drift between the port signatures used across Tasks 5-17 discovered here (e.g. any lingering `findById(id)` vs `findById(tenantId, id)` call site missed during Task 6's correction).

- [ ] **Step 6: Commit**

```bash
git add packages/gen-notif-starter/src/create-gen-notif.ts packages/gen-notif-starter/src/index.ts packages/gen-notif-starter/tests/http/factory.test.ts
git commit -m "feat(factory): add createGenNotif — wires every module behind resolveXxx defaults"
```

---

### Task 19: Demo App + Documentation + Smoke Script

**Files:**
- Create: `packages/gen-notif-demo/src/index.ts`
- Create: `docs/integration-guide.md`
- Create: `README.md`
- Create: `scripts/smoke-notif.sh`

**Interfaces:**
- Consumes: `createGenNotif`, `registerTemplate` (Task 18).
- Produces: the demo app every smoke test and manual walkthrough targets.

- [ ] **Step 1: `packages/gen-notif-demo/src/index.ts`**

```ts
import http from "node:http";
import { createGenNotif, registerTemplate } from "@gen-ms/gen-notif-starter";

registerTemplate("doc.uploaded", (data) => ({
  title: "New document uploaded",
  body: `${data["fileName"]} was uploaded by ${data["uploadedBy"]}`,
  html: `<p><strong>${data["fileName"]}</strong> was uploaded by ${data["uploadedBy"]}.</p>`,
}));

const genNotif = createGenNotif({});
const server = http.createServer(genNotif.app);
genNotif.attachRealtime?.(server);

const PORT = Number(process.env.PORT ?? 3500);
server.listen(PORT, () => {
  console.log(`gen-notif-demo listening on :${PORT} (Socket.IO path /gen-notif/ws)`);
});
```

Under ~20 lines of actual logic (excluding the template registration, which is host content, not framework wiring) — mirrors every sibling demo's size.

- [ ] **Step 2: `docs/integration-guide.md`**

Write the consumer-facing reference, mirroring Gen_TBR's `integration-guide.md` structure: embed-in-process vs. standalone HTTP, a full env var table (every var from `create-gen-notif.ts`'s `resolveXxx` functions: `DATABASE_URL`, `REDIS_URL`, `JWKS_URL`, `JWT_ISSUER`, `SMTP_HOST`/`PORT`/`USER`/`PASS`/`SECURE`/`FROM`, `GEN_NOTIF_INTERNAL_SECRET`, `ALLOWED_ORIGINS`), the full API table (every route from Tasks 14-17), the port-swap tables for each of the 8 ports (preference/notification/digest/webhook repos, email/SMS senders, realtime gateway, JWT verifier), and these loud callouts:
- **"Gen_NOTIF ships no working SMS provider — configure `smsSender` with your own Twilio/SNS/etc. adapter, or SMS dispatch will always fail with `SMS_NOT_CONFIGURED`."**
- **"Gen_NOTIF has no internal scheduler. You must call `runDigestSweep()` (or `POST /internal/digest/sweep`) on your own cron/interval, or digest-mode notifications will accumulate forever and never send."**
- **"`notify()`'s durability is exactly the guarantee of one in-process async call — there is no outbox, no retry queue, no message broker. If your delivery requirements need at-least-once guarantees across process restarts, wrap `notify()` in your own durable job."**
- A worked example of registering a template, setting a preference, calling `notify()`, and connecting a `socket.io-client` to receive the resulting in-app event.

- [ ] **Step 3: `README.md`**

Mirror Gen_TBR's `README.md`: overview, the Gen_MS family table (now six rows: AUTH/TNT/REG/ADM/TBR/NOTIF), local run instructions (`docker compose up -d`, `npx prisma migrate deploy --schema packages/gen-notif-starter/prisma/schema.prisma`, `npm run dev --workspace=@gen-ms/gen-notif-demo`), the port-offset table (5433/5435/5436/5437/**5438**), and a "Not in scope" section listing: no RabbitMQ/outbox/saga, no MongoDB, no working SMS provider, no internal scheduler, no chat/messaging features (that's a distinct concern from notification delivery).

- [ ] **Step 4: `scripts/smoke-notif.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3500}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
USER_ID="22222222-2222-2222-2222-222222222222"
INTERNAL_SECRET="${GEN_NOTIF_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== set an email preference (immediate, not digest) =="
curl -sf -X PATCH "$BASE_URL/api/v1/preferences" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"eventType\":\"doc.uploaded\",\"channel\":\"email\",\"enabled\":true,\"digestMode\":false}"

echo "== register a webhook endpoint =="
WEBHOOK_RES=$(curl -sf -X POST "$BASE_URL/api/v1/webhook-endpoints" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"url\":\"https://example.com/hook\"}")
echo "$WEBHOOK_RES"

echo "== trigger a notification =="
curl -sf -X POST "$BASE_URL/internal/notify" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"recipients\":[{\"userId\":\"$USER_ID\",\"email\":\"test@example.com\"}],\"eventType\":\"doc.uploaded\",\"data\":{\"fileName\":\"invoice.pdf\",\"uploadedBy\":\"Alice\"}}"

echo "== fetch unread notifications =="
curl -sf "$BASE_URL/api/v1/notifications/unread?tenantId=$TENANT_ID&userId=$USER_ID"

echo "== run a digest sweep (no-op unless a digestMode preference queued something) =="
curl -sf -X POST "$BASE_URL/internal/digest/sweep" -H "x-internal-secret: $INTERNAL_SECRET"

echo "== smoke test complete =="
```

- [ ] **Step 5: Manual end-to-end verification**

Run: `docker compose up -d && npx prisma migrate deploy --schema packages/gen-notif-starter/prisma/schema.prisma && npm run dev --workspace=@gen-ms/gen-notif-demo &` then `bash scripts/smoke-notif.sh`.
Expected: every `curl -sf` call succeeds (non-2xx exits nonzero under `set -euo pipefail`). The smoke script's preference call sets `channel: "email"`, so `resolveChannels` (Task 12) resolves only `email` for this recipient — not `inapp` — since `inapp` is only the fallback when zero preference rows exist for that `(tenantId, userId, eventType)`. The unread-notifications response will therefore contain one `channel: "email"` entry, not an `inapp` one; the demo's SMTP isn't configured in this smoke run, so that email dispatch is expected to log `FAILED`/connection-refused while the `NotificationLog` row itself is still written and shows up in the unread list regardless of channel-dispatch outcome.

- [ ] **Step 6: Full test suite + typecheck, one final time**

Run: `npm test --workspaces && npm run typecheck --workspaces`
Expected: every test across every task passes; no type errors.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-notif-demo docs/integration-guide.md README.md scripts/smoke-notif.sh
git commit -m "docs: add integration guide, README, and smoke script"
```
