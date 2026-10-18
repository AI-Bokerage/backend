# Gen_REG Starter/Demo Library Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure Gen_REG from a single deployable Node app into an npm-workspace monorepo with two packages — `gen-reg-starter` (the reusable, configurable library) and `gen-reg-demo` (a thin app that runs the starter standalone) — mirroring the org's Gen_Auth/Gen_TNT starter/demo convention.

**Architecture:** `gen-reg-starter` contains everything Phase 1 built (domain, services, ports, default adapters, Prisma schema, route factories, worker sweep functions) plus a new `createGenReg(config)` factory that is the package's single entry point. Every major dependency (repo, email sender, TNT client, Auth client) and module (signup routes, verify-email routes, worker) is individually swappable/toggleable via `config`, defaulting to Phase 1's existing built-ins when left unset. `gen-reg-demo` becomes a single thin file that calls `createGenReg({})` and boots it over HTTP.

**Tech Stack:** Same as Phase 1 — Node 20+, TypeScript (NodeNext, `.ts`-extension imports), Express 4 + express-async-errors, zod, Prisma/PostgreSQL, vitest + supertest + `@testcontainers/postgresql`, native `fetch`. New: npm workspaces (no new package manager — plain npm, which has had native workspace support since v7).

## Global Constraints

- Repo root: `C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_REG` (git repo, `main` branch — Phase 1 already merged, at commit `285e37d` per the last Phase 1 session).
- Design spec: `docs/superpowers/specs/2026-07-22-gen-reg-starter-library-design.md`.
- Relative imports use explicit `.ts` extensions (`from "../foo.ts"`) — unchanged convention from Phase 1, `rewriteRelativeImportExtensions` in each package's `tsconfig.json` rewrites them to `.js` at build.
- No new runtime dependency beyond what's needed for the workspace split itself — no pnpm, no Turborepo, no Lerna. Plain npm workspaces.
- Package names: `@gen-ms/gen-reg-starter` (published) and `@gen-ms/gen-reg-demo` (private, not published).
- Every existing Phase 1 test must keep passing (same assertions, same behavior) after the move — this plan is a restructuring, not a behavior change, until the new factory/module-toggle tests are added in Task 5.
- `ISignupSessionRepo`, `ITntClient`, `IAuthClient`, `EmailSender` are the four swappable interfaces — every one of `SignupService`, `VerifyEmailService`, `createGenReg`'s resolvers must depend on the interface type, never a concrete class, so consumers can substitute any implementation.

---

### Task 1: npm workspace scaffold + move Phase 1 source into `gen-reg-starter`

Purely mechanical — no logic changes. Moves every existing file into `packages/gen-reg-starter/`, converts the root `package.json` into a workspace root, and fixes one real bug the move would otherwise introduce (a `.gitignore` pattern that stops matching once the directory nests one level deeper).

**Files:**
- Modify: `package.json` (root — becomes workspace root)
- Modify: `.gitignore` (fix the `src/__generated__/` anchoring bug)
- Create: `packages/gen-reg-starter/package.json`
- Move: `tsconfig.json` → `packages/gen-reg-starter/tsconfig.json`
- Move: `vitest.config.ts` → `packages/gen-reg-starter/vitest.config.ts`
- Move: `.env.example` → `packages/gen-reg-starter/.env.example`
- Move: `src/` → `packages/gen-reg-starter/src/`
- Move: `prisma/` → `packages/gen-reg-starter/prisma/`
- Move: `tests/` → `packages/gen-reg-starter/tests/`
- Test: none new — this task is verified by the existing Phase 1 test suite passing unchanged in its new location.

**Interfaces:** None new. Every existing interface/class/function keeps its current name and signature in this task — renames happen in Tasks 2–3.

- [ ] **Step 1: Convert the root `package.json` into a workspace root**

Replace the entire contents of `package.json` with:

```json
{
  "name": "gen-reg-workspace",
  "private": true,
  "workspaces": [
    "packages/*"
  ],
  "scripts": {
    "test": "npm test --workspace=@gen-ms/gen-reg-starter",
    "build": "npm run build --workspace=@gen-ms/gen-reg-starter && npm run build --workspace=@gen-ms/gen-reg-demo",
    "dev": "npm run dev --workspace=@gen-ms/gen-reg-demo"
  }
}
```

- [ ] **Step 2: Fix the `.gitignore` anchoring bug before the move**

The current `.gitignore` has `src/__generated__/` on its own line. A gitignore pattern containing a slash anywhere but the end is anchored to the directory holding the `.gitignore` file — it does **not** match at any nested depth. Once `src/` moves to `packages/gen-reg-starter/src/`, that line would stop matching, and the generated Prisma client (a large binary + generated `.d.ts`/`.js` tree) would become trackable by git.

Read the current `.gitignore`, then replace the `src/__generated__/` line with `**/src/__generated__/` (matches at any depth):

```
# .gitignore
node_modules/
dist/
.env
*.log
**/src/__generated__/
```

- [ ] **Step 3: Create the `packages/gen-reg-starter` directory and move everything into it**

```bash
mkdir -p packages/gen-reg-starter
git mv tsconfig.json packages/gen-reg-starter/tsconfig.json
git mv vitest.config.ts packages/gen-reg-starter/vitest.config.ts
git mv .env.example packages/gen-reg-starter/.env.example
git mv src packages/gen-reg-starter/src
git mv prisma packages/gen-reg-starter/prisma
git mv tests packages/gen-reg-starter/tests
```

Note: `src/__generated__/prisma/` and any local `.env`/`node_modules/` under `src`/root are gitignored and untracked, so `git mv` won't touch them — that's fine, they get regenerated/reinstalled in Step 5 below. If a stray untracked `.env` or `node_modules` directory is left behind at the old root path after the `git mv` commands, delete it manually (it's not tracked, so this doesn't touch git history):

```bash
rm -rf node_modules .env
```

- [ ] **Step 4: Write `packages/gen-reg-starter/package.json`**

```json
{
  "name": "@gen-ms/gen-reg-starter",
  "version": "0.1.0",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": "./dist/index.js"
  },
  "files": [
    "dist",
    "prisma/schema.prisma",
    "prisma/migrations"
  ],
  "scripts": {
    "build": "prisma generate && tsc -p tsconfig.json",
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
    "typescript": "^5.7.3",
    "vitest": "^2.0.0"
  }
}
```

Note: `tsx` is dropped from this package's `devDependencies` — it was only used for `dev`/`dev:worker` watch scripts, which move to `gen-reg-demo` in Task 7 (a library package has no reason to run its own dev server).

