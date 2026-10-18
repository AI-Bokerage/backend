# Gen_TBR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_TBR — a TypeScript/Express/Prisma library owning per-tenant white-label branding and custom-domain ownership verification, consumed via a single `createGenTbr(config)` factory, with an independently runnable demo app.

**Architecture:** Hexagonal (ports + default adapters). Two feature modules (`branding`, `domains`) share one Postgres database and one Express app assembled by the factory. No sibling Gen_MS library is imported as a code dependency — all cross-library contact is HTTP-or-port (`ITntClient`, trusted `tenantId`).

**Tech Stack:** Node 20, TypeScript (ESM, NodeNext, `.ts` import extensions), Express 4 + helmet + cors + express-async-errors, Zod 3, Prisma 5 + PostgreSQL 15, `@aws-sdk/client-s3`, `node:dns/promises`, pino + pino-http, Vitest 2 + supertest + Testcontainers.

## Global Constraints

- Node.js 20, `@types/node ^20`. ESM only (`"type": "module"`).
- `.ts` extensions on ALL relative imports (rewritten to `.js` at build via `rewriteRelativeImportExtensions`). Omitting one is a build error, not a style nit.
- Every thrown error crossing a request boundary is an `AppError` subclass — never a raw `Error`. Exception: `GenTbrConfigError`, thrown only at `createGenTbr()` boot time.
- No `console.log` in library code (`packages/gen-tbr-starter/src/**`). The demo app may `console.log` its one startup line.
- PostgreSQL runs on port **5437** (AUTH=5433, TNT=5435, REG=5436 are siblings — do not collide).
- No Redis/Valkey, no background worker/scheduler anywhere in v1.
- `tenantId` is a UUID, validated for shape only (Zod `.uuid()`), never checked for existence against Gen_TNT.
- Domain strings: lowercase + trim on every write, reject length > 253 chars, strip trailing dot.
- The public manifest endpoint (`GET /branding/:tenantId/manifest` and its `?domain=` variant) has **no auth** — not `internalSecret`, not host auth. Every other mutating endpoint expects the host to have mounted its own auth in front; Gen_TBR only additionally offers an `internalSecret` middleware for `/internal` routes and the demo.
- Never store asset bytes in Postgres — only the `IAssetStore`-returned URL.
- Prisma cannot express a partial unique index — the primary-domain-uniqueness backstop is added via raw SQL in a migration, not the `schema.prisma` DSL.
- Ship `prisma/schema.prisma` + `prisma/migrations` inside the published package (`files` in `package.json`); do not bundle a `dist`-local copy.

---

## File Structure

```
Gen_TBR/
├── package.json                              # workspace root
├── docker-compose.yml                        # postgres:5437, db "gentbr"
├── .gitignore
├── .env.example
├── docs/
│   ├── integration-guide.md
│   └── superpowers/{specs,plans}/
├── packages/
│   ├── gen-tbr-starter/
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── vitest.config.ts
│   │   ├── prisma/
│   │   │   ├── schema.prisma
│   │   │   └── migrations/
│   │   │       ├── 0001_init/migration.sql
│   │   │       └── 0002_tenant_domain_one_primary/migration.sql
│   │   ├── src/
│   │   │   ├── create-gen-tbr.ts             # factory
│   │   │   ├── index.ts                      # public barrel
│   │   │   ├── config/env.ts                 # Zod schema + requireEnv()
│   │   │   ├── common/
│   │   │   │   ├── logger.ts
│   │   │   │   ├── errors.ts
│   │   │   │   └── types.ts
│   │   │   ├── domain/ports/
│   │   │   │   ├── tenant-branding.repository.port.ts
│   │   │   │   ├── tenant-domain.repository.port.ts
│   │   │   │   ├── asset-store.port.ts
│   │   │   │   ├── dns-verifier.port.ts
│   │   │   │   └── tnt-client.port.ts
│   │   │   ├── infra/
│   │   │   │   ├── persistence/prisma-client.ts
│   │   │   │   ├── storage/s3-asset-store.ts
│   │   │   │   ├── dns/node-dns-verifier.ts
│   │   │   │   └── tnt-client/tnt-client.ts
│   │   │   ├── modules/
│   │   │   │   ├── branding/v1/
│   │   │   │   │   ├── repo.ts               # PrismaTenantBrandingRepo
│   │   │   │   │   ├── branding.service.ts
│   │   │   │   │   ├── branding.controller.ts
│   │   │   │   │   └── branding.router.ts
│   │   │   │   └── domains/v1/
│   │   │   │       ├── repo.ts                # PrismaTenantDomainRepo
│   │   │   │       ├── domain.service.ts
│   │   │   │       ├── domain.controller.ts
│   │   │   │       └── domain.router.ts
│   │   │   └── middleware/
│   │   │       ├── error-handler.ts
│   │   │       ├── internal-secret.ts
│   │   │       └── validate-tenant-id.ts
│   │   └── tests/
│   │       ├── support/postgres-container.ts
│   │       ├── unit/**
│   │       ├── integration/**
│   │       └── http/**
│   └── gen-tbr-demo/
│       ├── package.json
│       └── src/index.ts
└── scripts/
    └── smoke-branding.sh
```

---

### Task 1: Workspace + package scaffolding

**Files:**
- Create: `package.json` (root)
- Create: `.gitignore`
- Create: `.env.example`
- Create: `docker-compose.yml`
- Create: `packages/gen-tbr-starter/package.json`
- Create: `packages/gen-tbr-starter/tsconfig.json`
- Create: `packages/gen-tbr-starter/vitest.config.ts`
- Create: `packages/gen-tbr-demo/package.json`
- Test: none (no logic yet) — deliverable verified by running `npm install` and `npm run build --workspaces` with an empty `src/index.ts` stub

**Interfaces:**
- Produces: the workspace npm scripts (`build`, `test`) every later task's steps invoke.

- [ ] **Step 1: Root workspace `package.json`**

```json
{
  "name": "gen-tbr",
  "private": true,
  "workspaces": ["packages/*"],
  "scripts": {
    "build": "npm run build --workspaces --if-present",
    "test": "npm run test --workspaces --if-present"
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
coverage/
```

- [ ] **Step 3: `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: gentbr
      POSTGRES_USER: gentbr
      POSTGRES_PASSWORD: gentbr
    ports: ["5437:5432"]
    volumes: ["gentbr-pg:/var/lib/postgresql/data"]
volumes:
  gentbr-pg: {}
```

- [ ] **Step 4: `.env.example`**

```
DATABASE_URL=postgresql://gentbr:gentbr@localhost:5437/gentbr
GEN_TBR_INTERNAL_SECRET=change-me
GEN_TBR_S3_BUCKET=gen-tbr-assets
GEN_TBR_S3_REGION=us-east-1
GEN_TBR_S3_ENDPOINT=
GEN_TBR_S3_ACCESS_KEY_ID=
GEN_TBR_S3_SECRET_ACCESS_KEY=
GEN_TNT_BASE_URL=
```

- [ ] **Step 5: `packages/gen-tbr-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-tbr-starter",
  "version": "0.1.0",
  "type": "module",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "files": ["dist", "prisma/schema.prisma", "prisma/migrations"],
  "scripts": {
    "build": "tsc -p tsconfig.json",
    "postinstall": "prisma generate --schema=prisma/schema.prisma",
    "test": "vitest run"
  },
  "dependencies": {
    "express": "^4.19.2",
    "express-async-errors": "^3.1.1",
    "helmet": "^7.1.0",
    "cors": "^2.8.5",
    "zod": "^3.23.8",
    "@prisma/client": "^5.20.0",
    "@aws-sdk/client-s3": "^3.632.0",
    "pino": "^9.3.2",
    "pino-http": "^10.3.0",
    "multer": "^1.4.5-lts.1"
  },
  "devDependencies": {
    "typescript": "^5.5.4",
    "prisma": "^5.20.0",
    "vitest": "^2.0.5",
    "supertest": "^7.0.0",
    "@testcontainers/postgresql": "^10.13.1",
    "pino-pretty": "^11.2.2",
    "@types/express": "^4.17.21",
    "@types/node": "^20.14.15",
    "@types/cors": "^2.8.17",
    "@types/multer": "^1.4.11",
    "@types/supertest": "^6.0.2"
  }
}
```

- [ ] **Step 6: `packages/gen-tbr-starter/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022"],
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "rootDir": "src",
    "outDir": "dist",
    "declaration": true,
    "strict": true,
    "skipLibCheck": true,
    "allowImportingTsExtensions": true,
    "rewriteRelativeImportExtensions": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src"]
}
```

- [ ] **Step 7: `packages/gen-tbr-starter/vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    fakeTimers: {
      toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"],
    },
  },
});
```

- [ ] **Step 8: `packages/gen-tbr-demo/package.json`**

```json
{
  "name": "@gen-ms/gen-tbr-demo",
  "version": "0.1.0",
  "type": "module",
  "private": true,
  "main": "dist/index.js",
  "scripts": {
    "build": "tsc -p tsconfig.json",
    "start": "node dist/index.js"
  },
  "dependencies": {
    "@gen-ms/gen-tbr-starter": "*"
  },
  "devDependencies": {
    "typescript": "^5.5.4",
    "@types/node": "^20.14.15"
  }
}
```

Also create `packages/gen-tbr-demo/tsconfig.json` identical in shape to Step 6 but with no Prisma-specific settings needed (same compilerOptions block works verbatim).

- [ ] **Step 9: Stub entry points so the build has something to compile**

Create `packages/gen-tbr-starter/src/index.ts`:
```ts
export {};
```

- [ ] **Step 10: Verify install + build**

Run: `npm install && npm run build --workspaces --if-present`
Expected: succeeds with no output files beyond an empty `dist/index.js`.

- [ ] **Step 11: Commit**

```bash
git add package.json .gitignore .env.example docker-compose.yml packages/gen-tbr-starter/package.json packages/gen-tbr-starter/tsconfig.json packages/gen-tbr-starter/vitest.config.ts packages/gen-tbr-starter/src/index.ts packages/gen-tbr-demo/package.json packages/gen-tbr-demo/tsconfig.json
git commit -m "chore: scaffold Gen_TBR workspace"
```

---

### Task 2: Prisma schema + migrations

**Files:**
- Create: `packages/gen-tbr-starter/prisma/schema.prisma`
- Create: `packages/gen-tbr-starter/prisma/migrations/0001_init/migration.sql` (generated, then reviewed)
- Create: `packages/gen-tbr-starter/prisma/migrations/0002_tenant_domain_one_primary/migration.sql`

**Interfaces:**
- Produces: `@prisma/client` types `TenantBranding`, `TenantDomain`, enums `BrandingTheme`, `DomainStatus`, `DomainVerificationMethod` — consumed by Task 6's Prisma repo adapters.

- [ ] **Step 1: Write `schema.prisma`**

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum BrandingTheme {
  SYSTEM
  LIGHT
  DARK
}

enum DomainStatus {
  PENDING
  VERIFIED
  ACTIVE
  DETACHED
}

enum DomainVerificationMethod {
  TXT
  CNAME
}

model TenantBranding {
  tenantId       String        @id @db.Uuid
  displayName    String        @db.VarChar(120)
  tagline        String?       @db.VarChar(200)
  logoUrl        String?       @db.VarChar(1024)
  logoDarkUrl    String?       @db.VarChar(1024)
  faviconUrl     String?       @db.VarChar(1024)
  primaryColor   String?       @db.VarChar(32)
  secondaryColor String?       @db.VarChar(32)
  accentColor    String?       @db.VarChar(32)
  fontFamily     String?       @db.VarChar(120)
  theme          BrandingTheme @default(SYSTEM)
  rawMeta        Json?
  updatedAt      DateTime      @updatedAt @db.Timestamptz(6)

  @@map("tenant_branding")
}

