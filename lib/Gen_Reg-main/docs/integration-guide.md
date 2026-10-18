# Gen_REG Integration Guide

Two ways to use Gen_REG. Pick one:

- **Embed** `@gen-ms/gen-reg-starter` directly in your Node/Express app (same process, no network hop). Recommended if your app is Node.
- **Call it over HTTP** as a standalone service (`gen-reg-demo`, or your own thin wrapper). Recommended for non-Node consumers.

Either way, you also need: a running Gen_TNT instance (tenant provisioning), a running Gen_Auth instance (user registration), and — if you enable the payment module (on by default) — a Stripe and/or Razorpay account and a running Valkey/Redis instance.

---

## 1. Embedding `gen-reg-starter` in your app

### 1.1 Add the dependency

Published to GitHub Packages as `@gen-ms/gen-reg-starter`. Configure your `.npmrc` to point that scope at GitHub Packages, then:

```bash
npm install @gen-ms/gen-reg-starter
```

Until publishing is wired up for real, build and link it locally from this repo instead:

```bash
cd Gen_REG
npm run build --workspace=@gen-ms/gen-reg-starter
npm link --workspace=@gen-ms/gen-reg-starter
# in your app:
npm link @gen-ms/gen-reg-starter
```

### 1.2 Call `createGenReg()` — no autoconfiguration magic

Unlike Gen_TNT/Gen_Auth's Spring Boot autoconfiguration, Gen_REG is Node-idiomatic: a single factory function you call explicitly, not a classpath-scan side effect.

```typescript
import { createGenReg } from "@gen-ms/gen-reg-starter";
import express from "express";

const { app, worker } = createGenReg({
  // Every field below is optional — unset falls back to the built-in default,
  // built from env vars (see §1.3). Override any of them to swap in your own
  // implementation (a different email provider, a fake for tests, etc).
});

const host = express();
host.use(app);       // or mount `app` directly and call app.listen() yourself
worker?.start();      // background sweeps — omit if modules.worker is false
app.listen(3200);
```

### 1.3 Configuration

Everything is either a `GenRegConfig` field (override at construction time) or an env var (used to build the default when no override is supplied). Nothing is required unless the module that needs it is enabled and no override was given — misconfiguration throws `GenRegConfigError` synchronously at `createGenReg()` call time, not on the first request.

```typescript
interface GenRegConfig {
  repo?: ISignupSessionRepo;                                          // default: Prisma + Postgres (DATABASE_URL)
  emailSender?: EmailSender;                                          // default: console-log stub, no env needed
  tntClient?: ITntClient;                                             // default: HTTP client (GEN_TNT_BASE_URL, GEN_TNT_INTERNAL_SECRET)
  authClient?: IAuthClient;                                           // default: HTTP client (GEN_AUTH_BASE_URL)
  paymentProviders?: Partial<Record<"stripe" | "razorpay", IPaymentProvider>>; // default: Stripe/Razorpay HTTP adapters, built from env
  defaultPaymentProvider?: "stripe" | "razorpay";                     // default: "stripe"
  webhookDedupStore?: IWebhookDedupStore;                             // default: Valkey NX-lock store (VALKEY_URL)
  captchaVerifier?: ICaptchaVerifier;                                 // default: TurnstileCaptchaVerifier (TURNSTILE_SECRET_KEY)
  modules?: {
    signup?: boolean;       // default: true
    verifyEmail?: boolean;  // default: true
    payment?: boolean;      // default: true
    captcha?: boolean;      // default: false
    worker?: boolean;       // default: true
  };
}
```

```bash
# .env — required only for whichever defaults you actually use (no override supplied)
DATABASE_URL=postgresql://user:pass@host:5432/genreg
PORT=3200
GEN_TNT_BASE_URL=https://tnt.example.com
GEN_TNT_INTERNAL_SECRET=<shared secret with Gen_TNT>
GEN_AUTH_BASE_URL=https://auth.example.com

# Payment module (skip entirely if modules.payment: false)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
RAZORPAY_KEY_ID=rzp_live_...          # only required if defaultPaymentProvider is "razorpay"
RAZORPAY_KEY_SECRET=...               # (or if you're passing paymentProvider: "razorpay" per-request
RAZORPAY_WEBHOOK_SECRET=...           #  and haven't supplied a paymentProviders.razorpay override)
VALKEY_URL=redis://host:6379

# Captcha module (skip entirely if modules.captcha: false)
TURNSTILE_SECRET_KEY=<Turnstile secret from Cloudflare>
TURNSTILE_SITE_KEY=<Turnstile site key (dev/test envs only; production uses frontend site key)>
```

