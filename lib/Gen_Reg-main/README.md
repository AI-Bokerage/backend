# Gen_REG

Generalized signup/registration service, genericized from CPMS-Platform's `reg-svc`. Structured as an npm workspace with two packages, mirroring the org's Gen_Auth/Gen_TNT starter/demo convention:

- **`packages/gen-reg-starter`** — the reusable library. Signup validation, email verification, Gen_TNT provisioning handoff, and the background worker, all behind a single configurable `createGenReg(config)` entry point. Every major dependency (repo, email sender, Gen_TNT client, Gen_Auth client) and module (signup routes, verify-email routes, worker) is individually swappable/toggleable — unset fields fall back to the built-in defaults. Published to GitHub Packages as `@gen-ms/gen-reg-starter`.
- **`packages/gen-reg-demo`** — thin reference app. Calls `createGenReg({})` with no overrides and runs it standalone over HTTP — proves the starter works outside its own package and gives non-Node consumers an HTTP surface to call.

See `docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md` for Phase 1's original design and `docs/superpowers/specs/2026-07-22-gen-reg-starter-library-design.md` for the starter/demo split's design. For the full API/config reference and how to wire up Stripe/Razorpay webhooks, see [`docs/integration-guide.md`](docs/integration-guide.md).

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

## Payment / plan selection

The signup funnel now has a paid step between email verification and provisioning:

```
STARTED -> EMAIL_VERIFIED -> PLAN_SELECTED -> PAYMENT_PENDING -> PAYMENT_SUCCEEDED -> PROVISIONING -> ACTIVE
```

- `POST /api/v1/signup/select-plan` (`sessionId`, `planCode`, `billingCycle`) moves `EMAIL_VERIFIED` -> `PLAN_SELECTED`.
- `POST /api/v1/signup/checkout` (`sessionId`, `successUrl`, `cancelUrl`, optional `paymentProvider`) moves `PLAN_SELECTED` -> `PAYMENT_PENDING` and returns a hosted checkout URL from the chosen provider.
- `POST /api/v1/signup/webhooks/:provider` (`stripe` or `razorpay`) verifies the provider's webhook signature and, on a completed-checkout event, moves `PAYMENT_PENDING` -> `PAYMENT_SUCCEEDED`, handing off to the existing provisioning worker.

This is on by default via `modules: { payment: true }`; set it to `false` to disable the routes entirely (e.g. a deployment that provisions without billing).

New `createGenReg()` config fields for this module:

- `paymentProviders` — `Partial<Record<"stripe" | "razorpay", IPaymentProvider>>`, same override-or-default-adapter pattern as `repo`/`emailSender`/`tntClient`/`authClient`. Unset entries fall back to the built-in `StripePaymentProvider`/`RazorpayPaymentProvider` HTTP adapters, built from env.
- `defaultPaymentProvider` — `"stripe" | "razorpay"`, defaults to `"stripe"`. The provider `createGenReg()` picks when a checkout request doesn't specify one; this is the provider whose env vars are required at boot (the other provider's env vars are optional and only enforced if partially set).
- `webhookDedupStore` — `IWebhookDedupStore`, defaults to a Valkey-backed store (`ValkeyDedupStore`) that prevents a re-delivered webhook event from being processed twice.

When payment is enabled and no overrides are supplied, these env vars are required: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (or the Razorpay equivalents, `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET`/`RAZORPAY_WEBHOOK_SECRET`, if `defaultPaymentProvider` is `"razorpay"`), and `VALKEY_URL`.

Plans are a hardcoded static table in `common/plans.ts` — no external pricing service.

## Captcha

Bot protection on `POST /api/v1/signup` via Turnstile:

- `modules: { captcha: true }` enables the captcha middleware (default `false`). When enabled, `POST /api/v1/signup` requires a valid captcha token (`captchaToken` field in the request body); requests without one get `400 CAPTCHA_TOKEN_MISSING`. Tokens are verified server-side against `TURNSTILE_SECRET_KEY`.
- `captchaVerifier` — `ICaptchaVerifier`, defaults to `TurnstileCaptchaVerifier`, built from `TURNSTILE_SECRET_KEY`. Override to swap in a fake or alternate provider.
- **Dev-only token-gen page:** `GET /api/v1/captcha/token-gen` (non-production only, mounted when `modules.captcha && NODE_ENV !== "production"`) generates a dev token for testing. Requires `TURNSTILE_SITE_KEY` in the server env.

When captcha is enabled and no overrides are supplied, `TURNSTILE_SECRET_KEY` is required at boot.

## What's not here yet

Abandoned-signup recovery emails, the wizard read-model, idempotency-key middleware, and a RabbitMQ event bus are deferred — see `docs/superpowers/specs/2026-07-21-gen-reg-signup-provisioning-design.md`'s "Explicitly out of scope" section. Each is planned as its own sub-project, landing as a configurable module on this starter/demo split rather than an ad-hoc addition.