model TenantDomain {
  id                 String                   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  tenantId           String                   @db.Uuid
  domain             String                   @db.VarChar(253)
  verificationToken  String                   @db.VarChar(64)
  status             DomainStatus             @default(PENDING)
  verificationMethod DomainVerificationMethod
  verifiedAt         DateTime?                @db.Timestamptz(6)
  lastCheckedAt      DateTime?                @db.Timestamptz(6)
  isPrimary          Boolean                  @default(false)
  createdAt          DateTime                 @default(now()) @db.Timestamptz(6)
  updatedAt          DateTime                 @updatedAt @db.Timestamptz(6)

  @@unique([domain])
  @@index([tenantId])
  @@map("tenant_domain")
}
```

- [ ] **Step 2: Generate the initial migration**

Run: `cd packages/gen-tbr-starter && npx prisma migrate dev --name init --create-only`
Expected: creates `prisma/migrations/0001_init/migration.sql` with `CREATE TABLE`/`CREATE TYPE` statements for both models.

- [ ] **Step 3: Add the partial-unique primary-domain index in a second migration**

Run: `npx prisma migrate dev --name tenant_domain_one_primary --create-only`

Edit the generated `packages/gen-tbr-starter/prisma/migrations/0002_tenant_domain_one_primary/migration.sql` to contain exactly:

```sql
CREATE UNIQUE INDEX "tenant_domain_one_primary_per_tenant"
  ON "tenant_domain" ("tenantId")
  WHERE "isPrimary" = true AND "status" <> 'DETACHED';
```

- [ ] **Step 4: Apply migrations against the local Postgres and generate the client**

Run: `docker compose up -d postgres && cd packages/gen-tbr-starter && npx prisma migrate deploy && npx prisma generate`
Expected: both migrations apply cleanly; `node_modules/.prisma/client` regenerated with `TenantBranding`/`TenantDomain` types.

- [ ] **Step 5: Manual sanity check of the partial index**

Run (psql, or `npx prisma studio` is not enough — use raw SQL):
```sql
INSERT INTO tenant_branding ... -- skip, not needed for this check
```
Instead just confirm the index exists:
```bash
docker compose exec postgres psql -U gentbr -d gentbr -c "\d tenant_domain"
```
Expected: output lists `tenant_domain_one_primary_per_tenant` as a unique, partial index.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-tbr-starter/prisma
git commit -m "feat: add Prisma schema for TenantBranding and TenantDomain"
```

---

### Task 3: Common layer — logger, errors, types

**Files:**
- Create: `packages/gen-tbr-starter/src/common/logger.ts`
- Create: `packages/gen-tbr-starter/src/common/errors.ts`
- Create: `packages/gen-tbr-starter/src/common/types.ts`
- Test: `packages/gen-tbr-starter/tests/unit/common/errors.test.ts`

**Interfaces:**
- Produces: `logger` (pino instance with `.child()`), `AppError` + all subclasses, `GenTbrConfigError` — used by every later task.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/common/errors.test.ts
import { describe, it, expect } from "vitest";
import {
  AppError,
  BrandingNotFoundError,
  BrandingValidationError,
  DomainNotFoundError,
  DomainAlreadyClaimedError,
  DomainVerificationFailedError,
  InvalidDomainStatusTransitionError,
  AssetStoreError,
  AssetNotFoundError,
  GenTbrConfigError,
} from "../../../src/common/errors.ts";

