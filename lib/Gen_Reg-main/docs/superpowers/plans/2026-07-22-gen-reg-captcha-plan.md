# Gen_REG Turnstile Captcha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Cloudflare Turnstile bot protection in front of `POST /api/v1/signup`, configurable via a `modules.captcha` toggle (default `false`), following the same ports/adapters + lazy-env pattern as every other adapter in `createGenReg()`.

**Architecture:** One new port (`ICaptchaVerifier`) with one adapter (`TurnstileCaptchaVerifier`, plain `fetch` to Cloudflare's siteverify endpoint). A middleware (`createCaptchaMiddleware`) is conditionally mounted in front of only the `POST /signup` route — a small necessary change to the existing `signupRoutes()` factory lets it accept an optional middleware. A ported dev-only Turnstile token-gen HTML page rounds out the local-testing story.

**Tech Stack:** Same as the rest of `gen-reg-starter` (Node, Express, Zod, Vitest, TDD). No new npm dependencies — Turnstile verification is a single `fetch` call, matching `HttpAuthClient`/`HttpTntClient`'s existing style.

## Global Constraints

- All paths below are under `packages/gen-reg-starter/` unless stated otherwise.
- New ports go in `src/domain/ports/*.port.ts`; the new adapter goes in `src/infra/captcha/`.
- New env vars follow the existing lazy/eager split in `src/config/env.ts`: `TURNSTILE_SECRET_KEY` and `TURNSTILE_SITE_KEY` are lazy (via `requireEnv()`, only required when actually needed); `TURNSTILE_VERIFY_URL`/`TURNSTILE_CDN_URL` are eager (added to `EnvSchema` with real Cloudflare URLs as defaults — they're stable public endpoints, not secrets).
- `modules.captcha` defaults to `false` — `createGenReg({})` with today's `.env` must keep reproducing exactly today's behavior with zero new required config.
- Test baseline before this plan starts: `gen-reg-starter` 17 files / 98 tests passing, typecheck clean. Every task must end with the full suite passing (baseline + this task's new tests) and a clean typecheck.
- No new npm dependencies — `TurnstileCaptchaVerifier` uses the global `fetch`, tested via `vi.stubGlobal("fetch", ...)` exactly like `HttpAuthClient`'s test file.

---

### Task 1: `ICaptchaVerifier` port + `TurnstileCaptchaVerifier` adapter

**Files:**
- Create: `packages/gen-reg-starter/src/domain/ports/captcha-verifier.port.ts`
- Create: `packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.ts`
- Test: `packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.test.ts`

**Interfaces:**
- Produces: `CaptchaVerifyResult`, `ICaptchaVerifier` — consumed by Task 2 (middleware), Task 4 (factory wiring).
- Produces: `TurnstileCaptchaVerifier` (constructor: `secretKey: string, verifyUrl: string`) implementing `ICaptchaVerifier` — consumed by Task 4.

- [ ] **Step 1: Write the port**

```typescript
// src/domain/ports/captcha-verifier.port.ts
export interface CaptchaVerifyResult {
  verified: boolean;
  errorCodes?: string[];
}

export interface ICaptchaVerifier {
  verify(token: string): Promise<CaptchaVerifyResult>;
}
```

- [ ] **Step 2: Write the failing test**

```typescript
// src/infra/captcha/turnstile-verifier.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { TurnstileCaptchaVerifier } from "./turnstile-verifier.ts";

function mockFetchOnce(ok: boolean, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok,
      json: async () => body,
    }),
  );
}

describe("TurnstileCaptchaVerifier", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("returns verified true when Cloudflare reports success", async () => {
    mockFetchOnce(true, { success: true, challenge_ts: "2026-01-01T00:00:00Z" });
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("test-token");

    expect(result).toEqual({ verified: true, errorCodes: undefined });
  });

  it("returns verified false with errorCodes when Cloudflare reports failure", async () => {
    mockFetchOnce(true, { success: false, "error-codes": ["invalid-input-response"] });
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("bad-token");

    expect(result).toEqual({ verified: false, errorCodes: ["invalid-input-response"] });
  });

  it("fails closed (verified false) when Cloudflare returns a non-2xx response", async () => {
    mockFetchOnce(false, {});
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("any-token");

    expect(result.verified).toBe(false);
    expect(result.errorCodes).toEqual(["api_error"]);
  });

  it("fails closed (verified false) when fetch throws, without throwing itself", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("any-token");

    expect(result.verified).toBe(false);
    expect(result.errorCodes).toEqual(["verification_failed"]);
  });

  it("posts the token and secret to the configured verify URL", async () => {
    mockFetchOnce(true, { success: true });
    const verifier = new TurnstileCaptchaVerifier("my-secret", "https://example.com/verify");

    await verifier.verify("the-token");

    expect(fetch).toHaveBeenCalledWith(
      "https://example.com/verify",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ response: "the-token", secret: "my-secret" }),
      }),
    );
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.test.ts
```

Expected: FAIL — `./turnstile-verifier.ts` does not exist.

- [ ] **Step 4: Write the adapter**

```typescript
// src/infra/captcha/turnstile-verifier.ts
import type { CaptchaVerifyResult, ICaptchaVerifier } from "../../domain/ports/captcha-verifier.port.ts";

export class TurnstileCaptchaVerifier implements ICaptchaVerifier {
  constructor(
    private readonly secretKey: string,
    private readonly verifyUrl: string,
  ) {}

  async verify(token: string): Promise<CaptchaVerifyResult> {
    try {
      const response = await fetch(this.verifyUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ response: token, secret: this.secretKey }),
      });

      if (!response.ok) {
        return { verified: false, errorCodes: ["api_error"] };
      }

      const data = (await response.json()) as {
        success: boolean;
        "error-codes"?: string[];
      };

      return { verified: data.success, errorCodes: data["error-codes"] };
    } catch {
      return { verified: false, errorCodes: ["verification_failed"] };
    }
  }
}
```

(Cloudflare's siteverify response uses the key `error-codes`, not `errorCodes` — mapped to this port's camelCase `errorCodes` field here.)

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.test.ts
```

Expected: 5 tests PASS.

- [ ] **Step 6: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 18 files / 103 tests passing, typecheck clean.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-reg-starter/src/domain/ports/captcha-verifier.port.ts \
        packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.ts \
        packages/gen-reg-starter/src/infra/captcha/turnstile-verifier.test.ts
git commit -m "feat: add ICaptchaVerifier port and TurnstileCaptchaVerifier adapter"
```

---

### Task 2: Captcha errors + `createCaptchaMiddleware`

**Files:**
- Modify: `packages/gen-reg-starter/src/common/errors.ts`
- Create: `packages/gen-reg-starter/src/middleware/captcha-verify.ts`
- Test: `packages/gen-reg-starter/src/middleware/captcha-verify.test.ts`

**Interfaces:**
- Consumes: `ICaptchaVerifier` (Task 1).
- Produces: `CaptchaTokenMissingError`, `CaptchaVerificationFailedError` — consumed by Task 4 (index.ts exports). Produces: `createCaptchaMiddleware(verifier: ICaptchaVerifier): RequestHandler` — consumed by Task 3 (signup routes) and Task 4 (factory wiring).

- [ ] **Step 1: Add the new error classes**

In `packages/gen-reg-starter/src/common/errors.ts`, add after `WebhookSignatureInvalidError` (before the `GenRegConfigError` boot-time-error comment block):

```typescript
export class CaptchaTokenMissingError extends AppError {
  constructor() {
    super(400, "CAPTCHA_TOKEN_MISSING", "CAPTCHA token is required");
  }
}

export class CaptchaVerificationFailedError extends AppError {
  constructor() {
    super(400, "CAPTCHA_VERIFICATION_FAILED", "CAPTCHA verification failed");
  }
}
```

- [ ] **Step 2: Write the failing test**

```typescript
// src/middleware/captcha-verify.test.ts
import { describe, it, expect, vi } from "vitest";
import type { Request, Response, NextFunction } from "express";
import { createCaptchaMiddleware } from "./captcha-verify.ts";
import { CaptchaTokenMissingError, CaptchaVerificationFailedError } from "../common/errors.ts";
import type { ICaptchaVerifier } from "../domain/ports/captcha-verifier.port.ts";

function fakeVerifier(verified: boolean): ICaptchaVerifier {
  return { verify: vi.fn(async () => ({ verified })) };
}

function fakeReqRes(body: Record<string, unknown>) {
  const req = { body } as Request;
  const res = {} as Response;
  const next = vi.fn() as unknown as NextFunction;
  return { req, res, next };
}

describe("createCaptchaMiddleware", () => {
  it("throws CaptchaTokenMissingError when captchaToken is missing, without calling the verifier", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({});

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaTokenMissingError);
    expect(verifier.verify).not.toHaveBeenCalled();
  });

  it("throws CaptchaTokenMissingError when captchaToken is not a string", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: 12345 });

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaTokenMissingError);
    expect(verifier.verify).not.toHaveBeenCalled();
  });

  it("throws CaptchaVerificationFailedError when the verifier reports not verified", async () => {
    const verifier = fakeVerifier(false);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: "a-token" });

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaVerificationFailedError);
    expect(verifier.verify).toHaveBeenCalledWith("a-token");
  });

  it("calls next() when the verifier reports verified", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: "a-token" });

    await middleware(req, res, next);

    expect(next).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/middleware/captcha-verify.test.ts
```

Expected: FAIL — `./captcha-verify.ts` does not exist.

- [ ] **Step 4: Write the middleware**

```typescript
// src/middleware/captcha-verify.ts
import type { Request, Response, NextFunction, RequestHandler } from "express";
import type { ICaptchaVerifier } from "../domain/ports/captcha-verifier.port.ts";
import { CaptchaTokenMissingError, CaptchaVerificationFailedError } from "../common/errors.ts";

export function createCaptchaMiddleware(verifier: ICaptchaVerifier): RequestHandler {
  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const token = req.body?.captchaToken;

    if (typeof token !== "string" || token.length === 0) {
      throw new CaptchaTokenMissingError();
    }

    const result = await verifier.verify(token);

    if (!result.verified) {
      throw new CaptchaVerificationFailedError();
    }

    next();
  };
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/middleware/captcha-verify.test.ts
```

Expected: 4 tests PASS.

- [ ] **Step 6: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 19 files / 107 tests passing, typecheck clean.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-reg-starter/src/common/errors.ts \
        packages/gen-reg-starter/src/middleware/captcha-verify.ts \
        packages/gen-reg-starter/src/middleware/captcha-verify.test.ts
git commit -m "feat: add captcha error classes and createCaptchaMiddleware"
```

---

### Task 3: Signup route hook + captcha dev page + env additions

**Files:**
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/routes.ts`
- Modify: `packages/gen-reg-starter/src/config/env.ts`
- Create: `packages/gen-reg-starter/src/modules/captcha/v1/routes.ts`
- Test: `packages/gen-reg-starter/src/modules/signup/v1/routes.test.ts`

**Interfaces:**
- Changes: `signupRoutes(controller: SignupController, captchaMiddleware?: RequestHandler): Router` — widened signature, backward compatible (second param optional) — consumed by Task 4.
- Produces: `captchaDevRoutes(siteKey: string, cdnUrl: string): Router` — consumed by Task 4.
- Produces: `TURNSTILE_VERIFY_URL`, `TURNSTILE_CDN_URL` on the eagerly-parsed `env` object — consumed by Task 4.

- [ ] **Step 1: Write the failing test for the routes change**

`packages/gen-reg-starter/src/modules/signup/v1/routes.ts` currently has no test file — add one covering the new optional-middleware behavior:

```typescript
// src/modules/signup/v1/routes.test.ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { signupRoutes } from "./routes.ts";
import type { SignupController } from "./controller.ts";

function fakeController(): SignupController {
  return {
    startSignup: vi.fn((_req, res) => res.status(201).json({ ok: true })),
    checkSubdomain: vi.fn((_req, res) => res.status(200).json({ ok: true })),
    resumeSignup: vi.fn((_req, res) => res.status(200).json({ ok: true })),
  } as unknown as SignupController;
}

describe("signupRoutes", () => {
  it("mounts POST /signup without a captcha middleware when none is supplied", async () => {
    const controller = fakeController();
    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller));

    const res = await request(app).post("/signup").send({});
    expect(res.status).toBe(201);
    expect(controller.startSignup).toHaveBeenCalledOnce();
  });

  it("runs the supplied captcha middleware before POST /signup's controller", async () => {
    const controller = fakeController();
    const order: string[] = [];
    const captchaMiddleware = vi.fn((_req, _res, next) => {
      order.push("captcha");
      next();
    });
    (controller.startSignup as ReturnType<typeof vi.fn>).mockImplementation((_req, res) => {
      order.push("controller");
      res.status(201).json({ ok: true });
    });

    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller, captchaMiddleware));

    await request(app).post("/signup").send({});

    expect(captchaMiddleware).toHaveBeenCalledOnce();
    expect(order).toEqual(["captcha", "controller"]);
  });

  it("does not run the captcha middleware on check-subdomain or resume", async () => {
    const controller = fakeController();
    const captchaMiddleware = vi.fn((_req, _res, next) => next());
    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller, captchaMiddleware));

    await request(app).get("/signup/check-subdomain?value=acme");
    await request(app).get("/signup/resume?token=abc");

    expect(captchaMiddleware).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/modules/signup/v1/routes.test.ts
```

Expected: FAIL — `signupRoutes` doesn't accept a second argument yet (the test still runs, but the second/third assertions about middleware behavior fail since nothing invokes it).

- [ ] **Step 3: Update `signupRoutes`**

Replace `packages/gen-reg-starter/src/modules/signup/v1/routes.ts` entirely:

```typescript
// src/modules/signup/v1/routes.ts
import { Router, type RequestHandler } from "express";
import type { SignupController } from "./controller.ts";

export function signupRoutes(controller: SignupController, captchaMiddleware?: RequestHandler): Router {
  const router = Router();
  router.post("/signup", ...(captchaMiddleware ? [captchaMiddleware] : []), controller.startSignup);
  router.get("/signup/check-subdomain", controller.checkSubdomain);
  router.get("/signup/resume", controller.resumeSignup);
  return router;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/modules/signup/v1/routes.test.ts
```

Expected: 3 tests PASS.

- [ ] **Step 5: Add the eager env vars**

Replace `packages/gen-reg-starter/src/config/env.ts` entirely:

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
  TURNSTILE_VERIFY_URL: z.string().url().default("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
  TURNSTILE_CDN_URL: z.string().url().default("https://challenges.cloudflare.com/turnstile/v0/api.js"),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it — as opposed to EnvSchema above, which eagerly validates
// only the fields every consumer needs regardless of which modules/adapters
// they enable. DATABASE_URL, GEN_TNT_BASE_URL, GEN_TNT_INTERNAL_SECRET,
// GEN_AUTH_BASE_URL, STRIPE_*/RAZORPAY_*/VALKEY_URL, and TURNSTILE_SECRET_KEY/
// TURNSTILE_SITE_KEY are intentionally NOT in EnvSchema — they're read through
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

(Only the two new fields and the comment's variable list changed — everything else in this file is unchanged from its current state.)

- [ ] **Step 6: Write the captcha dev-page routes**

```typescript
// src/modules/captcha/v1/routes.ts
import { randomBytes } from "node:crypto";
import { Router } from "express";

function tokenGenHtml(siteKey: string, nonce: string, cdnUrl: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <title>Turnstile Token Generator — Gen_REG dev</title>
  <script nonce="${nonce}" src="${cdnUrl}" async defer></script>
  <style>
    *, *::before, *::after { box-sizing: border-box; }
    body {
      font-family: system-ui, sans-serif;
      background: #0f1117;
      color: #e2e8f0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      gap: 24px;
      padding: 24px;
    }
    h1 { font-size: 1.25rem; font-weight: 600; margin: 0; }
    p  { font-size: 0.875rem; color: #94a3b8; margin: 0; text-align: center; }
    #widget-wrap {
      padding: 20px;
      background: #1e2532;
      border-radius: 12px;
      border: 1px solid #2d3748;
    }
    #result { display: none; flex-direction: column; gap: 12px; width: 100%; max-width: 560px; }
    #result label { font-size: 0.8rem; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; }
    #token-box {
      width: 100%;
      min-height: 80px;
      background: #1e2532;
      border: 1px solid #2d3748;
      border-radius: 8px;
      padding: 12px;
      color: #68d391;
      font-family: monospace;
      font-size: 0.8rem;
      word-break: break-all;
      white-space: pre-wrap;
      resize: none;
    }
    #copy-btn {
      align-self: flex-end;
      padding: 8px 20px;
      background: #3b82f6;
      color: #fff;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 500;
    }
    #copy-btn:hover { background: #2563eb; }
    #copy-btn.copied { background: #10b981; }
  </style>
</head>
<body>
  <h1>Turnstile Token Generator</h1>
  <p>Solve the challenge to get a token, then paste it into a POST /api/v1/signup request as "captchaToken".</p>

  <div id="widget-wrap">
    <div
      class="cf-turnstile"
      data-sitekey="${siteKey}"
      data-callback="onTokenReceived"
      data-theme="dark"
    ></div>
  </div>

  <div id="result">
    <label>Token — paste this as "captchaToken" in your signup request body</label>
    <textarea id="token-box" readonly></textarea>
    <button id="copy-btn">Copy token</button>
  </div>

  <p style="font-size:0.8rem;color:#64748b;text-align:center;max-width:480px">
    Token is single-use and expires in ~5 minutes.
  </p>

  <script nonce="${nonce}">
    function onTokenReceived(token) {
      var box = document.getElementById('token-box');
      var result = document.getElementById('result');
      box.value = token;
      result.style.display = 'flex';
      navigator.clipboard.writeText(token).catch(function() {});
    }

    document.getElementById('copy-btn').addEventListener('click', function() {
      var token = document.getElementById('token-box').value;
      var btn = document.getElementById('copy-btn');
      navigator.clipboard.writeText(token).then(function() {
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(function() {
          btn.textContent = 'Copy token';
          btn.classList.remove('copied');
        }, 2000);
      });
    });
  </script>
</body>
</html>`;
}

export function captchaDevRoutes(siteKey: string, cdnUrl: string): Router {
  const router = Router();

  router.get("/captcha/token-gen", (_req, res) => {
    const nonce = randomBytes(16).toString("base64");
    const cdnOrigin = new URL(cdnUrl).origin;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.setHeader(
      "Content-Security-Policy",
      [
        `script-src 'nonce-${nonce}' ${cdnOrigin}`,
        `frame-src ${cdnOrigin}`,
        `default-src 'self'`,
        `style-src 'unsafe-inline'`,
      ].join("; "),
    );
    res.send(tokenGenHtml(siteKey, nonce, cdnUrl));
  });

  return router;
}
```

- [ ] **Step 7: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 20 files / 110 tests passing, typecheck clean.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-reg-starter/src/modules/signup/v1/routes.ts \
        packages/gen-reg-starter/src/modules/signup/v1/routes.test.ts \
        packages/gen-reg-starter/src/config/env.ts \
        packages/gen-reg-starter/src/modules/captcha
git commit -m "feat: signupRoutes accepts optional captcha middleware, add captcha dev-page route and TURNSTILE_VERIFY_URL/CDN_URL env"
```

---

### Task 4: Wire into `createGenReg()`

**Files:**
- Modify: `packages/gen-reg-starter/src/create-gen-reg.ts`
- Modify: `packages/gen-reg-starter/src/index.ts`
- Modify: `packages/gen-reg-starter/src/create-gen-reg.test.ts`

**Interfaces:**
- Consumes: everything produced by Tasks 1-3.
- Produces: `GenRegConfig.captchaVerifier`, `GenRegConfig.modules.captcha` — the public factory surface for this feature.

- [ ] **Step 1: Add imports, config fields, and the resolver**

In `packages/gen-reg-starter/src/create-gen-reg.ts`, add these imports alongside the existing ones (after the `ValkeyDedupStore` import):

```typescript
import { TurnstileCaptchaVerifier } from "./infra/captcha/turnstile-verifier.ts";
import { createCaptchaMiddleware } from "./middleware/captcha-verify.ts";
import { captchaDevRoutes } from "./modules/captcha/v1/routes.ts";
import type { ICaptchaVerifier } from "./domain/ports/captcha-verifier.port.ts";
```

Add `captcha?: boolean;` to `GenRegModulesConfig` (after `payment?: boolean;`, before `worker?: boolean;`):

```typescript
export interface GenRegModulesConfig {
  signup?: boolean;
  verifyEmail?: boolean;
  payment?: boolean;
  captcha?: boolean;
  worker?: boolean;
}
```

Add `captchaVerifier?: ICaptchaVerifier;` to `GenRegConfig` (after `webhookDedupStore?: IWebhookDedupStore;`):

```typescript
export interface GenRegConfig {
  repo?: ISignupSessionRepo;
  emailSender?: EmailSender;
  tntClient?: ITntClient;
  authClient?: IAuthClient;
  paymentProviders?: Partial<Record<PaymentProviderKind, IPaymentProvider>>;
  defaultPaymentProvider?: PaymentProviderKind;
  webhookDedupStore?: IWebhookDedupStore;
  captchaVerifier?: ICaptchaVerifier;
  modules?: GenRegModulesConfig;
}
```

Add a `resolveCaptchaVerifier` function (after `resolveWebhookDedupStore`, before `buildWorker`):

```typescript
function resolveCaptchaVerifier(override: ICaptchaVerifier | undefined): ICaptchaVerifier {
  if (override) return override;
  const secretKey = requireEnv("TURNSTILE_SECRET_KEY");
  return new TurnstileCaptchaVerifier(secretKey, env.TURNSTILE_VERIFY_URL);
}
```

- [ ] **Step 2: Wire the toggle, resolver, and routes into `createGenReg()`**

In the `modules` object inside `createGenReg()`, add `captcha` (after `payment`, before `worker`):

```typescript
  const modules: Required<GenRegModulesConfig> = {
    signup: config.modules?.signup ?? true,
    verifyEmail: config.modules?.verifyEmail ?? true,
    payment: config.modules?.payment ?? true,
    captcha: config.modules?.captcha ?? false,
    worker: config.modules?.worker ?? true,
  };
```

Change the `if (modules.signup) { ... }` block to build and pass the captcha middleware:

```typescript
  if (modules.signup) {
    const authClient = resolveAuthClient(config.authClient);
    const signupService = new SignupService(repo, tntClient!, authClient, emailSender);
    const captchaMiddleware = modules.captcha
      ? createCaptchaMiddleware(resolveCaptchaVerifier(config.captchaVerifier))
      : undefined;
    app.use("/api/v1", signupRoutes(new SignupController(signupService), captchaMiddleware));
  }
```

Add the dev-only token-gen page mount right after the `modules.signup` block (before the `paymentProviders` block):

```typescript
  if (modules.captcha && env.NODE_ENV !== "production") {
    const siteKey = requireEnv("TURNSTILE_SITE_KEY");
    app.use("/api/v1", captchaDevRoutes(siteKey, env.TURNSTILE_CDN_URL));
  }
```

- [ ] **Step 3: Run the full suite and typecheck (expect a specific, known failure)**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: typecheck clean (the new fields/imports compile fine), but `create-gen-reg.test.ts`'s existing `"override path — proves pluggability actually wires through"` describe's first test (`"uses the supplied repo/emailSender/tntClient/authClient..."`) still passes — `modules.captcha` defaults to `false`, so nothing about that existing test needed to change. No test should newly fail from Steps 1-2 alone; if any does, stop and re-read Step 2's placement before proceeding.

- [ ] **Step 4: Update `index.ts` exports**

In `packages/gen-reg-starter/src/index.ts`, add after the `IWebhookDedupStore` type export:

```typescript
export type { CaptchaVerifyResult, ICaptchaVerifier } from "./domain/ports/captcha-verifier.port.ts";
```

Add after the `ValkeyDedupStore` export:

```typescript
export { TurnstileCaptchaVerifier } from "./infra/captcha/turnstile-verifier.ts";
```

Add after the `errorHandler` export, inside the errors export list (after `WebhookSignatureInvalidError`):

```typescript
  CaptchaTokenMissingError,
  CaptchaVerificationFailedError,
```

- [ ] **Step 5: Extend `create-gen-reg.test.ts`**

Add a `fakeCaptchaVerifier` helper after the existing `fakeDedupStore` function:

```typescript
function fakeCaptchaVerifier(verified: boolean): import("./domain/ports/captcha-verifier.port.ts").ICaptchaVerifier {
  return { verify: vi.fn(async () => ({ verified })) };
}
```

Add `process.env.TURNSTILE_SECRET_KEY = "test-turnstile-secret";` to the `describe("default config", ...)` block's `beforeEach`, alongside the existing seven lines (this does not change what those two tests exercise — `modules.captcha` still defaults to `false` for them — it just keeps the env consistent with a "full env set" default-config baseline, matching the pattern already established for the other optional modules' env vars).

Add a new nested `describe` block, after `describe("module toggles", ...)`'s closing brace and before `describe("lazy env validation", ...)`:

```typescript
  describe("captcha", () => {
    it("does not require a captchaToken when modules.captcha is false (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo({ existsActiveForSubdomain: vi.fn(async () => false) }),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { payment: false, worker: false },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(201);
    });

    it("rejects a signup with no captchaToken when modules.captcha is true", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(400);
      expect(res.body.error).toBe("CAPTCHA_TOKEN_MISSING");
    });

    it("uses the supplied captchaVerifier and blocks a signup it reports as not verified", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const captchaVerifier = fakeCaptchaVerifier(false);
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier,
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
        captchaToken: "some-token",
      });

      expect(res.status).toBe(400);
      expect(res.body.error).toBe("CAPTCHA_VERIFICATION_FAILED");
      expect(captchaVerifier.verify).toHaveBeenCalledWith("some-token");
    });

    it("allows a signup through when the supplied captchaVerifier reports verified", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ existsActiveForSubdomain: vi.fn(async () => false) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
        captchaToken: "some-token",
      });

      expect(res.status).toBe(201);
    });

    it("does not guard check-subdomain, resume, select-plan, or checkout even when modules.captcha is true", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED })) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        paymentProviders: { stripe: fakePaymentProvider("stripe") },
        webhookDedupStore: fakeDedupStore(),
        modules: { worker: false, captcha: true },
      });

      const checkSubdomain = await request(app).get("/api/v1/signup/check-subdomain?value=someslug");
      const resume = await request(app).get("/api/v1/signup/resume?token=some-resume-token");
      const selectPlan = await request(app)
        .post("/api/v1/signup/select-plan")
        .send({ sessionId: "11111111-1111-1111-1111-111111111111", planCode: "STARTER" });
      const checkout = await request(app)
        .post("/api/v1/signup/checkout")
        .send({
          sessionId: "11111111-1111-1111-1111-111111111111",
          successUrl: "https://example.com/success",
          cancelUrl: "https://example.com/cancel",
        });

      // None of these send a captchaToken. A 400 with CAPTCHA_TOKEN_MISSING would mean
      // the captcha middleware incorrectly guarded this route; anything else (200, 404,
      // 409 SIGNUP_STEP_OUT_OF_ORDER, etc.) proves it wasn't guarded, which is correct —
      // only POST /signup should ever be gated by captcha.
      expect(checkSubdomain.status).not.toBe(400);
      expect(resume.status).not.toBe(400);
      expect(selectPlan.status).not.toBe(400);
      expect(checkout.status).not.toBe(400);
    });
  });
```

Then, inside `describe("lazy env validation", ...)`, add two more tests after the existing last test:

```typescript
    it("does not require TURNSTILE_SECRET_KEY when modules.captcha is false (default)", async () => {
      delete process.env.TURNSTILE_SECRET_KEY;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, payment: false, worker: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when captcha is enabled, no captchaVerifier override, and TURNSTILE_SECRET_KEY is missing", async () => {
      delete process.env.TURNSTILE_SECRET_KEY;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          authClient: fakeAuthClient(),
          modules: { signup: true, verifyEmail: false, payment: false, worker: false, captcha: true },
        }),
      ).toThrow(GenRegConfigError);
    });
```

- [ ] **Step 6: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: all tests passing (baseline from Task 3 plus 7 new `create-gen-reg.test.ts` cases: 5 in the new `describe("captcha", ...)` block, 2 in `lazy env validation`), typecheck clean. Treat the exact passing count as correct as long as it is zero-failures — running totals noted throughout this plan are estimates.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-reg-starter/src/create-gen-reg.ts \
        packages/gen-reg-starter/src/index.ts \
        packages/gen-reg-starter/src/create-gen-reg.test.ts
git commit -m "feat: wire captcha module into createGenReg(), add modules.captcha toggle"
```

---

### Task 5: Final verification, env examples, README

**Files:**
- Modify: `packages/gen-reg-starter/.env.example`
- Modify: `packages/gen-reg-demo/.env.example`
- Modify: `README.md`
- Modify: `docs/integration-guide.md`

- [ ] **Step 1: Update both `.env.example` files**

Add to the end of both `packages/gen-reg-starter/.env.example` and `packages/gen-reg-demo/.env.example`:

```
TURNSTILE_SECRET_KEY=replace_me
TURNSTILE_SITE_KEY=replace_me
```

(`TURNSTILE_VERIFY_URL`/`TURNSTILE_CDN_URL` are not added here — they have real, working Cloudflare defaults and don't need a local override.)

- [ ] **Step 2: Update README.md**

Add a new `## Captcha` section after the existing `## Payment / plan selection` section, before `## What's not here yet`, documenting: the `modules.captcha` toggle (default `false`), the `captchaVerifier` config field and its default (`TurnstileCaptchaVerifier`, built from `TURNSTILE_SECRET_KEY`), that only `POST /signup` is guarded, and the dev-only token-gen page (`GET /api/v1/captcha/token-gen`, non-production only). Remove "Turnstile captcha" from the `## What's not here yet` list. Match the existing README's established section style/terseness.

- [ ] **Step 3: Update the integration guide**

In `docs/integration-guide.md`, add `captchaVerifier?: ICaptchaVerifier;` and `captcha?: boolean;` to the `GenRegConfig` code block in §1.3, with the same inline-comment style as the existing fields. Add `TURNSTILE_SECRET_KEY`/`TURNSTILE_SITE_KEY` to the `.env` example block in §1.3. Add a short new numbered note to §1.3's bullet list explaining the default-`false` toggle and that only `POST /signup` is guarded. Add `POST /api/v1/signup` to the endpoint table in §1.4 if a captcha note is needed there (mention `captchaToken` as a body field alongside the existing signup fields in §3's "Signup" subsection — add one line: `"captchaToken": "..."  // required only if modules.captcha is true`). Add `CAPTCHA_TOKEN_MISSING` (400) and `CAPTCHA_VERIFICATION_FAILED` (400) to the error reference table in §5.

- [ ] **Step 4: Run full verification**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npm test --workspace=@gen-ms/gen-reg-demo
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
npx tsc -p packages/gen-reg-demo/tsconfig.json --noEmit
npm run build --workspace=@gen-ms/gen-reg-starter
npm run build --workspace=@gen-ms/gen-reg-demo
```

Expected: everything green, no regressions to the Phase 1 / starter-demo-split / payment-plan-selection baselines. Build the starter first if `gen-reg-demo`'s test/typecheck needs its `dist/` current (established pattern from the previous sub-project).

- [ ] **Step 5: Commit**

```bash
git add packages/gen-reg-starter/.env.example \
        packages/gen-reg-demo/.env.example \
        README.md \
        docs/integration-guide.md
git commit -m "docs: document captcha config, update .env.example and integration guide"
```

---

## Self-Review Notes (for the plan author / executor)

- `signupRoutes`'s widened signature (`captchaMiddleware?` as a second, optional parameter) is fully backward compatible — no existing caller breaks.
- The dev token-gen page is mounted under `/api/v1` alongside everything else (`GET /api/v1/captcha/token-gen`), not at a bare `/captcha/token-gen` path, for consistency with every other route in this app living under that prefix.
- `TURNSTILE_SITE_KEY` is only ever required when the dev page actually mounts (`modules.captcha && NODE_ENV !== "production"`) — a production deployment with captcha enabled never needs it server-side, since the real site key belongs in the *frontend's* Turnstile widget, not in Gen_REG's own env.
