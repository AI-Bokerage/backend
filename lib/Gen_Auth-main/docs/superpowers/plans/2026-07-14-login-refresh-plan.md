# Register + Login + Refresh Rotation + Logout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Gen_AUTH a working, generic auth flow: register a user, log in, refresh a session with rotation + replay detection, log out one session or every session, and let a trusted admin caller provision users directly.

**Architecture:** Layered per-module: `rotation.ts` (pure decision logic) → `token.ts` (pure token/crypto helpers) → `repo.ts` (thin Prisma reads/writes) → `service.ts` (orchestrates the above, throws `AppError` on failure) → `routes.ts` (HTTP layer: zod validation, status codes, calls service). Each layer is unit-testable independently; `service.ts` tests mock `repo.ts`/`token.ts` so no live Postgres is needed for the test suite.

**Tech Stack:** Express, Zod, Prisma (`@prisma/client`), `argon2`, `jose` (RS256 JWT + JWKS), `node:crypto` (refresh token generation/hashing), Vitest.

## Global Constraints

- RS256 JWT via the existing single static keypair in `src/infra/jwt/keys.ts` — no multi-key rotation in this slice.
- Argon2id params: `memoryCost: 65536` (KiB, i.e. 64MB), `timeCost: 3`, `parallelism: 1`, `hashLength: 32` — copied from the CPMS `SecurityConfig.java` baseline. Salt is not separately configurable in the `argon2` npm package (it auto-generates one); do not pass a `saltLength` option, it doesn't exist in this library's API.
- Refresh token: 32 random bytes (`node:crypto.randomBytes`), base64url-encoded, returned raw to the client exactly once. Only its SHA-256 hex hash is ever persisted.
- Replay detection is Postgres-only (no Redis for this piece) — a token row with `revokedAt` already set means reuse; revoke the entire family via `familyId`.
- All rejected refresh tokens (expired, replayed, or simply unknown) return the same generic `401 { error: "invalid refresh token" }` — never a different message per case, to avoid leaking which case occurred.
- Public `POST /register` never accepts a `roles` field. Only `POST /internal/v1/users` (gated by `X-Internal-Secret`) can set roles at creation time.
- Tokens are returned in the JSON response body (`accessToken`, `refreshToken`, `expiresIn` in seconds) — never as cookies.
- `INTERNAL_API_SECRET` is a required env var with no default — the service fails to start if it's unset (Zod throws at `env.ts` import time), same behavior as `DATABASE_URL` already has.
- Out of scope for every task below: lockout enforcement, JWKS multi-key rotation, magic-link password reset, OAuth.

---

### Task 1: Rotation decision logic (pure)

**Files:**

- Create: `src/modules/auth/v1/rotation.ts`
- Test: `src/modules/auth/v1/rotation.test.ts`

**Interfaces:**

- Produces: `RefreshTokenRow` (`{ familyId: string; revokedAt: Date | null; absoluteExpiresAt: Date }`), `RotationDecision` (`{ action: 'rotate' } | { action: 'reject-expired' } | { action: 'reject-replay'; familyId: string }`), `decideRotation(row: RefreshTokenRow, now: Date): RotationDecision` — consumed by Task 3's `service.ts`.

- [ ] **Step 1: Write the failing tests**

```typescript
// src/modules/auth/v1/rotation.test.ts
import { describe, expect, it } from "vitest";
import { decideRotation } from "./rotation.js";

describe("decideRotation", () => {
  const future = new Date("2030-01-01T00:00:00Z");
  const past = new Date("2020-01-01T00:00:00Z");
  const now = new Date("2025-01-01T00:00:00Z");

  it("rotates a fresh, unrevoked, unexpired token", () => {
    const result = decideRotation(
      { familyId: "fam-1", revokedAt: null, absoluteExpiresAt: future },
      now,
    );
    expect(result).toEqual({ action: "rotate" });
  });

  it("rejects as replay when the token is already revoked but not expired", () => {
    const result = decideRotation(
      { familyId: "fam-1", revokedAt: past, absoluteExpiresAt: future },
      now,
    );
    expect(result).toEqual({ action: "reject-replay", familyId: "fam-1" });
  });

  it("rejects as expired when past absoluteExpiresAt, even if never revoked", () => {
    const result = decideRotation(
      { familyId: "fam-1", revokedAt: null, absoluteExpiresAt: past },
      now,
    );
    expect(result).toEqual({ action: "reject-expired" });
  });

  it("prefers expired over replay when both are true", () => {
    const result = decideRotation(
      { familyId: "fam-1", revokedAt: past, absoluteExpiresAt: past },
      now,
    );
    expect(result).toEqual({ action: "reject-expired" });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pnpm vitest run src/modules/auth/v1/rotation.test.ts`