describe("error taxonomy", () => {
  it("BrandingNotFoundError is a 404 AppError", () => {
    const err = new BrandingNotFoundError("t-1");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(404);
    expect(err.code).toBe("BRANDING_NOT_FOUND");
  });

  it("DomainAlreadyClaimedError is a 409 AppError", () => {
    const err = new DomainAlreadyClaimedError("example.com");
    expect(err.statusCode).toBe(409);
    expect(err.code).toBe("DOMAIN_ALREADY_CLAIMED");
  });

  it("GenTbrConfigError is a plain Error, not an AppError", () => {
    const err = new GenTbrConfigError("missing DATABASE_URL");
    expect(err).toBeInstanceOf(Error);
    expect(err).not.toBeInstanceOf(AppError);
    expect(err.name).toBe("GenTbrConfigError");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/common/errors.test.ts`
Expected: FAIL — `src/common/errors.ts` does not exist.

- [ ] **Step 3: Write `common/errors.ts`**

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

export class BrandingNotFoundError extends AppError {
  constructor(tenantId: string) {
    super(404, "BRANDING_NOT_FOUND", `No branding found for tenant ${tenantId}`);
  }
}

export class BrandingValidationError extends AppError {
  constructor(message: string) {
    super(400, "BRANDING_VALIDATION_ERROR", message);
  }
}

export class DomainNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "DOMAIN_NOT_FOUND", `No domain found with id ${id}`);
  }
}

export class DomainAlreadyClaimedError extends AppError {
  constructor(domain: string) {
    super(409, "DOMAIN_ALREADY_CLAIMED", `Domain ${domain} is already claimed`);
  }
}

export class DomainVerificationFailedError extends AppError {
  constructor(domain: string) {
    super(409, "DOMAIN_VERIFICATION_FAILED", `DNS verification failed for ${domain}`);
  }
}

export class InvalidDomainStatusTransitionError extends AppError {
  constructor(from: string, to: string) {
    super(409, "INVALID_DOMAIN_STATUS_TRANSITION", `Cannot transition domain from ${from} to ${to}`);
  }
}

export class AssetStoreError extends AppError {
  constructor(message: string) {
    super(502, "ASSET_STORE_ERROR", message);
  }
}

export class AssetNotFoundError extends AppError {
  constructor(assetId: string) {
    super(404, "ASSET_NOT_FOUND", `No asset found with id ${assetId}`);
  }
}

export class GenTbrConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenTbrConfigError";
  }
}
```

- [ ] **Step 4: Write `common/logger.ts`**

```ts
import pino from "pino";

const isDev = process.env.NODE_ENV !== "production";

export const logger = pino(
  isDev
    ? { level: "debug", transport: { target: "pino-pretty" } }
    : { level: "info" },
);
```

- [ ] **Step 5: Write `common/types.ts`**

```ts
export type Theme = "SYSTEM" | "LIGHT" | "DARK";
export type DomainStatus = "PENDING" | "VERIFIED" | "ACTIVE" | "DETACHED";
export type DomainVerificationMethod = "TXT" | "CNAME";
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/common/errors.test.ts`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-tbr-starter/src/common packages/gen-tbr-starter/tests/unit/common
git commit -m "feat: add error taxonomy, logger, and shared types"
```

---

### Task 4: Env config — Zod schema + lazy `requireEnv`

**Files:**
- Create: `packages/gen-tbr-starter/src/config/env.ts`
- Test: `packages/gen-tbr-starter/tests/unit/config/env.test.ts`

**Interfaces:**
- Consumes: `GenTbrConfigError` from `../common/errors.ts` (Task 3).
- Produces: `requireEnv(name: string): string` (throws `GenTbrConfigError` if unset), `optionalEnv(name: string): string | undefined` — consumed by every `resolveXxx` default-adapter builder in Tasks 6–9 and the factory in Task 15.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/config/env.test.ts
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "../../../src/config/env.ts";
import { GenTbrConfigError } from "../../../src/common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_TBR_TEST_VAR";

  afterEach(() => {
    delete process.env[KEY];
  });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenTbrConfigError when unset", () => {
    delete process.env[KEY];
    expect(() => requireEnv(KEY)).toThrow(GenTbrConfigError);
    expect(() => requireEnv(KEY)).toThrow(/GEN_TBR_TEST_VAR/);
  });
});

describe("optionalEnv", () => {
  it("returns undefined when unset", () => {
    expect(optionalEnv("GEN_TBR_NOT_SET")).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/config/env.test.ts`
Expected: FAIL — `src/config/env.ts` does not exist.

- [ ] **Step 3: Write `config/env.ts`**

```ts
import { GenTbrConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenTbrConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string): string | undefined {
  return process.env[name] || undefined;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/config/env.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/config packages/gen-tbr-starter/tests/unit/config
git commit -m "feat: add lazy env resolution helpers"
```

---

### Task 5: Domain ports

**Files:**
- Create: `packages/gen-tbr-starter/src/domain/ports/tenant-branding.repository.port.ts`
- Create: `packages/gen-tbr-starter/src/domain/ports/tenant-domain.repository.port.ts`
- Create: `packages/gen-tbr-starter/src/domain/ports/asset-store.port.ts`
- Create: `packages/gen-tbr-starter/src/domain/ports/dns-verifier.port.ts`
- Create: `packages/gen-tbr-starter/src/domain/ports/tnt-client.port.ts`
- Test: none (pure type declarations — verified by `tsc --noEmit` in Step 2)

**Interfaces:**
- Produces (types consumed throughout every later task):
  - `BrandingRecord`, `UpsertBrandingInput`, `ITenantBrandingRepo`
  - `TenantDomainRecord`, `CreateDomainInput`, `ITenantDomainRepo`
  - `AssetRef`, `PutAssetResult`, `IAssetStore`
  - `DnsRecordKind`, `DnsRecord`, `IDnsVerifier`
  - `ITntClient`

- [ ] **Step 1: Write `domain/ports/tenant-branding.repository.port.ts`**

```ts
import type { Theme } from "../../common/types.ts";

export interface BrandingRecord {
  tenantId: string;
  displayName: string;
  tagline: string | null;
  logoUrl: string | null;
  logoDarkUrl: string | null;
  faviconUrl: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  accentColor: string | null;
  fontFamily: string | null;
  theme: Theme;
  rawMeta: unknown;
  updatedAt: Date;
}

export interface UpsertBrandingInput {
  tenantId: string;
  displayName: string;
  tagline?: string | null;
  logoUrl?: string | null;
  logoDarkUrl?: string | null;
  faviconUrl?: string | null;
  primaryColor?: string | null;
  secondaryColor?: string | null;
  accentColor?: string | null;
  fontFamily?: string | null;
  theme?: Theme;
  rawMeta?: unknown;
}

export type PatchBrandingInput = Partial<Omit<UpsertBrandingInput, "tenantId">>;

export interface ITenantBrandingRepo {
  findByTenantId(tenantId: string): Promise<BrandingRecord | null>;
  upsert(input: UpsertBrandingInput): Promise<BrandingRecord>;
  patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord | null>;
}
```

- [ ] **Step 2: Write `domain/ports/tenant-domain.repository.port.ts`**

```ts
import type { DomainStatus, DomainVerificationMethod } from "../../common/types.ts";

export interface TenantDomainRecord {
  id: string;
  tenantId: string;
  domain: string;
  verificationToken: string;
  status: DomainStatus;
  verificationMethod: DomainVerificationMethod;
  verifiedAt: Date | null;
  lastCheckedAt: Date | null;
  isPrimary: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateDomainInput {
  tenantId: string;
  domain: string;
  verificationToken: string;
  verificationMethod: DomainVerificationMethod;
}

export interface UpdateDomainStatusFields {
  verifiedAt?: Date;
  lastCheckedAt?: Date;
}

export interface ITenantDomainRepo {
  create(input: CreateDomainInput): Promise<TenantDomainRecord>;
  findById(id: string): Promise<TenantDomainRecord | null>;
  findByDomain(domain: string): Promise<TenantDomainRecord | null>;
  listByTenant(tenantId: string, opts?: { includeDetached?: boolean }): Promise<TenantDomainRecord[]>;
  updateStatus(id: string, status: DomainStatus, fields?: UpdateDomainStatusFields): Promise<TenantDomainRecord>;
  setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord>;
  touchLastChecked(id: string, at: Date): Promise<void>;
}
```

- [ ] **Step 3: Write `domain/ports/asset-store.port.ts`**

```ts
export interface AssetRef {
  assetId: string;
  tenantId: string;
  contentType: string;
  bytes: Buffer;
}

export interface PutAssetResult {
  assetId: string;
  url: string;
}

export interface IAssetStore {
  put(ref: AssetRef): Promise<PutAssetResult>;
  get(tenantId: string, assetId: string): Promise<{ url: string } | null>;
}
```

- [ ] **Step 4: Write `domain/ports/dns-verifier.port.ts`**

```ts
export type DnsRecordKind = "TXT" | "CNAME";

export interface DnsRecord {
  kind: DnsRecordKind;
  name: string;
  expectedValue: string;
}

export interface IDnsVerifier {
  resolveTxt(hostname: string): Promise<string[][]>;
  resolveCname(hostname: string): Promise<string[]>;
}
```

- [ ] **Step 5: Write `domain/ports/tnt-client.port.ts`**

```ts
export interface ITntClient {
  notifyTenantSuspended(tenantId: string): Promise<void>;
}
```

- [ ] **Step 6: Verify the project still type-checks**

Run: `cd packages/gen-tbr-starter && npx tsc --noEmit`
Expected: no errors (these are unreferenced type-only files at this point, which is fine).

- [ ] **Step 7: Commit**

```bash
git add packages/gen-tbr-starter/src/domain
git commit -m "feat: add domain ports (repos, asset store, dns verifier, tnt client)"
```

---

### Task 6: Prisma repo adapters + testcontainers helper

**Files:**
- Create: `packages/gen-tbr-starter/src/infra/persistence/prisma-client.ts`
- Create: `packages/gen-tbr-starter/src/modules/branding/v1/repo.ts`
- Create: `packages/gen-tbr-starter/src/modules/domains/v1/repo.ts`
- Create: `packages/gen-tbr-starter/tests/support/postgres-container.ts`
- Test: `packages/gen-tbr-starter/tests/integration/branding-repo.test.ts`
- Test: `packages/gen-tbr-starter/tests/integration/domain-repo.test.ts`

**Interfaces:**
- Consumes: `ITenantBrandingRepo`, `ITenantDomainRepo` and their types (Task 5), `requireEnv` (Task 4).
- Produces: `getPrismaClient(): PrismaClient` (singleton), `PrismaTenantBrandingRepo`, `PrismaTenantDomainRepo` — consumed by the factory's `resolveBrandingRepo`/`resolveDomainRepo` in Task 15.

- [ ] **Step 1: Write `tests/support/postgres-container.ts`**

```ts
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";

let container: StartedPostgreSqlContainer | undefined;

export async function startTestPostgres(): Promise<string> {
  container = await new PostgreSqlContainer("postgres:15").start();
  const url = container.getConnectionUri();
  execSync("npx prisma migrate deploy", {
    cwd: new URL("../..", import.meta.url).pathname,
    env: { ...process.env, DATABASE_URL: url },
    stdio: "inherit",
  });
  return url;
}

export async function stopTestPostgres(): Promise<void> {
  await container?.stop();
  container = undefined;
}
```

- [ ] **Step 2: Write the failing repo integration tests**

```ts
// packages/gen-tbr-starter/tests/integration/branding-repo.test.ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@prisma/client";
import { startTestPostgres, stopTestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantBrandingRepo } from "../../src/modules/branding/v1/repo.ts";

describe("PrismaTenantBrandingRepo", () => {
  let prisma: PrismaClient;
  let repo: PrismaTenantBrandingRepo;

  beforeAll(async () => {
    const url = await startTestPostgres();
    prisma = new PrismaClient({ datasources: { db: { url } } });
    repo = new PrismaTenantBrandingRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await stopTestPostgres();
  });

  it("upserts then reads back a branding row", async () => {
    const tenantId = "11111111-1111-1111-1111-111111111111";
    await repo.upsert({ tenantId, displayName: "Acme Co" });
    const found = await repo.findByTenantId(tenantId);
    expect(found?.displayName).toBe("Acme Co");
    expect(found?.theme).toBe("SYSTEM");
  });

  it("returns null for an unknown tenant", async () => {
    const found = await repo.findByTenantId("22222222-2222-2222-2222-222222222222");
    expect(found).toBeNull();
  });

  it("patch partially updates an existing row", async () => {
    const tenantId = "33333333-3333-3333-3333-333333333333";
    await repo.upsert({ tenantId, displayName: "Beta Inc" });
    const patched = await repo.patch(tenantId, { tagline: "Beta rocks" });
    expect(patched?.tagline).toBe("Beta rocks");
    expect(patched?.displayName).toBe("Beta Inc");
  });
});
```

```ts
// packages/gen-tbr-starter/tests/integration/domain-repo.test.ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@prisma/client";
import { startTestPostgres, stopTestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantDomainRepo } from "../../src/modules/domains/v1/repo.ts";

describe("PrismaTenantDomainRepo", () => {
  let prisma: PrismaClient;
  let repo: PrismaTenantDomainRepo;
  const tenantId = "44444444-4444-4444-4444-444444444444";

  beforeAll(async () => {
    const url = await startTestPostgres();
    prisma = new PrismaClient({ datasources: { db: { url } } });
    repo = new PrismaTenantDomainRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await stopTestPostgres();
  });

  it("creates a domain in PENDING status", async () => {
    const created = await repo.create({
      tenantId,
      domain: "example.com",
      verificationToken: "a".repeat(64),
      verificationMethod: "TXT",
    });
    expect(created.status).toBe("PENDING");
  });

  it("rejects a duplicate domain via the unique constraint", async () => {
    await expect(
      repo.create({
        tenantId,
        domain: "example.com",
        verificationToken: "b".repeat(64),
        verificationMethod: "TXT",
      }),
    ).rejects.toThrow();
  });

  it("setPrimary revokes the prior primary atomically", async () => {
    const d1 = await repo.create({
      tenantId,
      domain: "one.example.org",
      verificationToken: "c".repeat(64),
      verificationMethod: "TXT",
    });
    const d2 = await repo.create({
      tenantId,
      domain: "two.example.org",
      verificationToken: "d".repeat(64),
      verificationMethod: "TXT",
    });
    await repo.setPrimary(tenantId, d1.id);
    let refreshed1 = await repo.findById(d1.id);
    expect(refreshed1?.isPrimary).toBe(true);

    await repo.setPrimary(tenantId, d2.id);
    refreshed1 = await repo.findById(d1.id);
    const refreshed2 = await repo.findById(d2.id);
    expect(refreshed1?.isPrimary).toBe(false);
    expect(refreshed2?.isPrimary).toBe(true);
  });

  it("listByTenant excludes DETACHED by default", async () => {
    const d = await repo.create({
      tenantId,
      domain: "detach-me.example.net",
      verificationToken: "e".repeat(64),
      verificationMethod: "TXT",
    });
    await repo.updateStatus(d.id, "DETACHED");
    const list = await repo.listByTenant(tenantId);
    expect(list.find((x) => x.id === d.id)).toBeUndefined();
    const listAll = await repo.listByTenant(tenantId, { includeDetached: true });
    expect(listAll.find((x) => x.id === d.id)).toBeDefined();
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/integration`
Expected: FAIL — `src/infra/persistence/prisma-client.ts` and both repo files do not exist yet.

- [ ] **Step 4: Write `infra/persistence/prisma-client.ts`**

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

- [ ] **Step 5: Write `modules/branding/v1/repo.ts`**

```ts
import type { PrismaClient } from "@prisma/client";
import type {
  ITenantBrandingRepo,
  BrandingRecord,
  UpsertBrandingInput,
  PatchBrandingInput,
} from "../../../domain/ports/tenant-branding.repository.port.ts";

export class PrismaTenantBrandingRepo implements ITenantBrandingRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async findByTenantId(tenantId: string): Promise<BrandingRecord | null> {
    const row = await this.prisma.tenantBranding.findUnique({ where: { tenantId } });
    return row as BrandingRecord | null;
  }

  async upsert(input: UpsertBrandingInput): Promise<BrandingRecord> {
    const { tenantId, ...rest } = input;
    const row = await this.prisma.tenantBranding.upsert({
      where: { tenantId },
      create: { tenantId, ...rest },
      update: rest,
    });
    return row as BrandingRecord;
  }

  async patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord | null> {
    const exists = await this.prisma.tenantBranding.findUnique({ where: { tenantId } });
    if (!exists) return null;
    const row = await this.prisma.tenantBranding.update({ where: { tenantId }, data: input });
    return row as BrandingRecord;
  }
}
```

- [ ] **Step 6: Write `modules/domains/v1/repo.ts`**

```ts
import type { PrismaClient } from "@prisma/client";
import type {
  ITenantDomainRepo,
  TenantDomainRecord,
  CreateDomainInput,
  UpdateDomainStatusFields,
} from "../../../domain/ports/tenant-domain.repository.port.ts";
import type { DomainStatus } from "../../../common/types.ts";

export class PrismaTenantDomainRepo implements ITenantDomainRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(input: CreateDomainInput): Promise<TenantDomainRecord> {
    const row = await this.prisma.tenantDomain.create({ data: input });
    return row as TenantDomainRecord;
  }

  async findById(id: string): Promise<TenantDomainRecord | null> {
    const row = await this.prisma.tenantDomain.findUnique({ where: { id } });
    return row as TenantDomainRecord | null;
  }

  async findByDomain(domain: string): Promise<TenantDomainRecord | null> {
    const row = await this.prisma.tenantDomain.findUnique({ where: { domain } });
    return row as TenantDomainRecord | null;
  }

  async listByTenant(
    tenantId: string,
    opts?: { includeDetached?: boolean },
  ): Promise<TenantDomainRecord[]> {
    const rows = await this.prisma.tenantDomain.findMany({
      where: {
        tenantId,
        ...(opts?.includeDetached ? {} : { status: { not: "DETACHED" } }),
      },
      orderBy: { createdAt: "asc" },
    });
    return rows as TenantDomainRecord[];
  }

  async updateStatus(
    id: string,
    status: DomainStatus,
    fields?: UpdateDomainStatusFields,
  ): Promise<TenantDomainRecord> {
    const row = await this.prisma.tenantDomain.update({
      where: { id },
      data: { status, ...fields },
    });
    return row as TenantDomainRecord;
  }

  async setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord> {
    return this.prisma.$transaction(async (tx) => {
      await tx.tenantDomain.updateMany({
        where: { tenantId, isPrimary: true },
        data: { isPrimary: false },
      });
      const row = await tx.tenantDomain.update({
        where: { id },
        data: { isPrimary: true },
      });
      return row as TenantDomainRecord;
    });
  }

  async touchLastChecked(id: string, at: Date): Promise<void> {
    await this.prisma.tenantDomain.update({ where: { id }, data: { lastCheckedAt: at } });
  }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/integration`
Expected: PASS (requires Docker available for Testcontainers).

- [ ] **Step 8: Commit**

```bash
git add packages/gen-tbr-starter/src/infra/persistence packages/gen-tbr-starter/src/modules/branding/v1/repo.ts packages/gen-tbr-starter/src/modules/domains/v1/repo.ts packages/gen-tbr-starter/tests/support packages/gen-tbr-starter/tests/integration
git commit -m "feat: add Prisma repo adapters for branding and domains"
```

---

### Task 7: `NodeDnsVerifier` adapter

**Files:**
- Create: `packages/gen-tbr-starter/src/infra/dns/node-dns-verifier.ts`
- Test: `packages/gen-tbr-starter/tests/unit/infra/node-dns-verifier.test.ts`

**Interfaces:**
- Consumes: `IDnsVerifier` (Task 5).
- Produces: `NodeDnsVerifier` — consumed by the factory's `resolveDnsVerifier` in Task 15 and by `DomainService` in Task 11 (via the port, not the concrete class).

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/infra/node-dns-verifier.test.ts
import { describe, it, expect, vi } from "vitest";

vi.mock("node:dns/promises", () => ({
  resolveTxt: vi.fn(async (hostname: string) => {
    if (hostname === "_gen-tbr-verify.example.com") return [["gen-tbr-verify=abc123"]];
    throw Object.assign(new Error("ENOTFOUND"), { code: "ENOTFOUND" });
  }),
  resolveCname: vi.fn(async (hostname: string) => {
    if (hostname === "_gen-tbr.example.com") return ["verify.gen-tbr.example.net"];
    throw Object.assign(new Error("ENOTFOUND"), { code: "ENOTFOUND" });
  }),
}));

import { NodeDnsVerifier } from "../../../src/infra/dns/node-dns-verifier.ts";

describe("NodeDnsVerifier", () => {
  it("resolveTxt returns records on a match", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveTxt("_gen-tbr-verify.example.com");
    expect(records).toEqual([["gen-tbr-verify=abc123"]]);
  });

  it("resolveTxt returns [] on ENOTFOUND instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveTxt("_gen-tbr-verify.nowhere.invalid");
    expect(records).toEqual([]);
  });

  it("resolveCname returns [] on ENOTFOUND instead of throwing", async () => {
    const verifier = new NodeDnsVerifier();
    const records = await verifier.resolveCname("_gen-tbr.nowhere.invalid");
    expect(records).toEqual([]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/node-dns-verifier.test.ts`
Expected: FAIL — file does not exist.

- [ ] **Step 3: Write `infra/dns/node-dns-verifier.ts`**

```ts
import * as dns from "node:dns/promises";
import type { IDnsVerifier } from "../../domain/ports/dns-verifier.port.ts";

function isNotFound(err: unknown): boolean {
  return typeof err === "object" && err !== null && "code" in err &&
    (err as { code?: string }).code === "ENOTFOUND";
}

export class NodeDnsVerifier implements IDnsVerifier {
  async resolveTxt(hostname: string): Promise<string[][]> {
    try {
      return await dns.resolveTxt(hostname);
    } catch (err) {
      if (isNotFound(err)) return [];
      throw err;
    }
  }

  async resolveCname(hostname: string): Promise<string[]> {
    try {
      return await dns.resolveCname(hostname);
    } catch (err) {
      if (isNotFound(err)) return [];
      throw err;
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/node-dns-verifier.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/infra/dns packages/gen-tbr-starter/tests/unit/infra/node-dns-verifier.test.ts
git commit -m "feat: add NodeDnsVerifier adapter"
```

---

### Task 8: `S3AssetStore` adapter

**Files:**
- Create: `packages/gen-tbr-starter/src/infra/storage/s3-asset-store.ts`
- Test: `packages/gen-tbr-starter/tests/unit/infra/s3-asset-store.test.ts`

**Interfaces:**
- Consumes: `IAssetStore`, `AssetRef`, `PutAssetResult` (Task 5); `AssetStoreError` (Task 3).
- Produces: `S3AssetStore` (constructor takes `{ bucket, region, endpoint?, accessKeyId, secretAccessKey, publicBaseUrl? }`) — consumed by the factory's `resolveAssetStore` in Task 15.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/infra/s3-asset-store.test.ts
import { describe, it, expect, vi } from "vitest";

const sendMock = vi.fn();
vi.mock("@aws-sdk/client-s3", () => ({
  S3Client: vi.fn(() => ({ send: sendMock })),
  PutObjectCommand: vi.fn((input) => ({ input })),
}));

import { S3AssetStore } from "../../../src/infra/storage/s3-asset-store.ts";
import { AssetStoreError } from "../../../src/common/errors.ts";

describe("S3AssetStore", () => {
  it("put() writes the object and returns a constructed URL", async () => {
    sendMock.mockResolvedValueOnce({});
    const store = new S3AssetStore({
      bucket: "gen-tbr-assets",
      region: "us-east-1",
      accessKeyId: "id",
      secretAccessKey: "secret",
    });
    const result = await store.put({
      assetId: "asset-1",
      tenantId: "tenant-1",
      contentType: "image/png",
      bytes: Buffer.from("fake-bytes"),
    });
    expect(result.assetId).toBe("asset-1");
    expect(result.url).toContain("tenant-1/assets/asset-1");
  });

  it("put() wraps a send failure in AssetStoreError", async () => {
    sendMock.mockRejectedValueOnce(new Error("network down"));
    const store = new S3AssetStore({
      bucket: "gen-tbr-assets",
      region: "us-east-1",
      accessKeyId: "id",
      secretAccessKey: "secret",
    });
    await expect(
      store.put({ assetId: "asset-2", tenantId: "tenant-1", contentType: "image/png", bytes: Buffer.from("x") }),
    ).rejects.toThrow(AssetStoreError);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/s3-asset-store.test.ts`
Expected: FAIL — file does not exist.

- [ ] **Step 3: Write `infra/storage/s3-asset-store.ts`**

```ts
import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import type { IAssetStore, AssetRef, PutAssetResult } from "../../domain/ports/asset-store.port.ts";
import { AssetStoreError } from "../../common/errors.ts";

export interface S3AssetStoreConfig {
  bucket: string;
  region: string;
  endpoint?: string;
  accessKeyId: string;
  secretAccessKey: string;
  publicBaseUrl?: string;
}

export class S3AssetStore implements IAssetStore {
  private readonly client: S3Client;

  constructor(private readonly config: S3AssetStoreConfig) {
    this.client = new S3Client({
      region: config.region,
      endpoint: config.endpoint,
      credentials: { accessKeyId: config.accessKeyId, secretAccessKey: config.secretAccessKey },
    });
  }

  private keyFor(tenantId: string, assetId: string): string {
    return `tenants/${tenantId}/assets/${assetId}`;
  }

  private urlFor(key: string): string {
    if (this.config.publicBaseUrl) return `${this.config.publicBaseUrl}/${key}`;
    return `https://${this.config.bucket}.s3.${this.config.region}.amazonaws.com/${key}`;
  }

  async put(ref: AssetRef): Promise<PutAssetResult> {
    const key = this.keyFor(ref.tenantId, ref.assetId);
    try {
      await this.client.send(
        new PutObjectCommand({
          Bucket: this.config.bucket,
          Key: key,
          Body: ref.bytes,
          ContentType: ref.contentType,
        }),
      );
    } catch (err) {
      throw new AssetStoreError(err instanceof Error ? err.message : "S3 put failed");
    }
    return { assetId: ref.assetId, url: this.urlFor(key) };
  }

  async get(tenantId: string, assetId: string): Promise<{ url: string } | null> {
    return { url: this.urlFor(this.keyFor(tenantId, assetId)) };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/s3-asset-store.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/infra/storage packages/gen-tbr-starter/tests/unit/infra/s3-asset-store.test.ts
git commit -m "feat: add S3AssetStore adapter"
```

---

### Task 9: `HttpTntClient` adapter (optional port)

**Files:**
- Create: `packages/gen-tbr-starter/src/infra/tnt-client/tnt-client.ts`
- Test: `packages/gen-tbr-starter/tests/unit/infra/tnt-client.test.ts`

**Interfaces:**
- Consumes: `ITntClient` (Task 5).
- Produces: `HttpTntClient` (constructor takes `{ baseUrl }`) — consumed by the factory's `resolveTntClient` in Task 15, which resolves to `undefined` when unconfigured.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/infra/tnt-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpTntClient } from "../../../src/infra/tnt-client/tnt-client.ts";

describe("HttpTntClient", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("notifyTenantSuspended POSTs to the configured base URL", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpTntClient({ baseUrl: "http://gen-tnt.internal" });
    await client.notifyTenantSuspended("tenant-1");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gen-tnt.internal/internal/tenants/tenant-1/suspended",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/tnt-client.test.ts`
Expected: FAIL — file does not exist.

- [ ] **Step 3: Write `infra/tnt-client/tnt-client.ts`**

```ts
import type { ITntClient } from "../../domain/ports/tnt-client.port.ts";

export interface HttpTntClientConfig {
  baseUrl: string;
}

export class HttpTntClient implements ITntClient {
  constructor(private readonly config: HttpTntClientConfig) {}

  async notifyTenantSuspended(tenantId: string): Promise<void> {
    await fetch(`${this.config.baseUrl}/internal/tenants/${tenantId}/suspended`, {
      method: "POST",
    });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/infra/tnt-client.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/infra/tnt-client packages/gen-tbr-starter/tests/unit/infra/tnt-client.test.ts
git commit -m "feat: add HttpTntClient adapter"
```

---

### Task 10: `BrandingService`

**Files:**
- Create: `packages/gen-tbr-starter/src/modules/branding/v1/branding.service.ts`
- Test: `packages/gen-tbr-starter/tests/unit/modules/branding.service.test.ts`

**Interfaces:**
- Consumes: `ITenantBrandingRepo`, `BrandingRecord`, `UpsertBrandingInput`, `PatchBrandingInput` (Task 5); `ITenantDomainRepo` (Task 5, for domain-based manifest lookup); `BrandingNotFoundError`, `BrandingValidationError` (Task 3).
- Produces: `BrandingService` class with methods `get(tenantId)`, `upsert(input)`, `patch(tenantId, input)`, `getManifest({ tenantId?, domain? })` — consumed by `branding.controller.ts` in Task 13.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/modules/branding.service.test.ts
import { describe, it, expect, vi, beforeEach } from "vitest";
import { BrandingService } from "../../../src/modules/branding/v1/branding.service.ts";
import { BrandingNotFoundError, BrandingValidationError } from "../../../src/common/errors.ts";
import type { ITenantBrandingRepo, BrandingRecord } from "../../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../../src/domain/ports/tenant-domain.repository.port.ts";

function makeBrandingRepo(overrides: Partial<ITenantBrandingRepo> = {}): ITenantBrandingRepo {
  return {
    findByTenantId: vi.fn(async () => null),
    upsert: vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as BrandingRecord),
    patch: vi.fn(async () => null),
    ...overrides,
  };
}

function makeDomainRepo(overrides: Partial<ITenantDomainRepo> = {}): ITenantDomainRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(),
    updateStatus: vi.fn(),
    setPrimary: vi.fn(),
    touchLastChecked: vi.fn(),
    ...overrides,
  } as ITenantDomainRepo;
}

describe("BrandingService", () => {
  it("get() throws BrandingNotFoundError when nothing exists", async () => {
    const service = new BrandingService(makeBrandingRepo(), makeDomainRepo());
    await expect(service.get("tenant-1")).rejects.toThrow(BrandingNotFoundError);
  });

  it("upsert() rejects an invalid hex color", async () => {
    const service = new BrandingService(makeBrandingRepo(), makeDomainRepo());
    await expect(
      service.upsert({ tenantId: "tenant-1", displayName: "Acme", primaryColor: "not-a-hex" }),
    ).rejects.toThrow(BrandingValidationError);
  });

  it("upsert() accepts a valid hex color and delegates to the repo", async () => {
    const upsertSpy = vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as BrandingRecord);
    const service = new BrandingService(makeBrandingRepo({ upsert: upsertSpy }), makeDomainRepo());
    const result = await service.upsert({ tenantId: "tenant-1", displayName: "Acme", primaryColor: "#1f6feb" });
    expect(upsertSpy).toHaveBeenCalled();
    expect(result.primaryColor).toBe("#1f6feb");
  });

  it("getManifest({tenantId}) returns the brand directly", async () => {
    const record: BrandingRecord = {
      tenantId: "tenant-1", displayName: "Acme", tagline: null, logoUrl: null, logoDarkUrl: null,
      faviconUrl: null, primaryColor: null, secondaryColor: null, accentColor: null, fontFamily: null,
      theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
    };
    const service = new BrandingService(
      makeBrandingRepo({ findByTenantId: vi.fn(async () => record) }),
      makeDomainRepo(),
    );
    const manifest = await service.getManifest({ tenantId: "tenant-1" });
    expect(manifest.displayName).toBe("Acme");
  });

  it("getManifest({domain}) resolves tenantId via a VERIFIED/ACTIVE domain lookup", async () => {
    const record: BrandingRecord = {
      tenantId: "tenant-9", displayName: "Domain Brand", tagline: null, logoUrl: null, logoDarkUrl: null,
      faviconUrl: null, primaryColor: null, secondaryColor: null, accentColor: null, fontFamily: null,
      theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
    };
    const domainRecord = { tenantId: "tenant-9", status: "ACTIVE" } as TenantDomainRecord;
    const service = new BrandingService(
      makeBrandingRepo({ findByTenantId: vi.fn(async () => record) }),
      makeDomainRepo({ findByDomain: vi.fn(async () => domainRecord) }),
    );
    const manifest = await service.getManifest({ domain: "acme.example.com" });
    expect(manifest.tenantId).toBe("tenant-9");
  });

  it("getManifest() throws BrandingNotFoundError when the domain has no VERIFIED/ACTIVE match", async () => {
    const service = new BrandingService(
      makeBrandingRepo(),
      makeDomainRepo({ findByDomain: vi.fn(async () => null) }),
    );
    await expect(service.getManifest({ domain: "nowhere.example.com" })).rejects.toThrow(BrandingNotFoundError);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/modules/branding.service.test.ts`
Expected: FAIL — file does not exist.

- [ ] **Step 3: Write `modules/branding/v1/branding.service.ts`**

```ts
import type { ITenantBrandingRepo, BrandingRecord, UpsertBrandingInput, PatchBrandingInput } from "../../../domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../../domain/ports/tenant-domain.repository.port.ts";
import { BrandingNotFoundError, BrandingValidationError } from "../../../common/errors.ts";

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

function assertValidColors(input: { primaryColor?: string | null; secondaryColor?: string | null; accentColor?: string | null }): void {
  for (const [field, value] of Object.entries(input)) {
    if (value != null && !HEX_COLOR.test(value)) {
      throw new BrandingValidationError(`${field} must be a 6-digit hex color, got "${value}"`);
    }
  }
}

export class BrandingService {
  constructor(
    private readonly brandingRepo: ITenantBrandingRepo,
    private readonly domainRepo: ITenantDomainRepo,
  ) {}

  async get(tenantId: string): Promise<BrandingRecord> {
    const record = await this.brandingRepo.findByTenantId(tenantId);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }

  async upsert(input: UpsertBrandingInput): Promise<BrandingRecord> {
    assertValidColors(input);
    return this.brandingRepo.upsert(input);
  }

  async patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord> {
    assertValidColors(input);
    const record = await this.brandingRepo.patch(tenantId, input);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }

  async getManifest(opts: { tenantId?: string; domain?: string }): Promise<BrandingRecord> {
    let tenantId = opts.tenantId;
    if (!tenantId && opts.domain) {
      const domainRecord = await this.domainRepo.findByDomain(opts.domain);
      if (!domainRecord || (domainRecord.status !== "VERIFIED" && domainRecord.status !== "ACTIVE")) {
        throw new BrandingNotFoundError(opts.domain);
      }
      tenantId = domainRecord.tenantId;
    }
    if (!tenantId) throw new BrandingNotFoundError("unknown");
    const record = await this.brandingRepo.findByTenantId(tenantId);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/modules/branding.service.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/modules/branding/v1/branding.service.ts packages/gen-tbr-starter/tests/unit/modules/branding.service.test.ts
git commit -m "feat: add BrandingService"
```

---

### Task 11: `DomainService`

**Files:**
- Create: `packages/gen-tbr-starter/src/modules/domains/v1/domain.service.ts`
- Test: `packages/gen-tbr-starter/tests/unit/modules/domain.service.test.ts`

**Interfaces:**
- Consumes: `ITenantDomainRepo`, `TenantDomainRecord`, `CreateDomainInput` (Task 5); `IDnsVerifier` (Task 5); `DomainNotFoundError`, `DomainVerificationFailedError`, `InvalidDomainStatusTransitionError` (Task 3).
- Produces: `DomainService` class with methods `claim({tenantId, domain})`, `list(tenantId, opts)`, `verify(id)`, `activate(id)`, `detach(id)`, `setPrimary(tenantId, id)` — consumed by `domain.controller.ts` in Task 14.

- [ ] **Step 1: Write the failing test**

```ts
// packages/gen-tbr-starter/tests/unit/modules/domain.service.test.ts
import { describe, it, expect, vi } from "vitest";
import { DomainService } from "../../../src/modules/domains/v1/domain.service.ts";
import { DomainNotFoundError, DomainVerificationFailedError, InvalidDomainStatusTransitionError } from "../../../src/common/errors.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../../src/domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../../src/domain/ports/dns-verifier.port.ts";

function record(overrides: Partial<TenantDomainRecord> = {}): TenantDomainRecord {
  return {
    id: "domain-1", tenantId: "tenant-1", domain: "example.com", verificationToken: "a".repeat(64),
    status: "PENDING", verificationMethod: "TXT", verifiedAt: null, lastCheckedAt: null,
    isPrimary: false, createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function makeDomainRepo(overrides: Partial<ITenantDomainRepo> = {}): ITenantDomainRepo {
  return {
    create: vi.fn(async (input) => record(input)),
    findById: vi.fn(async () => record()),
    findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(async () => []),
    updateStatus: vi.fn(async (id, status) => record({ id, status })),
    setPrimary: vi.fn(async (tenantId, id) => record({ id, tenantId, isPrimary: true })),
    touchLastChecked: vi.fn(async () => {}),
    ...overrides,
  } as ITenantDomainRepo;
}

function makeDnsVerifier(overrides: Partial<IDnsVerifier> = {}): IDnsVerifier {
  return {
    resolveTxt: vi.fn(async () => []),
    resolveCname: vi.fn(async () => []),
    ...overrides,
  };
}

describe("DomainService", () => {
  it("claim() normalizes the domain (lowercase, trim, strip trailing dot)", async () => {
    const createSpy = vi.fn(async (input) => record(input));
    const service = new DomainService(makeDomainRepo({ create: createSpy }), makeDnsVerifier());
    await service.claim({ tenantId: "tenant-1", domain: "  Example.COM. " });
    expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ domain: "example.com" }));
  });

  it("verify() flips PENDING to VERIFIED on a DNS TXT match", async () => {
    const pending = record({ status: "PENDING", verificationMethod: "TXT", verificationToken: "abc123" });
    const updateSpy = vi.fn(async (id, status) => record({ id, status }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending), updateStatus: updateSpy }),
      makeDnsVerifier({ resolveTxt: vi.fn(async () => [["gen-tbr-verify=abc123"]]) }),
    );
    const result = await service.verify("domain-1");
    expect(result.status).toBe("VERIFIED");
    expect(updateSpy).toHaveBeenCalledWith("domain-1", "VERIFIED", expect.any(Object));
  });

  it("verify() throws DomainVerificationFailedError on no match, and still touches lastCheckedAt", async () => {
    const pending = record({ status: "PENDING", verificationMethod: "TXT", verificationToken: "abc123" });
    const touchSpy = vi.fn(async () => {});
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending), touchLastChecked: touchSpy }),
      makeDnsVerifier({ resolveTxt: vi.fn(async () => [["gen-tbr-verify=WRONG"]]) }),
    );
    await expect(service.verify("domain-1")).rejects.toThrow(DomainVerificationFailedError);
    expect(touchSpy).toHaveBeenCalled();
  });

  it("verify() throws DomainNotFoundError for an unknown id", async () => {
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => null) }),
      makeDnsVerifier(),
    );
    await expect(service.verify("missing")).rejects.toThrow(DomainNotFoundError);
  });

  it("activate() rejects a non-VERIFIED domain", async () => {
    const pending = record({ status: "PENDING" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending) }),
      makeDnsVerifier(),
    );
    await expect(service.activate("domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("activate() allows VERIFIED to ACTIVE", async () => {
    const verified = record({ status: "VERIFIED" });
    const updateSpy = vi.fn(async (id, status) => record({ id, status }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => verified), updateStatus: updateSpy }),
      makeDnsVerifier(),
    );
    const result = await service.activate("domain-1");
    expect(result.status).toBe("ACTIVE");
  });

  it("detach() rejects an already-DETACHED domain", async () => {
    const detached = record({ status: "DETACHED" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => detached) }),
      makeDnsVerifier(),
    );
    await expect(service.detach("domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("setPrimary() delegates to the repo's atomic setPrimary", async () => {
    const active = record({ status: "ACTIVE" });
    const setPrimarySpy = vi.fn(async (tenantId, id) => record({ id, tenantId, isPrimary: true }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => active), setPrimary: setPrimarySpy }),
      makeDnsVerifier(),
    );
    const result = await service.setPrimary("tenant-1", "domain-1");
    expect(result.isPrimary).toBe(true);
    expect(setPrimarySpy).toHaveBeenCalledWith("tenant-1", "domain-1");
  });

  it("setPrimary() rejects a non-ACTIVE domain", async () => {
    const pending = record({ status: "PENDING" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending) }),
      makeDnsVerifier(),
    );
    await expect(service.setPrimary("tenant-1", "domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/modules/domain.service.test.ts`
Expected: FAIL — file does not exist.

- [ ] **Step 3: Write `modules/domains/v1/domain.service.ts`**

```ts
import * as crypto from "node:crypto";
import type { ITenantDomainRepo, TenantDomainRecord, CreateDomainInput } from "../../../domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../../domain/ports/dns-verifier.port.ts";
import { DomainNotFoundError, DomainVerificationFailedError, InvalidDomainStatusTransitionError } from "../../../common/errors.ts";
import type { DomainVerificationMethod } from "../../../common/types.ts";

function normalizeDomain(raw: string): string {
  return raw.trim().toLowerCase().replace(/\.$/, "");
}

function txtRecordName(domain: string): string {
  return `_gen-tbr-verify.${domain}`;
}

function cnameRecordName(domain: string): string {
  return `_gen-tbr.${domain}`;
}

export class DomainService {
  constructor(
    private readonly domainRepo: ITenantDomainRepo,
    private readonly dnsVerifier: IDnsVerifier,
  ) {}

  async claim(input: { tenantId: string; domain: string; verificationMethod?: DomainVerificationMethod }): Promise<TenantDomainRecord> {
    const domain = normalizeDomain(input.domain);
    const verificationToken = crypto.randomBytes(32).toString("hex");
    const create: CreateDomainInput = {
      tenantId: input.tenantId,
      domain,
      verificationToken,
      verificationMethod: input.verificationMethod ?? "TXT",
    };
    return this.domainRepo.create(create);
  }

  async list(tenantId: string, opts?: { includeDetached?: boolean }): Promise<TenantDomainRecord[]> {
    return this.domainRepo.listByTenant(tenantId, opts);
  }

  private async findOrThrow(id: string): Promise<TenantDomainRecord> {
    const record = await this.domainRepo.findById(id);
    if (!record) throw new DomainNotFoundError(id);
    return record;
  }

  async verify(id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    const now = new Date();

    let matched = false;
    if (domainRecord.verificationMethod === "TXT") {
      const records = await this.dnsVerifier.resolveTxt(txtRecordName(domainRecord.domain));
      matched = records.some((set) => set.join("").includes(domainRecord.verificationToken));
    } else {
      const records = await this.dnsVerifier.resolveCname(cnameRecordName(domainRecord.domain));
      matched = records.some((value) => value.includes(domainRecord.verificationToken));
    }

    if (!matched) {
      await this.domainRepo.touchLastChecked(id, now);
      throw new DomainVerificationFailedError(domainRecord.domain);
    }

    return this.domainRepo.updateStatus(id, "VERIFIED", { verifiedAt: now, lastCheckedAt: now });
  }

  async activate(id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.status !== "VERIFIED") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "ACTIVE");
    }
    return this.domainRepo.updateStatus(id, "ACTIVE");
  }

  async detach(id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.status === "DETACHED") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "DETACHED");
    }
    return this.domainRepo.updateStatus(id, "DETACHED");
  }

  async setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.status !== "ACTIVE") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "PRIMARY");
    }
    return this.domainRepo.setPrimary(tenantId, id);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/modules/domain.service.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-tbr-starter/src/modules/domains/v1/domain.service.ts packages/gen-tbr-starter/tests/unit/modules/domain.service.test.ts
git commit -m "feat: add DomainService"
```

---

### Task 12: Middleware — error handler, internal secret, tenant-id validation

**Files:**
- Create: `packages/gen-tbr-starter/src/middleware/error-handler.ts`
- Create: `packages/gen-tbr-starter/src/middleware/internal-secret.ts`
- Create: `packages/gen-tbr-starter/src/middleware/validate-tenant-id.ts`
- Test: `packages/gen-tbr-starter/tests/unit/middleware/error-handler.test.ts`
- Test: `packages/gen-tbr-starter/tests/unit/middleware/internal-secret.test.ts`

**Interfaces:**
- Consumes: `AppError`, `logger` (Task 3).
- Produces: `errorHandler` (Express error middleware), `internalSecret(secret: string)` (returns middleware), `validateTenantId` (Express middleware validating `req.params.tenantId` as a UUID, throwing `BrandingValidationError`-shaped 400 via `AppError`) — consumed by every router in Tasks 13–14 and the factory in Task 15.

- [ ] **Step 1: Write the failing tests**

```ts
// packages/gen-tbr-starter/tests/unit/middleware/error-handler.test.ts
import { describe, it, expect } from "vitest";
import express from "express";
import request from "supertest";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { BrandingNotFoundError } from "../../../src/common/errors.ts";

describe("errorHandler", () => {
  it("maps an AppError to its statusCode and { error, message } body", async () => {
    const app = express();
    app.get("/boom", () => {
      throw new BrandingNotFoundError("tenant-1");
    });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "BRANDING_NOT_FOUND", message: expect.stringContaining("tenant-1") });
  });

  it("maps an unknown Error to a 500 with a generic message", async () => {
    const app = express();
    app.get("/boom", () => {
      throw new Error("something unexpected");
    });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("INTERNAL_ERROR");
  });
});
```

```ts
// packages/gen-tbr-starter/tests/unit/middleware/internal-secret.test.ts
import { describe, it, expect } from "vitest";
import express from "express";
import request from "supertest";
import { internalSecret } from "../../../src/middleware/internal-secret.ts";
import { errorHandler } from "../../../src/middleware/error-handler.ts";

describe("internalSecret", () => {
  function buildApp(secret: string) {
    const app = express();
    app.get("/internal/ping", internalSecret(secret), (_req, res) => res.json({ ok: true }));
    app.use(errorHandler);
    return app;
  }

  it("401s when the header is missing", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping");
    expect(res.status).toBe(401);
  });

  it("401s when the header doesn't match", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping").set("X-Internal-Secret", "wrong");
    expect(res.status).toBe(401);
  });

  it("passes through when the header matches", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping").set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/middleware`
Expected: FAIL — files do not exist.

- [ ] **Step 3: Write `middleware/error-handler.ts`**

```ts
import type { Request, Response, NextFunction } from "express";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({ error: err.code, message: err.message });
    return;
  }
  logger.error({ err }, "unhandled error");
  res.status(500).json({ error: "INTERNAL_ERROR", message: "Internal server error" });
}
```

- [ ] **Step 4: Write `middleware/internal-secret.ts`**

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

- [ ] **Step 5: Write `middleware/validate-tenant-id.ts`**

```ts
import type { Request, Response, NextFunction } from "express";
import { z } from "zod";
import { BrandingValidationError } from "../common/errors.ts";

const uuidSchema = z.string().uuid();

export function validateTenantId(req: Request, _res: Response, next: NextFunction): void {
  const result = uuidSchema.safeParse(req.params.tenantId);
  if (!result.success) {
    next(new BrandingValidationError(`tenantId must be a UUID, got "${req.params.tenantId}"`));
    return;
  }
  next();
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/middleware`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-tbr-starter/src/middleware packages/gen-tbr-starter/tests/unit/middleware
git commit -m "feat: add error-handler, internal-secret, and tenant-id validation middleware"
```

---

### Task 13: Branding + assets router/controller

**Files:**
- Create: `packages/gen-tbr-starter/src/modules/branding/v1/branding.controller.ts`
- Create: `packages/gen-tbr-starter/src/modules/branding/v1/branding.router.ts`
- Test: `packages/gen-tbr-starter/tests/http/branding.http.test.ts`

**Interfaces:**
- Consumes: `BrandingService` (Task 10), `IAssetStore` (Task 5), `validateTenantId`, `internalSecret` (Task 12), `AssetNotFoundError` (Task 3).
- Produces: `createBrandingRouter(deps: { brandingService: BrandingService; assetStore?: IAssetStore; internalSecretValue: string; assetsEnabled: boolean }): Router` — mounted by the factory at `/api/v1/branding` in Task 15.

- [ ] **Step 1: Write the failing HTTP test**

```ts
// packages/gen-tbr-starter/tests/http/branding.http.test.ts
import { describe, it, expect, vi, beforeEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { createBrandingRouter } from "../../src/modules/branding/v1/branding.router.ts";
import { BrandingService } from "../../src/modules/branding/v1/branding.service.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ITenantBrandingRepo, BrandingRecord } from "../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../src/domain/ports/tenant-domain.repository.port.ts";

function buildApp() {
  const record: BrandingRecord = {
    tenantId: "11111111-1111-1111-1111-111111111111", displayName: "Acme", tagline: null,
    logoUrl: null, logoDarkUrl: null, faviconUrl: null, primaryColor: null, secondaryColor: null,
    accentColor: null, fontFamily: null, theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
  };
  const brandingRepo: ITenantBrandingRepo = {
    findByTenantId: vi.fn(async (id) => (id === record.tenantId ? record : null)),
    upsert: vi.fn(async (input) => ({ ...record, ...input })),
    patch: vi.fn(async (id, input) => (id === record.tenantId ? { ...record, ...input } : null)),
  };
  const domainRepo: ITenantDomainRepo = {
    create: vi.fn(), findById: vi.fn(), findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(), updateStatus: vi.fn(), setPrimary: vi.fn(), touchLastChecked: vi.fn(),
  } as ITenantDomainRepo;
  const service = new BrandingService(brandingRepo, domainRepo);

  const app = express();
  app.use(express.json());
  app.use(
    "/api/v1/branding",
    createBrandingRouter({ brandingService: service, internalSecretValue: "s3cret", assetsEnabled: false }),
  );
  app.use(errorHandler);
  return { app, record };
}

describe("branding router", () => {
  it("GET /:tenantId requires the internal secret", async () => {
    const { app, record } = buildApp();
    const res = await request(app).get(`/api/v1/branding/${record.tenantId}`);
    expect(res.status).toBe(401);
  });

  it("GET /:tenantId returns the brand with the secret header", async () => {
    const { app, record } = buildApp();
    const res = await request(app)
      .get(`/api/v1/branding/${record.tenantId}`)
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme");
  });

  it("GET /:tenantId/manifest is genuinely unauthenticated", async () => {
    const { app, record } = buildApp();
    const res = await request(app).get(`/api/v1/branding/${record.tenantId}/manifest`);
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme");
  });

  it("PUT /:tenantId upserts with the secret header", async () => {
    const { app, record } = buildApp();
    const res = await request(app)
      .put(`/api/v1/branding/${record.tenantId}`)
      .set("X-Internal-Secret", "s3cret")
      .send({ displayName: "Acme Updated" });
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme Updated");
  });

  it("GET /:tenantId/manifest 404s for an unknown tenant", async () => {
    const { app } = buildApp();
    const res = await request(app).get("/api/v1/branding/22222222-2222-2222-2222-222222222222/manifest");
    expect(res.status).toBe(404);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/http/branding.http.test.ts`
Expected: FAIL — router/controller files do not exist.

- [ ] **Step 3: Write `modules/branding/v1/branding.controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { BrandingService } from "./branding.service.ts";
import type { IAssetStore } from "../../../domain/ports/asset-store.port.ts";
import { AssetNotFoundError } from "../../../common/errors.ts";

const upsertBodySchema = z.object({
  displayName: z.string().min(1).max(120),
  tagline: z.string().max(200).nullish(),
  logoUrl: z.string().url().nullish(),
  logoDarkUrl: z.string().url().nullish(),
  faviconUrl: z.string().url().nullish(),
  primaryColor: z.string().nullish(),
  secondaryColor: z.string().nullish(),
  accentColor: z.string().nullish(),
  fontFamily: z.string().max(120).nullish(),
  theme: z.enum(["SYSTEM", "LIGHT", "DARK"]).optional(),
  rawMeta: z.unknown().optional(),
});

const patchBodySchema = upsertBodySchema.partial();

export function makeBrandingController(service: BrandingService, assetStore?: IAssetStore) {
  return {
    async get(req: Request, res: Response) {
      const record = await service.get(req.params.tenantId);
      res.json(record);
    },

    async upsert(req: Request, res: Response) {
      const body = upsertBodySchema.parse(req.body);
      const record = await service.upsert({ tenantId: req.params.tenantId, ...body });
      res.json(record);
    },

    async patch(req: Request, res: Response) {
      const body = patchBodySchema.parse(req.body);
      const record = await service.patch(req.params.tenantId, body);
      res.json(record);
    },

    async manifest(req: Request, res: Response) {
      const domain = typeof req.query.domain === "string" ? req.query.domain : undefined;
      const record = domain
        ? await service.getManifest({ domain })
        : await service.getManifest({ tenantId: req.params.tenantId });
      res.json(record);
    },

    async uploadAsset(req: Request, res: Response) {
      if (!assetStore) throw new AssetNotFoundError("assets module disabled");
      const file = (req as Request & { file?: Express.Multer.File }).file;
      if (!file) throw new AssetNotFoundError("no file uploaded");
      const assetId = crypto.randomUUID();
      const result = await assetStore.put({
        assetId,
        tenantId: req.params.tenantId,
        contentType: file.mimetype,
        bytes: file.buffer,
      });
      res.json(result);
    },

    async getAsset(req: Request, res: Response) {
      if (!assetStore) throw new AssetNotFoundError(req.params.assetId);
      const found = await assetStore.get(req.params.tenantId, req.params.assetId);
      if (!found) throw new AssetNotFoundError(req.params.assetId);
      res.json(found);
    },
  };
}
```

- [ ] **Step 4: Write `modules/branding/v1/branding.router.ts`**

```ts
import { Router } from "express";
import multer from "multer";
import { makeBrandingController } from "./branding.controller.ts";
import type { BrandingService } from "./branding.service.ts";
import type { IAssetStore } from "../../../domain/ports/asset-store.port.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface BrandingRouterDeps {
  brandingService: BrandingService;
  assetStore?: IAssetStore;
  internalSecretValue: string;
  assetsEnabled: boolean;
}

export function createBrandingRouter(deps: BrandingRouterDeps): Router {
  const router = Router();
  const controller = makeBrandingController(deps.brandingService, deps.assetStore);
  const requireSecret = internalSecret(deps.internalSecretValue);
  const upload = multer({ storage: multer.memoryStorage() });

  router.get("/:tenantId/manifest", validateTenantId, (req, res, next) => {
    controller.manifest(req, res).catch(next);
  });
  router.get("/manifest", (req, res, next) => {
    controller.manifest(req, res).catch(next);
  });

  router.get("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.get(req, res).catch(next);
  });
  router.put("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.upsert(req, res).catch(next);
  });
  router.patch("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.patch(req, res).catch(next);
  });

  if (deps.assetsEnabled) {
    router.post(
      "/:tenantId/assets",
      requireSecret,
      validateTenantId,
      upload.single("file"),
      (req, res, next) => {
        controller.uploadAsset(req, res).catch(next);
      },
    );
    router.get("/:tenantId/assets/:assetId", requireSecret, validateTenantId, (req, res, next) => {
      controller.getAsset(req, res).catch(next);
    });
  }

  return router;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/http/branding.http.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-tbr-starter/src/modules/branding/v1/branding.controller.ts packages/gen-tbr-starter/src/modules/branding/v1/branding.router.ts packages/gen-tbr-starter/tests/http/branding.http.test.ts
git commit -m "feat: add branding+assets router and controller"
```

---

### Task 14: Domains router/controller

**Files:**
- Create: `packages/gen-tbr-starter/src/modules/domains/v1/domain.controller.ts`
- Create: `packages/gen-tbr-starter/src/modules/domains/v1/domain.router.ts`
- Test: `packages/gen-tbr-starter/tests/http/domains.http.test.ts`

**Interfaces:**
- Consumes: `DomainService` (Task 11), `internalSecret` (Task 12).
- Produces: `createDomainsRouter(deps: { domainService: DomainService; internalSecretValue: string }): Router` — mounted by the factory at `/api/v1/domains` in Task 15.

- [ ] **Step 1: Write the failing HTTP test covering the full claim→verify→activate→primary→detach flow**

```ts
// packages/gen-tbr-starter/tests/http/domains.http.test.ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { createDomainsRouter } from "../../src/modules/domains/v1/domain.router.ts";
import { DomainService } from "../../src/modules/domains/v1/domain.service.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../src/domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../src/domain/ports/dns-verifier.port.ts";

function buildApp() {
  const store = new Map<string, TenantDomainRecord>();
  let seq = 0;

  const domainRepo: ITenantDomainRepo = {
    create: vi.fn(async (input) => {
      const rec: TenantDomainRecord = {
        id: `domain-${++seq}`, tenantId: input.tenantId, domain: input.domain,
        verificationToken: input.verificationToken, status: "PENDING",
        verificationMethod: input.verificationMethod, verifiedAt: null, lastCheckedAt: null,
        isPrimary: false, createdAt: new Date(), updatedAt: new Date(),
      };
      store.set(rec.id, rec);
      return rec;
    }),
    findById: vi.fn(async (id) => store.get(id) ?? null),
    findByDomain: vi.fn(async (domain) => [...store.values()].find((r) => r.domain === domain) ?? null),
    listByTenant: vi.fn(async (tenantId, opts) =>
      [...store.values()].filter((r) => r.tenantId === tenantId && (opts?.includeDetached || r.status !== "DETACHED")),
    ),
    updateStatus: vi.fn(async (id, status, fields) => {
      const rec = store.get(id)!;
      const updated = { ...rec, status, ...fields };
      store.set(id, updated);
      return updated;
    }),
    setPrimary: vi.fn(async (tenantId, id) => {
      for (const [key, rec] of store) {
        if (rec.tenantId === tenantId) store.set(key, { ...rec, isPrimary: key === id });
      }
      return store.get(id)!;
    }),
    touchLastChecked: vi.fn(async (id, at) => {
      const rec = store.get(id)!;
      store.set(id, { ...rec, lastCheckedAt: at });
    }),
  };

  const dnsVerifier: IDnsVerifier = {
    resolveTxt: vi.fn(async () => {
      const rec = [...store.values()][0];
      return [[`gen-tbr-verify=${rec.verificationToken}`]];
    }),
    resolveCname: vi.fn(async () => []),
  };

  const service = new DomainService(domainRepo, dnsVerifier);
  const app = express();
  app.use(express.json());
  app.use("/api/v1/domains", createDomainsRouter({ domainService: service, internalSecretValue: "s3cret" }));
  app.use(errorHandler);
  return { app };
}

describe("domains router", () => {
  it("runs the full claim -> verify -> activate -> primary -> detach lifecycle", async () => {
    const { app } = buildApp();
    const auth = (req: any) => req.set("X-Internal-Secret", "s3cret");

    const create = await auth(request(app).post("/api/v1/domains")).send({
      tenantId: "11111111-1111-1111-1111-111111111111",
      domain: "Example.COM",
    });
    expect(create.status).toBe(201);
    expect(create.body.status).toBe("PENDING");
    expect(create.body.domain).toBe("example.com");
    const id = create.body.id;

    const verify = await auth(request(app).post(`/api/v1/domains/${id}/verify`));
    expect(verify.status).toBe(200);
    expect(verify.body.status).toBe("VERIFIED");

    const activate = await auth(request(app).post(`/api/v1/domains/${id}/activate`));
    expect(activate.status).toBe(200);
    expect(activate.body.status).toBe("ACTIVE");

    const primary = await auth(
      request(app).post(`/api/v1/domains/${id}/primary`).send({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(primary.status).toBe(200);
    expect(primary.body.isPrimary).toBe(true);

    const detach = await auth(request(app).post(`/api/v1/domains/${id}/detach`));
    expect(detach.status).toBe(200);
    expect(detach.body.status).toBe("DETACHED");

    const list = await auth(
      request(app).get("/api/v1/domains").query({ tenantId: "11111111-1111-1111-1111-111111111111" }),
    );
    expect(list.body).toEqual([]);
  });

  it("requires the internal secret on every mutating route", async () => {
    const { app } = buildApp();
    const res = await request(app).post("/api/v1/domains").send({ tenantId: "t1", domain: "x.com" });
    expect(res.status).toBe(401);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/http/domains.http.test.ts`
Expected: FAIL — router/controller files do not exist.

- [ ] **Step 3: Write `modules/domains/v1/domain.controller.ts`**

```ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { DomainService } from "./domain.service.ts";
import { BrandingValidationError } from "../../../common/errors.ts";

const claimBodySchema = z.object({
  tenantId: z.string().uuid(),
  domain: z.string().min(1).max(253),
  verificationMethod: z.enum(["TXT", "CNAME"]).optional(),
});

const primaryBodySchema = z.object({
  tenantId: z.string().uuid(),
});

export function makeDomainController(service: DomainService) {
  return {
    async create(req: Request, res: Response) {
      const body = claimBodySchema.parse(req.body);
      const record = await service.claim(body);
      res.status(201).json(record);
    },

    async list(req: Request, res: Response) {
      const tenantId = req.query.tenantId;
      if (typeof tenantId !== "string") {
        throw new BrandingValidationError("tenantId query param is required");
      }
      const records = await service.list(tenantId);
      res.json(records);
    },

    async verify(req: Request, res: Response) {
      const record = await service.verify(req.params.id);
      res.json(record);
    },

    async activate(req: Request, res: Response) {
      const record = await service.activate(req.params.id);
      res.json(record);
    },

    async detach(req: Request, res: Response) {
      const record = await service.detach(req.params.id);
      res.json(record);
    },

    async primary(req: Request, res: Response) {
      const body = primaryBodySchema.parse(req.body);
      const record = await service.setPrimary(body.tenantId, req.params.id);
      res.json(record);
    },
  };
}
```

- [ ] **Step 4: Write `modules/domains/v1/domain.router.ts`**

```ts
import { Router } from "express";
import { makeDomainController } from "./domain.controller.ts";
import type { DomainService } from "./domain.service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface DomainsRouterDeps {
  domainService: DomainService;
  internalSecretValue: string;
}

export function createDomainsRouter(deps: DomainsRouterDeps): Router {
  const router = Router();
  const controller = makeDomainController(deps.domainService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.post("/", requireSecret, (req, res, next) => controller.create(req, res).catch(next));
  router.get("/", requireSecret, (req, res, next) => controller.list(req, res).catch(next));
  router.post("/:id/verify", requireSecret, (req, res, next) => controller.verify(req, res).catch(next));
  router.post("/:id/activate", requireSecret, (req, res, next) => controller.activate(req, res).catch(next));
  router.post("/:id/detach", requireSecret, (req, res, next) => controller.detach(req, res).catch(next));
  router.post("/:id/primary", requireSecret, (req, res, next) => controller.primary(req, res).catch(next));

  return router;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/http/domains.http.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-tbr-starter/src/modules/domains/v1/domain.controller.ts packages/gen-tbr-starter/src/modules/domains/v1/domain.router.ts packages/gen-tbr-starter/tests/http/domains.http.test.ts
git commit -m "feat: add domains router and controller"
```

---

### Task 15: `createGenTbr` factory + public barrel

**Files:**
- Create: `packages/gen-tbr-starter/src/create-gen-tbr.ts`
- Modify: `packages/gen-tbr-starter/src/index.ts`
- Test: `packages/gen-tbr-starter/tests/unit/create-gen-tbr.test.ts`

**Interfaces:**
- Consumes: everything from Tasks 3–14 (`getPrismaClient`, `PrismaTenantBrandingRepo`, `PrismaTenantDomainRepo`, `S3AssetStore`, `NodeDnsVerifier`, `HttpTntClient`, `BrandingService`, `DomainService`, `createBrandingRouter`, `createDomainsRouter`, `errorHandler`, `requireEnv`, `optionalEnv`, `GenTbrConfigError`).
- Produces: `createGenTbr(config: GenTbrConfig): GenTbrInstance`, `GenTbrConfig`, `GenTbrModulesConfig`, `GenTbrInstance` — the public entry point re-exported by `index.ts`; nothing later consumes this (it's the top of the dependency graph).

- [ ] **Step 1: Write the failing tests**

```ts
// packages/gen-tbr-starter/tests/unit/create-gen-tbr.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import request from "supertest";
import { createGenTbr } from "../../src/create-gen-tbr.ts";
import { GenTbrConfigError } from "../../src/common/errors.ts";
import type { ITenantBrandingRepo } from "../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../src/domain/ports/tenant-domain.repository.port.ts";

const fakeBrandingRepo: ITenantBrandingRepo = {
  findByTenantId: vi.fn(async () => null),
  upsert: vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as any),
  patch: vi.fn(async () => null),
};
const fakeDomainRepo: ITenantDomainRepo = {
  create: vi.fn(), findById: vi.fn(), findByDomain: vi.fn(async () => null),
  listByTenant: vi.fn(async () => []), updateStatus: vi.fn(), setPrimary: vi.fn(), touchLastChecked: vi.fn(),
} as ITenantDomainRepo;

describe("createGenTbr", () => {
  const ORIGINAL_ENV = { ...process.env };

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV };
  });
  afterEach(() => {
    process.env = { ...ORIGINAL_ENV };
  });

  it("boots cleanly with injected repos and no env required", () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { assets: false },
    });
    expect(instance.app).toBeDefined();
  });

  it("throws GenTbrConfigError when no DATABASE_URL and no repo override is given", () => {
    delete process.env.DATABASE_URL;
    expect(() => createGenTbr({ internalSecret: "s3cret" })).toThrow(GenTbrConfigError);
  });

  it("a disabled module's routes 404 instead of merely being undocumented", async () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { domains: false, assets: false },
    });
    const res = await request(instance.app)
      .get("/api/v1/domains")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(404);
  });

  it("GET /health returns ok unauthenticated", async () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { assets: false },
    });
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/create-gen-tbr.test.ts`
Expected: FAIL — `src/create-gen-tbr.ts` does not exist.

- [ ] **Step 3: Write `create-gen-tbr.ts`**

```ts
import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaTenantBrandingRepo } from "./modules/branding/v1/repo.ts";
import { PrismaTenantDomainRepo } from "./modules/domains/v1/repo.ts";
import { S3AssetStore } from "./infra/storage/s3-asset-store.ts";
import { NodeDnsVerifier } from "./infra/dns/node-dns-verifier.ts";
import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
import { BrandingService } from "./modules/branding/v1/branding.service.ts";
import { DomainService } from "./modules/domains/v1/domain.service.ts";
import { createBrandingRouter } from "./modules/branding/v1/branding.router.ts";
import { createDomainsRouter } from "./modules/domains/v1/domain.router.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { requireEnv, optionalEnv } from "./config/env.ts";
import { GenTbrConfigError } from "./common/errors.ts";
import type { ITenantBrandingRepo } from "./domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "./domain/ports/tenant-domain.repository.port.ts";
import type { IAssetStore } from "./domain/ports/asset-store.port.ts";
import type { IDnsVerifier } from "./domain/ports/dns-verifier.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export interface GenTbrModulesConfig {
  branding?: boolean;
  domains?: boolean;
  manifest?: boolean;
  assets?: boolean;
}

export interface GenTbrConfig {
  brandingRepo?: ITenantBrandingRepo;
  domainRepo?: ITenantDomainRepo;
  assetStore?: IAssetStore;
  dnsVerifier?: IDnsVerifier;
  tntClient?: ITntClient;
  modules?: GenTbrModulesConfig;
  internalSecret?: string;
}

export interface GenTbrInstance {
  app: Express;
}

function resolveBrandingRepo(override?: ITenantBrandingRepo): ITenantBrandingRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantBrandingRepo(getPrismaClient());
}

