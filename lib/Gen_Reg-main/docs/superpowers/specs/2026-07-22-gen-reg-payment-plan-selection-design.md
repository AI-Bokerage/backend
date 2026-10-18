# Gen_REG Phase 2, Sub-Project 2 — Payment & Plan Selection

## Goal

Insert plan selection and payment confirmation into the signup funnel between email verification and tenant provisioning, matching the original `reg-svc`'s `PLAN_SELECTED → PAYMENT_PENDING → PAYMENT_SUCCEEDED` states (ADR-001), adapted to Gen_REG's existing HTTP-polling provisioning worker (no gRPC callback, no RabbitMQ event bus — those remain separate, not-yet-built future sub-projects).

## Why

Gen_REG's Phase 1 signup flow jumps straight from `EMAIL_VERIFIED` to tenant provisioning with no plan choice and no payment step — acceptable for a bare-bones MVP, but not a real product signup. This sub-project closes that gap while staying inside Gen_REG's already-established architecture: the configurable `createGenReg()` factory (Phase 2 sub-project 1), ports/adapters for swappable dependencies, and lazy env validation.

## Scope

**In scope:**
- New `SignupState` values: `PLAN_SELECTED`, `PAYMENT_PENDING`, `PAYMENT_SUCCEEDED`, inserted between `EMAIL_VERIFIED` and `PROVISIONING`.
- Three new modules: `select-plan`, `checkout`, `webhooks`, following the existing `modules/<name>/v1/{schema,service,controller,routes}.ts` shape.
- `IPaymentProvider` port with two default implementations: Stripe and Razorpay, selectable per-request or via a configured default.
- Valkey-backed webhook dedup (`IWebhookDedupStore` port, NX-lock pattern, matches ADR-003).
- Hardcoded static plan table (no new DB table, no Gen_PPM service).
- Migration adding `selectedPlanCode`, `selectedBillingCycle`, `paymentProvider`, `checkoutSessionId`, `paymentCustomerId` to `SignupSession`.
- `docker-compose.yml` gains a `valkey` service for local dev.
- All three modules gated by one new `modules.payment` toggle in `createGenReg`.