Expected: FAIL with "Cannot find module './rotation.js'" (file doesn't exist yet)

- [ ] **Step 3: Write minimal implementation**

```typescript
// src/modules/auth/v1/rotation.ts
export interface RefreshTokenRow {
  familyId: string;
  revokedAt: Date | null;
  absoluteExpiresAt: Date;
}

export type RotationDecision =
  | { action: "rotate" }
  | { action: "reject-expired" }
  | { action: "reject-replay"; familyId: string };

export function decideRotation(
  row: RefreshTokenRow,
  now: Date,
): RotationDecision {
  if (now > row.absoluteExpiresAt) {
    return { action: "reject-expired" };
  }
  if (row.revokedAt !== null) {
    return { action: "reject-replay", familyId: row.familyId };
  }
  return { action: "rotate" };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pnpm vitest run src/modules/auth/v1/rotation.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/modules/auth/v1/rotation.ts src/modules/auth/v1/rotation.test.ts
git commit -m "add refresh token rotation/replay decision logic"
```

---

### Task 2: Token helpers (pure crypto + JWT signing)

**Files:**

- Create: `src/modules/auth/v1/token.ts`
- Test: `src/modules/auth/v1/token.test.ts`

**Interfaces:**

- Consumes: nothing from earlier tasks.
- Produces: `generateRefreshTokenValue(): string`, `hashRefreshToken(raw: string): string`, `computeAbsoluteExpiry(now: Date, ttlDays: number): Date`, `AccessTokenClaims` (`{ sub: string; orgId: string | null; roles: string[] }`), `SignOptions` (`{ kid: string; issuer: string; ttlMinutes: number }`), `signAccessToken(privateKey: KeyLike, claims: AccessTokenClaims, opts: SignOptions): Promise<{ token: string; jti: string }>` — all consumed by Task 3's `service.ts`. `signAccessToken` takes the private key as a parameter (not importing `infra/jwt/keys.ts` itself) so it stays testable with a throwaway keypair.

- [ ] **Step 1: Write the failing tests**

```typescript
// src/modules/auth/v1/token.test.ts
import { describe, expect, it } from "vitest";
import { generateKeyPair, jwtVerify } from "jose";
import {
  computeAbsoluteExpiry,
  generateRefreshTokenValue,
  hashRefreshToken,
  signAccessToken,
} from "./token.js";

describe("generateRefreshTokenValue", () => {
  it("generates a base64url string with no padding/slashes", () => {
    const value = generateRefreshTokenValue();
    expect(value).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(value.length).toBeGreaterThan(30);
  });

  it("generates a different value each call", () => {
    expect(generateRefreshTokenValue()).not.toBe(generateRefreshTokenValue());
  });
});

describe("hashRefreshToken", () => {
  it("is deterministic for the same input", () => {
    expect(hashRefreshToken("abc")).toBe(hashRefreshToken("abc"));
  });

  it("produces a 64-char hex sha256 digest", () => {
    expect(hashRefreshToken("abc")).toMatch(/^[a-f0-9]{64}$/);
  });

  it("differs for different inputs", () => {
    expect(hashRefreshToken("abc")).not.toBe(hashRefreshToken("xyz"));
  });
});

describe("computeAbsoluteExpiry", () => {
  it("adds the given number of days to now", () => {
    const now = new Date("2025-01-01T00:00:00Z");
    const result = computeAbsoluteExpiry(now, 30);
    expect(result).toEqual(new Date("2025-01-31T00:00:00Z"));
  });
});

describe("signAccessToken", () => {
  it("signs a JWT whose claims round-trip through verification", async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const { token, jti } = await signAccessToken(
      privateKey,
      { sub: "user-1", orgId: "org-1", roles: ["admin"] },
      { kid: "test-kid", issuer: "genauth-test", ttlMinutes: 15 },
    );

    const { payload } = await jwtVerify(token, publicKey, {
      algorithms: ["RS256"],
    });
    expect(payload.sub).toBe("user-1");
    expect(payload.orgId).toBe("org-1");
    expect(payload.roles).toEqual(["admin"]);
    expect(payload.iss).toBe("genauth-test");
    expect(payload.jti).toBe(jti);
  });

  it("sets orgId to null when the user has no org", async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const { token } = await signAccessToken(
      privateKey,
      { sub: "user-1", orgId: null, roles: [] },
      { kid: "test-kid", issuer: "genauth-test", ttlMinutes: 15 },
    );
    const { payload } = await jwtVerify(token, publicKey, {
      algorithms: ["RS256"],
    });
    expect(payload.orgId).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pnpm vitest run src/modules/auth/v1/token.test.ts`
Expected: FAIL with "Cannot find module './token.js'"

- [ ] **Step 3: Write minimal implementation**

```typescript
// src/modules/auth/v1/token.ts
import { randomBytes, createHash, randomUUID } from "node:crypto";
import { SignJWT, type KeyLike } from "jose";

export function generateRefreshTokenValue(): string {
  return randomBytes(32).toString("base64url");
}

export function hashRefreshToken(raw: string): string {
  return createHash("sha256").update(raw).digest("hex");
}

export function computeAbsoluteExpiry(now: Date, ttlDays: number): Date {
  return new Date(now.getTime() + ttlDays * 24 * 60 * 60 * 1000);
}

export interface AccessTokenClaims {
  sub: string;
  orgId: string | null;
  roles: string[];
}

export interface SignOptions {
  kid: string;
  issuer: string;
  ttlMinutes: number;
}

export async function signAccessToken(
  privateKey: KeyLike,
  claims: AccessTokenClaims,
  opts: SignOptions,
): Promise<{ token: string; jti: string }> {
  const jti = randomUUID();
  const token = await new SignJWT({ orgId: claims.orgId, roles: claims.roles })
    .setProtectedHeader({ alg: "RS256", kid: opts.kid })
    .setSubject(claims.sub)
    .setIssuer(opts.issuer)
    .setIssuedAt()
    .setExpirationTime(`${opts.ttlMinutes}m`)
    .setJti(jti)
    .sign(privateKey);
  return { token, jti };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pnpm vitest run src/modules/auth/v1/token.test.ts`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add src/modules/auth/v1/token.ts src/modules/auth/v1/token.test.ts
git commit -m "add refresh/access token helpers"
```

---

### Task 3: Repo + Service layer

**Files:**

- Create: `src/modules/auth/v1/repo.ts`
- Create: `src/modules/auth/v1/service.ts`
- Test: `src/modules/auth/v1/service.test.ts`

**Interfaces:**

- Consumes: `decideRotation`/`RefreshTokenRow` (Task 1), `generateRefreshTokenValue`/`hashRefreshToken`/`computeAbsoluteExpiry`/`signAccessToken`/`AccessTokenClaims`/`SignOptions` (Task 2), `prisma` from `src/infra/db/prisma.ts` (existing), `getPrivateKey` from `src/infra/jwt/keys.ts` (existing), `env` from `src/config/env.ts` (existing — uses `JWT_KID`, `JWT_ISSUER`, `ACCESS_TOKEN_TTL_MINUTES`, `REFRESH_TOKEN_TTL_DAYS`, all already defined), `AppError` from `src/common/errors/app-error.ts` (existing).
- Produces: `AuthResult` (`{ accessToken: string; refreshToken: string; expiresIn: number }`), `register(input: { email: string; password: string; orgId?: string | null; roles?: string[] }): Promise<{ userId: string }>`, `login(input: { email: string; password: string }): Promise<AuthResult>`, `refresh(rawRefreshToken: string): Promise<AuthResult>`, `logout(rawRefreshToken: string): Promise<void>`, `logoutAll(rawRefreshToken: string): Promise<void>` — all consumed by Task 4's `routes.ts` and Task 5's internal routes.

- [ ] **Step 1: Make every vitest invocation load `.env`, not just `pnpm test`**

`service.test.ts` (written in the next step) imports `service.ts`, which imports `env.ts` — the first test file in this project to do so. Nothing currently loads `.env` when vitest runs (only the `dev` script does, via `tsx --env-file`), so `envSchema.parse(process.env)` would throw on missing `DATABASE_URL` etc. the moment vitest imports it, unrelated to whatever logic is actually being tested. This plan runs vitest both via `pnpm test` and via ad-hoc `pnpm vitest run <file>` commands, so the fix needs to work for both — a vitest config with a setup file, not a change to one npm script. Node 24 (this project's `.nvmrc` target) has `process.loadEnvFile()` built in, so no new dependency is needed.

```typescript
// vitest.setup.ts — new file, project root
process.loadEnvFile(".env");
```

```typescript
// vitest.config.ts — new file, project root
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    setupFiles: ["./vitest.setup.ts"],
  },
});
```

Run: `pnpm test`
Expected: `No test files found, exiting with code 0` (same as before — this step only fixes env loading, doesn't add tests yet)

- [ ] **Step 2: Write the repo layer (thin Prisma wrappers, no branching logic — not test-first)**

```typescript
// src/modules/auth/v1/repo.ts
import type { RefreshToken, User } from "@prisma/client";
import { prisma } from "../../../infra/db/prisma.js";