Notes:

- **The non-default provider is optional, but not partially so.** If `defaultPaymentProvider` is `"stripe"` (the default), Razorpay's three env vars can all be unset — `paymentProviders.razorpay` just resolves to `undefined`, and any request that names `paymentProvider: "razorpay"` gets a `400 PAYMENT_PROVIDER_NOT_CONFIGURED`. But if you set *some* of Razorpay's vars and not others, `createGenReg()` throws at boot — partial config is treated as a real misconfiguration, not an intentional opt-out.
- **Valkey is a hard requirement whenever `modules.payment` is true** (no in-memory fallback) — it's what stops a re-delivered payment webhook from double-provisioning a tenant.
- **Postgres is a hard requirement** whenever `repo` isn't overridden — `ISignupSessionRepo`'s Prisma implementation is the only built-in.
- **Captcha is off by default** (`modules.captcha: false`). When enabled, `TURNSTILE_SECRET_KEY` is required; `TURNSTILE_SITE_KEY` is only required in non-production environments (for the dev token-gen page). In production, the frontend's own Turnstile widget uses its own site key.
- The Prisma schema/migrations ship inside the published package (`prisma/schema.prisma`, `prisma/migrations/`) and regenerate the client automatically via a `postinstall` script — you don't need to run `prisma generate` yourself after `npm install`.

### 1.4 Endpoints you get for free

Once called, `app` (the `Express` instance `createGenReg()` returns) has these routes, gated by the `modules` flags above:

```
GET    /health
POST   /api/v1/signup                            (modules.signup)
GET    /api/v1/signup/check-subdomain             (modules.signup)
GET    /api/v1/signup/resume                      (modules.signup)
GET    /api/v1/signup/:sessionId                  (modules.signup)
GET    /api/v1/signup/verify-email                (modules.verifyEmail)
POST   /api/v1/signup/select-plan                 (modules.payment)
POST   /api/v1/signup/checkout                    (modules.payment)
POST   /api/v1/signup/webhooks/:provider          (modules.payment)
GET    /api/v1/captcha/token-gen                 (modules.captcha, non-production only)
```