- [ ] **Step 5: Install, regenerate the Prisma client, and set up a local `.env`**

```bash
npm install
cp packages/gen-reg-starter/.env.example packages/gen-reg-starter/.env
npm run prisma:generate --workspace=@gen-ms/gen-reg-starter
```

Expected: `npm install` resolves the workspace (creates a root `node_modules` with hoisted deps, and a symlink-free `packages/gen-reg-starter` since it's the only real package so far); `prisma:generate` writes the client to `packages/gen-reg-starter/src/__generated__/prisma`.

- [ ] **Step 6: Run the full existing test suite in its new location**

Docker must be running (Testcontainers-backed `repo.test.ts`).

```bash
npm test --workspace=@gen-ms/gen-reg-starter
```

Expected: `9 passed (9)`, `47 passed (47)` — the exact same counts as Phase 1's final state, just resolved from the new path.

```bash
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add package.json .gitignore packages/gen-reg-starter/package.json
git commit -m "chore: convert Gen_REG into an npm workspace, move Phase 1 source into gen-reg-starter"
```

Note: `git mv` in Step 3 already staged the moved files — this commit captures both the moves and the new/modified config files together as one coherent change. Verify with `git status` before committing that `package-lock.json` is not staged (matches Phase 1's convention of not committing lockfiles).

---

### Task 2: Extract `ITntClient` — rename `TntClient` → `HttpTntClient`

**Files:**
- Create: `packages/gen-reg-starter/src/domain/ports/tnt-client.port.ts`
- Modify: `packages/gen-reg-starter/src/infra/tnt-client/tnt-client.ts`
- Modify: `packages/gen-reg-starter/src/infra/tnt-client/tnt-client.test.ts`
- Modify: `packages/gen-reg-starter/src/server.ts`
- Modify: `packages/gen-reg-starter/src/worker.ts`
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/service.ts`
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts`
- Modify: `packages/gen-reg-starter/src/modules/verify-email/v1/service.ts`
- Modify: `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts`
- Modify: `packages/gen-reg-starter/src/worker.test.ts`

**Interfaces:**
- Produces: `ITntClient` interface (with `TenantResponse`, `ProvisioningJobResponse`, `CreateTenantInput` types) — consumed by Task 5's factory, by `SignupService`/`VerifyEmailService`, and by any future consumer supplying a custom TNT client.
- Produces: `HttpTntClient` class (implements `ITntClient`) — today's `TntClient`, renamed, no behavior change.

- [ ] **Step 1: Run the existing test suite to confirm the starting baseline**

```bash
npx vitest run packages/gen-reg-starter/src/infra/tnt-client/tnt-client.test.ts packages/gen-reg-starter/src/modules/signup/v1/service.test.ts packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts packages/gen-reg-starter/src/worker.test.ts
```

Expected: all PASS (this is a baseline check before renaming, not a red/green TDD cycle — the rename doesn't change behavior).

- [ ] **Step 2: Create the port file**

`packages/gen-reg-starter/src/domain/ports/tnt-client.port.ts`:
```typescript
// src/domain/ports/tnt-client.port.ts
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

export interface ITntClient {
  isSlugTaken(slug: string): Promise<boolean>;
  createTenant(input: CreateTenantInput): Promise<TenantResponse>;
  getTenant(id: string): Promise<TenantResponse>;
  getJob(jobId: string): Promise<ProvisioningJobResponse>;
}
```

- [ ] **Step 3: Rewrite `tnt-client.ts` to implement the interface**

`packages/gen-reg-starter/src/infra/tnt-client/tnt-client.ts` — replace entire contents:
```typescript
// src/infra/tnt-client/tnt-client.ts
import { GenTntProvisioningError } from "../../common/errors.ts";
import type {
  ITntClient,
  TenantResponse,
  ProvisioningJobResponse,
  CreateTenantInput,
} from "../../domain/ports/tnt-client.port.ts";

export class HttpTntClient implements ITntClient {
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

- [ ] **Step 4: Update `tnt-client.test.ts`**

In `packages/gen-reg-starter/src/infra/tnt-client/tnt-client.test.ts`, change the import and every constructor call:
- `import { TntClient } from "./tnt-client.ts";` → `import { HttpTntClient } from "./tnt-client.ts";`
- Every `new TntClient(...)` → `new HttpTntClient(...)` (5 occurrences: one per `it` block).

No other change — same assertions, same mock setup.

- [ ] **Step 5: Update `server.ts`**

In `packages/gen-reg-starter/src/server.ts`:
- `import { TntClient } from "./infra/tnt-client/tnt-client.ts";` → `import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";`
- `const tntClient = new TntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);` → `const tntClient = new HttpTntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);`

(This file gets fully replaced in Task 5 — this is a minimal, non-breaking update to keep it compiling until then.)

- [ ] **Step 6: Update `worker.ts`**

In `packages/gen-reg-starter/src/worker.ts`:
- `import { TntClient } from "./infra/tnt-client/tnt-client.ts";` → replace with two imports:
  ```typescript
  import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
  import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
  ```
- `export async function sweepProvisioning(repo: ISignupSessionRepo, tntClient: TntClient): Promise<void> {` → change the parameter type: `tntClient: ITntClient`
- `const tntClient = new TntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);` (in the bottom bootstrap block) → `const tntClient = new HttpTntClient(env.GEN_TNT_BASE_URL, env.GEN_TNT_INTERNAL_SECRET);`

- [ ] **Step 7: Update `worker.test.ts`**

In `packages/gen-reg-starter/src/worker.test.ts`:
- `import type { TntClient } from "./infra/tnt-client/tnt-client.ts";` → `import type { ITntClient } from "./domain/ports/tnt-client.port.ts";`
- Every `} as unknown as TntClient;` → `} as unknown as ITntClient;` (3 occurrences).

- [ ] **Step 8: Update `SignupService`**

In `packages/gen-reg-starter/src/modules/signup/v1/service.ts`:
- `import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";` → `import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";`
- Constructor parameter: `private readonly tntClient: TntClient,` → `private readonly tntClient: ITntClient,`

- [ ] **Step 9: Update `service.test.ts` (signup)**

In `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts`:
- `import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";` → `import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";`
- `function fakeTntClient(overrides: Partial<TntClient> = {}): TntClient {` → `function fakeTntClient(overrides: Partial<ITntClient> = {}): ITntClient {`
- `return { isSlugTaken: vi.fn(async () => false), ...overrides } as unknown as TntClient;` → `... as unknown as ITntClient;`

- [ ] **Step 10: Update `VerifyEmailService`**

In `packages/gen-reg-starter/src/modules/verify-email/v1/service.ts`:
- `import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";` → `import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";`
- Constructor parameter: `private readonly tntClient: TntClient,` → `private readonly tntClient: ITntClient,`

- [ ] **Step 11: Update `service.test.ts` (verify-email)**

In `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts`:
- `import type { TntClient } from "../../../infra/tnt-client/tnt-client.ts";` → `import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";`
- `function fakeTntClient(overrides: Partial<TntClient> = {}): TntClient {` → `function fakeTntClient(overrides: Partial<ITntClient> = {}): ITntClient {`
- `} as unknown as TntClient;` → `} as unknown as ITntClient;`

- [ ] **Step 12: Run the full suite to confirm everything still passes**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: same `47 passed (47)`, no type errors. The rename is purely structural — every test that passed before still passes with identical assertions.

- [ ] **Step 13: Commit**

```bash
git add packages/gen-reg-starter/src/domain/ports/tnt-client.port.ts \
        packages/gen-reg-starter/src/infra/tnt-client/tnt-client.ts \
        packages/gen-reg-starter/src/infra/tnt-client/tnt-client.test.ts \
        packages/gen-reg-starter/src/server.ts \
        packages/gen-reg-starter/src/worker.ts \
        packages/gen-reg-starter/src/worker.test.ts \
        packages/gen-reg-starter/src/modules/signup/v1/service.ts \
        packages/gen-reg-starter/src/modules/signup/v1/service.test.ts \
        packages/gen-reg-starter/src/modules/verify-email/v1/service.ts \
        packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts
git commit -m "refactor: extract ITntClient port, rename TntClient to HttpTntClient"
```

---

### Task 3: Extract `IAuthClient` — rename `AuthClient` → `HttpAuthClient`

**Files:**
- Create: `packages/gen-reg-starter/src/domain/ports/auth-client.port.ts`
- Modify: `packages/gen-reg-starter/src/infra/auth-client/auth-client.ts`
- Modify: `packages/gen-reg-starter/src/infra/auth-client/auth-client.test.ts`
- Modify: `packages/gen-reg-starter/src/server.ts`
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/service.ts`
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts`

**Interfaces:**
- Produces: `IAuthClient` interface (with `RegisterResult` type) — consumed by Task 5's factory and by `SignupService`.
- Produces: `HttpAuthClient` class (implements `IAuthClient`) — today's `AuthClient`, renamed, no behavior change.

- [ ] **Step 1: Run the existing test suite to confirm the starting baseline**

```bash
npx vitest run packages/gen-reg-starter/src/infra/auth-client/auth-client.test.ts packages/gen-reg-starter/src/modules/signup/v1/service.test.ts
```

Expected: all PASS.

- [ ] **Step 2: Create the port file**

`packages/gen-reg-starter/src/domain/ports/auth-client.port.ts`:
```typescript
// src/domain/ports/auth-client.port.ts
export interface RegisterResult {
  userId: string;
}

export interface IAuthClient {
  register(email: string, password: string): Promise<RegisterResult>;
}
```

- [ ] **Step 3: Rewrite `auth-client.ts` to implement the interface**

`packages/gen-reg-starter/src/infra/auth-client/auth-client.ts` — replace entire contents:
```typescript
// src/infra/auth-client/auth-client.ts
import { SignupEmailAlreadyRegisteredError, GenAuthRegistrationError } from "../../common/errors.ts";
import type { IAuthClient, RegisterResult } from "../../domain/ports/auth-client.port.ts";

export class HttpAuthClient implements IAuthClient {
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

- [ ] **Step 4: Update `auth-client.test.ts`**

In `packages/gen-reg-starter/src/infra/auth-client/auth-client.test.ts`:
- `import { AuthClient } from "./auth-client.ts";` → `import { HttpAuthClient } from "./auth-client.ts";`
- Every `new AuthClient(...)` → `new HttpAuthClient(...)` (3 occurrences).

- [ ] **Step 5: Update `server.ts`**

In `packages/gen-reg-starter/src/server.ts`:
- `import { AuthClient } from "./infra/auth-client/auth-client.ts";` → `import { HttpAuthClient } from "./infra/auth-client/auth-client.ts";`
- `const authClient = new AuthClient(env.GEN_AUTH_BASE_URL);` → `const authClient = new HttpAuthClient(env.GEN_AUTH_BASE_URL);`

- [ ] **Step 6: Update `SignupService`**

In `packages/gen-reg-starter/src/modules/signup/v1/service.ts`:
- `import type { AuthClient } from "../../../infra/auth-client/auth-client.ts";` → `import type { IAuthClient } from "../../../domain/ports/auth-client.port.ts";`
- Constructor parameter: `private readonly authClient: AuthClient,` → `private readonly authClient: IAuthClient,`

- [ ] **Step 7: Update `service.test.ts` (signup)**

In `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts`:
- `import type { AuthClient } from "../../../infra/auth-client/auth-client.ts";` → `import type { IAuthClient } from "../../../domain/ports/auth-client.port.ts";`
- `function fakeAuthClient(overrides: Partial<AuthClient> = {}): AuthClient {` → `function fakeAuthClient(overrides: Partial<IAuthClient> = {}): IAuthClient {`
- `return { register: vi.fn(async () => ({ userId: "auth-user-1" })), ...overrides } as unknown as AuthClient;` → `... as unknown as IAuthClient;`

- [ ] **Step 8: Run the full suite to confirm everything still passes**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: `47 passed (47)`, no type errors.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-reg-starter/src/domain/ports/auth-client.port.ts \
        packages/gen-reg-starter/src/infra/auth-client/auth-client.ts \
        packages/gen-reg-starter/src/infra/auth-client/auth-client.test.ts \
        packages/gen-reg-starter/src/server.ts \
        packages/gen-reg-starter/src/modules/signup/v1/service.ts \
        packages/gen-reg-starter/src/modules/signup/v1/service.test.ts
git commit -m "refactor: extract IAuthClient port, rename AuthClient to HttpAuthClient"
```

---

### Task 4: Lazy env validation — `requireEnv()` + `GenRegConfigError`

**Files:**
- Modify: `packages/gen-reg-starter/src/common/errors.ts`
- Modify: `packages/gen-reg-starter/src/config/env.ts`
- Test: `packages/gen-reg-starter/src/config/env.test.ts`

**Interfaces:**
- Produces: `GenRegConfigError` class (extends `Error`, NOT `AppError` — this is a boot-time configuration error, never thrown during request handling, so it has no HTTP status code and is never seen by `errorHandler`) — consumed by Task 5's factory.
- Produces: `requireEnv(key: string): string` — consumed by Task 5's factory resolvers.
- Changes: `EnvSchema` drops `DATABASE_URL`, `GEN_TNT_BASE_URL`, `GEN_TNT_INTERNAL_SECRET`, `GEN_AUTH_BASE_URL` entirely (they move to `requireEnv()`-based lazy lookups at the point of use in Task 5, not part of the always-parsed `env` object). Every remaining field (`PORT`, `NODE_ENV`, `LOG_LEVEL`, the four TTL/interval fields) already has a `.default(...)`, so `env` stays always-parseable and never throws.

- [ ] **Step 1: Write the failing test**

`packages/gen-reg-starter/src/config/env.test.ts`:
```typescript
// src/config/env.test.ts
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv } from "./env.ts";
import { GenRegConfigError } from "../common/errors.ts";

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

  it("throws GenRegConfigError with a clear message when the env var is missing", () => {
    delete process.env.SOME_TEST_VAR;
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenRegConfigError);
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(/SOME_TEST_VAR/);
  });

  it("throws GenRegConfigError when the env var is set to an empty string", () => {
    process.env.SOME_TEST_VAR = "";
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenRegConfigError);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/config/env.test.ts
```

Expected: FAIL — `requireEnv` is not exported from `./env.ts`, `GenRegConfigError` is not exported from `../common/errors.ts`.

- [ ] **Step 3: Add `GenRegConfigError` to `errors.ts`**

In `packages/gen-reg-starter/src/common/errors.ts`, add at the end of the file (after `GenTntProvisioningError`):

```typescript
// Boot-time configuration error — thrown by requireEnv() when createGenReg()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: this is never thrown during request handling and
// never reaches errorHandler, so it has no HTTP status code.
export class GenRegConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenRegConfigError";
  }
}
```

- [ ] **Step 4: Rewrite `env.ts`**

`packages/gen-reg-starter/src/config/env.ts` — replace entire contents:
```typescript
// src/config/env.ts
import "dotenv/config";
import { z } from "zod";
import { GenRegConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  PORT: z.coerce.number().default(3200),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),
  EMAIL_VERIFICATION_TTL_SECS: z.coerce.number().default(86400),
  RESUME_TOKEN_TTL_SECS: z.coerce.number().default(604800),
  PROVISIONING_POLL_INTERVAL_MS: z.coerce.number().default(5000),
  ABANDON_SWEEP_INTERVAL_MS: z.coerce.number().default(300000),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it — as opposed to EnvSchema above, which eagerly validates
// only the fields every consumer needs regardless of which modules/adapters
// they enable. DATABASE_URL, GEN_TNT_BASE_URL, GEN_TNT_INTERNAL_SECRET, and
// GEN_AUTH_BASE_URL are intentionally NOT in EnvSchema — they're read through
// this function instead, only when the module that needs them is enabled and
// no override was supplied (see create-gen-reg.ts's resolver functions).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenRegConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/config/env.test.ts
```

Expected: all 3 tests PASS.

- [ ] **Step 6: Run the full suite — expect two known failures to investigate**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
```

`server.ts` and `worker.ts` still reference `env.GEN_TNT_BASE_URL`, `env.GEN_TNT_INTERNAL_SECRET`, and `env.GEN_AUTH_BASE_URL` directly (fields that no longer exist on the parsed `env` object as of Step 4) — this will now be a TypeScript compile error, not a test failure. Confirm this specific error:

```bash
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: errors in `src/server.ts` and `src/worker.ts` — `Property 'GEN_TNT_BASE_URL' does not exist on type '{ PORT: number; ... }'` (and similarly for the other two removed fields). This is expected and get fixed in Task 5, which replaces `server.ts` entirely and trims `worker.ts`'s bootstrap block. Do not attempt to patch `server.ts`/`worker.ts` in this task — Task 5 subsumes them.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-reg-starter/src/common/errors.ts \
        packages/gen-reg-starter/src/config/env.ts \
        packages/gen-reg-starter/src/config/env.test.ts
git commit -m "refactor: move DATABASE_URL/GEN_TNT_*/GEN_AUTH_BASE_URL to lazy requireEnv() validation"
```

Note: this commit intentionally leaves `server.ts`/`worker.ts` in a non-compiling state — Task 5 (the very next task) fixes it. Do not skip ahead or leave a gap between these two tasks in a single working session.

---

### Task 5: The `createGenReg()` factory — absorbs `server.ts` and trims `worker.ts`

This is the core of the whole project. `server.ts`'s `buildApp()` becomes the factory's internals, parameterized by `config`; `worker.ts` loses its standalone bootstrap block (its two sweep functions stay, used by the factory instead). `server.ts` and `server.test.ts` are deleted — their behavior is fully covered by the new factory and its tests.

**Files:**
- Create: `packages/gen-reg-starter/src/create-gen-reg.ts`
- Create: `packages/gen-reg-starter/src/create-gen-reg.test.ts`
- Create: `packages/gen-reg-starter/src/index.ts`
- Modify: `packages/gen-reg-starter/src/worker.ts` (trim to just the two exported sweep functions)
- Delete: `packages/gen-reg-starter/src/server.ts`
- Delete: `packages/gen-reg-starter/src/server.test.ts`

**Interfaces:**
- Produces: `createGenReg(config: GenRegConfig): GenRegInstance` — the package's single entry point, consumed by `gen-reg-demo` (Task 7) and by `index.ts`'s public export surface.
- Produces: `GenRegConfig`, `GenRegModulesConfig`, `GenRegWorker`, `GenRegInstance` types.
- Consumes: `ISignupSessionRepo` (existing), `ITntClient`/`IAuthClient` (Tasks 2–3), `EmailSender` (existing), `requireEnv`/`GenRegConfigError` (Task 4), `sweepProvisioning`/`sweepAbandoned` (existing, from the trimmed `worker.ts`).

- [ ] **Step 1: Trim `worker.ts` to just the two exported functions**

`packages/gen-reg-starter/src/worker.ts` — replace entire contents:
```typescript
// src/worker.ts
import { SignupState, ABANDONABLE_STATES } from "./domain/enums/signup-state.enum.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export async function sweepProvisioning(repo: ISignupSessionRepo, tntClient: ITntClient): Promise<void> {
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
```

`worker.test.ts` needs no change in this step — it already only imports/tests these two functions and doesn't touch the bootstrap block being removed.

- [ ] **Step 2: Delete `server.ts` and `server.test.ts`**

```bash
git rm packages/gen-reg-starter/src/server.ts packages/gen-reg-starter/src/server.test.ts
```

Their behavior is fully re-created (and re-tested) by `create-gen-reg.ts`/`create-gen-reg.test.ts` in the next steps.

- [ ] **Step 3: Write the failing factory test**

`packages/gen-reg-starter/src/create-gen-reg.test.ts`:
```typescript
// src/create-gen-reg.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import request from "supertest";
import { SignupState } from "./domain/enums/signup-state.enum.ts";
import { GenRegConfigError } from "./common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "./domain/ports/auth-client.port.ts";
import type { EmailSender } from "./common/email-sender.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.STARTED,
    emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,
    authUserId: "auth-user-1", provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(async () => baseSession()),
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

function fakeTntClient(overrides: Partial<ITntClient> = {}): ITntClient {
  return {
    isSlugTaken: vi.fn(async () => false),
    createTenant: vi.fn(async () => ({
      id: "tenant-1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null,
      primaryOwnerUserId: "auth-user-1", provisioningJobId: "job-1", createdAt: "2026-01-01T00:00:00Z",
    })),
    getTenant: vi.fn(async () => ({ id: "tenant-1", status: "ACTIVE" }) as any),
    getJob: vi.fn(async () => ({ status: "IN_PROGRESS", lastError: null }) as any),
    ...overrides,
  };
}

function fakeAuthClient(overrides: Partial<IAuthClient> = {}): IAuthClient {
  return { register: vi.fn(async () => ({ userId: "auth-user-1" })), ...overrides };
}

function fakeEmailSender(): EmailSender {
  return { sendVerificationEmail: vi.fn(async () => undefined) };
}

describe("createGenReg", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe("default config", () => {
    beforeEach(() => {
      process.env.DATABASE_URL = "postgresql://unused/unused";
      process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
      process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
      process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
    });

    it("GET /health returns ok", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({});
      const res = await request(app).get("/health");
      expect(res.status).toBe(200);
      expect(res.body).toEqual({ status: "ok" });
    });

    it("GET /api/v1/signup/:sessionId returns 404 for a malformed id", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({ repo: fakeRepo() });
      const res = await request(app).get("/api/v1/signup/not-a-uuid");
      expect(res.status).toBe(404);
      expect(res.body.error).toBe("SIGNUP_SESSION_NOT_FOUND");
    });
  });

  describe("override path — proves pluggability actually wires through", () => {
    it("uses the supplied repo/emailSender/tntClient/authClient instead of building defaults", async () => {
      const repo = fakeRepo({
        existsActiveForSubdomain: vi.fn(async () => false),
        create: vi.fn(async () => baseSession({ id: "session-42" })),
      });
      const tntClient = fakeTntClient();
      const authClient = fakeAuthClient();
      const emailSender = fakeEmailSender();

      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({ repo, tntClient, authClient, emailSender });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(201);
      expect(authClient.register).toHaveBeenCalledWith("founder@example.com", "hunter2222");
      expect(repo.create).toHaveBeenCalledOnce();
      expect(emailSender.sendVerificationEmail).toHaveBeenCalledOnce();
      // tntClient.isSlugTaken is called during signup validation — proves the
      // supplied fake, not a real HttpTntClient, was actually used.
      expect(tntClient.isSlugTaken).toHaveBeenCalledWith("acme");
    });
  });

  describe("module toggles", () => {
    it("does not return a worker when modules.worker is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { worker } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { worker: false },
      });
      expect(worker).toBeUndefined();
    });

    it("returns a worker with start/stop when modules.worker is true (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { worker } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
      });
      expect(worker).toBeDefined();
      expect(typeof worker!.start).toBe("function");
      expect(typeof worker!.stop).toBe("function");
      worker!.start();
      worker!.stop();
    });

    it("does not mount signup routes when modules.signup is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { signup: false, worker: false },
      });
      const res = await request(app).post("/api/v1/signup").send({});
      expect(res.status).toBe(404);
    });

    it("does not mount verify-email routes when modules.verifyEmail is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { verifyEmail: false, worker: false },
      });
      const res = await request(app).get("/api/v1/signup/verify-email?token=abc");
      expect(res.status).toBe(404);
    });
  });

  describe("lazy env validation", () => {
    const ORIGINAL_ENV = { ...process.env };

    afterEach(() => {
      process.env = { ...ORIGINAL_ENV };
    });

    it("does not require GEN_TNT_BASE_URL when signup/verifyEmail/worker are all disabled", async () => {
      delete process.env.GEN_TNT_BASE_URL;
      delete process.env.GEN_TNT_INTERNAL_SECRET;
      delete process.env.GEN_AUTH_BASE_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, worker: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when signup is enabled, no tntClient override, and GEN_TNT_BASE_URL is missing", async () => {
      delete process.env.GEN_TNT_BASE_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          authClient: fakeAuthClient(),
          modules: { signup: true, verifyEmail: false, worker: false },
        }),
      ).toThrow(GenRegConfigError);
    });
  });
});
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/create-gen-reg.test.ts
```

Expected: FAIL — `./create-gen-reg.ts` doesn't exist yet.

- [ ] **Step 5: Write `create-gen-reg.ts`**

`packages/gen-reg-starter/src/create-gen-reg.ts`:
```typescript
// src/create-gen-reg.ts
import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import { z } from "zod";
import { env, requireEnv } from "./config/env.ts";
import { logger } from "./common/logger.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
import { HttpAuthClient } from "./infra/auth-client/auth-client.ts";
import { ConsoleEmailSender } from "./common/email-sender.ts";
import { SignupService } from "./modules/signup/v1/service.ts";
import { SignupController } from "./modules/signup/v1/controller.ts";
import { signupRoutes } from "./modules/signup/v1/routes.ts";
import { VerifyEmailService } from "./modules/verify-email/v1/service.ts";
import { VerifyEmailController } from "./modules/verify-email/v1/controller.ts";
import { verifyEmailRoutes } from "./modules/verify-email/v1/routes.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { SignupSessionNotFoundError } from "./common/errors.ts";
import { sweepProvisioning, sweepAbandoned } from "./worker.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "./domain/ports/auth-client.port.ts";
import type { EmailSender } from "./common/email-sender.ts";