export function findUserByEmail(email: string): Promise<User | null> {
  return prisma.user.findUnique({ where: { email } });
}

export function findUserById(id: string): Promise<User | null> {
  return prisma.user.findUnique({ where: { id } });
}

export function createUser(input: {
  email: string;
  passwordHash: string;
  orgId: string | null;
  roles: string[];
}): Promise<User> {
  return prisma.user.create({ data: input });
}

export function createRefreshToken(input: {
  userId: string;
  tokenHash: string;
  familyId: string;
  generation: number;
  absoluteExpiresAt: Date;
}): Promise<RefreshToken> {
  return prisma.refreshToken.create({ data: input });
}

export function findRefreshTokenByHash(
  tokenHash: string,
): Promise<RefreshToken | null> {
  return prisma.refreshToken.findUnique({ where: { tokenHash } });
}

export function revokeToken(id: string, now: Date): Promise<RefreshToken> {
  return prisma.refreshToken.update({
    where: { id },
    data: { revokedAt: now },
  });
}

export function revokeFamily(
  familyId: string,
  now: Date,
): Promise<{ count: number }> {
  return prisma.refreshToken.updateMany({
    where: { familyId, revokedAt: null },
    data: { revokedAt: now },
  });
}

export function revokeAllForUser(
  userId: string,
  now: Date,
): Promise<{ count: number }> {
  return prisma.refreshToken.updateMany({
    where: { userId, revokedAt: null },
    data: { revokedAt: now },
  });
}
```

- [ ] **Step 3: Write the failing service tests (mocking repo, token, argon2)**

```typescript
// src/modules/auth/v1/service.test.ts
import { beforeEach, describe, expect, it, vi } from "vitest";

// vi.mock() factories are hoisted above regular `const` declarations by
// Vitest's transform — referencing a plain `const x = vi.fn()` inside one
// throws a "Cannot access before initialization" TDZ error at runtime.
// vi.hoisted() declares the mock fns in the same hoisted scope as vi.mock
// itself, which is the documented fix.
const { hash, verify } = vi.hoisted(() => ({ hash: vi.fn(), verify: vi.fn() }));
vi.mock("argon2", () => ({
  default: { hash, verify, argon2id: 2 },
  hash,
  verify,
  argon2id: 2,
}));

const {
  findUserByEmail,
  findUserById,
  createUser,
  createRefreshToken,
  findRefreshTokenByHash,
  revokeToken,
  revokeFamily,
  revokeAllForUser,
} = vi.hoisted(() => ({
  findUserByEmail: vi.fn(),
  findUserById: vi.fn(),
  createUser: vi.fn(),
  createRefreshToken: vi.fn(),
  findRefreshTokenByHash: vi.fn(),
  revokeToken: vi.fn(),
  revokeFamily: vi.fn(),
  revokeAllForUser: vi.fn(),
}));
vi.mock("./repo.js", () => ({
  findUserByEmail,
  findUserById,
  createUser,
  createRefreshToken,
  findRefreshTokenByHash,
  revokeToken,
  revokeFamily,
  revokeAllForUser,
}));

const { signAccessToken } = vi.hoisted(() => ({ signAccessToken: vi.fn() }));
vi.mock("./token.js", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./token.js")>();
  return { ...actual, signAccessToken };
});

const { getPrivateKey } = vi.hoisted(() => ({
  getPrivateKey: vi.fn().mockResolvedValue("fake-private-key"),
}));
vi.mock("../../../infra/jwt/keys.js", () => ({ getPrivateKey }));

import { AppError } from "../../../common/errors/app-error.js";
import * as authService from "./service.js";

beforeEach(() => {
  vi.clearAllMocks();
  signAccessToken.mockResolvedValue({
    token: "fake.jwt.token",
    jti: "fake-jti",
  });
});

describe("register", () => {
  it("creates a user with a hashed password when the email is free", async () => {
    findUserByEmail.mockResolvedValue(null);
    hash.mockResolvedValue("hashed-password");
    createUser.mockResolvedValue({ id: "user-1" });

    const result = await authService.register({
      email: "a@b.com",
      password: "password123",
    });

    expect(result).toEqual({ userId: "user-1" });
    expect(createUser).toHaveBeenCalledWith({
      email: "a@b.com",
      passwordHash: "hashed-password",
      orgId: null,
      roles: [],
    });
  });

  it("rejects with 409 when the email is already registered", async () => {
    findUserByEmail.mockResolvedValue({ id: "existing-user" });

    await expect(
      authService.register({ email: "a@b.com", password: "password123" }),
    ).rejects.toMatchObject({ statusCode: 409 } satisfies Partial<AppError>);
    expect(createUser).not.toHaveBeenCalled();
  });
});