function resolveDomainRepo(override?: ITenantDomainRepo): ITenantDomainRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantDomainRepo(getPrismaClient());
}

function resolveAssetStore(override?: IAssetStore): IAssetStore {
  if (override) return override;
  return new S3AssetStore({
    bucket: requireEnv("GEN_TBR_S3_BUCKET"),
    region: requireEnv("GEN_TBR_S3_REGION"),
    endpoint: optionalEnv("GEN_TBR_S3_ENDPOINT"),
    accessKeyId: requireEnv("GEN_TBR_S3_ACCESS_KEY_ID"),
    secretAccessKey: requireEnv("GEN_TBR_S3_SECRET_ACCESS_KEY"),
  });
}

function resolveDnsVerifier(override?: IDnsVerifier): IDnsVerifier {
  return override ?? new NodeDnsVerifier();
}

function resolveTntClient(override?: ITntClient): ITntClient | undefined {
  if (override) return override;
  const baseUrl = optionalEnv("GEN_TNT_BASE_URL");
  return baseUrl ? new HttpTntClient({ baseUrl }) : undefined;
}

function resolveInternalSecret(override?: string): string {
  return override ?? requireEnv("GEN_TBR_INTERNAL_SECRET");
}

export function createGenTbr(config: GenTbrConfig): GenTbrInstance {
  const modules: Required<GenTbrModulesConfig> = {
    branding: config.modules?.branding ?? true,
    domains: config.modules?.domains ?? true,
    manifest: config.modules?.manifest ?? true,
    assets: config.modules?.assets ?? true,
  };

  if (!modules.branding && !modules.domains) {
    throw new GenTbrConfigError("At least one of modules.branding or modules.domains must be enabled");
  }

  const internalSecretValue = resolveInternalSecret(config.internalSecret);

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  if (modules.branding) {
    const brandingRepo = resolveBrandingRepo(config.brandingRepo);
    const domainRepo = resolveDomainRepo(config.domainRepo);
    const assetStore = modules.assets ? resolveAssetStore(config.assetStore) : undefined;
    const brandingService = new BrandingService(brandingRepo, domainRepo);
    app.use(
      "/api/v1/branding",
      createBrandingRouter({
        brandingService,
        assetStore,
        internalSecretValue,
        assetsEnabled: modules.assets,
      }),
    );
  }

  if (modules.domains) {
    const domainRepo = resolveDomainRepo(config.domainRepo);
    const dnsVerifier = resolveDnsVerifier(config.dnsVerifier);
    const domainService = new DomainService(domainRepo, dnsVerifier);
    app.use("/api/v1/domains", createDomainsRouter({ domainService, internalSecretValue }));
  }

  // resolveTntClient is exercised for its resolution behavior even though no v1
  // route consumes it yet (see spec §7.2 — a future re-verify hook).
  resolveTntClient(config.tntClient);

  app.use(errorHandler);

  return { app };
}
```

- [ ] **Step 4: Write `index.ts`**

```ts
export { createGenTbr } from "./create-gen-tbr.ts";
export type { GenTbrConfig, GenTbrModulesConfig, GenTbrInstance } from "./create-gen-tbr.ts";