const SessionIdParamSchema = z.string().uuid();

export interface GenRegModulesConfig {
  signup?: boolean;
  verifyEmail?: boolean;
  worker?: boolean;
}

export interface GenRegConfig {
  repo?: ISignupSessionRepo;
  emailSender?: EmailSender;
  tntClient?: ITntClient;
  authClient?: IAuthClient;
  modules?: GenRegModulesConfig;
}

export interface GenRegWorker {
  start(): void;
  stop(): void;
}

export interface GenRegInstance {
  app: Express;
  worker?: GenRegWorker;
}

function resolveRepo(override: ISignupSessionRepo | undefined): ISignupSessionRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaSignupSessionRepo(getPrismaClient());
}

function resolveTntClient(override: ITntClient | undefined): ITntClient {
  if (override) return override;
  const baseUrl = requireEnv("GEN_TNT_BASE_URL");
  const secret = requireEnv("GEN_TNT_INTERNAL_SECRET");
  return new HttpTntClient(baseUrl, secret);
}

function resolveAuthClient(override: IAuthClient | undefined): IAuthClient {
  if (override) return override;
  const baseUrl = requireEnv("GEN_AUTH_BASE_URL");
  return new HttpAuthClient(baseUrl);
}

function resolveEmailSender(override: EmailSender | undefined): EmailSender {
  return override ?? new ConsoleEmailSender();
}