Mount `app` under whatever prefix you want in your own Express setup, or run it as-is. See [§3](#3-http-api-reference) for full request/response shapes.

### 1.5 The background worker

`worker` (returned alongside `app`, `undefined` if `modules.worker` is false) has `start()`/`stop()`. It runs two sweeps on intervals (`PROVISIONING_POLL_INTERVAL_MS`, default 5s; `ABANDON_SWEEP_INTERVAL_MS`, default 5min): poll Gen_TNT for provisioning completion, and mark long-idle sessions `ABANDONED`. Call `worker.start()` once your process is ready to serve; call `worker.stop()` on graceful shutdown.

---

## 2. Calling it over HTTP as a standalone service

Use `gen-reg-demo` as-is, or deploy your own thin wrapper the same way (it's `createGenReg({})` + `app.listen()` + `worker?.start()`, nothing more — see `packages/gen-reg-demo/src/index.ts`).

```bash
docker compose up -d                                    # Postgres :5436, Valkey :6382
cp packages/gen-reg-demo/.env.example packages/gen-reg-demo/.env
# fill in GEN_TNT_BASE_URL / GEN_AUTH_BASE_URL / STRIPE_*/RAZORPAY_*/VALKEY_URL for real
npm install
npm run dev                                              # HTTP API on :3200
```

Then call it like any REST API, from any language — no auth header required on these routes (unlike Gen_TNT's `X-Internal-Secret` gate; Gen_REG's endpoints are meant to be reached from a browser/frontend directly).

---

## 3. HTTP API reference

All request bodies are JSON (`Content-Type: application/json`) except the webhook endpoint, which needs the raw body. All error responses share one shape: `{ "error": "<CODE>", "message": "<human-readable>" }` — see [§5](#5-error-reference) for the full code list.

### Signup

```
POST /api/v1/signup
{
  "email": "founder@example.com",
  "password": "hunter2222",           // min 8 chars
  "desiredSubdomain": "acme",
  "companyName": "Acme Corp",         // optional
  "fullName": "...", "phone": "...",  // optional
  "source": "web",                    // optional, defaults "web"
  "referralCode": "...", "utmSource": "...", "utmMedium": "...", "utmCampaign": "..."  // optional
  "captchaToken": "..."               // required only if modules.captcha is true
}
```
→ `201`, `{ sessionId, message }`. Registers the user with Gen_Auth first — a failure there means no `SignupSession` is created at all. Sends a verification-link email (via `emailSender`).

```
GET /api/v1/signup/check-subdomain?value=acme
```
→ `200`, `{ available, normalized, valid }` — checks locally and against Gen_TNT.

```
GET /api/v1/signup/resume?token=<resumeToken>
```
→ `200`, `{ sessionId, email, state, companyName, desiredSubdomain }` — `404 RESUME_TOKEN_NOT_FOUND` if unknown/expired.

```
GET /api/v1/signup/:sessionId
```
→ `200`, `{ sessionId, state, provisionedTenantId, lastProvisioningError }` — poll this to track progress through the funnel below.

```
GET /api/v1/signup/verify-email?token=<emailVerificationToken>
```
→ `200`, `{ sessionId, email, nextStep: "PLAN_SELECTION" }` — moves `STARTED` → `EMAIL_VERIFIED`. Does **not** provision anything by itself; provisioning now only starts after payment succeeds (see below).

### Plan selection & payment (all require `modules.payment: true`, the default)

```
POST /api/v1/signup/select-plan
{ "sessionId": "<uuid>", "planCode": "STARTER", "billingCycle": "MONTHLY" }  // billingCycle optional, defaults MONTHLY
```
→ `200`, `{ sessionId, planCode, billingCycle, nextStep: "CHECKOUT" }`. Requires `EMAIL_VERIFIED` (else `409 SIGNUP_STEP_OUT_OF_ORDER`). `planCode` must be one of the static table in `common/plans.ts`: `STARTER`, `PRO`, `ENTERPRISE` (else `404 PLAN_NOT_FOUND`) — there is no external pricing service, edit that file to change plans. Moves to `PLAN_SELECTED`.

```
POST /api/v1/signup/checkout
{ "sessionId": "<uuid>", "successUrl": "https://app.example.com/success", "cancelUrl": "https://app.example.com/cancel", "paymentProvider": "razorpay" }  // paymentProvider optional, defaults to defaultPaymentProvider
```
→ `201`, `{ sessionId, sessionUrl, customerId, orderId? }` — `sessionUrl` is the hosted checkout page to redirect the user to (`orderId` populated only for Razorpay). Requires `PLAN_SELECTED` (else `409`). `400 PAYMENT_PROVIDER_NOT_CONFIGURED` if the requested provider has no adapter configured. Moves to `PAYMENT_PENDING`.

```
POST /api/v1/signup/webhooks/:provider        # :provider is "stripe" or "razorpay"
```
Called by Stripe/Razorpay, not by your frontend — see [§4](#4-configuring-stripe--razorpay-webhooks) for how to point them at this route. Verifies the signature (`400 WEBHOOK_SIGNATURE_INVALID` if it doesn't match), dedups by event ID via `webhookDedupStore` (repeat deliveries are a silent no-op, `200`), then:
- `checkout.completed` → `PAYMENT_PENDING` → `PAYMENT_SUCCEEDED`, immediately calls Gen_TNT to create the tenant, → `PROVISIONING` (picked up by the background worker from here).
- `checkout.expired` / `checkout.failed` → reverts `PAYMENT_PENDING` → `PLAN_SELECTED`, so the user can retry checkout without re-selecting a plan.
- Anything else (wrong state, unknown session) → idempotent no-op, `200`.

### Full funnel

```
STARTED → EMAIL_VERIFIED → PLAN_SELECTED → PAYMENT_PENDING → PAYMENT_SUCCEEDED → PROVISIONING → ACTIVE
                                                    ↖___________________________________|
                                                       (checkout.expired / checkout.failed)
```
`PROVISION_FAILED` and `ABANDONED` are the two terminal failure states (see `GET /api/v1/signup/:sessionId`'s `lastProvisioningError`).

---

## 4. Configuring Stripe / Razorpay webhooks

Gen_REG doesn't call out to Stripe/Razorpay to register a webhook for you — configure it once on each provider's dashboard, pointing at your deployment's public URL:

**Stripe:** Dashboard → Developers → Webhooks → Add endpoint. URL: `https://your-gen-reg-host/api/v1/signup/webhooks/stripe`. Events to send: `checkout.session.completed`, `checkout.session.expired`, `payment_intent.payment_failed`. Copy the endpoint's signing secret into `STRIPE_WEBHOOK_SECRET`.

**Razorpay:** Dashboard → Settings → Webhooks → Add new webhook. URL: `https://your-gen-reg-host/api/v1/signup/webhooks/razorpay`. Active events: `payment.captured`, `payment_link.expired`, `payment.failed`. Copy the webhook secret into `RAZORPAY_WEBHOOK_SECRET`.

For local development, use each provider's CLI tunnel (`stripe listen --forward-to localhost:3200/api/v1/signup/webhooks/stripe`, or Razorpay's ngrok-based test flow) rather than trying to reach `localhost` from their servers directly.

---

## 5. Error reference

Every error response: `{ "error": "<CODE>", "message": "..." }`.

| Code | Status | Meaning |
|---|---|---|
| `SIGNUP_EMAIL_ALREADY_REGISTERED` | 409 | Email already has an active signup session |
| `SIGNUP_TENANT_SLUG_TAKEN` | 409 | Subdomain taken (locally or in Gen_TNT) |
| `SIGNUP_DOMAIN_BLOCKED` | 400 | Disposable email domain |
| `EMAIL_VERIFICATION_TOKEN_INVALID` | 400 | Bad/unknown verify-email token |
| `EMAIL_VERIFICATION_TOKEN_EXPIRED` | 400 | Verify-email token past its TTL |
| `EMAIL_ALREADY_VERIFIED` | 409 | Session already past `STARTED` |
| `CAPTCHA_TOKEN_MISSING` | 400 | Request body missing `captchaToken` when captcha module is enabled |
| `CAPTCHA_VERIFICATION_FAILED` | 400 | Captcha token verification failed (invalid, expired, or wrong site) |
| `RESUME_TOKEN_NOT_FOUND` | 404 | Bad/unknown/expired resume token |
| `SIGNUP_SESSION_NOT_FOUND` | 404 | No session with that ID |
| `SIGNUP_STEP_OUT_OF_ORDER` | 409 | Session isn't in the state this endpoint requires |
| `PLAN_NOT_FOUND` | 404 | `planCode` isn't in `common/plans.ts` |
| `PAYMENT_PROVIDER_ERROR` | 502 | Stripe/Razorpay SDK call failed |
| `PAYMENT_PROVIDER_NOT_CONFIGURED` | 400 | Requested `paymentProvider` has no adapter configured |
| `WEBHOOK_SIGNATURE_INVALID` | 400 | Webhook signature didn't verify |
| `GEN_AUTH_REGISTRATION_FAILED` | 502 | Gen_Auth registration call failed |
| `GEN_TNT_PROVISIONING_FAILED` | 502 | Gen_TNT tenant-creation call failed |
| `VALIDATION_ERROR` | 400 | Request body/query failed Zod validation |

`GenRegConfigError` is a separate, boot-time-only error (missing env var for an enabled module's default adapter) — it throws synchronously from `createGenReg()`, never appears in an HTTP response.

---

## 6. Local end-to-end smoke test

```bash
./scripts/smoke-signup.sh
```

Signs up, verifies email (you provide the token from the console-stub email log), selects a plan, creates a checkout session, and prints the checkout URL — completing payment (and thus reaching `ACTIVE`) needs either a real card in test mode or the provider's CLI to simulate the webhook. Good template for a CI health check that stops short of a live payment.