export type { ITenantBrandingRepo, BrandingRecord, UpsertBrandingInput, PatchBrandingInput } from "./domain/ports/tenant-branding.repository.port.ts";
export type { ITenantDomainRepo, TenantDomainRecord, CreateDomainInput } from "./domain/ports/tenant-domain.repository.port.ts";
export type { IAssetStore, AssetRef, PutAssetResult } from "./domain/ports/asset-store.port.ts";
export type { IDnsVerifier, DnsRecord, DnsRecordKind } from "./domain/ports/dns-verifier.port.ts";
export type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export { PrismaTenantBrandingRepo } from "./modules/branding/v1/repo.ts";
export { PrismaTenantDomainRepo } from "./modules/domains/v1/repo.ts";
export { S3AssetStore } from "./infra/storage/s3-asset-store.ts";
export { NodeDnsVerifier } from "./infra/dns/node-dns-verifier.ts";
export { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export {
  AppError,
  GenTbrConfigError,
  BrandingNotFoundError,
  BrandingValidationError,
  DomainNotFoundError,
  DomainAlreadyClaimedError,
  DomainVerificationFailedError,
  InvalidDomainStatusTransitionError,
  AssetStoreError,
  AssetNotFoundError,
} from "./common/errors.ts";
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd packages/gen-tbr-starter && npx vitest run tests/unit/create-gen-tbr.test.ts`
Expected: PASS.

- [ ] **Step 6: Run the full test suite**

Run: `cd packages/gen-tbr-starter && npx vitest run`
Expected: all suites (unit, http, integration) PASS.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-tbr-starter/src/create-gen-tbr.ts packages/gen-tbr-starter/src/index.ts packages/gen-tbr-starter/tests/unit/create-gen-tbr.test.ts
git commit -m "feat: add createGenTbr factory and public barrel"
```

---

### Task 16: `gen-tbr-demo` app

**Files:**
- Create: `packages/gen-tbr-demo/src/index.ts`

**Interfaces:**
- Consumes: `createGenTbr` from `@gen-ms/gen-tbr-starter` (Task 15).
- Produces: a runnable process listening on port 3400 — consumed by the smoke script in Task 17.

- [ ] **Step 1: Write `packages/gen-tbr-demo/src/index.ts`**

```ts
import { createGenTbr } from "@gen-ms/gen-tbr-starter";

const PORT = process.env.PORT ? Number(process.env.PORT) : 3400;

const { app } = createGenTbr({});

app.listen(PORT, () => {
  console.log(`gen-tbr-demo listening on http://localhost:${PORT}`);
});
```

- [ ] **Step 2: Build and manually smoke-run**

Run:
```bash
docker compose up -d postgres
cd packages/gen-tbr-starter && npx prisma migrate deploy && cd ../..
cp .env.example packages/gen-tbr-demo/.env   # fill in real values first
npm run build --workspaces --if-present
node --env-file=packages/gen-tbr-demo/.env packages/gen-tbr-demo/dist/index.js
```
Expected: process logs `gen-tbr-demo listening on http://localhost:3400` and stays up; `curl http://localhost:3400/health` returns `{"status":"ok"}`.