function buildWorker(repo: ISignupSessionRepo, tntClient: ITntClient): GenRegWorker {
  let provisioningInterval: ReturnType<typeof setInterval> | undefined;
  let abandonInterval: ReturnType<typeof setInterval> | undefined;

  return {
    start() {
      provisioningInterval = setInterval(() => {
        sweepProvisioning(repo, tntClient).catch((err) => logger.error({ err }, "sweepProvisioning failed"));
      }, env.PROVISIONING_POLL_INTERVAL_MS);

      abandonInterval = setInterval(() => {
        sweepAbandoned(repo).catch((err) => logger.error({ err }, "sweepAbandoned failed"));
      }, env.ABANDON_SWEEP_INTERVAL_MS);
    },
    stop() {
      if (provisioningInterval) clearInterval(provisioningInterval);
      if (abandonInterval) clearInterval(abandonInterval);
    },
  };
}

export function createGenReg(config: GenRegConfig): GenRegInstance {
  const modules: Required<GenRegModulesConfig> = {
    signup: config.modules?.signup ?? true,
    verifyEmail: config.modules?.verifyEmail ?? true,
    worker: config.modules?.worker ?? true,
  };

  const repo = resolveRepo(config.repo);
  const emailSender = resolveEmailSender(config.emailSender);

  const needsTntClient = modules.signup || modules.verifyEmail || modules.worker;
  const tntClient = needsTntClient ? resolveTntClient(config.tntClient) : undefined;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  if (modules.signup) {
    const authClient = resolveAuthClient(config.authClient);
    const signupService = new SignupService(repo, tntClient!, authClient, emailSender);
    app.use("/api/v1", signupRoutes(new SignupController(signupService)));

    app.get("/api/v1/signup/:sessionId", async (req, res) => {
      const parsed = SessionIdParamSchema.safeParse(req.params.sessionId);
      if (!parsed.success) throw new SignupSessionNotFoundError(req.params.sessionId);
      const session = await repo.findById(parsed.data);
      if (!session) throw new SignupSessionNotFoundError(req.params.sessionId);
      res.json({
        sessionId: session.id,
        state: session.state,
        provisionedTenantId: session.provisionedTenantId,
        lastProvisioningError: session.lastProvisioningError,
      });
    });
  }

  if (modules.verifyEmail) {
    const verifyEmailService = new VerifyEmailService(repo, tntClient!);
    app.use("/api/v1", verifyEmailRoutes(new VerifyEmailController(verifyEmailService)));
  }

  app.use(errorHandler);

  const worker = modules.worker ? buildWorker(repo, tntClient!) : undefined;

  return { app, worker };
}
```

Note on the `/api/v1/signup/:sessionId` polling route: it depends only on `repo`, not on `modules.signup` or `modules.verifyEmail` — but it's mounted inside the `modules.signup` block because it's part of the signup module's HTTP surface (checking on a signup session's progress). If a future consumer wants this route without full signup (e.g. `verifyEmail`-only), that's a gap to revisit then, not solved speculatively here.

- [ ] **Step 6: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/create-gen-reg.test.ts
```