describe("login", () => {
  it("issues a session for correct credentials", async () => {
    findUserByEmail.mockResolvedValue({
      id: "user-1",
      passwordHash: "hashed-password",
      orgId: "org-1",
      roles: ["member"],
      isActive: true,
    });
    verify.mockResolvedValue(true);
    createRefreshToken.mockResolvedValue({});

    const result = await authService.login({
      email: "a@b.com",
      password: "password123",
    });

    expect(result.accessToken).toBe("fake.jwt.token");
    expect(result.refreshToken).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(result.expiresIn).toBeGreaterThan(0);
    expect(createRefreshToken).toHaveBeenCalledTimes(1);
  });

  it("rejects with 401 for an unknown email", async () => {
    findUserByEmail.mockResolvedValue(null);

    await expect(
      authService.login({ email: "nope@b.com", password: "x" }),
    ).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
  });

  it("rejects with 401 for a wrong password", async () => {
    findUserByEmail.mockResolvedValue({
      id: "user-1",
      passwordHash: "hashed-password",
      orgId: null,
      roles: [],
      isActive: true,
    });
    verify.mockResolvedValue(false);

    await expect(
      authService.login({ email: "a@b.com", password: "wrong" }),
    ).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
  });

  it("rejects with 401 for a deactivated user", async () => {
    findUserByEmail.mockResolvedValue({
      id: "user-1",
      passwordHash: "hashed-password",
      orgId: null,
      roles: [],
      isActive: false,
    });

    await expect(
      authService.login({ email: "a@b.com", password: "password123" }),
    ).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
    expect(verify).not.toHaveBeenCalled();
  });
});

describe("refresh", () => {
  const future = new Date(Date.now() + 1000 * 60 * 60 * 24);
  const past = new Date(Date.now() - 1000 * 60 * 60 * 24);

  it("rotates a valid token: revokes the old row, creates a new one, returns new tokens", async () => {
    findRefreshTokenByHash.mockResolvedValue({
      id: "row-1",
      userId: "user-1",
      familyId: "fam-1",
      generation: 0,
      revokedAt: null,
      absoluteExpiresAt: future,
    });
    findUserById.mockResolvedValue({
      id: "user-1",
      orgId: "org-1",
      roles: ["member"],
      isActive: true,
    });
    revokeToken.mockResolvedValue({});
    createRefreshToken.mockResolvedValue({});

    const result = await authService.refresh("some-raw-token");

    expect(revokeToken).toHaveBeenCalledWith("row-1", expect.any(Date));
    expect(createRefreshToken).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: "user-1",
        familyId: "fam-1",
        generation: 1,
        absoluteExpiresAt: future,
      }),
    );
    expect(result.accessToken).toBe("fake.jwt.token");
    expect(revokeFamily).not.toHaveBeenCalled();
  });

  it("revokes the whole family and rejects with 401 on replay (already-revoked row)", async () => {
    findRefreshTokenByHash.mockResolvedValue({
      id: "row-1",
      userId: "user-1",
      familyId: "fam-1",
      generation: 2,
      revokedAt: past,
      absoluteExpiresAt: future,
    });
    revokeFamily.mockResolvedValue({ count: 3 });

    await expect(authService.refresh("reused-token")).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
    expect(revokeFamily).toHaveBeenCalledWith("fam-1", expect.any(Date));
    expect(createRefreshToken).not.toHaveBeenCalled();
  });

  it("rejects with 401 when past absoluteExpiresAt without touching revokeFamily", async () => {
    findRefreshTokenByHash.mockResolvedValue({
      id: "row-1",
      userId: "user-1",
      familyId: "fam-1",
      generation: 0,
      revokedAt: null,
      absoluteExpiresAt: past,
    });

    await expect(authService.refresh("expired-token")).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
    expect(revokeFamily).not.toHaveBeenCalled();
    expect(createRefreshToken).not.toHaveBeenCalled();
  });

  it("rejects with 401 for a token hash that matches no row", async () => {
    findRefreshTokenByHash.mockResolvedValue(null);

    await expect(authService.refresh("unknown-token")).rejects.toMatchObject({
      statusCode: 401,
    } satisfies Partial<AppError>);
  });
});

describe("logout", () => {
  it("revokes only the presented token's family", async () => {
    findRefreshTokenByHash.mockResolvedValue({
      id: "row-1",
      userId: "user-1",
      familyId: "fam-1",
    });
    revokeFamily.mockResolvedValue({ count: 1 });

    await authService.logout("some-token");

    expect(revokeFamily).toHaveBeenCalledWith("fam-1", expect.any(Date));
    expect(revokeAllForUser).not.toHaveBeenCalled();
  });

  it("is a no-op when the token is unknown (idempotent)", async () => {
    findRefreshTokenByHash.mockResolvedValue(null);

    await authService.logout("unknown-token");

    expect(revokeFamily).not.toHaveBeenCalled();
  });
});