- [ ] **Step 3: Commit**

```bash
git add packages/gen-tbr-demo/src/index.ts
git commit -m "feat: add gen-tbr-demo reference app"
```

---

### Task 17: Docs + smoke script

**Files:**
- Create: `docs/integration-guide.md`
- Create: `README.md`
- Create: `scripts/smoke-branding.sh`

**Interfaces:**
- Consumes: the running `gen-tbr-demo` from Task 16 (the smoke script drives it over HTTP).
- Produces: the consumer-facing documentation and a repeatable end-to-end smoke check — nothing downstream depends on these programmatically.

- [ ] **Step 1: Write `docs/integration-guide.md`**

```markdown
# Gen_TBR Integration Guide

## Embedding in-process

\`\`\`ts
import { createGenTbr } from "@gen-ms/gen-tbr-starter";

const { app } = createGenTbr({
  internalSecret: process.env.GEN_TBR_INTERNAL_SECRET,
});

hostApp.use("/tbr", app);
\`\`\`

## Running standalone (HTTP)

\`\`\`bash
docker compose up -d postgres
npx prisma migrate deploy --schema packages/gen-tbr-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-tbr-demo/dist/index.js
\`\`\`

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

**Gen_TBR does not terminate TLS or provision certificates.** It verifies domain ownership only; once a domain is `ACTIVE`, the host's edge layer (Caddy, Cloudflare, nginx, a CDN) is responsible for TLS termination and routing.

## Swapping adapters

- `assetStore`: implement `IAssetStore` (`put`/`get`) for local disk, GCS, Azure Blob, etc.
- `dnsVerifier`: implement `IDnsVerifier` (`resolveTxt`/`resolveCname`) to use a DoH resolver instead of the system resolver.

## Known limitations (v1)

- ASCII hostnames only — no IDN/punycode support.
- No background re-verification scheduler — verification is on-demand only (`POST /domains/:id/verify`).
```

