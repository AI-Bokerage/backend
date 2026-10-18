# Gen_REG Phase 2, Sub-Project 3 — Turnstile Captcha

## Goal

Add Cloudflare Turnstile bot protection in front of `POST /api/v1/signup`, matching the original `reg-svc`'s captcha implementation, adapted to Gen_REG's ports/adapters + `createGenReg()` config pattern.

## Why

The original `reg-svc` gated signup with Turnstile to block spam/bot registrations. Gen_REG's Phase 1 port dropped this (explicitly deferred). This sub-project restores it as a configurable module, consistent with how payment/plan-selection was added: a swappable port with one concrete adapter, a toggle in `GenRegConfig`, lazy env validation.

## Scope

**In scope:**
- `ICaptchaVerifier` port with one adapter: `TurnstileCaptchaVerifier` (Cloudflare siteverify HTTP call).
- A `createCaptchaMiddleware(verifier)` Express middleware, mounted only in front of `POST /api/v1/signup`.
- A `modules.captcha` toggle in `createGenReg()`, **default `false`**.
- The original's dev-only Turnstile token-gen HTML page (`GET /api/v1/captcha/token-gen`), ported, gated to non-production + captcha enabled.
- `captchaVerifier` override field in `GenRegConfig` for testability/swapping.

**Explicitly out of scope:**
- Any other captcha provider (hCaptcha, reCAPTCHA) — build behind `ICaptchaVerifier` only if actually needed later; not built now (nobody asked for it).
- Guarding any route other than `POST /signup` (no captcha on `check-subdomain`, `resume`, `select-plan`, `checkout` — none were guarded in the original either, and none carry the same spam-account-creation risk).

## Architecture

`modules.captcha` (default `false`) controls whether the captcha middleware gets inserted in front of `POST /api/v1/signup` — the toggle lives at route-mounting time in `create-gen-reg.ts`, not inside the middleware itself (unlike the original, which read an env flag inside the middleware). This requires a small, necessary change to the existing `modules/signup/v1/routes.ts`: `signupRoutes(controller, captchaMiddleware?)`, applying the middleware only to the `POST /signup` line — `check-subdomain` and `resume` are untouched.

A separate, optional dev page (`GET /api/v1/captcha/token-gen`) mounts only when `modules.captcha` is true and `NODE_ENV !== "production"` — a manual Turnstile-widget page for grabbing a real token to paste into a signup request without a real frontend.

## Components

```typescript
// domain/ports/captcha-verifier.port.ts
export interface CaptchaVerifyResult {
  verified: boolean;
  errorCodes?: string[];
}
export interface ICaptchaVerifier {
  verify(token: string): Promise<CaptchaVerifyResult>;
}
```

**`infra/captcha/turnstile-verifier.ts`** — `TurnstileCaptchaVerifier implements ICaptchaVerifier`, constructor `(secretKey: string, verifyUrl: string)`. POSTs `{ response: token, secret: secretKey }` to `verifyUrl` via plain global `fetch` (no injectable client — same style as `HttpAuthClient`/`HttpTntClient`, tested via `vi.stubGlobal("fetch", ...)`). A non-2xx response or a thrown/network error is treated as **fail-closed** (`verified: false`), never an uncaught exception.

**`common/errors.ts` additions:** `CaptchaTokenMissingError` (400, no `captchaToken` in the request body) and `CaptchaVerificationFailedError` (400, Turnstile rejected the token) — both extend `AppError`, same as every other error class.

**`middleware/captcha-verify.ts`** — `createCaptchaMiddleware(verifier: ICaptchaVerifier)` returns an Express middleware:
1. Read `req.body.captchaToken`. Missing or non-string → throw `CaptchaTokenMissingError` (verifier never called).
2. Call `verifier.verify(token)`. `!result.verified` → throw `CaptchaVerificationFailedError`.
3. Otherwise → `next()`.

**`modules/signup/v1/routes.ts` change:** `signupRoutes(controller: SignupController, captchaMiddleware?: RequestHandler)` — when supplied, inserted only on the `POST /signup` route registration, before `controller.startSignup`.

**`modules/captcha/v1/routes.ts`** (new, no service/controller — a static dev page) — `captchaDevRoutes(siteKey: string, cdnUrl: string): Router`, mounting `GET /captcha/token-gen`. Ported from the original: nonce-based CSP header (`script-src`/`frame-src` restricted to the Cloudflare origin derived from `cdnUrl`), renders the Turnstile widget, copies the resulting token to the clipboard for pasting into a manual signup request.

**`GenRegConfig` additions:**
```typescript
captchaVerifier?: ICaptchaVerifier;   // default: TurnstileCaptchaVerifier, built from env
modules?: {
  // ...existing fields...
  captcha?: boolean;                  // default: false
};
```

**New env vars:**
- `TURNSTILE_SECRET_KEY` — lazy (via `requireEnv`), required only when `modules.captcha` is true and no `captchaVerifier` override is supplied.
- `TURNSTILE_SITE_KEY` — lazy, required only when the dev token-gen page actually mounts (non-production + captcha enabled). Public value (safe to expose client-side), not a secret.
- `TURNSTILE_VERIFY_URL` / `TURNSTILE_CDN_URL` — eager (in the always-parsed `EnvSchema`, both have real Cloudflare URLs as defaults), matching `PORT`/`NODE_ENV`'s existing pattern — these are stable public endpoints, not secrets, so there's no reason to force every deployment to set them.

## Data flow & error handling

Captcha middleware runs before the controller — before `StartSignupSchema` parsing — since `captchaToken` is a middleware-level concern, not part of the signup DTO. Missing/non-string token → `CaptchaTokenMissingError` (400), Turnstile never called. Present → `TurnstileCaptchaVerifier.verify()` posts to Cloudflare; a non-2xx response, malformed body, or thrown/network error is fail-closed (`verified: false`) rather than crashing the request. `!verified` → `CaptchaVerificationFailedError` (400). Verified → `next()`, signup proceeds exactly as it does today. Both new errors extend `AppError` and flow through the existing `errorHandler` unchanged.

## Testing

- `TurnstileCaptchaVerifier`: mocked-`fetch` tests — Cloudflare `success: true` → verified; `success: false` → not verified with `errorCodes` populated; fetch throws → not verified (fail-closed, not an uncaught exception).
- `createCaptchaMiddleware`: fake `ICaptchaVerifier` — missing token → `CaptchaTokenMissingError`, verifier never invoked; verifier returns `verified: false` → `CaptchaVerificationFailedError`; `verified: true` → `next()` called.
- Factory-level (`create-gen-reg.test.ts`): `modules.captcha` false (default) → `POST /signup` works with no `captchaToken`, zero regression to existing signup tests. `modules.captcha` true + fake `captchaVerifier` override → missing token → 400; valid token + fake returning `verified: true` → 201 (override-path test, proves the fake was actually invoked). Lazy-env test: captcha enabled, no override, `TURNSTILE_SECRET_KEY` unset → `GenRegConfigError` at boot. One test confirming `check-subdomain`/`resume`/`select-plan`/`checkout` remain unguarded even with `modules.captcha: true`.

## Migration correctness bar

Complete when: (1) `createGenReg({})` with today's `.env` (captcha disabled by default) reproduces exact current signup behavior, zero regression; (2) `modules.captcha: true` + the override-path test proves `captchaVerifier` is actually invoked and actually blocks a request when it returns `verified: false`; (3) `check-subdomain`/`resume`/`select-plan`/`checkout` are proven to stay unguarded regardless of the `modules.captcha` setting.