Expected: all tests PASS.

- [ ] **Step 7: Write `index.ts` — the package's public export surface**

`packages/gen-reg-starter/src/index.ts`:
```typescript
// src/index.ts
export { createGenReg } from "./create-gen-reg.ts";
export type { GenRegConfig, GenRegModulesConfig, GenRegWorker, GenRegInstance } from "./create-gen-reg.ts";

export type {
  ISignupSessionRepo,
  SignupSessionRecord,
  CreateSignupSessionInput,
} from "./domain/ports/signup-session.repository.port.ts";
export type { EmailSender } from "./common/email-sender.ts";
export type {
  ITntClient,
  TenantResponse,
  ProvisioningJobResponse,
  CreateTenantInput,
} from "./domain/ports/tnt-client.port.ts";
export type { IAuthClient, RegisterResult } from "./domain/ports/auth-client.port.ts";

export { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
export { ConsoleEmailSender } from "./common/email-sender.ts";
export { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
export { HttpAuthClient } from "./infra/auth-client/auth-client.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { errorHandler } from "./middleware/error-handler.ts";
export {
  AppError,
  GenRegConfigError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
  SignupDomainBlockedError,
  EmailVerificationTokenInvalidError,
  EmailVerificationTokenExpiredError,
  EmailAlreadyVerifiedError,
  ResumeTokenNotFoundError,
  SignupSessionNotFoundError,
  GenAuthRegistrationError,
  GenTntProvisioningError,
} from "./common/errors.ts";
```