- [ ] **Step 2: Write `README.md`**

```markdown
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

\`\`\`bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-tbr-starter/prisma/schema.prisma
npm install
npm run build --workspaces --if-present
node packages/gen-tbr-demo/dist/index.js
\`\`\`

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
```

- [ ] **Step 3: Write `scripts/smoke-branding.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3400}"
SECRET="${GEN_TBR_INTERNAL_SECRET:?set GEN_TBR_INTERNAL_SECRET}"
TENANT_ID="${TENANT_ID:-11111111-1111-1111-1111-111111111111}"

echo "1/5 creating brand..."
curl -sf -X PUT "$BASE_URL/api/v1/branding/$TENANT_ID" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d '{"displayName":"Acme Co","primaryColor":"#1f6feb"}' > /dev/null

echo "2/5 claiming domain..."
DOMAIN_JSON=$(curl -sf -X POST "$BASE_URL/api/v1/domains" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"domain\":\"acme-smoke.example.com\"}")
DOMAIN_ID=$(echo "$DOMAIN_JSON" | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).id))")

echo "3/5 verifying domain (expected to fail without real DNS — smoke stops here for real deploys)..."
curl -s -X POST "$BASE_URL/api/v1/domains/$DOMAIN_ID/verify" -H "X-Internal-Secret: $SECRET" || true

echo "4/5 fetching manifest by tenantId..."
curl -sf "$BASE_URL/api/v1/branding/$TENANT_ID/manifest" | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).displayName))"

echo "5/5 done."
```

