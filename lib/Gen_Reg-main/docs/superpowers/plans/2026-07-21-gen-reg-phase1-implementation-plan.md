# Gen_REG Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_REG Phase 1 — a standalone Node/Express/Prisma service handling tenant signup, email verification, and provisioning handoff to Gen_TNT (via a new Gen_Auth registration call and a new Gen_TNT slug-lookup endpoint).

**Architecture:** Two entrypoints (`src/server.ts` HTTP API, `src/worker.ts` background poller) over a Postgres-backed `SignupSession` state machine (`STARTED → EMAIL_VERIFIED → PROVISIONING → ACTIVE/PROVISION_FAILED`, plus `ABANDONED`). Controller → service → repo layering, ports/adapters for the two external services (Gen_TNT, Gen_Auth).

**Tech Stack:** Node 20+, TypeScript (NodeNext, `.ts`-extension imports rewritten at build), Express 4 + express-async-errors, zod, Prisma/PostgreSQL, vitest + supertest + `@testcontainers/postgresql`, native `fetch` for HTTP clients (no axios/node-fetch dependency).

## Global Constraints

- Repo root: `C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_REG` (git repo already initialized, first commit `4dac613` is the design spec).
- No `@cpms/node-common` dependency — every small utility it provided (token gen/hash, error classes) is reimplemented locally in this repo.
- No MongoDB, Valkey/Redis, RabbitMQ, gRPC, Stripe/Razorpay, Turnstile captcha — all explicitly out of scope per the spec (`docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md`).
- Relative imports use explicit `.ts` extensions (`from "../foo.ts"`) — this is intentional, not a typo; `rewriteRelativeImportExtensions` in `tsconfig.json` rewrites them to `.js` at build time. Matches the org's existing `reg-svc`/`tbr-svc` convention.
- Every mutating/lookup method on `ISignupSessionRepo` and every method on `TntClient`/`AuthClient` defined in this plan is final — don't rename across tasks.

---

### Task 1: Gen_TNT — add slug-lookup endpoint

This task modifies the **Gen_TNT** repo (`C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_TNT`), not Gen_REG. It's a prerequisite every later Gen_REG task depends on (slug checks, both live and authoritative).