**Explicitly out of scope (deferred to their own future sub-projects):**
- gRPC provisioning callback (ADR-007) — Gen_REG keeps its existing HTTP-polling `sweepProvisioning` worker; the payment webhook triggers it the same way email verification used to.
- RabbitMQ event bus — no event publishing added here.
- Wizard read-model, Turnstile captcha, abandoned-signup recovery emails, generic idempotency-key middleware — unrelated, separately queued.
- Gen_PPM service / real pricing-service integration — plans are hardcoded; revisit only if a real pricing service becomes necessary.
- Checkout-session creation stampede prevention (the original's Valkey `getOrSet` cache around `createCheckoutSession`) — a rare double-click creating two checkout sessions is an acceptable MVP tradeoff.

## Architecture

Today, `VerifyEmailService` transitions `STARTED → EMAIL_VERIFIED` and then immediately creates the tenant and moves to `PROVISIONING`. That auto-provisioning step is extracted into a shared function (used by both nothing directly and the new webhook handler) and removed from `VerifyEmailService`, which now stops at `EMAIL_VERIFIED`.

```
STARTED --email verified--> EMAIL_VERIFIED --select-plan--> PLAN_SELECTED
  --checkout--> PAYMENT_PENDING --checkout.completed webhook--> PAYMENT_SUCCEEDED
  --(triggers extracted provisioning step)--> PROVISIONING --sweep worker--> ACTIVE

PAYMENT_PENDING --checkout.expired/failed webhook--> PLAN_SELECTED (retry path)
```

Three new modules:
- **select-plan** (`POST /api/v1/signup/select-plan`): `EMAIL_VERIFIED → PLAN_SELECTED`. Looks up `planCode` in a static table.
- **checkout** (`POST /api/v1/signup/checkout`): `PLAN_SELECTED → PAYMENT_PENDING`. Resolves an `IPaymentProvider` (request override or configured default) and calls `createCheckoutSession()`.
- **webhooks** (`POST /api/v1/signup/webhooks/:provider`): raw-body route, signature-verified, Valkey-deduped. `checkout.completed` → `PAYMENT_SUCCEEDED` → runs the extracted provisioning step → `PROVISIONING`. `checkout.expired`/`checkout.failed` → reverts to `PLAN_SELECTED`.

All three gate behind `modules.payment` (default `true`) in `createGenReg`, same pattern as `modules.signup`/`modules.verifyEmail`/`modules.worker`.

## Components

**`domain/ports/payment-provider.port.ts`** (new):
```typescript
export type PaymentProviderKind = "stripe" | "razorpay";

export interface PaymentCheckoutParams {
  sessionId: string;
  email: string;
  planCode: string;
  billingCycle: "MONTHLY" | "ANNUAL";
  amountCentsUsd: number | null;
  amountPaiseInr: number | null;
  successUrl: string;
  cancelUrl: string;
  trialDays?: number;
}

export interface PaymentCheckoutResult {
  sessionId: string;
  sessionUrl: string;
  customerId: string;
  orderId?: string; // Razorpay only
}

export type PaymentEventType = "checkout.completed" | "checkout.expired" | "checkout.failed" | "unknown";

export interface NormalizedPaymentWebhookEvent {
  id: string;
  type: PaymentEventType;
  sessionId: string;
  customerId?: string;
  metadata: Record<string, string>;
  raw: unknown;
}

export interface IPaymentProvider {
  readonly provider: PaymentProviderKind;
  createCheckoutSession(params: PaymentCheckoutParams): Promise<PaymentCheckoutResult>;
  getWebhookSignatureHeader(): string;
  verifyAndNormalizeWebhook(rawBody: Buffer, signature: string): NormalizedPaymentWebhookEvent;
}
```

Default implementations: `infra/payment/stripe-provider.ts` (`StripePaymentProvider`), `infra/payment/razorpay-provider.ts` (`RazorpayPaymentProvider`).

**`domain/ports/webhook-dedup.port.ts`** (new):
```typescript
export interface IWebhookDedupStore {
  /** Returns true if this is the first time `key` has been seen within `ttlSeconds`. */
  tryAcquire(key: string, ttlSeconds: number): Promise<boolean>;
  release(key: string): Promise<void>;
}
```
Default implementation: `infra/cache/valkey-dedup-store.ts` (`ValkeyDedupStore`, wraps `SET key 1 NX EX ttl`).

**`common/plans.ts`** (new): static array —
```typescript
export interface Plan {
  code: string;
  name: string;
  monthlyPriceCentsUsd: number | null;
  annualPriceCentsUsd: number | null;
  monthlyPricePaiseInr: number | null;
  annualPricePaiseInr: number | null;
  trialDays: number;
}
export const PLANS: Plan[] = [ /* ... */ ];
export function findPlanByCode(code: string): Plan | undefined;
```

**`GenRegConfig` additions** (in `create-gen-reg.ts`):
```typescript
export interface GenRegConfig {
  // ...existing fields...
  paymentProviders?: Partial<Record<PaymentProviderKind, IPaymentProvider>>;
  defaultPaymentProvider?: PaymentProviderKind; // default "stripe"
  webhookDedupStore?: IWebhookDedupStore;
  modules?: {
    // ...existing fields...
    payment?: boolean; // default true
  };
}
```

**New env vars** (lazy, via the existing `requireEnv()` pattern — only required when their default adapter is actually constructed): `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `VALKEY_URL`.

**New error classes** (`common/errors.ts`): `SignupStepOutOfOrderError` (expected vs. actual state, 409), `PlanNotFoundError` (404), `PaymentProviderError` (wraps provider SDK failures, 502), `WebhookSignatureInvalidError` (400).

**Schema migration**: adds `selectedPlanCode`, `selectedBillingCycle`, `paymentProvider`, `checkoutSessionId`, `paymentCustomerId` (all nullable strings) to `SignupSession`.

**`docker-compose.yml`**: adds a `valkey` service (`valkey/valkey` image) alongside the existing Postgres service.

## Data flow & error handling

**select-plan**: guards `attempt.state === EMAIL_VERIFIED` (else `SignupStepOutOfOrderError` → 409). Looks up `planCode` via `findPlanByCode` (else `PlanNotFoundError` → 404). Transitions to `PLAN_SELECTED`, persists `selectedPlanCode`/`selectedBillingCycle`.

**checkout**: guards `attempt.state === PLAN_SELECTED` (409 otherwise). Resolves `IPaymentProvider` — request-supplied `paymentProvider` field or `config.defaultPaymentProvider`. Provider SDK failure wraps into `PaymentProviderError` (502). Transitions to `PAYMENT_PENDING`, persists `checkoutSessionId`/`paymentProvider`.

**webhooks**: route uses `express.raw({ type: "application/json" })` instead of the app-wide `express.json()`, since signature verification needs the exact raw byte stream. Flow: `provider.verifyAndNormalizeWebhook(rawBody, signature)` (bad signature → `WebhookSignatureInvalidError` → 400) → `dedupStore.tryAcquire(event.id, 7 * 24 * 3600)`; `false` means duplicate delivery, log + respond 200 no-op. Real processing wrapped in try/catch: on exception, `dedupStore.release(event.id)` (so the provider's automatic retry can reprocess) then rethrow (500). On `checkout.completed`: guard `attempt.state === PAYMENT_PENDING` (already-processed webhooks are idempotent no-ops), transition to `PAYMENT_SUCCEEDED`, run the extracted provisioning step (calls `tntClient.createTenant`, persists `provisioningJobId`, transitions to `PROVISIONING` — identical to what `VerifyEmailService` used to do inline), picked up by the existing `sweepProvisioning` worker unchanged. On `checkout.expired`/`checkout.failed`: guard `attempt.state === PAYMENT_PENDING`, revert to `PLAN_SELECTED`.

Every transition guards on current state first (same idempotency discipline as ADR-001) — duplicate or out-of-order requests/webhooks are safe no-ops.

## Testing

Unit tests per service with fake `repo`/`IPaymentProvider`/`IWebhookDedupStore`: happy-path transition, wrong-state 409 guard, plan-not-found 404, provider-failure 502, bad-signature 400. Webhook signature tests use each provider's own SDK signing helper against real test fixtures, not hand-rolled HMAC. Dedup test: same event ID submitted twice — second call is a no-op (assert via spy). Retry test: handler throws mid-processing — dedup key released (assert via fake store) so a simulated retry reprocesses. One Testcontainers-backed integration test against a real Valkey instance proves the actual `SET NX EX` semantics (mirrors `repo.test.ts`'s Postgres container pattern).

Factory-level: extend `create-gen-reg.test.ts` — `modules.payment: false` → all three routes genuinely 404 (learned from the sub-project 1 route-shadowing bug: assert on a distinguishing side effect, not just status code); override-path test proves `paymentProviders.stripe`/`webhookDedupStore` fakes are actually invoked.

## Migration correctness bar

Complete when: (1) the full funnel — signup → verify-email → select-plan → checkout → webhook → provisioning → active — passes end-to-end against fakes in tests and, where practical, against a real Valkey/Postgres in a smoke run; (2) every new port (`IPaymentProvider` for both kinds, `IWebhookDedupStore`) has an override-path test proving the fake is actually invoked instead of the default; (3) `modules.payment: false` genuinely unmounts all three routes with a regression test in the same shape as the Task 5 fix.