describe("logoutAll", () => {
  it("revokes every family for the token's user", async () => {
    findRefreshTokenByHash.mockResolvedValue({
      id: "row-1",
      userId: "user-1",
      familyId: "fam-1",
    });
    revokeAllForUser.mockResolvedValue({ count: 3 });

    await authService.logoutAll("some-token");

    expect(revokeAllForUser).toHaveBeenCalledWith("user-1", expect.any(Date));
    expect(revokeFamily).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `pnpm vitest run src/modules/auth/v1/service.test.ts`
Expected: FAIL with "Cannot find module './service.js'"

- [ ] **Step 5: Write minimal implementation**

```typescript
// src/modules/auth/v1/service.ts
import argon2 from "argon2";
import { randomUUID } from "node:crypto";
import { AppError } from "../../../common/errors/app-error.js";
import { env } from "../../../config/env.js";
import { getPrivateKey } from "../../../infra/jwt/keys.js";
import * as repo from "./repo.js";
import { decideRotation } from "./rotation.js";
import {
  computeAbsoluteExpiry,
  generateRefreshTokenValue,
  hashRefreshToken,
  signAccessToken,
} from "./token.js";

const ARGON2_OPTIONS = {
  type: argon2.argon2id,
  memoryCost: 65536,
  timeCost: 3,
  parallelism: 1,
  hashLength: 32,
};

export interface AuthResult {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

async function issueSession(
  userId: string,
  familyId: string,
  generation: number,
  absoluteExpiresAt: Date,
  orgId: string | null,
  roles: string[],
): Promise<AuthResult> {
  const rawRefreshToken = generateRefreshTokenValue();
  await repo.createRefreshToken({
    userId,
    tokenHash: hashRefreshToken(rawRefreshToken),
    familyId,
    generation,
    absoluteExpiresAt,
  });
  const privateKey = await getPrivateKey();
  const { token: accessToken } = await signAccessToken(
    privateKey,
    { sub: userId, orgId, roles },
    {
      kid: env.JWT_KID,
      issuer: env.JWT_ISSUER,
      ttlMinutes: env.ACCESS_TOKEN_TTL_MINUTES,
    },
  );
  return {
    accessToken,
    refreshToken: rawRefreshToken,
    expiresIn: env.ACCESS_TOKEN_TTL_MINUTES * 60,
  };
}

export async function register(input: {
  email: string;
  password: string;
  orgId?: string | null;
  roles?: string[];
}): Promise<{ userId: string }> {
  const existing = await repo.findUserByEmail(input.email);
  if (existing) throw new AppError(409, "email already registered");
  const passwordHash = await argon2.hash(input.password, ARGON2_OPTIONS);
  const user = await repo.createUser({
    email: input.email,
    passwordHash,
    orgId: input.orgId ?? null,
    roles: input.roles ?? [],
  });
  return { userId: user.id };
}

export async function login(input: {
  email: string;
  password: string;
}): Promise<AuthResult> {
  const user = await repo.findUserByEmail(input.email);
  if (!user || !user.isActive) throw new AppError(401, "invalid credentials");
  const valid = await argon2.verify(user.passwordHash, input.password);
  if (!valid) throw new AppError(401, "invalid credentials");
  const now = new Date();
  return issueSession(
    user.id,
    randomUUID(),
    0,
    computeAbsoluteExpiry(now, env.REFRESH_TOKEN_TTL_DAYS),
    user.orgId,
    user.roles,
  );
}

export async function refresh(rawRefreshToken: string): Promise<AuthResult> {
  const row = await repo.findRefreshTokenByHash(
    hashRefreshToken(rawRefreshToken),
  );
  if (!row) throw new AppError(401, "invalid refresh token");

  const now = new Date();
  const decision = decideRotation(
    {
      familyId: row.familyId,
      revokedAt: row.revokedAt,
      absoluteExpiresAt: row.absoluteExpiresAt,
    },
    now,
  );

  if (decision.action === "reject-expired") {
    throw new AppError(401, "invalid refresh token");
  }
  if (decision.action === "reject-replay") {
    await repo.revokeFamily(decision.familyId, now);
    console.warn(
      `possible refresh token replay detected, family=${decision.familyId}`,
    );
    throw new AppError(401, "invalid refresh token");
  }

  const user = await repo.findUserById(row.userId);
  if (!user || !user.isActive) throw new AppError(401, "invalid refresh token");

  await repo.revokeToken(row.id, now);
  return issueSession(
    row.userId,
    row.familyId,
    row.generation + 1,
    row.absoluteExpiresAt,
    user.orgId,
    user.roles,
  );
}

export async function logout(rawRefreshToken: string): Promise<void> {
  const row = await repo.findRefreshTokenByHash(
    hashRefreshToken(rawRefreshToken),
  );
  if (!row) return;
  await repo.revokeFamily(row.familyId, new Date());
}

export async function logoutAll(rawRefreshToken: string): Promise<void> {
  const row = await repo.findRefreshTokenByHash(
    hashRefreshToken(rawRefreshToken),
  );
  if (!row) return;
  await repo.revokeAllForUser(row.userId, new Date());
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `pnpm vitest run src/modules/auth/v1/service.test.ts`
Expected: PASS (12 tests)

- [ ] **Step 7: Typecheck the whole project**

Run: `pnpm typecheck`
Expected: no errors

- [ ] **Step 8: Commit**

```bash
git add vitest.setup.ts vitest.config.ts src/modules/auth/v1/repo.ts src/modules/auth/v1/service.ts src/modules/auth/v1/service.test.ts
git commit -m "add auth service layer: register/login/refresh/logout/logoutAll"
```

---

### Task 4: Auth HTTP routes + REGISTRATION_MODE

**Files:**

- Modify: `src/config/env.ts` (add `REGISTRATION_MODE`)
- Modify: `.env.example` (document `REGISTRATION_MODE`)
- Modify: `src/modules/auth/v1/routes.ts` (replace the stub entirely)
- Test: `src/modules/auth/v1/routes.test.ts`

**Interfaces:**

- Consumes: `register`/`login`/`refresh`/`logout`/`logoutAll` from Task 3's `service.ts`, `AppError` (existing), `env.REGISTRATION_MODE` (this task).
- Produces: `authRoutes` (Express `Router`), mounted at `/api/v1/auth` in `src/app.ts` (already wired from the earlier scaffold — no change needed there).

- [ ] **Step 1: Add REGISTRATION_MODE to env config**

```typescript
// src/config/env.ts — full file after this change
import { z } from "zod";

const envSchema = z.object({
  NODE_ENV: z
    .enum(["development", "test", "production"])
    .default("development"),
  PORT: z.coerce.number().default(4100),
  DATABASE_URL: z.string().url(),
  REDIS_URL: z.string().url(),
  JWT_PRIVATE_KEY_PATH: z.string(),
  JWT_PUBLIC_KEY_PATH: z.string(),
  JWT_KID: z.string().default("default"),
  JWT_ISSUER: z.string().default("genauth"),
  ACCESS_TOKEN_TTL_MINUTES: z.coerce.number().default(15),
  REFRESH_TOKEN_TTL_DAYS: z.coerce.number().default(30),
  REGISTRATION_MODE: z.enum(["open", "disabled"]).default("open"),
  INTERNAL_API_SECRET: z.string().min(1),
});

export const env = envSchema.parse(process.env);
```

(This also adds `INTERNAL_API_SECRET`, needed by Task 5 — added here since both are one-line schema additions to the same file; Task 5 does not need to touch `env.ts` again.)

- [ ] **Step 2: Update .env.example**

```bash
# .env.example — full file after this change
NODE_ENV=development
PORT=4100
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/genauth
REDIS_URL=redis://localhost:6379
JWT_PRIVATE_KEY_PATH=./keys/jwt-private.pem
JWT_PUBLIC_KEY_PATH=./keys/jwt-public.pem
JWT_KID=default
JWT_ISSUER=genauth
ACCESS_TOKEN_TTL_MINUTES=15
REFRESH_TOKEN_TTL_DAYS=30
REGISTRATION_MODE=open
INTERNAL_API_SECRET=dev-secret-change-me
```

- [ ] **Step 3: Run typecheck to confirm the env change is consistent**

Run: `pnpm typecheck`
Expected: no errors (no other file references `env` in a way this breaks)

- [ ] **Step 4: Write the failing route tests (service mocked)**

```typescript
// src/modules/auth/v1/routes.test.ts
import express from "express";
import "express-async-errors";
import request from "supertest";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { errorHandler } from "../../../middleware/error-handler.js";
import { env } from "../../../config/env.js";

const { register, login, refresh, logout, logoutAll } = vi.hoisted(() => ({
  register: vi.fn(),
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  logoutAll: vi.fn(),
}));
vi.mock("./service.js", () => ({
  register,
  login,
  refresh,
  logout,
  logoutAll,
}));

import { authRoutes } from "./routes.js";

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use("/api/v1/auth", authRoutes);
  app.use(errorHandler);
  return app;
}

beforeEach(() => {
  vi.clearAllMocks();
  env.REGISTRATION_MODE = "open";
});

describe("POST /register", () => {
  it("returns 201 with the created userId", async () => {
    register.mockResolvedValue({ userId: "user-1" });
    const res = await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "a@b.com", password: "password123" });
    expect(res.status).toBe(201);
    expect(res.body).toEqual({ userId: "user-1" });
  });

  it("returns 400 for an invalid email", async () => {
    const res = await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "not-an-email", password: "password123" });
    expect(res.status).toBe(400);
    expect(register).not.toHaveBeenCalled();
  });

  it("returns 400 for a too-short password", async () => {
    const res = await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "a@b.com", password: "short" });
    expect(res.status).toBe(400);
  });

  it("ignores a roles field if the caller sends one", async () => {
    register.mockResolvedValue({ userId: "user-1" });
    await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "a@b.com", password: "password123", roles: ["admin"] });
    expect(register).toHaveBeenCalledWith({
      email: "a@b.com",
      password: "password123",
    });
  });

  it("returns 403 when REGISTRATION_MODE is disabled", async () => {
    env.REGISTRATION_MODE = "disabled";
    const res = await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "a@b.com", password: "password123" });
    expect(res.status).toBe(403);
    expect(register).not.toHaveBeenCalled();
  });

  it("returns 409 when the service reports a duplicate email", async () => {
    const { AppError } = await import("../../../common/errors/app-error.js");
    register.mockRejectedValue(new AppError(409, "email already registered"));
    const res = await request(buildApp())
      .post("/api/v1/auth/register")
      .send({ email: "a@b.com", password: "password123" });
    expect(res.status).toBe(409);
  });
});