**Files:**
- Modify: `gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java`
- Modify: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/TenantController.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/tenants/by-slug/{slug}` → `200` (tenant exists, body is `TenantResponse`) or `404` (free), gated by the existing `InternalSecretFilter`.

- [ ] **Step 1: Write the failing test**

Add to `gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java` (find the existing test class and add these two test methods inside it — check the file first for the exact mock setup pattern already used for `get`/`create`):

```java
    @Test
    void getBySlugReturns200WhenTaken() throws Exception {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).slug("acme").name("Acme")
            .status(TenantStatus.ACTIVE).primaryOwnerUserId(UUID.randomUUID()).build();
        when(tenantService.getTenantBySlug("acme")).thenReturn(tenant);

        mockMvc.perform(get("/api/v1/tenants/by-slug/acme").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void getBySlugReturns404WhenFree() throws Exception {
        when(tenantService.getTenantBySlug("free-slug")).thenThrow(new TenantNotFoundException("free-slug"));

        mockMvc.perform(get("/api/v1/tenants/by-slug/free-slug").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isNotFound());
    }
```

Check `TenantNotFoundException`'s constructor signature first (`gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/TenantNotFoundException.java`) — it currently takes a `UUID`. Add a `String` overload (or a second constructor) since a slug lookup has no UUID to report:

```java
    public TenantNotFoundException(String slug) {
        super("Tenant not found for slug: " + slug);
    }
```

Keep the existing `UUID` constructor as-is — this is an overload, not a replacement.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Gen_TNT && ./gradlew.bat :gen-tnt-starter:test --tests "*TenantControllerTest*" --console=plain`
Expected: FAIL — `getTenantBySlug` is not a method on `TenantService`, compile error.

- [ ] **Step 3: Implement**

Add to `TenantService.java` (after `getTenant`):

```java
    public Tenant getTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug).orElseThrow(() -> new TenantNotFoundException(slug));
    }
```

Add to `TenantController.java` (after the existing `get` method, same `@Tag`/security setup as the rest of the class):

```java
    @Operation(summary = "Check whether a slug is taken", description = "404 means free, 200 means taken — used for real-time signup-form availability checks and as the authoritative pre-create check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slug is taken",
                    content = @Content(schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Slug is free",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/by-slug/{slug}")
    public TenantResponse getBySlug(@PathVariable String slug) {
        return TenantResponse.from(tenantService.getTenantBySlug(slug));
    }
```

(`ErrorResponse` and the `@Operation`/`@ApiResponses` imports are already present in this file from the earlier swagger-annotation pass — reuse them, don't re-import.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Gen_TNT && ./gradlew.bat :gen-tnt-starter:test --tests "*TenantControllerTest*" --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Full suite + commit**

Run: `cd Gen_TNT && ./gradlew.bat :gen-tnt-starter:test --console=plain`
Expected: `BUILD SUCCESSFUL`, no regressions.

```bash
cd Gen_TNT
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java \
        gen-tnt-starter/src/main/java/com/example/tnt_svc/web/TenantController.java \
        gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/TenantNotFoundException.java \
        gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java
git commit -m "feat: add GET /api/v1/tenants/by-slug/{slug} for Gen_REG's signup flow"
```

---

### Task 2: Gen_REG — project scaffold

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `.env.example`
- Create: `.gitignore` (already exists from the design-spec commit — verify it covers `node_modules/`, `dist/`, `.env`, don't duplicate)
- Create: `prisma/schema.prisma`
- Create: `src/config/env.ts`
- Create: `src/common/logger.ts`
- Create: `src/server.ts` (health-check only for now — real routes come in Task 8)
- Test: none (scaffold-only task; verified by successful build + a smoke boot)

**Interfaces:**
- Produces: `env` object (validated config) importable from `../config/env.ts` by every later task. Exact shape below — don't add fields ad-hoc in later tasks, add them here if something's missing.
- Produces: `logger` (pino instance) importable from `../common/logger.ts`.

- [ ] **Step 1: Write package.json**

```json
{
  "name": "@gen-ms/gen-reg",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "tsx watch src/server.ts",
    "dev:worker": "tsx watch src/worker.ts",
    "build": "prisma generate && tsc -p tsconfig.json",
    "start": "node dist/server.js",
    "start:worker": "node dist/worker.js",
    "test": "vitest run",
    "test:watch": "vitest",
    "prisma:generate": "prisma generate",
    "prisma:migrate:dev": "prisma migrate dev",
    "prisma:migrate:deploy": "prisma migrate deploy"
  },
  "dependencies": {
    "@prisma/client": "^5.19.0",
    "dotenv": "^17.4.2",
    "express": "^4.21.0",
    "express-async-errors": "^3.1.1",
    "helmet": "^8.2.0",
    "cors": "^2.8.6",
    "pino": "^9.0.0",
    "pino-http": "^10.0.0",
    "zod": "^3.23.0"
  },
  "devDependencies": {
    "@testcontainers/postgresql": "^10.13.0",
    "@types/cors": "^2.8.19",
    "@types/express": "^4.17.0",
    "@types/node": "^20.19.41",
    "@types/supertest": "^6.0.0",
    "pino-pretty": "^13.1.3",
    "prisma": "^5.19.0",
    "supertest": "^7.0.0",
    "tsx": "^4.0.0",
    "typescript": "^5.7.3",
    "vitest": "^2.0.0"
  }
}
```

- [ ] **Step 2: Write tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "paths": {
      "@prisma/client": ["./src/__generated__/prisma"]
    },
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
  "exclude": ["node_modules", "dist", "tests"]
}
```

- [ ] **Step 3: Write .env.example**

```
DATABASE_URL=postgresql://postgres:postgres@localhost:5436/genreg
PORT=3200
NODE_ENV=development
LOG_LEVEL=info
GEN_TNT_BASE_URL=http://localhost:8201
GEN_TNT_INTERNAL_SECRET=dev-secret
GEN_AUTH_BASE_URL=http://localhost:8101
EMAIL_VERIFICATION_TTL_SECS=86400
RESUME_TOKEN_TTL_SECS=604800
PROVISIONING_POLL_INTERVAL_MS=5000
ABANDON_SWEEP_INTERVAL_MS=300000
```

Port `5436` for Postgres — distinct from PMP CANADA (5434), Gen_Auth, and Gen_TNT (5435), following the same distinct-port convention Gen_TNT's own `docker-compose.yml` documents.

- [ ] **Step 4: Write prisma/schema.prisma**

```prisma
generator client {
  provider = "prisma-client-js"
  output   = "../src/__generated__/prisma"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum SignupState {
  STARTED
  EMAIL_VERIFIED
  PROVISIONING
  ACTIVE
  PROVISION_FAILED
  ABANDONED
}

model SignupSession {
  id                         String      @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  email                      String      @db.VarChar(255)
  companyName                String?     @map("company_name") @db.VarChar(255)
  fullName                   String?     @map("full_name") @db.VarChar(255)
  phone                      String?     @db.VarChar(32)
  source                     String?     @db.VarChar(64)
  referralCode               String?     @map("referral_code") @db.VarChar(64)
  utmSource                  String?     @map("utm_source") @db.VarChar(128)
  utmMedium                  String?     @map("utm_medium") @db.VarChar(128)
  utmCampaign                String?     @map("utm_campaign") @db.VarChar(128)
  desiredSubdomain           String      @map("desired_subdomain") @db.VarChar(63)
  state                      SignupState @default(STARTED)
  emailVerificationTokenHash String?     @map("email_verification_token_hash") @db.VarChar(255)
  emailVerifiedAt            DateTime?   @map("email_verified_at") @db.Timestamptz(6)
  resumeTokenHash            String?     @map("resume_token_hash") @db.VarChar(255)
  authUserId                 String      @map("auth_user_id") @db.Uuid
  provisioningJobId          String?     @map("provisioning_job_id") @db.Uuid
  provisionedTenantId        String?     @map("provisioned_tenant_id") @db.Uuid
  lastProvisioningError      String?     @map("last_provisioning_error") @db.Text
  expiresAt                  DateTime    @map("expires_at") @db.Timestamptz(6)
  createdAt                  DateTime    @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt                  DateTime    @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([emailVerificationTokenHash])
  @@index([resumeTokenHash])
  @@map("signup_session")
}
```

No DB-level unique constraint on `email`/`desiredSubdomain` — matches the original `reg-svc` schema, which enforces "no other active session for this email/slug" as an application-level check (`existsActiveForEmail`/`existsActiveForSubdomain` in Task 4), not a DB constraint, since multiple `ABANDONED` sessions with the same email/slug are legal.

- [ ] **Step 5: Write src/config/env.ts**

```typescript
// src/config/env.ts
import "dotenv/config";
import { z } from "zod";

const EnvSchema = z.object({
  DATABASE_URL: z.string().min(1),
  PORT: z.coerce.number().default(3200),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),
  GEN_TNT_BASE_URL: z.string().url(),
  GEN_TNT_INTERNAL_SECRET: z.string().min(1),
  GEN_AUTH_BASE_URL: z.string().url(),
  EMAIL_VERIFICATION_TTL_SECS: z.coerce.number().default(86400),
  RESUME_TOKEN_TTL_SECS: z.coerce.number().default(604800),
  PROVISIONING_POLL_INTERVAL_MS: z.coerce.number().default(5000),
  ABANDON_SWEEP_INTERVAL_MS: z.coerce.number().default(300000),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;
```

- [ ] **Step 6: Write src/common/logger.ts**

```typescript
// src/common/logger.ts
import pino from "pino";
import { env } from "../config/env.ts";

export const logger = pino({
  level: env.LOG_LEVEL,
  transport: env.NODE_ENV === "development" ? { target: "pino-pretty" } : undefined,
});
```

- [ ] **Step 7: Write src/server.ts (health-check placeholder)**

```typescript
// src/server.ts
import express from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import { env } from "./config/env.ts";
import { logger } from "./common/logger.ts";

export function buildApp() {
  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  return app;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const app = buildApp();
  app.listen(env.PORT, () => {
    logger.info({ port: env.PORT }, "Gen_REG HTTP server started");
  });
}
```

- [ ] **Step 8: Install, generate, migrate, boot-check**

```bash
cd Gen_REG
npm install
```
Expected: installs cleanly.

```bash
cp .env.example .env
```
Then edit `.env` if your local Gen_TNT/Gen_Auth ports differ from the defaults.

Start a local Postgres on port 5436 (a one-off container is fine for now — `docker-compose.yml` comes in Task 10):
```bash
docker run -d --name gen-reg-postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=genreg -p 5436:5432 postgres:15
```

```bash
npm run prisma:migrate:dev -- --name init
```
Expected: creates `prisma/migrations/<timestamp>_init/migration.sql`, applies it, generates the Prisma client into `src/__generated__/prisma`.

```bash
npx tsc -p tsconfig.json --noEmit
```
Expected: no errors.

```bash
npm run dev
```
Expected: logs "Gen_REG HTTP server started", then in another terminal `curl http://localhost:3200/health` returns `{"status":"ok"}`. Stop the dev server (Ctrl+C) once confirmed.

- [ ] **Step 9: Commit**

```bash
git add package.json tsconfig.json .env.example prisma/ src/config/env.ts src/common/logger.ts src/server.ts
git commit -m "feat: scaffold Gen_REG project (Express + Prisma + Postgres)"
```

Note: `package-lock.json` and `src/__generated__/prisma/` should NOT be committed if `.gitignore` doesn't already exclude generated Prisma output — check `.gitignore` covers `src/__generated__/` and `node_modules/` before this commit; add `src/__generated__/` to `.gitignore` if it's missing.

---

### Task 3: Gen_REG — domain layer (state, errors, tokens, slugs, email port)

**Files:**
- Create: `src/domain/enums/signup-state.enum.ts`
- Create: `src/common/errors.ts`
- Create: `src/common/token.ts`
- Create: `src/common/slug.ts`
- Create: `src/config/disposable-domains.ts`
- Create: `src/common/email-sender.ts`
- Test: `src/common/token.test.ts`
- Test: `src/common/slug.test.ts`

**Interfaces:**
- Produces: `SignupState` enum, `TERMINAL_STATES`, `ABANDONABLE_STATES` — consumed by Task 4 (repo), Task 6/7 (services), Task 9 (worker).
- Produces: error classes (see full list below) — consumed by Task 5, 6, 7, 8.
- Produces: `generateToken(bytes?: number): string`, `hashToken(token: string): Promise<string>` — consumed by Task 6.
- Produces: `toSlug(raw: string): string`, `isValidSlug(slug: string): boolean`, `SLUG_MIN_LEN`, `SLUG_MAX_LEN`, `RESERVED_SUBDOMAINS: Set<string>` — consumed by Task 6.
- Produces: `EmailSender` interface + `ConsoleEmailSender` class — consumed by Task 6.

- [ ] **Step 1: Write the failing tests**

`src/common/token.test.ts`:
```typescript
import { describe, it, expect } from "vitest";
import { generateToken, hashToken } from "./token.ts";

describe("token", () => {
  it("generates a hex token of the requested byte length", () => {
    const token = generateToken(32);
    expect(token).toMatch(/^[0-9a-f]{64}$/);
  });

  it("generates different tokens on each call", () => {
    expect(generateToken()).not.toBe(generateToken());
  });

  it("hashes the same token to the same hash deterministically", async () => {
    const token = "fixed-test-token";
    const h1 = await hashToken(token);
    const h2 = await hashToken(token);
    expect(h1).toBe(h2);
    expect(h1).not.toBe(token);
  });
});
```

`src/common/slug.test.ts`:
```typescript
import { describe, it, expect } from "vitest";
import { toSlug, isValidSlug, RESERVED_SUBDOMAINS } from "./slug.ts";

describe("slug", () => {
  it("normalizes mixed-case, spaces, and punctuation into a valid slug", () => {
    expect(toSlug("  Acme Corp! ")).toBe("acme-corp");
  });

  it("collapses repeated separators", () => {
    expect(toSlug("a---b__c")).toBe("a-b-c");
  });

  it("rejects a slug shorter than the minimum length", () => {
    expect(isValidSlug("ab")).toBe(false);
  });

  it("rejects a slug starting with a hyphen", () => {
    expect(isValidSlug("-abc")).toBe(false);
  });

  it("accepts a well-formed slug", () => {
    expect(isValidSlug("acme-corp")).toBe(true);
  });

  it("flags reserved subdomains", () => {
    expect(RESERVED_SUBDOMAINS.has("www")).toBe(true);
    expect(RESERVED_SUBDOMAINS.has("acme-corp")).toBe(false);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/common/token.test.ts src/common/slug.test.ts`
Expected: FAIL — `./token.ts` and `./slug.ts` don't exist yet.

- [ ] **Step 3: Write the implementation files**

`src/domain/enums/signup-state.enum.ts`:
```typescript
// src/domain/enums/signup-state.enum.ts
export enum SignupState {
  STARTED = "STARTED",
  EMAIL_VERIFIED = "EMAIL_VERIFIED",
  PROVISIONING = "PROVISIONING",
  ACTIVE = "ACTIVE",
  PROVISION_FAILED = "PROVISION_FAILED",
  ABANDONED = "ABANDONED",
}

/** Terminal states — no further transitions allowed. */
export const TERMINAL_STATES: ReadonlySet<SignupState> = new Set([
  SignupState.ACTIVE,
  SignupState.PROVISION_FAILED,
  SignupState.ABANDONED,
]);

/** States the abandon-sweep (Task 9) may move to ABANDONED once expiresAt passes. */
export const ABANDONABLE_STATES: ReadonlySet<SignupState> = new Set([
  SignupState.STARTED,
  SignupState.EMAIL_VERIFIED,
]);
```

`src/common/errors.ts`:
```typescript
// src/common/errors.ts
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

export class SignupEmailAlreadyRegisteredError extends AppError {
  constructor(email: string) {
    super(409, "SIGNUP_EMAIL_ALREADY_REGISTERED", `Email "${email}" is already registered`);
  }
}

export class SignupTenantSlugTakenError extends AppError {
  constructor(slug: string) {
    super(409, "SIGNUP_TENANT_SLUG_TAKEN", `Subdomain "${slug}" is already taken`);
  }
}

export class SignupDomainBlockedError extends AppError {
  constructor(domain: string) {
    super(400, "SIGNUP_DOMAIN_BLOCKED", `Email domain "${domain}" is not allowed`);
  }
}

export class EmailVerificationTokenInvalidError extends AppError {
  constructor() {
    super(400, "EMAIL_VERIFICATION_TOKEN_INVALID", "Verification token is invalid");
  }
}

export class EmailVerificationTokenExpiredError extends AppError {
  constructor() {
    super(400, "EMAIL_VERIFICATION_TOKEN_EXPIRED", "Verification token has expired");
  }
}

export class EmailAlreadyVerifiedError extends AppError {
  constructor() {
    super(409, "EMAIL_ALREADY_VERIFIED", "Email is already verified");
  }
}

export class ResumeTokenNotFoundError extends AppError {
  constructor() {
    super(404, "RESUME_TOKEN_NOT_FOUND", "Resume token not found or expired");
  }
}

export class SignupSessionNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SIGNUP_SESSION_NOT_FOUND", `Signup session "${id}" not found`);
  }
}

export class GenAuthRegistrationError extends AppError {
  constructor(message: string) {
    super(502, "GEN_AUTH_REGISTRATION_FAILED", message);
  }
}

export class GenTntProvisioningError extends AppError {
  constructor(message: string) {
    super(502, "GEN_TNT_PROVISIONING_FAILED", message);
  }
}
```

`src/common/token.ts`:
```typescript
// src/common/token.ts
import { randomBytes, createHash } from "node:crypto";

export function generateToken(bytes = 32): string {
  return randomBytes(bytes).toString("hex");
}

// sha256, not bcrypt: these are lookup tokens with 256 bits of their own
// entropy (not low-entropy secrets like passwords), so a fast one-way
// digest is the correct tool — bcrypt's deliberate slowness buys nothing
// here and would needlessly slow down every verify-email/resume request.
export async function hashToken(token: string): Promise<string> {
  return createHash("sha256").update(token).digest("hex");
}
```

`src/common/slug.ts`:
```typescript
// src/common/slug.ts
export const SLUG_MIN_LEN = 3;
export const SLUG_MAX_LEN = 63;
export const SLUG_REGEX = /^[a-z][a-z0-9-]*[a-z0-9]$/;

export const RESERVED_SUBDOMAINS: ReadonlySet<string> = new Set([
  "www", "api", "app", "admin", "mail", "ftp", "localhost", "staging", "dev", "test", "docs", "status",
]);

export function toSlug(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

export function isValidSlug(slug: string): boolean {
  return slug.length >= SLUG_MIN_LEN && slug.length <= SLUG_MAX_LEN && SLUG_REGEX.test(slug);
}
```

`src/config/disposable-domains.ts`:
```typescript
// src/config/disposable-domains.ts
// ponytail: small hardcoded list, not the original's S3-backed uploadable
// blocklist — good enough for Phase 1; swap for a real fetched/updatable
// list if abuse from disposable-domain signups becomes a measured problem.
export const DISPOSABLE_DOMAINS: ReadonlySet<string> = new Set([
  "mailinator.com",
  "10minutemail.com",
  "guerrillamail.com",
  "tempmail.com",
  "yopmail.com",
]);
```

`src/common/email-sender.ts`:
```typescript
// src/common/email-sender.ts
import { logger } from "./logger.ts";

export interface EmailSender {
  sendVerificationEmail(to: string, verifyUrl: string): Promise<void>;
}

// ponytail: stdout stub, not real SES/SMTP — swap the implementation
// (same interface) when Phase 1 needs actual email delivery.
export class ConsoleEmailSender implements EmailSender {
  async sendVerificationEmail(to: string, verifyUrl: string): Promise<void> {
    logger.info({ to, verifyUrl }, "[email] verification link (console stub)");
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/common/token.test.ts src/common/slug.test.ts`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/domain/enums/signup-state.enum.ts src/common/errors.ts src/common/token.ts src/common/slug.ts \
        src/config/disposable-domains.ts src/common/email-sender.ts \
        src/common/token.test.ts src/common/slug.test.ts
git commit -m "feat: add domain layer — state enum, error classes, token/slug utils, email port"
```

---

### Task 4: Gen_REG — persistence (Prisma repo)

**Files:**
- Create: `src/domain/ports/signup-session.repository.port.ts`
- Create: `src/infra/persistence/prisma-client.ts`
- Create: `src/modules/signup/v1/repo.ts`
- Test: `tests/support/postgres-container.ts`
- Test: `src/modules/signup/v1/repo.test.ts`

**Interfaces:**
- Consumes: `SignupState` from Task 3.
- Produces: `SignupSessionRecord` type, `CreateSignupSessionInput` type, `ISignupSessionRepo` interface, `PrismaSignupSessionRepo` class (implements the interface) — consumed by Task 6, 7, 9.
- Produces: `getPrismaClient(): PrismaClient` — consumed by Task 8 (graceful shutdown), Task 9 (worker).

- [ ] **Step 1: Write the port interface**

`src/domain/ports/signup-session.repository.port.ts`:
```typescript
// src/domain/ports/signup-session.repository.port.ts
import type { SignupState } from "../enums/signup-state.enum.ts";

export interface SignupSessionRecord {
  id: string;
  email: string;
  companyName: string | null;
  fullName: string | null;
  phone: string | null;
  source: string | null;
  referralCode: string | null;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  desiredSubdomain: string;
  state: SignupState;
  emailVerificationTokenHash: string | null;
  emailVerifiedAt: Date | null;
  resumeTokenHash: string | null;
  authUserId: string;
  provisioningJobId: string | null;
  provisionedTenantId: string | null;
  lastProvisioningError: string | null;
  expiresAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateSignupSessionInput {
  email: string;
  companyName?: string;
  fullName?: string;
  phone?: string;
  source?: string;
  referralCode?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  desiredSubdomain: string;
  authUserId: string;
  emailVerificationTokenHash: string;
  resumeTokenHash: string;
  expiresAt: Date;
}

export interface ISignupSessionRepo {
  create(input: CreateSignupSessionInput): Promise<SignupSessionRecord>;
  findById(id: string): Promise<SignupSessionRecord | null>;
  findByEmailVerificationTokenHash(hash: string): Promise<SignupSessionRecord | null>;
  findByResumeTokenHash(hash: string): Promise<SignupSessionRecord | null>;
  existsActiveForEmail(email: string): Promise<boolean>;
  existsActiveForSubdomain(subdomain: string): Promise<boolean>;
  updateState(id: string, state: SignupState, patch?: Partial<SignupSessionRecord>): Promise<SignupSessionRecord>;
  findExpiredInStates(states: SignupState[], now: Date): Promise<SignupSessionRecord[]>;
  findInState(state: SignupState): Promise<SignupSessionRecord[]>;
}
```

- [ ] **Step 2: Write the Testcontainers helper**

`tests/support/postgres-container.ts`:
```typescript
// tests/support/postgres-container.ts
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { execSync } from "node:child_process";

export async function startTestPostgres(): Promise<{
  container: StartedPostgreSqlContainer;
  databaseUrl: string;
}> {
  const container = await new PostgreSqlContainer("postgres:15").start();
  const databaseUrl = container.getConnectionUri();

  // Apply the schema via `prisma migrate deploy` against the ephemeral container
  // instead of hand-maintaining a second copy of the DDL for tests.
  execSync("npx prisma migrate deploy", {
    env: { ...process.env, DATABASE_URL: databaseUrl },
    stdio: "inherit",
  });

  return { container, databaseUrl };
}
```

- [ ] **Step 3: Write the failing test**

`src/modules/signup/v1/repo.test.ts`:
```typescript
// src/modules/signup/v1/repo.test.ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@prisma/client";
import { startTestPostgres } from "../../../../tests/support/postgres-container.ts";
import { PrismaSignupSessionRepo } from "./repo.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";

describe("PrismaSignupSessionRepo", () => {
  let container: StartedPostgreSqlContainer;
  let prisma: PrismaClient;
  let repo: PrismaSignupSessionRepo;

  beforeAll(async () => {
    const started = await startTestPostgres();
    container = started.container;
    prisma = new PrismaClient({ datasources: { db: { url: started.databaseUrl } } });
    repo = new PrismaSignupSessionRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await container.stop();
  });

  function baseInput(overrides: Partial<Parameters<typeof repo.create>[0]> = {}) {
    return {
      email: "founder@example.com",
      desiredSubdomain: "acme",
      authUserId: "11111111-1111-1111-1111-111111111111",
      emailVerificationTokenHash: "hash-abc",
      resumeTokenHash: "resume-abc",
      expiresAt: new Date(Date.now() + 86_400_000),
      ...overrides,
    };
  }

  it("creates a session in STARTED state", async () => {
    const session = await repo.create(baseInput());
    expect(session.state).toBe(SignupState.STARTED);
    expect(session.email).toBe("founder@example.com");
  });

  it("finds a session by id", async () => {
    const created = await repo.create(baseInput({ email: "findme@example.com", desiredSubdomain: "findme" }));
    const found = await repo.findById(created.id);
    expect(found?.email).toBe("findme@example.com");
  });

  it("finds a session by email verification token hash", async () => {
    await repo.create(baseInput({ email: "token@example.com", desiredSubdomain: "tokentest", emailVerificationTokenHash: "unique-hash-1" }));
    const found = await repo.findByEmailVerificationTokenHash("unique-hash-1");
    expect(found?.email).toBe("token@example.com");
  });

  it("reports existsActiveForEmail true for a STARTED session, false after it's ABANDONED", async () => {
    const session = await repo.create(baseInput({ email: "active@example.com", desiredSubdomain: "activetest" }));
    expect(await repo.existsActiveForEmail("active@example.com")).toBe(true);

    await repo.updateState(session.id, SignupState.ABANDONED);
    expect(await repo.existsActiveForEmail("active@example.com")).toBe(false);
  });

  it("reports existsActiveForSubdomain the same way", async () => {
    await repo.create(baseInput({ email: "sub@example.com", desiredSubdomain: "subtest" }));
    expect(await repo.existsActiveForSubdomain("subtest")).toBe(true);
    expect(await repo.existsActiveForSubdomain("never-used-slug")).toBe(false);
  });

  it("updateState applies a patch alongside the state change", async () => {
    const session = await repo.create(baseInput({ email: "patch@example.com", desiredSubdomain: "patchtest" }));
    const updated = await repo.updateState(session.id, SignupState.EMAIL_VERIFIED, { emailVerifiedAt: new Date() });
    expect(updated.state).toBe(SignupState.EMAIL_VERIFIED);
    expect(updated.emailVerifiedAt).not.toBeNull();
  });

  it("findExpiredInStates only returns sessions past expiresAt in the given states", async () => {
    const expired = await repo.create(baseInput({
      email: "expired@example.com", desiredSubdomain: "expiredtest", expiresAt: new Date(Date.now() - 1000),
    }));
    await repo.create(baseInput({
      email: "notexpired@example.com", desiredSubdomain: "notexpiredtest", expiresAt: new Date(Date.now() + 86_400_000),
    }));

    const found = await repo.findExpiredInStates([SignupState.STARTED], new Date());
    expect(found.map((s) => s.id)).toContain(expired.id);
    expect(found.map((s) => s.email)).not.toContain("notexpired@example.com");
  });

  it("findInState returns only sessions in that exact state", async () => {
    const session = await repo.create(baseInput({ email: "instate@example.com", desiredSubdomain: "instatetest" }));
    await repo.updateState(session.id, SignupState.PROVISIONING);

    const found = await repo.findInState(SignupState.PROVISIONING);
    expect(found.map((s) => s.id)).toContain(session.id);
  });
});
```

- [ ] **Step 4: Run test to verify it fails**

Run: `npx vitest run src/modules/signup/v1/repo.test.ts`
Expected: FAIL — `./repo.ts` doesn't exist yet. (Requires Docker running locally for Testcontainers — same requirement Gen_TNT's own integration tests have.)

- [ ] **Step 5: Write the implementation**

`src/infra/persistence/prisma-client.ts`:
```typescript
// src/infra/persistence/prisma-client.ts
import { PrismaClient } from "@prisma/client";

let client: PrismaClient | undefined;

export function getPrismaClient(): PrismaClient {
  if (!client) {
    client = new PrismaClient();
  }
  return client;
}
```

`src/modules/signup/v1/repo.ts`:
```typescript
// src/modules/signup/v1/repo.ts
import type { PrismaClient } from "@prisma/client";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type {
  ISignupSessionRepo,
  SignupSessionRecord,
  CreateSignupSessionInput,
} from "../../../domain/ports/signup-session.repository.port.ts";

const NON_ACTIVE_STATES = [SignupState.ABANDONED, SignupState.PROVISION_FAILED];

export class PrismaSignupSessionRepo implements ISignupSessionRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(input: CreateSignupSessionInput): Promise<SignupSessionRecord> {
    return this.prisma.signupSession.create({ data: input }) as unknown as Promise<SignupSessionRecord>;
  }

  async findById(id: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findUnique({ where: { id } }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async findByEmailVerificationTokenHash(hash: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findFirst({
      where: { emailVerificationTokenHash: hash },
    }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async findByResumeTokenHash(hash: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findFirst({
      where: { resumeTokenHash: hash },
    }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async existsActiveForEmail(email: string): Promise<boolean> {
    const count = await this.prisma.signupSession.count({
      where: { email, state: { notIn: NON_ACTIVE_STATES } },
    });
    return count > 0;
  }

  async existsActiveForSubdomain(subdomain: string): Promise<boolean> {
    const count = await this.prisma.signupSession.count({
      where: { desiredSubdomain: subdomain, state: { notIn: NON_ACTIVE_STATES } },
    });
    return count > 0;
  }

  async updateState(
    id: string,
    state: SignupState,
    patch: Partial<SignupSessionRecord> = {},
  ): Promise<SignupSessionRecord> {
    return this.prisma.signupSession.update({
      where: { id },
      data: { state, ...patch },
    }) as unknown as Promise<SignupSessionRecord>;
  }

  async findExpiredInStates(states: SignupState[], now: Date): Promise<SignupSessionRecord[]> {
    return this.prisma.signupSession.findMany({
      where: { state: { in: states }, expiresAt: { lt: now } },
    }) as unknown as Promise<SignupSessionRecord[]>;
  }

  async findInState(state: SignupState): Promise<SignupSessionRecord[]> {
    return this.prisma.signupSession.findMany({ where: { state } }) as unknown as Promise<SignupSessionRecord[]>;
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `npx vitest run src/modules/signup/v1/repo.test.ts`
Expected: all PASS (needs Docker running).

- [ ] **Step 7: Commit**

```bash
git add src/domain/ports/signup-session.repository.port.ts src/infra/persistence/prisma-client.ts \
        src/modules/signup/v1/repo.ts tests/support/postgres-container.ts src/modules/signup/v1/repo.test.ts
git commit -m "feat: add Prisma-backed SignupSessionRepo with real-Postgres integration tests"
```

---

### Task 5: Gen_REG — Gen_TNT and Gen_Auth HTTP clients

**Files:**
- Create: `src/infra/tnt-client/tnt-client.ts`
- Create: `src/infra/auth-client/auth-client.ts`
- Test: `src/infra/tnt-client/tnt-client.test.ts`
- Test: `src/infra/auth-client/auth-client.test.ts`

**Interfaces:**
- Consumes: `SignupEmailAlreadyRegisteredError`, `GenAuthRegistrationError`, `GenTntProvisioningError` from Task 3.
- Produces: `TntClient` class with `isSlugTaken(slug)`, `createTenant(input)`, `getTenant(id)`, `getJob(jobId)` — consumed by Task 6, 7, 9.
- Produces: `AuthClient` class with `register(email, password)` — consumed by Task 6.
- Produces: `TenantResponse`, `ProvisioningJobResponse`, `RegisterResult` types — consumed by Task 6, 7, 9.

Both clients use global `fetch` (Node 20 has it built in) — mocked in tests via `vi.stubGlobal("fetch", ...)`, no HTTP mocking library dependency needed.

- [ ] **Step 1: Write the failing tests**

`src/infra/tnt-client/tnt-client.test.ts`:
```typescript
// src/infra/tnt-client/tnt-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { TntClient } from "./tnt-client.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("TntClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("isSlugTaken returns false on 404", async () => {
    mockFetchOnce(404, {});
    const client = new TntClient("http://gen-tnt", "secret");
    expect(await client.isSlugTaken("free-slug")).toBe(false);
  });

  it("isSlugTaken returns true on 200", async () => {
    mockFetchOnce(200, { id: "t1" });
    const client = new TntClient("http://gen-tnt", "secret");
    expect(await client.isSlugTaken("taken-slug")).toBe(true);
  });

  it("createTenant returns the tenant on 202", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null, primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(202, tenant);
    const client = new TntClient("http://gen-tnt", "secret");
    const result = await client.createTenant({ name: "Acme", slug: "acme", primaryOwnerUserId: "u1", idempotencyKey: "s1" });
    expect(result).toEqual(tenant);
  });

  it("createTenant throws GenTntProvisioningError on a non-202 response", async () => {
    mockFetchOnce(409, { error: "duplicate_slug" });
    const client = new TntClient("http://gen-tnt", "secret");
    await expect(
      client.createTenant({ name: "Acme", slug: "acme", primaryOwnerUserId: "u1", idempotencyKey: "s1" }),
    ).rejects.toThrow(/Gen_TNT createTenant failed/);
  });

  it("getJob returns the job on 200", async () => {
    const job = { id: "j1", tenantId: "t1", status: "COMPLETED", retryCount: 0, lastError: null, startedAt: null, completedAt: null, expiresAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, job);
    const client = new TntClient("http://gen-tnt", "secret");
    expect(await client.getJob("j1")).toEqual(job);
  });
});
```

`src/infra/auth-client/auth-client.test.ts`:
```typescript
// src/infra/auth-client/auth-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { AuthClient } from "./auth-client.ts";
import { SignupEmailAlreadyRegisteredError, GenAuthRegistrationError } from "../../common/errors.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("AuthClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("register returns userId on 201", async () => {
    mockFetchOnce(201, { userId: "u1" });
    const client = new AuthClient("http://gen-auth");
    expect(await client.register("a@example.com", "hunter22")).toEqual({ userId: "u1" });
  });

  it("register throws SignupEmailAlreadyRegisteredError on 409", async () => {
    mockFetchOnce(409, { error: "email_already_exists" });
    const client = new AuthClient("http://gen-auth");
    await expect(client.register("a@example.com", "hunter22")).rejects.toThrow(SignupEmailAlreadyRegisteredError);
  });

  it("register throws GenAuthRegistrationError on any other non-201 status", async () => {
    mockFetchOnce(500, { error: "internal" });
    const client = new AuthClient("http://gen-auth");
    await expect(client.register("a@example.com", "hunter22")).rejects.toThrow(GenAuthRegistrationError);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/infra/tnt-client/tnt-client.test.ts src/infra/auth-client/auth-client.test.ts`
Expected: FAIL — implementation files don't exist yet.

- [ ] **Step 3: Write the implementations**

`src/infra/tnt-client/tnt-client.ts`:
```typescript
// src/infra/tnt-client/tnt-client.ts
import { GenTntProvisioningError } from "../../common/errors.ts";

export interface TenantResponse {
  id: string;
  slug: string;
  name: string;
  status: string;
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface ProvisioningJobResponse {
  id: string;
  tenantId: string;
  status: string;
  retryCount: number;
  lastError: string | null;
  startedAt: string | null;
  completedAt: string | null;
  expiresAt: string;
}

export interface CreateTenantInput {
  name: string;
  slug: string;
  primaryOwnerUserId: string;
  idempotencyKey: string;
}

export class TntClient {
  constructor(
    private readonly baseUrl: string,
    private readonly internalSecret: string,
  ) {}

  private headers(): Record<string, string> {
    return { "X-Internal-Secret": this.internalSecret, "Content-Type": "application/json" };
  }

  async isSlugTaken(slug: string): Promise<boolean> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/by-slug/${encodeURIComponent(slug)}`, {
      headers: this.headers(),
    });
    if (res.status === 404) return false;
    if (res.status === 200) return true;
    throw new GenTntProvisioningError(`Gen_TNT slug check failed: ${res.status}`);
  }

  async createTenant(input: CreateTenantInput): Promise<TenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify(input),
    });
    if (res.status !== 202) {
      throw new GenTntProvisioningError(`Gen_TNT createTenant failed: ${res.status} ${await res.text()}`);
    }
    return res.json() as Promise<TenantResponse>;
  }

  async getTenant(id: string): Promise<TenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${id}`, { headers: this.headers() });
    if (!res.ok) throw new GenTntProvisioningError(`Gen_TNT getTenant failed: ${res.status}`);
    return res.json() as Promise<TenantResponse>;
  }

  async getJob(jobId: string): Promise<ProvisioningJobResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/provisioning/jobs/${jobId}`, { headers: this.headers() });
    if (!res.ok) throw new GenTntProvisioningError(`Gen_TNT getJob failed: ${res.status}`);
    return res.json() as Promise<ProvisioningJobResponse>;
  }
}
```

`src/infra/auth-client/auth-client.ts`:
```typescript
// src/infra/auth-client/auth-client.ts
import { SignupEmailAlreadyRegisteredError, GenAuthRegistrationError } from "../../common/errors.ts";

export interface RegisterResult {
  userId: string;
}

export class AuthClient {
  constructor(private readonly baseUrl: string) {}

  async register(email: string, password: string): Promise<RegisterResult> {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (res.status === 409) {
      throw new SignupEmailAlreadyRegisteredError(email);
    }
    if (res.status !== 201) {
      throw new GenAuthRegistrationError(`Gen_Auth register failed: ${res.status} ${await res.text()}`);
    }

    const body = (await res.json()) as { userId: string };
    return { userId: body.userId };
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/infra/tnt-client/tnt-client.test.ts src/infra/auth-client/auth-client.test.ts`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/infra/tnt-client/ src/infra/auth-client/
git commit -m "feat: add Gen_TNT and Gen_Auth HTTP clients"
```

---

### Task 6: Gen_REG — SignupService

**Files:**
- Create: `src/modules/signup/v1/schema.ts`
- Create: `src/modules/signup/v1/service.ts`
- Test: `src/modules/signup/v1/service.test.ts`

**Interfaces:**
- Consumes: `ISignupSessionRepo` (Task 4), `TntClient`/`AuthClient` (Task 5), error classes/`EmailSender`/token+slug utils (Task 3).
- Produces: `SignupService` class with `startSignup(input, context?)`, `checkSubdomainAvailability(raw)`, `resumeSignup(token)` — consumed by Task 8.
- Produces: `StartSignupSchema` (zod), `StartSignupInput` type — consumed by Task 8.

- [ ] **Step 1: Write the schema**

`src/modules/signup/v1/schema.ts`:
```typescript
// src/modules/signup/v1/schema.ts
import { z } from "zod";
import { SLUG_REGEX, SLUG_MIN_LEN, SLUG_MAX_LEN } from "../../../common/slug.ts";

export const StartSignupSchema = z.object({
  email: z.string().email("Invalid email address").max(255),
  password: z.string().min(8, "Password must be at least 8 characters").max(128),
  fullName: z.string().min(1).max(255).optional(),
  companyName: z.string().min(1).max(255).optional(),
  phone: z.string().min(7).max(32).optional(),
  source: z.string().min(1).max(64).default("web"),
  referralCode: z.string().max(64).optional(),
  utmSource: z.string().max(128).optional(),
  utmMedium: z.string().max(128).optional(),
  utmCampaign: z.string().max(128).optional(),
  desiredSubdomain: z
    .string()
    .min(SLUG_MIN_LEN)
    .max(SLUG_MAX_LEN)
    .regex(SLUG_REGEX, "Subdomain must start with a letter, end with a letter or digit, and contain only lowercase letters, digits, and hyphens"),
});

export type StartSignupInput = z.infer<typeof StartSignupSchema>;

export const SubdomainCheckSchema = z.object({
  value: z.string().min(1).max(100),
});

export type SubdomainCheckInput = z.infer<typeof SubdomainCheckSchema>;

export const ResumeSignupSchema = z.object({
  token: z.string().min(1).max(128),
});

export type ResumeSignupInput = z.infer<typeof ResumeSignupSchema>;
```

Note: `password`'s zod rule is a minimal shape check only (min 8 chars) — the real policy enforcement is Gen_Auth's own `@ValidPassword`, which runs server-side on the `AuthClient.register` call. Gen_REG doesn't duplicate that rule (per the design spec's error-handling section).

- [ ] **Step 2: Write the failing test**

`src/modules/signup/v1/service.test.ts`:
```typescript
// src/modules/signup/v1/service.test.ts
import { describe, it, expect, vi, beforeEach } from "vitest";
import { SignupService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import {
  SignupDomainBlockedError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
} from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";
import type { AuthClient } from "../../../infra/auth-client/auth-client.ts";
import type { EmailSender } from "../../../common/email-sender.ts";

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(async (input) => ({
      id: "session-1",
      state: SignupState.STARTED,
      companyName: null, fullName: null, phone: null, source: null, referralCode: null,
      utmSource: null, utmMedium: null, utmCampaign: null,
      emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,
      provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
      createdAt: new Date(), updatedAt: new Date(),
      ...input,
    } as SignupSessionRecord)),
    findById: vi.fn(async () => null),
    findByEmailVerificationTokenHash: vi.fn(async () => null),
    findByResumeTokenHash: vi.fn(async () => null),
    existsActiveForEmail: vi.fn(async () => false),
    existsActiveForSubdomain: vi.fn(async () => false),
    updateState: vi.fn(),
    findExpiredInStates: vi.fn(async () => []),
    findInState: vi.fn(async () => []),
    ...overrides,
  };
}

function fakeTntClient(overrides: Partial<TntClient> = {}): TntClient {
  return { isSlugTaken: vi.fn(async () => false), ...overrides } as unknown as TntClient;
}

function fakeAuthClient(overrides: Partial<AuthClient> = {}): AuthClient {
  return { register: vi.fn(async () => ({ userId: "auth-user-1" })), ...overrides } as unknown as AuthClient;
}

function fakeEmailSender(): EmailSender {
  return { sendVerificationEmail: vi.fn(async () => undefined) };
}

describe("SignupService", () => {
  const baseInput = {
    email: "founder@example.com",
    password: "hunter2222",
    desiredSubdomain: "acme",
    source: "web" as const,
  };

  it("rejects a disposable email domain before touching the repo", async () => {
    const repo = fakeRepo();
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());

    await expect(service.startSignup({ ...baseInput, email: "x@mailinator.com" })).rejects.toThrow(SignupDomainBlockedError);
    expect(repo.create).not.toHaveBeenCalled();
  });

  it("rejects a reserved subdomain", async () => {
    const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup({ ...baseInput, desiredSubdomain: "www" })).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("rejects when the email already has an active local session", async () => {
    const repo = fakeRepo({ existsActiveForEmail: vi.fn(async () => true) });
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupEmailAlreadyRegisteredError);
  });

  it("rejects when the subdomain is taken locally", async () => {
    const repo = fakeRepo({ existsActiveForSubdomain: vi.fn(async () => true) });
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("rejects when Gen_TNT reports the slug already taken", async () => {
    const service = new SignupService(
      fakeRepo(), fakeTntClient({ isSlugTaken: vi.fn(async () => true) }), fakeAuthClient(), fakeEmailSender(),
    );
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("registers with Gen_Auth, creates the session, and sends a verification email on success", async () => {
    const repo = fakeRepo();
    const authClient = fakeAuthClient();
    const emailSender = fakeEmailSender();
    const service = new SignupService(repo, fakeTntClient(), authClient, emailSender);

    const result = await service.startSignup(baseInput);

    expect(authClient.register).toHaveBeenCalledWith("founder@example.com", "hunter2222");
    expect(repo.create).toHaveBeenCalledWith(expect.objectContaining({ authUserId: "auth-user-1", email: "founder@example.com" }));
    expect(emailSender.sendVerificationEmail).toHaveBeenCalledOnce();
    expect(result.sessionId).toBe("session-1");
  });

  it("propagates a Gen_Auth registration failure without creating a session", async () => {
    const repo = fakeRepo();
    const authClient = fakeAuthClient({ register: vi.fn(async () => { throw new SignupEmailAlreadyRegisteredError("founder@example.com"); }) });
    const service = new SignupService(repo, fakeTntClient(), authClient, fakeEmailSender());

    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupEmailAlreadyRegisteredError);
    expect(repo.create).not.toHaveBeenCalled();
  });

  describe("checkSubdomainAvailability", () => {
    it("returns invalid for a malformed slug without querying anything", async () => {
      const repo = fakeRepo();
      const tntClient = fakeTntClient();
      const service = new SignupService(repo, tntClient, fakeAuthClient(), fakeEmailSender());

      const result = await service.checkSubdomainAvailability("ab");
      expect(result).toEqual({ available: false, normalized: "ab", valid: false });
      expect(tntClient.isSlugTaken).not.toHaveBeenCalled();
    });

    it("returns available:false for a reserved subdomain", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
      const result = await service.checkSubdomainAvailability("www");
      expect(result).toEqual({ available: false, normalized: "www", valid: true });
    });

    it("checks Gen_TNT when locally free", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient({ isSlugTaken: vi.fn(async () => true) }), fakeAuthClient(), fakeEmailSender());
      const result = await service.checkSubdomainAvailability("acme");
      expect(result).toEqual({ available: false, normalized: "acme", valid: true });
    });
  });

  describe("resumeSignup", () => {
    it("throws ResumeTokenNotFoundError when no session matches the hash", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
      await expect(service.resumeSignup("bad-token")).rejects.toThrow();
    });
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `npx vitest run src/modules/signup/v1/service.test.ts`
Expected: FAIL — `./service.ts` doesn't exist yet.

- [ ] **Step 4: Write the implementation**

`src/modules/signup/v1/service.ts`:
```typescript
// src/modules/signup/v1/service.ts
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";
import type { AuthClient } from "../../../infra/auth-client/auth-client.ts";
import type { EmailSender } from "../../../common/email-sender.ts";
import type { StartSignupInput } from "./schema.ts";
import { toSlug, isValidSlug, RESERVED_SUBDOMAINS } from "../../../common/slug.ts";
import { generateToken, hashToken } from "../../../common/token.ts";
import { DISPOSABLE_DOMAINS } from "../../../config/disposable-domains.ts";
import { env } from "../../../config/env.ts";
import {
  SignupDomainBlockedError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
  ResumeTokenNotFoundError,
} from "../../../common/errors.ts";

export interface SubdomainCheckResult {
  available: boolean;
  normalized: string;
  valid: boolean;
}

export interface ResumeSignupResult {
  sessionId: string;
  email: string;
  state: SignupSessionRecord["state"];
  companyName: string | null;
  desiredSubdomain: string;
}

export class SignupService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly tntClient: TntClient,
    private readonly authClient: AuthClient,
    private readonly emailSender: EmailSender,
  ) {}

  async startSignup(input: StartSignupInput): Promise<{ sessionId: string; message: string }> {
    const domain = input.email.split("@")[1]?.toLowerCase() ?? "";
    if (DISPOSABLE_DOMAINS.has(domain)) {
      throw new SignupDomainBlockedError(domain);
    }

    const desiredSubdomain = toSlug(input.desiredSubdomain);
    if (RESERVED_SUBDOMAINS.has(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }

    if (await this.repo.existsActiveForEmail(input.email)) {
      throw new SignupEmailAlreadyRegisteredError(input.email);
    }
    if (await this.repo.existsActiveForSubdomain(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }
    if (await this.tntClient.isSlugTaken(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }

    // Register with Gen_Auth before creating any local state — a failure here
    // (including Gen_Auth's own "already registered") must not leave behind a
    // SignupSession with no corresponding authUserId.
    const { userId } = await this.authClient.register(input.email, input.password);

    const emailVerificationToken = generateToken(32);
    const resumeToken = generateToken(32);
    const expiresAt = new Date(Date.now() + env.EMAIL_VERIFICATION_TTL_SECS * 1000);

    const session = await this.repo.create({
      email: input.email,
      companyName: input.companyName,
      fullName: input.fullName,
      phone: input.phone,
      source: input.source,
      referralCode: input.referralCode,
      utmSource: input.utmSource,
      utmMedium: input.utmMedium,
      utmCampaign: input.utmCampaign,
      desiredSubdomain,
      authUserId: userId,
      emailVerificationTokenHash: await hashToken(emailVerificationToken),
      resumeTokenHash: await hashToken(resumeToken),
      expiresAt,
    });

    await this.emailSender.sendVerificationEmail(
      input.email,
      `/verify-email?token=${emailVerificationToken}`,
    );

    return { sessionId: session.id, message: "Check your inbox to verify your email address." };
  }

  async checkSubdomainAvailability(raw: string): Promise<SubdomainCheckResult> {
    const normalized = toSlug(raw);

    if (!isValidSlug(normalized)) {
      return { available: false, normalized, valid: false };
    }
    if (RESERVED_SUBDOMAINS.has(normalized)) {
      return { available: false, normalized, valid: true };
    }
    if (await this.repo.existsActiveForSubdomain(normalized)) {
      return { available: false, normalized, valid: true };
    }
    const takenInTnt = await this.tntClient.isSlugTaken(normalized);
    return { available: !takenInTnt, normalized, valid: true };
  }

  async resumeSignup(token: string): Promise<ResumeSignupResult> {
    const tokenHash = await hashToken(token);
    const session = await this.repo.findByResumeTokenHash(tokenHash);
    if (!session) {
      throw new ResumeTokenNotFoundError();
    }

    return {
      sessionId: session.id,
      email: session.email,
      state: session.state,
      companyName: session.companyName,
      desiredSubdomain: session.desiredSubdomain,
    };
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npx vitest run src/modules/signup/v1/service.test.ts`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/modules/signup/v1/schema.ts src/modules/signup/v1/service.ts src/modules/signup/v1/service.test.ts
git commit -m "feat: add SignupService (validation, Gen_Auth registration, session creation)"
```

---

### Task 7: Gen_REG — VerifyEmailService (with Gen_TNT handoff)

**Files:**
- Create: `src/modules/verify-email/v1/service.ts`
- Test: `src/modules/verify-email/v1/service.test.ts`

**Interfaces:**
- Consumes: `ISignupSessionRepo` (Task 4), `TntClient` (Task 5), `SignupState`/errors (Task 3).
- Produces: `VerifyEmailService` class with `verifyEmail(token)` — consumed by Task 8.

- [ ] **Step 1: Write the failing test**

`src/modules/verify-email/v1/service.test.ts`:
```typescript
// src/modules/verify-email/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { VerifyEmailService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import {
  EmailVerificationTokenInvalidError,
  EmailAlreadyVerifiedError,
} from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.STARTED,
    emailVerificationTokenHash: "hash-1", emailVerifiedAt: null, resumeTokenHash: "resume-1",
    authUserId: "auth-user-1", provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByEmailVerificationTokenHash: vi.fn(async () => baseSession()),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
    ...overrides,
  };
}

function fakeTntClient(overrides: Partial<TntClient> = {}): TntClient {
  return {
    createTenant: vi.fn(async () => ({
      id: "tenant-1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null,
      primaryOwnerUserId: "auth-user-1", provisioningJobId: "job-1", createdAt: "2026-01-01T00:00:00Z",
    })),
    ...overrides,
  } as unknown as TntClient;
}

describe("VerifyEmailService", () => {
  it("throws EmailVerificationTokenInvalidError when no session matches the token", async () => {
    const repo = fakeRepo({ findByEmailVerificationTokenHash: vi.fn(async () => null) });
    const service = new VerifyEmailService(repo, fakeTntClient());
    await expect(service.verifyEmail("bad-token")).rejects.toThrow(EmailVerificationTokenInvalidError);
  });

  it("throws EmailAlreadyVerifiedError when the session isn't in STARTED", async () => {
    const repo = fakeRepo({
      findByEmailVerificationTokenHash: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED, emailVerifiedAt: new Date() })),
    });
    const service = new VerifyEmailService(repo, fakeTntClient());
    await expect(service.verifyEmail("token")).rejects.toThrow(EmailAlreadyVerifiedError);
  });

  it("marks the session EMAIL_VERIFIED, calls Gen_TNT, and transitions to PROVISIONING", async () => {
    const repo = fakeRepo();
    const tntClient = fakeTntClient();
    const service = new VerifyEmailService(repo, tntClient);

    const result = await service.verifyEmail("valid-token");

    expect(repo.updateState).toHaveBeenNthCalledWith(1, "session-1", SignupState.EMAIL_VERIFIED, expect.objectContaining({ emailVerificationTokenHash: null }));
    expect(tntClient.createTenant).toHaveBeenCalledWith({
      name: "Acme", slug: "acme", primaryOwnerUserId: "auth-user-1", idempotencyKey: "session-1",
    });
    expect(repo.updateState).toHaveBeenNthCalledWith(2, "session-1", SignupState.PROVISIONING, expect.objectContaining({
      provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
    }));
    expect(result).toEqual({ sessionId: "session-1", email: "founder@example.com", nextStep: "PROVISIONING" });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/modules/verify-email/v1/service.test.ts`
Expected: FAIL — `./service.ts` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

`src/modules/verify-email/v1/service.ts`:
```typescript
// src/modules/verify-email/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { hashToken } from "../../../common/token.ts";
import { EmailVerificationTokenInvalidError, EmailAlreadyVerifiedError } from "../../../common/errors.ts";

export interface VerifyEmailResult {
  sessionId: string;
  email: string;
  nextStep: "PROVISIONING";
}

export class VerifyEmailService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly tntClient: TntClient,
  ) {}

  async verifyEmail(token: string): Promise<VerifyEmailResult> {
    const tokenHash = await hashToken(token);
    const session = await this.repo.findByEmailVerificationTokenHash(tokenHash);

    if (!session) {
      throw new EmailVerificationTokenInvalidError();
    }
    if (session.state !== SignupState.STARTED) {
      if (session.emailVerifiedAt !== null) {
        throw new EmailAlreadyVerifiedError();
      }
      throw new EmailVerificationTokenInvalidError();
    }

    await this.repo.updateState(session.id, SignupState.EMAIL_VERIFIED, {
      emailVerifiedAt: new Date(),
      emailVerificationTokenHash: null,
    });

    const tenant = await this.tntClient.createTenant({
      name: session.companyName ?? session.email,
      slug: session.desiredSubdomain,
      primaryOwnerUserId: session.authUserId,
      idempotencyKey: session.id,
    });

    await this.repo.updateState(session.id, SignupState.PROVISIONING, {
      provisioningJobId: tenant.provisioningJobId,
      provisionedTenantId: tenant.id,
    });

    return { sessionId: session.id, email: session.email, nextStep: "PROVISIONING" };
  }
}
```

Note: if `tntClient.createTenant` throws (Gen_TNT unreachable or rejects), the session is left in `EMAIL_VERIFIED` with no `provisioningJobId` — that's a legitimate retry point, not silently dropped state. Task 9's worker doesn't sweep `EMAIL_VERIFIED` sessions for retry in Phase 1 (out of scope — the user would need to re-trigger verification or a future phase adds a dedicated retry endpoint); this is a known, documented gap, not an oversight.

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/modules/verify-email/v1/service.test.ts`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/verify-email/v1/service.ts src/modules/verify-email/v1/service.test.ts
git commit -m "feat: add VerifyEmailService with Gen_TNT provisioning handoff"
```

---

### Task 8: Gen_REG — Express wiring (controllers, routes, error handler)

**Files:**
- Create: `src/middleware/error-handler.ts`
- Create: `src/modules/signup/v1/controller.ts`
- Create: `src/modules/signup/v1/routes.ts`
- Create: `src/modules/verify-email/v1/controller.ts`
- Create: `src/modules/verify-email/v1/routes.ts`
- Modify: `src/server.ts`
- Test: `src/server.test.ts`

**Interfaces:**
- Consumes: `SignupService` (Task 6), `VerifyEmailService` (Task 7), `PrismaSignupSessionRepo` (Task 4), `TntClient`/`AuthClient` (Task 5), `AppError` (Task 3).
- Produces: `buildApp()` — the full wired Express app, consumed by Task 9's tests are separate; this is the final HTTP surface for Phase 1.

- [ ] **Step 1: Write the error-handler middleware**

`src/middleware/error-handler.ts`:
```typescript
// src/middleware/error-handler.ts
import type { NextFunction, Request, Response } from "express";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({ error: err.code, message: err.message });
    return;
  }

  logger.error({ err }, "Unhandled error");
  res.status(500).json({ error: "internal_error", message: "An unexpected error occurred" });
}
```

- [ ] **Step 2: Write the signup controller + routes**

`src/modules/signup/v1/controller.ts`:
```typescript
// src/modules/signup/v1/controller.ts
import type { Request, Response } from "express";
import type { SignupService } from "./service.ts";
import { StartSignupSchema, SubdomainCheckSchema, ResumeSignupSchema } from "./schema.ts";

export class SignupController {
  constructor(private readonly service: SignupService) {}

  startSignup = async (req: Request, res: Response): Promise<void> => {
    const input = StartSignupSchema.parse(req.body);
    const result = await this.service.startSignup(input);
    res.status(201).json(result);
  };

  checkSubdomain = async (req: Request, res: Response): Promise<void> => {
    const { value } = SubdomainCheckSchema.parse(req.query);
    const result = await this.service.checkSubdomainAvailability(value);
    res.status(200).json(result);
  };

  resumeSignup = async (req: Request, res: Response): Promise<void> => {
    const { token } = ResumeSignupSchema.parse(req.query);
    const result = await this.service.resumeSignup(token);
    res.status(200).json(result);
  };
}
```

`src/modules/signup/v1/routes.ts`:
```typescript
// src/modules/signup/v1/routes.ts
import { Router } from "express";
import type { SignupController } from "./controller.ts";

export function signupRoutes(controller: SignupController): Router {
  const router = Router();
  router.post("/signup", controller.startSignup);
  router.get("/signup/check-subdomain", controller.checkSubdomain);
  router.get("/signup/resume", controller.resumeSignup);
  return router;
}
```

- [ ] **Step 3: Write the verify-email controller + routes**

`src/modules/verify-email/v1/controller.ts`:
```typescript
// src/modules/verify-email/v1/controller.ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { VerifyEmailService } from "./service.ts";

const VerifyEmailQuerySchema = z.object({ token: z.string().min(1) });

export class VerifyEmailController {
  constructor(private readonly service: VerifyEmailService) {}

  verifyEmail = async (req: Request, res: Response): Promise<void> => {
    const { token } = VerifyEmailQuerySchema.parse(req.query);
    const result = await this.service.verifyEmail(token);
    res.status(200).json(result);
  };
}
```

`src/modules/verify-email/v1/routes.ts`:
```typescript
// src/modules/verify-email/v1/routes.ts
import { Router } from "express";
import type { VerifyEmailController } from "./controller.ts";

export function verifyEmailRoutes(controller: VerifyEmailController): Router {
  const router = Router();
  router.get("/signup/verify-email", controller.verifyEmail);
  return router;
}
```

Also add a `GET /api/v1/signup/:sessionId` polling route directly in `server.ts` (small enough not to need its own controller file — it's a single direct repo read, no service logic):

- [ ] **Step 4: Rewrite src/server.ts to wire everything together**

```typescript
// src/server.ts
import express from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import { env } from "./config/env.ts";
import { logger } from "./common/logger.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
import { TntClient } from "./infra/tnt-client/tnt-client.ts";
import { AuthClient } from "./infra/auth-client/auth-client.ts";
import { ConsoleEmailSender } from "./common/email-sender.ts";
import { SignupService } from "./modules/signup/v1/service.ts";
import { SignupController } from "./modules/signup/v1/controller.ts";
import { signupRoutes } from "./modules/signup/v1/routes.ts";
import { VerifyEmailService } from "./modules/verify-email/v1/service.ts";
import { VerifyEmailController } from "./modules/verify-email/v1/controller.ts";
import { verifyEmailRoutes } from "./modules/verify-email/v1/routes.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { SignupSessionNotFoundError } from "./common/errors.ts";

export function buildApp() {
  const prisma = getPrismaClient();
  const repo = new PrismaSignupSessionRepo(prisma);
  const tntClient = new TntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);
  const authClient = new AuthClient(env.GEN_AUTH_BASE_URL);
  const emailSender = new ConsoleEmailSender();

  const signupService = new SignupService(repo, tntClient, authClient, emailSender);
  const verifyEmailService = new VerifyEmailService(repo, tntClient);

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  app.use("/api/v1", signupRoutes(new SignupController(signupService)));
  app.use("/api/v1", verifyEmailRoutes(new VerifyEmailController(verifyEmailService)));

  app.get("/api/v1/signup/:sessionId", async (req, res) => {
    const session = await repo.findById(req.params.sessionId);
    if (!session) throw new SignupSessionNotFoundError(req.params.sessionId);
    res.json({
      sessionId: session.id,
      state: session.state,
      provisionedTenantId: session.provisionedTenantId,
      lastProvisioningError: session.lastProvisioningError,
    });
  });

  app.use(errorHandler);

  return app;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const app = buildApp();
  app.listen(env.PORT, () => {
    logger.info({ port: env.PORT }, "Gen_REG HTTP server started");
  });
}
```

- [ ] **Step 5: Write the failing integration test**

`src/server.test.ts`:
```typescript
// src/server.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import request from "supertest";

// Stub the Prisma-backed repo module before importing buildApp, so this test
// exercises real Express routing + validation + error-mapping without a
// database — repo-level correctness is already covered by Task 4's
// Testcontainers tests.
vi.mock("./infra/persistence/prisma-client.ts", () => ({ getPrismaClient: () => ({}) }));

const mockStartSignup = vi.fn();
const mockCheckSubdomain = vi.fn();
vi.mock("./modules/signup/v1/service.ts", () => ({
  SignupService: vi.fn().mockImplementation(() => ({
    startSignup: mockStartSignup,
    checkSubdomainAvailability: mockCheckSubdomain,
    resumeSignup: vi.fn(),
  })),
}));

describe("Gen_REG HTTP API", () => {
  beforeEach(() => {
    process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
    process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
    process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
    process.env.DATABASE_URL = "postgresql://unused/unused";
    vi.resetModules();
  });

  afterEach(() => vi.clearAllMocks());

  it("GET /health returns ok", async () => {
    const { buildApp } = await import("./server.ts");
    const res = await request(buildApp()).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("POST /api/v1/signup returns 400 on a malformed body (missing required fields)", async () => {
    const { buildApp } = await import("./server.ts");
    const res = await request(buildApp()).post("/api/v1/signup").send({ email: "not-an-email" });
    expect(res.status).toBe(500); // zod throws a plain Error here — see note below
  });

  it("GET /api/v1/signup/:sessionId returns 404 for an unknown session", async () => {
    const { buildApp } = await import("./server.ts");
    const res = await request(buildApp()).get("/api/v1/signup/00000000-0000-0000-0000-000000000000");
    expect(res.status).toBe(404);
    expect(res.body.error).toBe("SIGNUP_SESSION_NOT_FOUND");
  });
});
```

Note on the second test: a raw zod `ZodError` isn't an `AppError`, so it currently falls through `errorHandler`'s generic 500 branch — that's a real gap worth fixing now rather than shipping a wrong status code for every validation failure.

- [ ] **Step 6: Fix the zod-validation-error gap in error-handler.ts**

Update `src/middleware/error-handler.ts` to map `ZodError` to 400 before falling through to the generic 500:

```typescript
// src/middleware/error-handler.ts
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

Update the test's expectation to match:
```typescript
  it("POST /api/v1/signup returns 400 on a malformed body (missing required fields)", async () => {
    const { buildApp } = await import("./server.ts");
    const res = await request(buildApp()).post("/api/v1/signup").send({ email: "not-an-email" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `npx vitest run src/server.test.ts`
Expected: all PASS.

- [ ] **Step 8: Full test suite + typecheck**

Run: `npm test`
Expected: all suites PASS (Docker must be running for Task 4's Testcontainers-backed repo tests).

Run: `npx tsc -p tsconfig.json --noEmit`
Expected: no errors.

- [ ] **Step 9: Commit**

```bash
git add src/middleware/error-handler.ts src/modules/signup/v1/controller.ts src/modules/signup/v1/routes.ts \
        src/modules/verify-email/v1/controller.ts src/modules/verify-email/v1/routes.ts \
        src/server.ts src/server.test.ts
git commit -m "feat: wire Express app — signup/verify-email routes, error handler, session polling endpoint"
```

---

### Task 9: Gen_REG — background worker (provisioning poll + abandon sweep)

**Files:**
- Create: `src/worker.ts`
- Test: `src/worker.test.ts`

**Interfaces:**
- Consumes: `PrismaSignupSessionRepo` (Task 4), `TntClient` (Task 5), `SignupState`/`ABANDONABLE_STATES` (Task 3).
- Produces: `sweepProvisioning(repo, tntClient)`, `sweepAbandoned(repo)` — both exported for direct unit testing, plus the `worker.ts` entrypoint that schedules them on intervals.

- [ ] **Step 1: Write the failing test**

`src/worker.test.ts`:
```typescript
// src/worker.test.ts
import { describe, it, expect, vi } from "vitest";
import { sweepProvisioning, sweepAbandoned } from "./worker.ts";
import { SignupState } from "./domain/enums/signup-state.enum.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "./domain/ports/signup-session.repository.port.ts";
import type { TntClient } from "./infra/tnt-client/tnt-client.ts";

function session(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "s1", email: "a@example.com", companyName: null, fullName: null, phone: null, source: null,
    referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null, desiredSubdomain: "acme",
    state: SignupState.PROVISIONING, emailVerificationTokenHash: null, emailVerifiedAt: new Date(),
    resumeTokenHash: null, authUserId: "u1", provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
    lastProvisioningError: null, expiresAt: new Date(), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

describe("sweepProvisioning", () => {
  it("moves a session to ACTIVE when Gen_TNT reports the tenant ACTIVE", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "ACTIVE" })),
    } as unknown as TntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.ACTIVE);
  });

  it("moves a session to PROVISION_FAILED when Gen_TNT's job is DEAD", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "PROVISIONING" })),
      getJob: vi.fn(async () => ({ status: "DEAD", lastError: "step X failed permanently" })),
    } as unknown as TntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.PROVISION_FAILED, {
      lastProvisioningError: "step X failed permanently",
    });
  });

  it("leaves the session untouched while still PROVISIONING and the job isn't DEAD", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "PROVISIONING" })),
      getJob: vi.fn(async () => ({ status: "IN_PROGRESS", lastError: null })),
    } as unknown as TntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).not.toHaveBeenCalled();
  });
});

describe("sweepAbandoned", () => {
  it("marks expired STARTED/EMAIL_VERIFIED sessions ABANDONED", async () => {
    const repo = {
      findExpiredInStates: vi.fn(async () => [session({ state: SignupState.STARTED })]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;

    await sweepAbandoned(repo);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.ABANDONED);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/worker.test.ts`
Expected: FAIL — `./worker.ts` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

`src/worker.ts`:
```typescript
// src/worker.ts
import { env } from "./config/env.ts";
import { logger } from "./common/logger.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
import { TntClient } from "./infra/tnt-client/tnt-client.ts";
import { SignupState, ABANDONABLE_STATES } from "./domain/enums/signup-state.enum.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";

export async function sweepProvisioning(repo: ISignupSessionRepo, tntClient: TntClient): Promise<void> {
  const sessions = await repo.findInState(SignupState.PROVISIONING);

  for (const session of sessions) {
    if (!session.provisionedTenantId) continue;

    const tenant = await tntClient.getTenant(session.provisionedTenantId);
    if (tenant.status === "ACTIVE") {
      await repo.updateState(session.id, SignupState.ACTIVE);
      continue;
    }

    if (!session.provisioningJobId) continue;
    const job = await tntClient.getJob(session.provisioningJobId);
    if (job.status === "DEAD") {
      await repo.updateState(session.id, SignupState.PROVISION_FAILED, {
        lastProvisioningError: job.lastError,
      });
    }
    // Still PROVISIONING and the job isn't DEAD — leave it for the next sweep.
  }
}

export async function sweepAbandoned(repo: ISignupSessionRepo): Promise<void> {
  const expired = await repo.findExpiredInStates([...ABANDONABLE_STATES], new Date());
  for (const session of expired) {
    await repo.updateState(session.id, SignupState.ABANDONED);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const prisma = getPrismaClient();
  const repo = new PrismaSignupSessionRepo(prisma);
  const tntClient = new TntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);

  logger.info("Gen_REG worker started");

  setInterval(() => {
    sweepProvisioning(repo, tntClient).catch((err) => logger.error({ err }, "sweepProvisioning failed"));
  }, env.PROVISIONING_POLL_INTERVAL_MS);

  setInterval(() => {
    sweepAbandoned(repo).catch((err) => logger.error({ err }, "sweepAbandoned failed"));
  }, env.ABANDON_SWEEP_INTERVAL_MS);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/worker.test.ts`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/worker.ts src/worker.test.ts
git commit -m "feat: add background worker — provisioning-completion poll and abandon sweep"
```

---

### Task 10: Gen_REG — docker-compose, smoke script, README

**Files:**
- Create: `docker-compose.yml`
- Create: `scripts/smoke-signup.sh`
- Create: `README.md`

**Interfaces:**
- None new — this task wires together everything built in Tasks 1–9 for a real end-to-end run.

- [ ] **Step 1: Write docker-compose.yml**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: genreg
      POSTGRES_PASSWORD: postgres
    ports:
      - "5436:5432"   # distinct from PMP CANADA (5434), Gen_TNT (5435)
    volumes:
      - genreg_postgres_data:/var/lib/postgresql/data

volumes:
  genreg_postgres_data:
```

- [ ] **Step 2: Write the smoke script**

`scripts/smoke-signup.sh`:
```bash
#!/usr/bin/env bash
# scripts/smoke-signup.sh
# Full-path smoke test: signup -> verify-email -> poll until ACTIVE.
# Requires: Gen_REG (server + worker) running locally, plus a live Gen_TNT
# and Gen_Auth stack (and Gen_TNT's own mock step server) already up.
set -euo pipefail

BASE_URL="${GEN_REG_BASE_URL:-http://localhost:3200}"
EMAIL="smoketest-$(date +%s)@example.com"
SLUG="smoketest-$(date +%s)"

echo "Starting signup for $EMAIL / $SLUG"
SIGNUP_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"smoketest123\",\"companyName\":\"Smoke Test Co\",\"desiredSubdomain\":\"$SLUG\"}")

SESSION_ID=$(echo "$SIGNUP_RESPONSE" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4)
if [ -z "$SESSION_ID" ]; then
  echo "SMOKE FAILED: no sessionId in signup response: $SIGNUP_RESPONSE"
  exit 1
fi
echo "Session created: $SESSION_ID"

# Phase 1 uses a console-stub email sender — the verification link is logged
# by the server process, not actually emailed. Pull the token from the
# server's own log line for this smoke run instead of a real inbox.
echo "Check the Gen_REG server log for the verification link for $EMAIL, then export VERIFY_TOKEN and re-run this script's second half."
echo "(A fully automated version would swap ConsoleEmailSender for a test double the script can read from directly.)"

if [ -z "${VERIFY_TOKEN:-}" ]; then
  echo "SMOKE INCOMPLETE: set VERIFY_TOKEN and re-run to continue past email verification."
  exit 1
fi

curl -s "$BASE_URL/api/v1/signup/verify-email?token=$VERIFY_TOKEN" > /dev/null
echo "Email verified, polling for ACTIVE..."

for i in $(seq 1 30); do
  STATE=$(curl -s "$BASE_URL/api/v1/signup/$SESSION_ID" | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
  echo "  poll $i: state=$STATE"
  if [ "$STATE" = "ACTIVE" ]; then
    echo "SMOKE OK"
    exit 0
  fi
  if [ "$STATE" = "PROVISION_FAILED" ]; then
    echo "SMOKE FAILED: session reached PROVISION_FAILED"
    exit 1
  fi
  sleep 2
done

echo "SMOKE FAILED: timed out waiting for ACTIVE"
exit 1
```

Note: this script is honest about a real Phase 1 limitation — `ConsoleEmailSender` logs the verification link instead of emailing it, so full automation needs either a log-scraping step or a test-only `EmailSender` implementation that captures the token directly. Documenting this now rather than pretending the script is fully hands-off.

```bash
chmod +x scripts/smoke-signup.sh
```

- [ ] **Step 3: Write README.md**

```markdown
# Gen_REG

Generalized signup/registration service, genericized from CPMS-Platform's `reg-svc`. Phase 1: signup form, email verification, and provisioning handoff to Gen_TNT (with a supporting Gen_Auth registration call).

See `docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md` for the full design and `docs/superpowers/plans/2026-07-21-gen-reg-phase1-implementation-plan.md` for how it was built.

## Prerequisites

- A running Gen_TNT instance with the `GET /api/v1/tenants/by-slug/{slug}` endpoint (Task 1 of this plan — added to the Gen_TNT repo, not here).
- A running Gen_Auth instance (`POST /api/v1/auth/register` must be reachable and open).

## Running it locally

```bash
docker compose up -d          # Postgres on 5436
cp .env.example .env          # fill in GEN_TNT_BASE_URL / GEN_AUTH_BASE_URL if not using defaults
npm install
npm run prisma:migrate:dev
npm run dev                   # HTTP API on port 3200
```

In a second terminal:
```bash
npm run dev:worker            # background poller
```

## Testing

```bash
npm test
```

Repo-level tests (`src/modules/signup/v1/repo.test.ts`) spin up a real Postgres via Testcontainers — Docker must be running locally.

## What's not here yet (Phase 2+)

Payment/plan-selection, Turnstile captcha, abandoned-signup recovery emails, the wizard read-model, RabbitMQ event bus. See the design spec's "Explicitly out of scope" section for the full list and why each was deferred.
```

- [ ] **Step 4: Full-repo verification**

Run: `npm test`
Expected: all suites PASS.

Run: `npx tsc -p tsconfig.json --noEmit`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml scripts/smoke-signup.sh README.md
git commit -m "docs: add docker-compose, smoke-test script, and README"
```

---

## Self-Review Notes (for whoever executes this plan)

- **Spec coverage:** every "In scope" bullet from the design spec maps to a task — signup validation (6), real-time subdomain check (6), email verification (7), resume-signup (6), Gen_TNT handoff + poll (7, 9), the Gen_TNT slug endpoint (1), the Gen_Auth registration call (5, 6).
- **Known gap, intentionally not fixed in Phase 1:** if `VerifyEmailService.verifyEmail`'s Gen_TNT call throws, the session sits in `EMAIL_VERIFIED` with no automatic retry (see the note in Task 7). Flagging here so it isn't mistaken for an oversight during review.
- **Known gap in the smoke script:** not fully hands-off due to the console-stub email sender (see the note in Task 10). A future phase's real email integration would let this script pull the token from an actual inbox or a test double instead.
- **Known gap, intentionally not fixed in Phase 1:** if `SignupService.startSignup`'s `repo.create` call fails after `authClient.register` already succeeded, the Gen_Auth user is left orphaned with no corresponding `SignupSession` — the mirror of the documented Gen_TNT provisioning gap. Low-probability (a DB write failing right after a successful prior network call), and Phase 1 has no reconciliation job for either case by design.