- [ ] **Step 8: Run the full suite + typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: all suites pass (the exact count will be higher than Phase 1's 47 — this task added `create-gen-reg.test.ts`'s 9 tests and `env.test.ts`'s 3 tests from Task 4, minus the 4 tests removed with `server.test.ts`). No type errors.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-reg-starter/src/create-gen-reg.ts \
        packages/gen-reg-starter/src/create-gen-reg.test.ts \
        packages/gen-reg-starter/src/index.ts \
        packages/gen-reg-starter/src/worker.ts
git rm packages/gen-reg-starter/src/server.ts packages/gen-reg-starter/src/server.test.ts
git commit -m "feat: add createGenReg() factory, trim worker.ts, delete server.ts (absorbed into factory)"
```

---

### Task 6: Publish configuration for `gen-reg-starter`

**Files:**
- Modify: `packages/gen-reg-starter/package.json`

**Interfaces:** None new — packaging/metadata only.

- [ ] **Step 1: Add publish configuration**

In `packages/gen-reg-starter/package.json`, add `"repository"` and `"publishConfig"` fields (insert after `"version"`):

```json
  "repository": {
    "type": "git",
    "url": "git+https://github.com/GEN-MS/Gen_Reg.git"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com",
    "access": "restricted"
  },
```

The full file's top section now reads:
```json
{
  "name": "@gen-ms/gen-reg-starter",
  "version": "0.1.0",
  "repository": {
    "type": "git",
    "url": "git+https://github.com/GEN-MS/Gen_Reg.git"
  },
  "publishConfig": {
    "registry": "https://npm.pkg.github.com",
    "access": "restricted"
  },
  "type": "module",
  "main": "./dist/index.js",
  ...
```

(Leave everything else in the file — `main`, `types`, `exports`, `files`, `scripts`, `dependencies`, `devDependencies` — exactly as Task 1 wrote it.)

- [ ] **Step 2: Verify the package is publishable (dry run, no real publish)**

Actually publishing requires a GitHub Packages auth token (`NODE_AUTH_TOKEN`/`GITHUB_TOKEN`), which isn't available in this environment — do not attempt a real `npm publish`. Instead, verify the package would build and pack correctly:

```bash
npm run build --workspace=@gen-ms/gen-reg-starter
npm pack --workspace=@gen-ms/gen-reg-starter --dry-run
```

Expected: `build` succeeds (runs `prisma generate && tsc`, producing `packages/gen-reg-starter/dist/`); `pack --dry-run` lists the files that would be included in the published tarball (should include `dist/`, `prisma/schema.prisma`, `prisma/migrations/`, `package.json`, per the `"files"` field from Task 1 — should NOT include `src/`, `tests/`, `.env`, `node_modules/`).

- [ ] **Step 3: Commit**

```bash
git add packages/gen-reg-starter/package.json
git commit -m "chore: add GitHub Packages publish config for gen-reg-starter"
```

---

### Task 7: Build `gen-reg-demo`

**Files:**
- Create: `packages/gen-reg-demo/package.json`
- Create: `packages/gen-reg-demo/tsconfig.json`
- Create: `packages/gen-reg-demo/.env.example`
- Create: `packages/gen-reg-demo/src/index.ts`
- Test: `packages/gen-reg-demo/src/index.test.ts`

**Interfaces:**
- Consumes: `createGenReg` and all types/classes exported from `gen-reg-starter`'s `index.ts` (Task 5).
- Produces: nothing new for other tasks to consume — this is the leaf/terminal package.

- [ ] **Step 1: Write `package.json`**

```json
{
  "name": "@gen-ms/gen-reg-demo",
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
    "@gen-ms/gen-reg-starter": "*",
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

`"@gen-ms/gen-reg-starter": "*"` resolves to the local workspace package automatically via npm's workspace symlinking (npm has done this since v7 for any dependency whose name matches a workspace package and whose version range is satisfied — `"*"` always satisfies) — no special `workspace:` protocol needed.

- [ ] **Step 2: Write `tsconfig.json`**

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
  "exclude": ["node_modules", "dist", "src/**/*.test.ts"]
}
```

(No `@prisma/client` path alias needed here — `gen-reg-demo` never imports Prisma directly, only through `gen-reg-starter`'s public exports.)

- [ ] **Step 3: Write `.env.example`**

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

(Identical to Phase 1's original `.env.example` — this is the full var set the demo needs to run with every module enabled and no overrides.)

- [ ] **Step 4: Write the failing boot-smoke test**

`packages/gen-reg-demo/src/index.test.ts`:
```typescript
// src/index.test.ts
import { describe, it, expect, beforeEach } from "vitest";
import { createGenReg } from "@gen-ms/gen-reg-starter";

describe("gen-reg-demo boot", () => {
  beforeEach(() => {
    process.env.DATABASE_URL = "postgresql://unused/unused";
    process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
    process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
    process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
  });

  it("createGenReg({}) does not throw with a full env set", () => {
    expect(() => createGenReg({})).not.toThrow();
  });

  it("the returned app responds to GET /health", async () => {
    const request = (await import("supertest")).default;
    const { app } = createGenReg({});
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });
});
```

- [ ] **Step 5: Write `src/index.ts`**

```typescript
// src/index.ts
import "dotenv/config";
import { pathToFileURL } from "node:url";
import { createGenReg } from "@gen-ms/gen-reg-starter";

const PORT = Number(process.env.PORT ?? 3200);

export function startDemo() {
  const { app, worker } = createGenReg({});
  app.listen(PORT, () => {
    console.log(`Gen_REG demo listening on port ${PORT}`);
  });
  worker?.start();
  return { app, worker };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startDemo();
}
```

This one file replaces Phase 1's `server.ts` + `worker.ts` entry-point boilerplate entirely — including the Windows `pathToFileURL` entry-point-detection fix, applied here from the start rather than discovered as a bug (Phase 1 hit this as a real bug; this plan applies the known-good fix directly).

- [ ] **Step 6: Run the test to verify it fails, then passes**

```bash
npm install
npx vitest run packages/gen-reg-demo/src/index.test.ts
```

Expected: first run (before `src/index.ts` exists) FAILS with a module-not-found error; after Step 5, re-run:

```bash
npx vitest run packages/gen-reg-demo/src/index.test.ts
```

Expected: both tests PASS.

- [ ] **Step 7: Run the demo for a real boot check**

```bash
cp packages/gen-reg-demo/.env.example packages/gen-reg-demo/.env
npm run dev --workspace=@gen-ms/gen-reg-demo
```

Expected: logs `Gen_REG demo listening on port 3200`. In another terminal:
```bash
curl http://localhost:3200/health
```
Expected: `{"status":"ok"}`. Stop the dev server (Ctrl+C) once confirmed.

- [ ] **Step 8: Typecheck**

```bash
npx tsc -p packages/gen-reg-demo/tsconfig.json --noEmit
```

Expected: no errors.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-reg-demo/package.json \
        packages/gen-reg-demo/tsconfig.json \
        packages/gen-reg-demo/.env.example \
        packages/gen-reg-demo/src/index.ts \
        packages/gen-reg-demo/src/index.test.ts
git commit -m "feat: add gen-reg-demo — thin reference app running gen-reg-starter standalone"
```

Note: `packages/gen-reg-demo/.env` (created in Step 7) and `node_modules/` are gitignored (matches the existing root `.gitignore` patterns, which apply at any depth for `node_modules/` and `.env`) — verify with `git status` that neither is staged before committing.

---

### Task 8: Final verification, README, and script path updates

**Files:**
- Modify: `README.md`
- Modify: `scripts/smoke-signup.sh` (path/URL references only — logic unchanged)
- Test: none new — this task runs the full existing test matrix across both packages.

**Interfaces:** None new — this is the final consistency pass.

- [ ] **Step 1: Update `scripts/smoke-signup.sh`'s comments**

Read the current `scripts/smoke-signup.sh`. It already targets the running app purely via `GEN_REG_BASE_URL` (defaulting to `http://localhost:3200`) — no code change needed, since `gen-reg-demo` serves on the same port. Update only the file's header comment to reflect the new structure:

Change:
```bash
# scripts/smoke-signup.sh
# Full-path smoke test: signup -> verify-email -> poll until ACTIVE.
# Requires: Gen_REG (server + worker) running locally, plus a live Gen_TNT
# and Gen_Auth stack (and Gen_TNT's own mock step server) already up.
```
to:
```bash
# scripts/smoke-signup.sh
# Full-path smoke test: signup -> verify-email -> poll until ACTIVE.
# Requires: gen-reg-demo (npm run dev --workspace=@gen-ms/gen-reg-demo) running
# locally, plus a live Gen_TNT and Gen_Auth stack (and Gen_TNT's own mock step
# server) already up.
```

No other line changes.

- [ ] **Step 2: Rewrite `README.md`**

Replace entire contents:
```markdown
# Gen_REG

Generalized signup/registration service, genericized from CPMS-Platform's `reg-svc`. Structured as an npm workspace with two packages, mirroring the org's Gen_Auth/Gen_TNT starter/demo convention:

- **`packages/gen-reg-starter`** — the reusable library. Signup validation, email verification, Gen_TNT provisioning handoff, and the background worker, all behind a single configurable `createGenReg(config)` entry point. Every major dependency (repo, email sender, Gen_TNT client, Gen_Auth client) and module (signup routes, verify-email routes, worker) is individually swappable/toggleable — unset fields fall back to the built-in defaults. Published to GitHub Packages as `@gen-ms/gen-reg-starter`.
- **`packages/gen-reg-demo`** — thin reference app. Calls `createGenReg({})` with no overrides and runs it standalone over HTTP — proves the starter works outside its own package and gives non-Node consumers an HTTP surface to call.

See `docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md` for Phase 1's original design and `docs/superpowers/specs/2026-07-22-gen-reg-starter-library-design.md` for the starter/demo split's design.

## Prerequisites

- A running Gen_TNT instance with the `GET /api/v1/tenants/by-slug/{slug}` endpoint.
- A running Gen_Auth instance (`POST /api/v1/auth/register` must be reachable and open).

## Running it locally

```bash
docker compose up -d                                   # Postgres on 5436
cp packages/gen-reg-starter/.env.example packages/gen-reg-starter/.env
cp packages/gen-reg-demo/.env.example packages/gen-reg-demo/.env
npm install
npm run prisma:migrate:dev --workspace=@gen-ms/gen-reg-starter
npm run dev                                             # HTTP API on port 3200, via gen-reg-demo
```

## Testing

```bash
npm test                                                # runs gen-reg-starter's suite (Docker required for Testcontainers repo tests)
npm test --workspace=@gen-ms/gen-reg-demo               # boot-smoke test
```

## Using `gen-reg-starter` as a library

```typescript
import { createGenReg } from "@gen-ms/gen-reg-starter";

const { app, worker } = createGenReg({
  // Any field left unset falls back to the built-in default (Prisma repo,
  // console email sender, HTTP Gen_TNT/Gen_Auth clients, all modules on).
  emailSender: myRealEmailSender,
  modules: { worker: false },  // e.g. run the worker as a separate process/deployment
});

app.listen(3200);
```

## What's not here yet

Payment/plan selection, Turnstile captcha, abandoned-signup recovery emails, the wizard read-model, idempotency-key middleware, and a RabbitMQ event bus are deferred — see `docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md`'s "Explicitly out of scope" section. Each is planned as its own sub-project, landing as a configurable module on this starter/demo split rather than an ad-hoc addition.
```

- [ ] **Step 3: Full-workspace verification**

Docker must be running.

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npm test --workspace=@gen-ms/gen-reg-demo
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
npx tsc -p packages/gen-reg-demo/tsconfig.json --noEmit
```

Expected: all suites pass in both packages, no type errors in either.

- [ ] **Step 4: Re-run the smoke script against the demo**

```bash
npm run dev --workspace=@gen-ms/gen-reg-demo &
sleep 2
bash scripts/smoke-signup.sh
```

Expected: same behavior as Phase 1 — signup succeeds, script prompts for `VERIFY_TOKEN` from the demo's console log (the `ConsoleEmailSender` stub is unchanged). Stop the background dev server once confirmed (`kill %1` or Ctrl+C if run in foreground instead).

- [ ] **Step 5: Commit**

```bash
git add README.md scripts/smoke-signup.sh
git commit -m "docs: update README and smoke script for the starter/demo workspace split"
```

---

## Self-Review Notes (for whoever executes this plan)

- **Spec coverage:** every element of the design spec maps to a task — workspace split + move (Task 1), `ITntClient`/`IAuthClient` extraction (Tasks 2–3), lazy env validation + `GenRegConfigError` (Task 4), the `createGenReg()` factory with all four swappable dependencies and three module toggles (Task 5), GitHub Packages publish config (Task 6), `gen-reg-demo` (Task 7), docs/final verification (Task 8).
- **Migration correctness bar** (from the spec): satisfied by Task 5's default-config test (`createGenReg({})` reproduces Phase 1's `server.test.ts` assertions) and override-path test (proves every one of `repo`/`emailSender`/`tntClient`/`authClient` is actually used when supplied, not just type-compatible).
- **Known gap, intentionally not fixed in this plan:** the `/api/v1/signup/:sessionId` polling route is bundled inside the `modules.signup` toggle rather than having its own toggle — noted inline in Task 5, revisit if a real consumer needs `verifyEmail`-only with polling.
- **Deliberately out of scope:** RabbitMQ, Valkey, and all 6 deferred Phase 2 features (payment, captcha, recovery emails, wizard read-model, idempotency middleware, event bus) — this plan only builds the starter/demo split they'll eventually land on top of.