describe("POST /login", () => {
  it("returns 200 with the session on success", async () => {
    login.mockResolvedValue({
      accessToken: "a",
      refreshToken: "b",
      expiresIn: 900,
    });
    const res = await request(buildApp())
      .post("/api/v1/auth/login")
      .send({ email: "a@b.com", password: "password123" });
    expect(res.status).toBe(200);
    expect(res.body).toEqual({
      accessToken: "a",
      refreshToken: "b",
      expiresIn: 900,
    });
  });

  it("returns 401 when the service rejects credentials", async () => {
    const { AppError } = await import("../../../common/errors/app-error.js");
    login.mockRejectedValue(new AppError(401, "invalid credentials"));
    const res = await request(buildApp())
      .post("/api/v1/auth/login")
      .send({ email: "a@b.com", password: "wrong" });
    expect(res.status).toBe(401);
  });
});

describe("POST /refresh", () => {
  it("returns 200 with rotated tokens", async () => {
    refresh.mockResolvedValue({
      accessToken: "a2",
      refreshToken: "b2",
      expiresIn: 900,
    });
    const res = await request(buildApp())
      .post("/api/v1/auth/refresh")
      .send({ refreshToken: "old-token" });
    expect(res.status).toBe(200);
    expect(refresh).toHaveBeenCalledWith("old-token");
  });

  it("returns 400 when refreshToken is missing", async () => {
    const res = await request(buildApp()).post("/api/v1/auth/refresh").send({});
    expect(res.status).toBe(400);
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("POST /logout", () => {
  it("returns 204 and calls logout with the token", async () => {
    logout.mockResolvedValue(undefined);
    const res = await request(buildApp())
      .post("/api/v1/auth/logout")
      .send({ refreshToken: "tok" });
    expect(res.status).toBe(204);
    expect(logout).toHaveBeenCalledWith("tok");
  });
});

describe("POST /logout-all", () => {
  it("returns 204 and calls logoutAll with the token", async () => {
    logoutAll.mockResolvedValue(undefined);
    const res = await request(buildApp())
      .post("/api/v1/auth/logout-all")
      .send({ refreshToken: "tok" });
    expect(res.status).toBe(204);
    expect(logoutAll).toHaveBeenCalledWith("tok");
  });
});
```

- [ ] **Step 5: Add supertest as a dev dependency**

Run: `pnpm add -D supertest @types/supertest`
Expected: adds both packages to `package.json`/`pnpm-lock.yaml`

- [ ] **Step 6: Run tests to verify they fail**

Run: `pnpm vitest run src/modules/auth/v1/routes.test.ts`
Expected: FAIL (`authRoutes` is still the old stub — `/register` etc. don't exist, or `register`/`login` mocks are never called because the real routes.ts doesn't import `./service.js` yet)

- [ ] **Step 7: Replace the route stub with the real implementation**

```typescript
// src/modules/auth/v1/routes.ts — replaces the entire stub file
import { Router } from "express";
import { z } from "zod";
import { env } from "../../../config/env.js";
import { AppError } from "../../../common/errors/app-error.js";
import * as authService from "./service.js";

export const authRoutes = Router();

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  orgId: z.string().optional(),
});

authRoutes.post("/register", async (req, res) => {
  if (env.REGISTRATION_MODE === "disabled") {
    throw new AppError(403, "self-registration disabled");
  }
  const body = registerSchema.parse(req.body);
  const result = await authService.register(body);
  res.status(201).json(result);
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

authRoutes.post("/login", async (req, res) => {
  const body = loginSchema.parse(req.body);
  const result = await authService.login(body);
  res.status(200).json(result);
});

const refreshSchema = z.object({ refreshToken: z.string().min(1) });

authRoutes.post("/refresh", async (req, res) => {
  const body = refreshSchema.parse(req.body);
  const result = await authService.refresh(body.refreshToken);
  res.status(200).json(result);
});

authRoutes.post("/logout", async (req, res) => {
  const body = refreshSchema.parse(req.body);
  await authService.logout(body.refreshToken);
  res.status(204).send();
});

authRoutes.post("/logout-all", async (req, res) => {
  const body = refreshSchema.parse(req.body);
  await authService.logoutAll(body.refreshToken);
  res.status(204).send();
});
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `pnpm vitest run src/modules/auth/v1/routes.test.ts`
Expected: PASS (11 tests)

- [ ] **Step 9: Run the full test suite and typecheck**

Run: `pnpm test && pnpm typecheck`
Expected: all pass, no type errors

- [ ] **Step 10: Commit**

```bash
git add src/config/env.ts .env.example src/modules/auth/v1/routes.ts src/modules/auth/v1/routes.test.ts package.json pnpm-lock.yaml
git commit -m "wire real auth routes: register/login/refresh/logout/logout-all"
```

---

### Task 5: Internal user-provisioning endpoint

**Files:**

- Create: `src/middleware/internal-secret.middleware.ts`
- Test: `src/middleware/internal-secret.middleware.test.ts`
- Create: `src/modules/internal/users/v1/routes.ts`
- Modify: `src/app.ts` (mount the new route)

**Interfaces:**

- Consumes: `env.INTERNAL_API_SECRET` (added in Task 4), `register` from `service.ts` (Task 3).
- Produces: `internalSecretMiddleware` (Express middleware), `internalUsersRoutes` (Express `Router`), mounted at `/internal/v1/users` in `app.ts`.

- [ ] **Step 1: Write the failing middleware test**

```typescript
// src/middleware/internal-secret.middleware.test.ts
import express from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { env } from "../config/env.js";
import { internalSecretMiddleware } from "./internal-secret.middleware.js";

function buildApp() {
  const app = express();
  app.use(internalSecretMiddleware);
  app.get("/", (_req, res) => res.status(200).json({ ok: true }));
  return app;
}

beforeEach(() => {
  env.INTERNAL_API_SECRET = "correct-secret";
});

describe("internalSecretMiddleware", () => {
  it("allows the request through with the correct header", async () => {
    const res = await request(buildApp())
      .get("/")
      .set("X-Internal-Secret", "correct-secret");
    expect(res.status).toBe(200);
  });

  it("rejects with 403 when the header is missing", async () => {
    const res = await request(buildApp()).get("/");
    expect(res.status).toBe(403);
  });

  it("rejects with 403 when the header is wrong", async () => {
    const res = await request(buildApp())
      .get("/")
      .set("X-Internal-Secret", "wrong-secret");
    expect(res.status).toBe(403);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run src/middleware/internal-secret.middleware.test.ts`
Expected: FAIL with "Cannot find module './internal-secret.middleware.js'"

- [ ] **Step 3: Write minimal implementation**

```typescript
// src/middleware/internal-secret.middleware.ts
import type { NextFunction, Request, Response } from "express";
import { env } from "../config/env.js";

export function internalSecretMiddleware(
  req: Request,
  res: Response,
  next: NextFunction,
) {
  const header = req.headers["x-internal-secret"];
  if (header !== env.INTERNAL_API_SECRET) {
    res.status(403).json({ error: "invalid internal secret" });
    return;
  }
  next();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run src/middleware/internal-secret.middleware.test.ts`
Expected: PASS (3 tests)

- [ ] **Step 5: Create the internal users route (no dedicated test — thin wrapper over already-tested `register`)**

```typescript
// src/modules/internal/users/v1/routes.ts
import { Router } from "express";
import { z } from "zod";
import * as authService from "../../../auth/v1/service.js";

export const internalUsersRoutes = Router();

const createUserSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  orgId: z.string().optional(),
  roles: z.array(z.string()).optional(),
});

internalUsersRoutes.post("/", async (req, res) => {
  const body = createUserSchema.parse(req.body);
  const result = await authService.register(body);
  res.status(201).json(result);
});
```

- [ ] **Step 6: Mount the new route in app.ts**

```typescript
// src/app.ts — full file after this change
import "express-async-errors";
import express from "express";
import pinoHttp from "pino-http";
import { errorHandler } from "./middleware/error-handler.js";
import { notFoundHandler } from "./middleware/not-found.js";
import { internalSecretMiddleware } from "./middleware/internal-secret.middleware.js";
import { authRoutes } from "./modules/auth/v1/routes.js";
import { jwksRoutes } from "./modules/jwks/v1/routes.js";
import { internalUsersRoutes } from "./modules/internal/users/v1/routes.js";

export const app = express();

app.use(pinoHttp());
app.use(express.json());

app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.use("/.well-known/jwks.json", jwksRoutes);
app.use("/api/v1/auth", authRoutes);
app.use("/internal/v1/users", internalSecretMiddleware, internalUsersRoutes);

app.use(notFoundHandler);
app.use(errorHandler);
```

- [ ] **Step 7: Run the full test suite and typecheck**

Run: `pnpm test && pnpm typecheck && pnpm lint`
Expected: all pass, no type/lint errors

- [ ] **Step 8: Commit**

```bash
git add src/middleware/internal-secret.middleware.ts src/middleware/internal-secret.middleware.test.ts src/modules/internal/users/v1/routes.ts src/app.ts
git commit -m "add internal user-provisioning endpoint gated by shared secret"
```

---

### Task 6: README update + manual integration verification

**Files:**

- Modify: `README.md`

**Interfaces:**

- Consumes: nothing new — this documents Tasks 1–5's endpoints and runs the full flow against real Postgres+Redis.

- [ ] **Step 1: Update README's status section and document the new endpoints**

Replace the "Status: infra scaffold only" section and the "Porting plan" section's framing (they're now partially done) with:

```markdown
## Status

**Done:** register, login, refresh (rotation + Postgres-only replay detection), logout, logout-all, internal user-provisioning endpoint (`/internal/v1/users`, gated by `X-Internal-Secret`). See `docs/superpowers/specs/2026-07-14-login-refresh-design.md` for the full design and `docs/superpowers/plans/2026-07-14-login-refresh-plan.md` for how it was built.

**Not done yet:** lockout enforcement on login, JWKS multi-key rotation (single static key), magic-link password reset.

## Endpoints

| Method | Path                      | Auth                       | Notes                                            |
| ------ | ------------------------- | -------------------------- | ------------------------------------------------ |
| POST   | `/api/v1/auth/register`   | none                       | Disabled (403) when `REGISTRATION_MODE=disabled` |
| POST   | `/api/v1/auth/login`      | none                       |                                                  |
| POST   | `/api/v1/auth/refresh`    | refresh token in body      | Rotates; old token dead immediately              |
| POST   | `/api/v1/auth/logout`     | refresh token in body      | Revokes this session only                        |
| POST   | `/api/v1/auth/logout-all` | refresh token in body      | Revokes every session for that user              |
| POST   | `/internal/v1/users`      | `X-Internal-Secret` header | Always available; only path that can set `roles` |
| GET    | `/.well-known/jwks.json`  | none                       |                                                  |
```

- [ ] **Step 2: Add the manual integration check to README**

```markdown
## Manual integration check (run before trusting this in any environment)

Requires Docker (`docker-compose up -d`) and a real `.env` (see Running It above).

\`\`\`bash

# 1. Register

curl -s -X POST localhost:4100/api/v1/auth/register \
-H 'content-type: application/json' \
-d '{"email":"test@example.com","password":"password123"}'

# 2. Login — save the refreshToken from the response

curl -s -X POST localhost:4100/api/v1/auth/login \
-H 'content-type: application/json' \
-d '{"email":"test@example.com","password":"password123"}'

# 3. Refresh with that refreshToken — get a new pair back

curl -s -X POST localhost:4100/api/v1/auth/refresh \
-H 'content-type: application/json' \
-d '{"refreshToken":"<refreshToken from step 2>"}'

# 4. Re-use the OLD (step 2) refreshToken again — must now 401 (replay detected)

curl -s -i -X POST localhost:4100/api/v1/auth/refresh \
-H 'content-type: application/json' \
-d '{"refreshToken":"<refreshToken from step 2>"}'

# 5. The NEW refresh token from step 3 should ALSO now 401 — replay revoked the whole family

curl -s -i -X POST localhost:4100/api/v1/auth/refresh \
-H 'content-type: application/json' \
-d '{"refreshToken":"<refreshToken from step 3>"}'

# 6. Log in again (fresh session), then log out, then confirm refresh 401s

curl -s -X POST localhost:4100/api/v1/auth/login -H 'content-type: application/json' \
-d '{"email":"test@example.com","password":"password123"}'
curl -s -X POST localhost:4100/api/v1/auth/logout -H 'content-type: application/json' \
-d '{"refreshToken":"<refreshToken from step 6 login>"}'
curl -s -i -X POST localhost:4100/api/v1/auth/refresh -H 'content-type: application/json' \
-d '{"refreshToken":"<refreshToken from step 6 login>"}'
\`\`\`

Expected: step 4 and 5 both return 401; the final refresh in step 6 also returns 401.
```

- [ ] **Step 3: Actually run the manual check above against real Postgres+Redis**

Run: `docker-compose up -d`, `pnpm prisma migrate deploy`, `pnpm dev`, then execute every curl command from Step 2 in order.
Expected: matches every "Expected" note inline above — especially steps 4 and 5 both returning `401`.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "document new auth endpoints and manual verification steps"
```

## Self-Review Notes

- **Spec coverage:** register ✅ (Task 4), login ✅ (Task 3+4), refresh+rotation+replay ✅ (Task 1+3+4), logout ✅ (Task 3+4), logout-all ✅ (Task 3+4), internal provisioning + REGISTRATION_MODE toggle ✅ (Task 4+5), Argon2id params ✅ (Task 3), Postgres-only replay ✅ (Task 1+3), JSON token delivery ✅ (Task 3 returns body, no cookie code anywhere), generic 401 messaging ✅ (Task 3 uses the same message string in every reject branch).
- **Placeholder scan:** none found — every step has runnable code.
- **Type consistency:** `AuthResult`, `RefreshTokenRow`, `RotationDecision`, `AccessTokenClaims`, `SignOptions` are defined once (Tasks 1–2) and reused with identical shapes in Tasks 3–4; `repo.ts`'s function names (`findUserByEmail`, `createRefreshToken`, etc.) match exactly between Task 3's implementation and its mocks in `service.test.ts`.