- [ ] **Step 4: Make the script executable and run it against the demo**

Run:
```bash
chmod +x scripts/smoke-branding.sh
GEN_TBR_INTERNAL_SECRET=<value from your .env> ./scripts/smoke-branding.sh
```
Expected: prints `Acme Co` on step 4, exits 0.

- [ ] **Step 5: Commit**

```bash
git add docs/integration-guide.md README.md scripts/smoke-branding.sh
git commit -m "docs: add integration guide, README, and smoke script"
```

---

## Self-Review Notes

- **Spec coverage:** every §-numbered SYSTEM_PROMPT.md section and design-spec section maps to a task — scaffolding (Task 1), Prisma model + partial-unique index (Task 2), errors/logger (Task 3), env resolution (Task 4), all 5 ports (Task 5), Prisma adapters + testcontainers (Task 6), DNS/S3/Tnt adapters (Tasks 7-9), both services incl. state machine + primary invariant (Tasks 10-11), middleware incl. the public-manifest exception (Task 12), both routers (Tasks 13-14), the factory with module toggles and lazy `resolveXxx` (Task 15), the demo (Task 16), docs + smoke script (Task 17).
- **Type consistency verified across tasks:** `BrandingRecord`/`UpsertBrandingInput`/`PatchBrandingInput` (Task 5) match field-for-field what `PrismaTenantBrandingRepo` (Task 6), `BrandingService` (Task 10), and `branding.controller.ts` (Task 13) use. `TenantDomainRecord`/`CreateDomainInput` (Task 5) match `PrismaTenantDomainRepo` (Task 6), `DomainService` (Task 11), and `domain.controller.ts` (Task 14). `GenTbrConfig`/`GenTbrModulesConfig`/`GenTbrInstance` (Task 15) match the shape given in the design spec §4 exactly.
- **No placeholders:** every step ships runnable code; no "TBD"/"add error handling later" markers.
