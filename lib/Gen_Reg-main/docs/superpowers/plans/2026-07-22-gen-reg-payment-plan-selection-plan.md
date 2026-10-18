# Gen_REG Payment & Plan Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Insert plan selection and payment confirmation (`PLAN_SELECTED → PAYMENT_PENDING → PAYMENT_SUCCEEDED`) into Gen_REG's signup funnel between email verification and tenant provisioning, with pluggable Stripe/Razorpay payment providers and Valkey-backed webhook dedup.

**Architecture:** Three new modules (`select-plan`, `checkout`, `webhooks`) follow the existing `modules/<name>/v1/{schema,service,controller,routes}.ts` shape. A new `IPaymentProvider` port has two default adapters (Stripe, Razorpay); a new `IWebhookDedupStore` port has one default adapter (Valkey NX lock). `VerifyEmailService` stops auto-provisioning; the webhook handler now triggers provisioning after payment succeeds, reusing the existing `sweepProvisioning` worker unchanged.

**Tech Stack:** Same as Phase 1/sub-project 1 (Node, Express, Prisma, Zod, Vitest, TDD) plus the `stripe` and `razorpay` SDKs and `ioredis` (Valkey speaks the Redis protocol).

## Global Constraints

- All new/modified source lives under `packages/gen-reg-starter/` (this package's root for all paths below is `packages/gen-reg-starter/`).
- Relative imports use the `.ts` extension in source (rewritten to `.js` at compile time by `rewriteRelativeImportExtensions` — this is the existing convention, follow it exactly).
- New error classes extend `AppError(statusCode, code, message)` from `src/common/errors.ts`, same as all existing errors.
- New env vars are validated **lazily** via the existing `requireEnv(key: string): string` in `src/config/env.ts` — never add them to the eagerly-parsed `EnvSchema`. They are only required when their default adapter is actually constructed (no override supplied) and its owning module is enabled.
- New optional dependencies/adapters in `GenRegConfig` follow the existing pattern: `field?: Interface`, resolved via a `resolveX(override)` function that returns the override if present, else lazily validates env and constructs the default.
- New module toggles follow the existing pattern: `modules?: { ...existing, payment?: boolean }`, default `true`.
- Test baseline before this plan starts: `gen-reg-starter` 10 files / 56 tests passing, `gen-reg-demo` 1 file / 2 tests passing, both typecheck clean. Every task must end with the full `gen-reg-starter` suite passing (baseline count + this task's new tests) and a clean typecheck.
- All new ports live in `src/domain/ports/*.port.ts`; all new default adapter implementations live in `src/infra/<area>/*.ts`.
- Migrations are generated via `npx prisma migrate dev --name <name>` (run from `packages/gen-reg-starter/` with `DATABASE_URL` pointed at the local dev Postgres from `docker-compose.yml`) — never hand-author migration SQL or folder names.
- New payment-provider adapters must not make real network calls in unit tests — inject the underlying SDK client so tests supply a fake, mirroring how `HttpTntClient`/`HttpAuthClient` are tested today.

---

### Task 1: State machine, schema migration, error classes, repo type widening

This is the foundational task — no new behavior yet, just the types and DB shape every later task builds on.

**Files:**
- Modify: `packages/gen-reg-starter/src/domain/enums/signup-state.enum.ts`
- Modify: `packages/gen-reg-starter/prisma/schema.prisma`
- Modify: `packages/gen-reg-starter/src/domain/ports/signup-session.repository.port.ts`
- Modify: `packages/gen-reg-starter/src/common/errors.ts`
- Modify: `packages/gen-reg-starter/src/create-gen-reg.test.ts` (test helper widening)
- Modify: `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts` (test helper widening)
- Modify: `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts` (test helper widening)
- Modify: `packages/gen-reg-starter/src/worker.test.ts` (test helper widening)
- Create (generated): `packages/gen-reg-starter/prisma/migrations/<timestamp>_add_payment_plan_selection/migration.sql`

**Interfaces:**
- Produces: `SignupState.PLAN_SELECTED`, `SignupState.PAYMENT_PENDING`, `SignupState.PAYMENT_SUCCEEDED` — consumed by every later task.
- Produces: `SignupSessionRecord` widened with `selectedPlanCode: string | null`, `selectedBillingCycle: string | null`, `paymentProvider: string | null`, `checkoutSessionId: string | null`, `paymentCustomerId: string | null` — consumed by Tasks 6/7/8.
- Produces: `SignupStepOutOfOrderError(expected: string, actual: string)` (409), `PlanNotFoundError(planCode: string)` (404), `PaymentProviderError(message: string)` (502), `WebhookSignatureInvalidError()` (400) — consumed by Tasks 6/7/8.

- [ ] **Step 1: Update the state enum**

Replace `packages/gen-reg-starter/src/domain/enums/signup-state.enum.ts` entirely:

```typescript
// src/domain/enums/signup-state.enum.ts
export enum SignupState {
  STARTED = "STARTED",
  EMAIL_VERIFIED = "EMAIL_VERIFIED",
  PLAN_SELECTED = "PLAN_SELECTED",
  PAYMENT_PENDING = "PAYMENT_PENDING",
  PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED",
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

/** States the abandon-sweep (Task 9, Phase 1) may move to ABANDONED once expiresAt passes. */
export const ABANDONABLE_STATES: ReadonlySet<SignupState> = new Set([
  SignupState.STARTED,
  SignupState.EMAIL_VERIFIED,
]);
```

(Only the enum gained three members — `TERMINAL_STATES`/`ABANDONABLE_STATES` are unchanged. Plan/payment states are deliberately NOT abandonable in this task; abandoned-cart recovery for those states is out of scope, per the design spec.)

- [ ] **Step 2: Update the Prisma schema**

In `packages/gen-reg-starter/prisma/schema.prisma`, replace the `enum SignupState` block and add five new fields to `model SignupSession`:

```prisma
generator client {
  provider = "prisma-client-js"
  output   = "../__generated__/prisma"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum SignupState {
  STARTED
  EMAIL_VERIFIED
  PLAN_SELECTED
  PAYMENT_PENDING
  PAYMENT_SUCCEEDED
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
  selectedPlanCode           String?     @map("selected_plan_code") @db.VarChar(64)
  selectedBillingCycle       String?     @map("selected_billing_cycle") @db.VarChar(16)
  paymentProvider            String?     @map("payment_provider") @db.VarChar(16)
  checkoutSessionId          String?     @map("checkout_session_id") @db.VarChar(255)
  paymentCustomerId          String?     @map("payment_customer_id") @db.VarChar(255)
  provisioningJobId          String?     @map("provisioning_job_id") @db.Uuid
  provisionedTenantId        String?     @map("provisioned_tenant_id") @db.Uuid
  lastProvisioningError      String?     @map("last_provisioning_error") @db.Text
  expiresAt                  DateTime    @map("expires_at") @db.Timestamptz(6)
  createdAt                  DateTime    @default(now()) @map("created_at") @db.Timestamptz(6)
  updatedAt                  DateTime    @updatedAt @map("updated_at") @db.Timestamptz(6)

  @@index([emailVerificationTokenHash])
  @@index([resumeTokenHash])
  @@index([checkoutSessionId])
  @@map("signup_session")
}
```

- [ ] **Step 3: Widen `SignupSessionRecord`**

In `packages/gen-reg-starter/src/domain/ports/signup-session.repository.port.ts`, add five fields to the `SignupSessionRecord` interface (insert after `authUserId`, before `provisioningJobId`):

```typescript
  authUserId: string;
  selectedPlanCode: string | null;
  selectedBillingCycle: string | null;
  paymentProvider: string | null;
  checkoutSessionId: string | null;
  paymentCustomerId: string | null;
  provisioningJobId: string | null;
```

(`CreateSignupSessionInput` and `ISignupSessionRepo`'s method signatures are unchanged — these new fields are only ever written via `updateState`'s patch, never at creation time.)

- [ ] **Step 4: Add the new error classes**

In `packages/gen-reg-starter/src/common/errors.ts`, add after `GenTntProvisioningError` (before `GenRegConfigError`):

```typescript
export class SignupStepOutOfOrderError extends AppError {
  constructor(expected: string, actual: string) {
    super(409, "SIGNUP_STEP_OUT_OF_ORDER", `Expected session state "${expected}" but found "${actual}"`);
  }
}

export class PlanNotFoundError extends AppError {
  constructor(planCode: string) {
    super(404, "PLAN_NOT_FOUND", `Plan "${planCode}" not found`);
  }
}

export class PaymentProviderError extends AppError {
  constructor(message: string) {
    super(502, "PAYMENT_PROVIDER_ERROR", message);
  }
}

export class WebhookSignatureInvalidError extends AppError {
  constructor() {
    super(400, "WEBHOOK_SIGNATURE_INVALID", "Webhook signature verification failed");
  }
}
```

- [ ] **Step 5: Widen the four test helper builders**

In `packages/gen-reg-starter/src/create-gen-reg.test.ts`, inside `baseSession()`'s returned object, insert after the `authUserId: "auth-user-1",` line:

```typescript
    authUserId: "auth-user-1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
```

(replacing the existing `authUserId: "auth-user-1", provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,` line with the above.)

In `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts`, apply the identical replacement to its `baseSession()`.

In `packages/gen-reg-starter/src/worker.test.ts`, inside `session()`'s returned object, insert after `authUserId: "u1",`:

```typescript
    authUserId: "u1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
```

(replacing the existing `authUserId: "u1", provisioningJobId: "job-1", provisionedTenantId: "tenant-1",` line.)

In `packages/gen-reg-starter/src/modules/signup/v1/service.test.ts`, inside `fakeRepo()`'s `create` mock object literal, insert after `resumeTokenHash: null,`:

```typescript
      emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,
      selectedPlanCode: null, selectedBillingCycle: null, paymentProvider: null,
      checkoutSessionId: null, paymentCustomerId: null,
      provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
```

(replacing the existing `emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,` / `provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,` lines.)

- [ ] **Step 6: Generate the migration**

From `packages/gen-reg-starter/`, with a local `.env` present (copy from `.env.example` if missing) and the `docker-compose.yml` Postgres running:

```bash
npx prisma migrate dev --name add_payment_plan_selection
```

Expected: a new `prisma/migrations/<timestamp>_add_payment_plan_selection/migration.sql` is generated and applied to the local dev DB. Prisma Client regenerates automatically as part of this command.

- [ ] **Step 7: Run the full test suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 10 files / 56 tests passing (unchanged — this task only widens types, no new test cases), typecheck clean.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-reg-starter/src/domain/enums/signup-state.enum.ts \
        packages/gen-reg-starter/prisma/schema.prisma \
        packages/gen-reg-starter/prisma/migrations \
        packages/gen-reg-starter/src/domain/ports/signup-session.repository.port.ts \
        packages/gen-reg-starter/src/common/errors.ts \
        packages/gen-reg-starter/src/create-gen-reg.test.ts \
        packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts \
        packages/gen-reg-starter/src/modules/signup/v1/service.test.ts \
        packages/gen-reg-starter/src/worker.test.ts
git commit -m "feat: add PLAN_SELECTED/PAYMENT_PENDING/PAYMENT_SUCCEEDED states, payment columns, and payment error classes"
```

---

### Task 2: Hardcoded plan table

**Files:**
- Create: `packages/gen-reg-starter/src/common/plans.ts`
- Test: `packages/gen-reg-starter/src/common/plans.test.ts`

**Interfaces:**
- Produces: `Plan` interface, `PLANS: Plan[]`, `findPlanByCode(code: string): Plan | undefined` — consumed by Task 6 (select-plan) and Task 7 (checkout).

- [ ] **Step 1: Write the failing test**

```typescript
// src/common/plans.test.ts
import { describe, it, expect } from "vitest";
import { PLANS, findPlanByCode } from "./plans.ts";

describe("plans", () => {
  it("PLANS contains at least a STARTER and a PRO plan", () => {
    expect(PLANS.some((p) => p.code === "STARTER")).toBe(true);
    expect(PLANS.some((p) => p.code === "PRO")).toBe(true);
  });

  it("findPlanByCode returns the matching plan", () => {
    const plan = findPlanByCode("STARTER");
    expect(plan).toBeDefined();
    expect(plan!.code).toBe("STARTER");
  });

  it("findPlanByCode returns undefined for an unknown code", () => {
    expect(findPlanByCode("NOT_A_PLAN")).toBeUndefined();
  });

  it("every plan has non-negative prices in both currencies or null for custom pricing", () => {
    for (const plan of PLANS) {
      if (plan.monthlyPriceCentsUsd !== null) expect(plan.monthlyPriceCentsUsd).toBeGreaterThanOrEqual(0);
      if (plan.annualPriceCentsUsd !== null) expect(plan.annualPriceCentsUsd).toBeGreaterThanOrEqual(0);
      if (plan.monthlyPricePaiseInr !== null) expect(plan.monthlyPricePaiseInr).toBeGreaterThanOrEqual(0);
      if (plan.annualPricePaiseInr !== null) expect(plan.annualPricePaiseInr).toBeGreaterThanOrEqual(0);
    }
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/common/plans.test.ts
```

Expected: FAIL — `./plans.ts` does not exist.

- [ ] **Step 3: Write the plan table**

```typescript
// src/common/plans.ts
export interface Plan {
  code: string;
  name: string;
  /** Price in USD cents (e.g. 2900 = $29.00). null for custom/enterprise pricing. */
  monthlyPriceCentsUsd: number | null;
  annualPriceCentsUsd: number | null;
  /** Price in INR paise (e.g. 249900 = ₹2499.00). null for custom/enterprise pricing. */
  monthlyPricePaiseInr: number | null;
  annualPricePaiseInr: number | null;
  trialDays: number;
}

export const PLANS: Plan[] = [
  {
    code: "STARTER",
    name: "Starter",
    monthlyPriceCentsUsd: 2900,
    annualPriceCentsUsd: 29000,
    monthlyPricePaiseInr: 249900,
    annualPricePaiseInr: 2499000,
    trialDays: 14,
  },
  {
    code: "PRO",
    name: "Pro",
    monthlyPriceCentsUsd: 9900,
    annualPriceCentsUsd: 99000,
    monthlyPricePaiseInr: 799900,
    annualPricePaiseInr: 7999000,
    trialDays: 14,
  },
  {
    code: "ENTERPRISE",
    name: "Enterprise",
    monthlyPriceCentsUsd: null,
    annualPriceCentsUsd: null,
    monthlyPricePaiseInr: null,
    annualPricePaiseInr: null,
    trialDays: 30,
  },
];

export function findPlanByCode(code: string): Plan | undefined {
  return PLANS.find((p) => p.code === code);
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/common/plans.test.ts
```

Expected: 4 tests PASS.

- [ ] **Step 5: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 11 files / 60 tests passing, typecheck clean.

- [ ] **Step 6: Commit**

```bash
git add packages/gen-reg-starter/src/common/plans.ts packages/gen-reg-starter/src/common/plans.test.ts
git commit -m "feat: add hardcoded plan table (common/plans.ts)"
```

---

### Task 3: `IPaymentProvider` port + Stripe adapter

**Files:**
- Create: `packages/gen-reg-starter/src/domain/ports/payment-provider.port.ts`
- Create: `packages/gen-reg-starter/src/infra/payment/stripe-provider.ts`
- Test: `packages/gen-reg-starter/src/infra/payment/stripe-provider.test.ts`
- Modify: `packages/gen-reg-starter/package.json` (add `stripe` dependency)

**Interfaces:**
- Produces: `PaymentProviderKind`, `PaymentCheckoutParams`, `PaymentCheckoutResult`, `PaymentEventType`, `NormalizedPaymentWebhookEvent`, `IPaymentProvider` — consumed by Task 4 (Razorpay adapter), Tasks 6/7/8 (modules), Task 9 (factory).
- Produces: `StripePaymentProvider` (constructor: `secretKey: string, webhookSecret: string`) implementing `IPaymentProvider` — consumed by Task 9.

- [ ] **Step 1: Add the `stripe` dependency**

In `packages/gen-reg-starter/package.json`, add to `dependencies` (alphabetical, after `pino-http`):

```json
    "pino-http": "^10.0.0",
    "stripe": "^17.0.0",
    "zod": "^3.23.0"
```

Run `npm install` from the repo root.

- [ ] **Step 2: Write the port**

```typescript
// src/domain/ports/payment-provider.port.ts
export type PaymentProviderKind = "stripe" | "razorpay";

export interface PaymentCheckoutParams {
  sessionId: string;
  email: string;
  planCode: string;
  billingCycle: "MONTHLY" | "ANNUAL";
  /** Price in USD cents. null for custom/enterprise pricing. */
  amountCentsUsd: number | null;
  /** Price in INR paise. null for custom/enterprise pricing. */
  amountPaiseInr: number | null;
  successUrl: string;
  cancelUrl: string;
  trialDays?: number;
}

export interface PaymentCheckoutResult {
  sessionId: string;
  sessionUrl: string;
  customerId: string;
  /** Razorpay order ID — present only for the Razorpay provider. */
  orderId?: string;
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

- [ ] **Step 3: Write the failing test**

```typescript
// src/infra/payment/stripe-provider.test.ts
import { describe, it, expect, vi } from "vitest";
import Stripe from "stripe";
import { StripePaymentProvider } from "./stripe-provider.ts";
import { PaymentProviderError, WebhookSignatureInvalidError } from "../../common/errors.ts";

function fakeStripeClient(overrides: Partial<Stripe> = {}): Stripe {
  return {
    checkout: {
      sessions: {
        create: vi.fn(async () => ({
          id: "cs_test_123",
          url: "https://checkout.stripe.com/pay/cs_test_123",
          customer: "cus_test_123",
        })),
      },
    },
    webhooks: {
      constructEvent: vi.fn(),
    },
    ...overrides,
  } as unknown as Stripe;
}

describe("StripePaymentProvider", () => {
  it("has provider = 'stripe'", () => {
    const provider = new StripePaymentProvider("sk_test", "whsec_test", fakeStripeClient());
    expect(provider.provider).toBe("stripe");
  });

  it("createCheckoutSession calls Stripe with the right params and normalizes the result", async () => {
    const client = fakeStripeClient();
    const provider = new StripePaymentProvider("sk_test", "whsec_test", client);

    const result = await provider.createCheckoutSession({
      sessionId: "session-1",
      email: "founder@example.com",
      planCode: "STARTER",
      billingCycle: "MONTHLY",
      amountCentsUsd: 2900,
      amountPaiseInr: 249900,
      successUrl: "https://app.example.com/success",
      cancelUrl: "https://app.example.com/cancel",
      trialDays: 14,
    });

    expect(client.checkout.sessions.create).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: "subscription",
        customer_email: "founder@example.com",
        success_url: "https://app.example.com/success",
        cancel_url: "https://app.example.com/cancel",
        metadata: expect.objectContaining({ session_id: "session-1", plan_code: "STARTER" }),
      }),
    );
    expect(result).toEqual({
      sessionId: "cs_test_123",
      sessionUrl: "https://checkout.stripe.com/pay/cs_test_123",
      customerId: "cus_test_123",
    });
  });

  it("wraps a Stripe SDK failure in PaymentProviderError", async () => {
    const client = fakeStripeClient({
      checkout: { sessions: { create: vi.fn(async () => { throw new Error("Stripe down"); }) } },
    } as any);
    const provider = new StripePaymentProvider("sk_test", "whsec_test", client);

    await expect(
      provider.createCheckoutSession({
        sessionId: "s1", email: "a@example.com", planCode: "STARTER", billingCycle: "MONTHLY",
        amountCentsUsd: 2900, amountPaiseInr: null, successUrl: "https://x", cancelUrl: "https://y",
      }),
    ).rejects.toThrow(PaymentProviderError);
  });

  it("getWebhookSignatureHeader returns 'stripe-signature'", () => {
    const provider = new StripePaymentProvider("sk_test", "whsec_test", fakeStripeClient());
    expect(provider.getWebhookSignatureHeader()).toBe("stripe-signature");
  });

  it("verifyAndNormalizeWebhook normalizes a real Stripe test event (checkout.session.completed)", () => {
    const secret = "whsec_test_secret";
    const stripeClient = new Stripe("sk_test_dummy");
    const payload = JSON.stringify({
      id: "evt_1",
      type: "checkout.session.completed",
      data: {
        object: {
          id: "cs_test_123",
          customer: "cus_test_123",
          metadata: { session_id: "session-1", plan_code: "STARTER" },
        },
      },
    });
    const header = stripeClient.webhooks.generateTestHeaderString({ payload, secret });

    const provider = new StripePaymentProvider("sk_test", secret, stripeClient);
    const event = provider.verifyAndNormalizeWebhook(Buffer.from(payload), header);

    expect(event.type).toBe("checkout.completed");
    expect(event.sessionId).toBe("cs_test_123");
    expect(event.customerId).toBe("cus_test_123");
    expect(event.metadata).toEqual({ session_id: "session-1", plan_code: "STARTER" });
  });

  it("verifyAndNormalizeWebhook throws WebhookSignatureInvalidError for a bad signature", () => {
    const provider = new StripePaymentProvider("sk_test", "whsec_correct", new Stripe("sk_test_dummy"));
    expect(() =>
      provider.verifyAndNormalizeWebhook(Buffer.from("{}"), "t=1,v1=deadbeef"),
    ).toThrow(WebhookSignatureInvalidError);
  });
});
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/infra/payment/stripe-provider.test.ts
```

Expected: FAIL — `./stripe-provider.ts` does not exist.

- [ ] **Step 5: Write the adapter**

```typescript
// src/infra/payment/stripe-provider.ts
import Stripe from "stripe";
import type {
  IPaymentProvider,
  PaymentCheckoutParams,
  PaymentCheckoutResult,
  NormalizedPaymentWebhookEvent,
  PaymentEventType,
} from "../../domain/ports/payment-provider.port.ts";
import { PaymentProviderError, WebhookSignatureInvalidError } from "../../common/errors.ts";

function mapEventType(stripeType: string): PaymentEventType {
  switch (stripeType) {
    case "checkout.session.completed":
      return "checkout.completed";
    case "checkout.session.expired":
      return "checkout.expired";
    case "payment_intent.payment_failed":
      return "checkout.failed";
    default:
      return "unknown";
  }
}

export class StripePaymentProvider implements IPaymentProvider {
  readonly provider = "stripe" as const;

  constructor(
    private readonly secretKey: string,
    private readonly webhookSecret: string,
    private readonly client: Stripe = new Stripe(secretKey),
  ) {}

  async createCheckoutSession(params: PaymentCheckoutParams): Promise<PaymentCheckoutResult> {
    try {
      const session = await this.client.checkout.sessions.create({
        mode: "subscription",
        customer_email: params.email,
        line_items: [
          {
            price_data: {
              currency: "usd",
              unit_amount: params.amountCentsUsd ?? 0,
              recurring: { interval: params.billingCycle === "ANNUAL" ? "year" : "month" },
              product_data: { name: params.planCode },
            },
            quantity: 1,
          },
        ],
        subscription_data: params.trialDays ? { trial_period_days: params.trialDays } : undefined,
        success_url: params.successUrl,
        cancel_url: params.cancelUrl,
        metadata: {
          session_id: params.sessionId,
          plan_code: params.planCode,
          billing_cycle: params.billingCycle,
        },
      });

      return {
        sessionId: session.id,
        sessionUrl: session.url ?? "",
        customerId: typeof session.customer === "string" ? session.customer : "",
      };
    } catch (err) {
      throw new PaymentProviderError(err instanceof Error ? err.message : "Stripe checkout session creation failed");
    }
  }

  getWebhookSignatureHeader(): string {
    return "stripe-signature";
  }

  verifyAndNormalizeWebhook(rawBody: Buffer, signature: string): NormalizedPaymentWebhookEvent {
    let event: Stripe.Event;
    try {
      event = this.client.webhooks.constructEvent(rawBody, signature, this.webhookSecret);
    } catch {
      throw new WebhookSignatureInvalidError();
    }

    const object = event.data.object as Record<string, unknown>;
    const metadataRaw = (object.metadata as Record<string, string> | undefined) ?? {};

    return {
      id: event.id,
      type: mapEventType(event.type),
      sessionId: typeof object.id === "string" ? object.id : "",
      customerId: typeof object.customer === "string" ? object.customer : undefined,
      metadata: metadataRaw,
      raw: event,
    };
  }
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/infra/payment/stripe-provider.test.ts
```

Expected: 6 tests PASS.

- [ ] **Step 7: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 13 files / 66 tests passing, typecheck clean. If the installed `stripe` package's types differ from what's shown above (SDK minor-version drift), fix the call sites to match — do not suppress with `any`.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-reg-starter/package.json packages/gen-reg-starter/package-lock.json \
        packages/gen-reg-starter/src/domain/ports/payment-provider.port.ts \
        packages/gen-reg-starter/src/infra/payment/stripe-provider.ts \
        packages/gen-reg-starter/src/infra/payment/stripe-provider.test.ts
git commit -m "feat: add IPaymentProvider port and StripePaymentProvider adapter"
```

---

### Task 4: Razorpay adapter

**Files:**
- Create: `packages/gen-reg-starter/src/infra/payment/razorpay-provider.ts`
- Test: `packages/gen-reg-starter/src/infra/payment/razorpay-provider.test.ts`
- Modify: `packages/gen-reg-starter/package.json` (add `razorpay` dependency)

**Interfaces:**
- Consumes: `IPaymentProvider`, `PaymentCheckoutParams`, `PaymentCheckoutResult`, `NormalizedPaymentWebhookEvent` (Task 3).
- Produces: `RazorpayPaymentProvider` (constructor: `keyId: string, keySecret: string, webhookSecret: string`) implementing `IPaymentProvider` — consumed by Task 9.

Razorpay's checkout model uses Payment Links (not hosted "sessions" like Stripe) — `sessionUrl` maps to the payment link's `short_url`, `sessionId` maps to the payment link's `id`, and `orderId` is populated from the associated order. Webhook signature verification uses Razorpay's documented HMAC-SHA256 scheme over the raw body with the webhook secret.

- [ ] **Step 1: Add the `razorpay` dependency**

In `packages/gen-reg-starter/package.json`, add to `dependencies` (alphabetical, after `pino-http`, before `stripe`):

```json
    "pino-http": "^10.0.0",
    "razorpay": "^2.9.4",
    "stripe": "^17.0.0",
```

Run `npm install` from the repo root.

- [ ] **Step 2: Write the failing test**

```typescript
// src/infra/payment/razorpay-provider.test.ts
import { describe, it, expect, vi } from "vitest";
import crypto from "node:crypto";
import { RazorpayPaymentProvider } from "./razorpay-provider.ts";
import { PaymentProviderError, WebhookSignatureInvalidError } from "../../common/errors.ts";

function fakeRazorpayClient(overrides: Record<string, unknown> = {}) {
  return {
    paymentLink: {
      create: vi.fn(async () => ({
        id: "plink_test_123",
        short_url: "https://rzp.io/i/test123",
      })),
    },
    ...overrides,
  };
}

describe("RazorpayPaymentProvider", () => {
  it("has provider = 'razorpay'", () => {
    const provider = new RazorpayPaymentProvider("key_id", "key_secret", "webhook_secret", fakeRazorpayClient() as any);
    expect(provider.provider).toBe("razorpay");
  });

  it("createCheckoutSession creates a payment link and normalizes the result", async () => {
    const client = fakeRazorpayClient();
    const provider = new RazorpayPaymentProvider("key_id", "key_secret", "webhook_secret", client as any);

    const result = await provider.createCheckoutSession({
      sessionId: "session-1",
      email: "founder@example.com",
      planCode: "STARTER",
      billingCycle: "MONTHLY",
      amountCentsUsd: null,
      amountPaiseInr: 249900,
      successUrl: "https://app.example.com/success",
      cancelUrl: "https://app.example.com/cancel",
    });

    expect(client.paymentLink.create).toHaveBeenCalledWith(
      expect.objectContaining({
        amount: 249900,
        currency: "INR",
        customer: expect.objectContaining({ email: "founder@example.com" }),
        notes: expect.objectContaining({ session_id: "session-1", plan_code: "STARTER" }),
      }),
    );
    expect(result).toEqual({
      sessionId: "plink_test_123",
      sessionUrl: "https://rzp.io/i/test123",
      customerId: "",
      orderId: "plink_test_123",
    });
  });

  it("wraps a Razorpay SDK failure in PaymentProviderError", async () => {
    const client = fakeRazorpayClient({
      paymentLink: { create: vi.fn(async () => { throw new Error("Razorpay down"); }) },
    });
    const provider = new RazorpayPaymentProvider("key_id", "key_secret", "webhook_secret", client as any);

    await expect(
      provider.createCheckoutSession({
        sessionId: "s1", email: "a@example.com", planCode: "STARTER", billingCycle: "MONTHLY",
        amountCentsUsd: null, amountPaiseInr: 249900, successUrl: "https://x", cancelUrl: "https://y",
      }),
    ).rejects.toThrow(PaymentProviderError);
  });

  it("getWebhookSignatureHeader returns 'x-razorpay-signature'", () => {
    const provider = new RazorpayPaymentProvider("key_id", "key_secret", "webhook_secret", fakeRazorpayClient() as any);
    expect(provider.getWebhookSignatureHeader()).toBe("x-razorpay-signature");
  });

  it("verifyAndNormalizeWebhook normalizes a payment.captured event with a valid signature", () => {
    const webhookSecret = "webhook_secret";
    const payload = JSON.stringify({
      event: "payment.captured",
      payload: {
        payment: {
          entity: {
            id: "pay_test_123",
            notes: { session_id: "session-1", plan_code: "STARTER" },
          },
        },
      },
    });
    const signature = crypto.createHmac("sha256", webhookSecret).update(payload).digest("hex");

    const provider = new RazorpayPaymentProvider("key_id", "key_secret", webhookSecret, fakeRazorpayClient() as any);
    const event = provider.verifyAndNormalizeWebhook(Buffer.from(payload), signature);

    expect(event.type).toBe("checkout.completed");
    expect(event.sessionId).toBe("pay_test_123");
    expect(event.metadata).toEqual({ session_id: "session-1", plan_code: "STARTER" });
  });

  it("verifyAndNormalizeWebhook throws WebhookSignatureInvalidError for a bad signature", () => {
    const provider = new RazorpayPaymentProvider("key_id", "key_secret", "webhook_secret", fakeRazorpayClient() as any);
    expect(() =>
      provider.verifyAndNormalizeWebhook(Buffer.from("{}"), "deadbeef"),
    ).toThrow(WebhookSignatureInvalidError);
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/infra/payment/razorpay-provider.test.ts
```

Expected: FAIL — `./razorpay-provider.ts` does not exist.

- [ ] **Step 4: Write the adapter**

```typescript
// src/infra/payment/razorpay-provider.ts
import crypto from "node:crypto";
import Razorpay from "razorpay";
import type {
  IPaymentProvider,
  PaymentCheckoutParams,
  PaymentCheckoutResult,
  NormalizedPaymentWebhookEvent,
  PaymentEventType,
} from "../../domain/ports/payment-provider.port.ts";
import { PaymentProviderError, WebhookSignatureInvalidError } from "../../common/errors.ts";

function mapEventType(razorpayEvent: string): PaymentEventType {
  switch (razorpayEvent) {
    case "payment.captured":
      return "checkout.completed";
    case "payment_link.expired":
      return "checkout.expired";
    case "payment.failed":
      return "checkout.failed";
    default:
      return "unknown";
  }
}

export class RazorpayPaymentProvider implements IPaymentProvider {
  readonly provider = "razorpay" as const;

  constructor(
    keyId: string,
    keySecret: string,
    private readonly webhookSecret: string,
    private readonly client: Razorpay = new Razorpay({ key_id: keyId, key_secret: keySecret }),
  ) {}

  async createCheckoutSession(params: PaymentCheckoutParams): Promise<PaymentCheckoutResult> {
    try {
      const amount = params.amountPaiseInr ?? 0;
      const link = await this.client.paymentLink.create({
        amount,
        currency: "INR",
        customer: { email: params.email },
        callback_url: params.successUrl,
        callback_method: "get",
        notes: {
          session_id: params.sessionId,
          plan_code: params.planCode,
          billing_cycle: params.billingCycle,
        },
      });

      return {
        sessionId: link.id,
        sessionUrl: link.short_url,
        customerId: "",
        orderId: link.id,
      };
    } catch (err) {
      throw new PaymentProviderError(err instanceof Error ? err.message : "Razorpay payment link creation failed");
    }
  }

  getWebhookSignatureHeader(): string {
    return "x-razorpay-signature";
  }

  verifyAndNormalizeWebhook(rawBody: Buffer, signature: string): NormalizedPaymentWebhookEvent {
    const expected = crypto.createHmac("sha256", this.webhookSecret).update(rawBody).digest("hex");
    const expectedBuf = Buffer.from(expected, "hex");
    const signatureBuf = Buffer.from(signature, "hex");
    if (
      expectedBuf.length !== signatureBuf.length ||
      !crypto.timingSafeEqual(expectedBuf, signatureBuf)
    ) {
      throw new WebhookSignatureInvalidError();
    }

    const body = JSON.parse(rawBody.toString("utf8")) as {
      event: string;
      payload: { payment?: { entity?: { id: string; notes?: Record<string, string> } } };
    };
    const entity = body.payload.payment?.entity;

    return {
      id: entity?.id ?? "",
      type: mapEventType(body.event),
      sessionId: entity?.id ?? "",
      metadata: entity?.notes ?? {},
      raw: body,
    };
  }
}
```

(`crypto.timingSafeEqual` requires equal-length buffers — the length check above avoids it throwing on a malformed/short signature header, treating that case as an invalid signature rather than an uncaught exception.)

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/infra/payment/razorpay-provider.test.ts
```

Expected: 6 tests PASS.

- [ ] **Step 6: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 14 files / 72 tests passing, typecheck clean. If the installed `razorpay` package's types differ from what's shown above, fix the call sites to match — do not suppress with `any`.

- [ ] **Step 7: Commit**

```bash
git add packages/gen-reg-starter/package.json packages/gen-reg-starter/package-lock.json \
        packages/gen-reg-starter/src/infra/payment/razorpay-provider.ts \
        packages/gen-reg-starter/src/infra/payment/razorpay-provider.test.ts
git commit -m "feat: add RazorpayPaymentProvider adapter"
```

---

### Task 5: `IWebhookDedupStore` port + Valkey adapter + docker-compose

**Files:**
- Create: `packages/gen-reg-starter/src/domain/ports/webhook-dedup.port.ts`
- Create: `packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.ts`
- Test: `packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.test.ts`
- Modify: `packages/gen-reg-starter/package.json` (add `ioredis` dependency, `@testcontainers/redis` devDependency)
- Modify: `docker-compose.yml` (add `valkey` service)

**Interfaces:**
- Produces: `IWebhookDedupStore { tryAcquire(key: string, ttlSeconds: number): Promise<boolean>; release(key: string): Promise<void>; }` — consumed by Task 8 (webhooks module), Task 9 (factory).
- Produces: `ValkeyDedupStore` (constructor: `redisUrl: string`) implementing `IWebhookDedupStore` — consumed by Task 9.

- [ ] **Step 1: Add dependencies**

In `packages/gen-reg-starter/package.json`, add to `dependencies` (alphabetical, after `helmet`, before `pino`):

```json
    "helmet": "^8.2.0",
    "ioredis": "^5.4.1",
    "pino": "^9.0.0",
```

Add to `devDependencies` (alphabetical, after `@testcontainers/postgresql`):

```json
    "@testcontainers/postgresql": "^10.13.0",
    "@testcontainers/redis": "^10.13.0",
```

Run `npm install` from the repo root.

- [ ] **Step 2: Add the `valkey` service to docker-compose**

In `docker-compose.yml` (repo root), add alongside `postgres`:

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

  valkey:
    image: valkey/valkey:8-alpine
    ports:
      - "6382:6379"   # distinct from any other local Redis/Valkey instance

volumes:
  genreg_postgres_data:
```

- [ ] **Step 3: Write the port**

```typescript
// src/domain/ports/webhook-dedup.port.ts
export interface IWebhookDedupStore {
  /** Returns true the first time `key` is seen within `ttlSeconds`; false on any repeat within that window. */
  tryAcquire(key: string, ttlSeconds: number): Promise<boolean>;
  /** Releases `key` so a subsequent tryAcquire for it succeeds again — used to let a failed webhook be retried. */
  release(key: string): Promise<void>;
}
```

- [ ] **Step 4: Write the failing test**

```typescript
// src/infra/cache/valkey-dedup-store.test.ts
import { describe, it, expect, afterAll } from "vitest";
import { RedisContainer, StartedRedisContainer } from "@testcontainers/redis";
import { ValkeyDedupStore } from "./valkey-dedup-store.ts";

describe("ValkeyDedupStore", () => {
  let container: StartedRedisContainer;
  let store: ValkeyDedupStore;

  afterAll(async () => {
    await store?.disconnect();
    await container?.stop();
  });

  it("tryAcquire returns true the first time and false on a repeat within the TTL", async () => {
    container = await new RedisContainer("valkey/valkey:8-alpine").start();
    store = new ValkeyDedupStore(container.getConnectionUrl());

    const first = await store.tryAcquire("evt-1", 60);
    const second = await store.tryAcquire("evt-1", 60);

    expect(first).toBe(true);
    expect(second).toBe(false);
  });

  it("release lets a subsequent tryAcquire for the same key succeed again", async () => {
    await store.tryAcquire("evt-2", 60);
    await store.release("evt-2");
    const reacquired = await store.tryAcquire("evt-2", 60);
    expect(reacquired).toBe(true);
  });

  it("different keys are independent", async () => {
    const a = await store.tryAcquire("evt-3", 60);
    const b = await store.tryAcquire("evt-4", 60);
    expect(a).toBe(true);
    expect(b).toBe(true);
  });
});
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.test.ts
```

Expected: FAIL — `./valkey-dedup-store.ts` does not exist. (Requires Docker running locally for the Testcontainers Redis/Valkey image, same as `repo.test.ts`'s Postgres container.)

- [ ] **Step 6: Write the adapter**

```typescript
// src/infra/cache/valkey-dedup-store.ts
import Redis from "ioredis";
import type { IWebhookDedupStore } from "../../domain/ports/webhook-dedup.port.ts";

export class ValkeyDedupStore implements IWebhookDedupStore {
  private readonly client: Redis;

  constructor(redisUrl: string) {
    // lazyConnect: this adapter gets constructed by createGenReg() whenever
    // modules.payment is enabled, even in tests/processes that never touch a
    // webhook route — an eager connection attempt would open a real socket
    // (and risk an unhandled 'error' event) for callers who never use it.
    this.client = new Redis(redisUrl, { lazyConnect: true });
  }

  async tryAcquire(key: string, ttlSeconds: number): Promise<boolean> {
    const result = await this.client.set(`wh:payment:${key}`, "1", "EX", ttlSeconds, "NX");
    return result === "OK";
  }

  async release(key: string): Promise<void> {
    await this.client.del(`wh:payment:${key}`);
  }

  async disconnect(): Promise<void> {
    await this.client.quit();
  }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.test.ts
```

Expected: 3 tests PASS.

- [ ] **Step 8: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 15 files / 75 tests passing, typecheck clean.

- [ ] **Step 9: Commit**

```bash
git add packages/gen-reg-starter/package.json packages/gen-reg-starter/package-lock.json \
        docker-compose.yml \
        packages/gen-reg-starter/src/domain/ports/webhook-dedup.port.ts \
        packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.ts \
        packages/gen-reg-starter/src/infra/cache/valkey-dedup-store.test.ts
git commit -m "feat: add IWebhookDedupStore port, ValkeyDedupStore adapter, and docker-compose valkey service"
```

---

### Task 6: `select-plan` module

**Files:**
- Create: `packages/gen-reg-starter/src/modules/select-plan/v1/schema.ts`
- Create: `packages/gen-reg-starter/src/modules/select-plan/v1/service.ts`
- Create: `packages/gen-reg-starter/src/modules/select-plan/v1/controller.ts`
- Create: `packages/gen-reg-starter/src/modules/select-plan/v1/routes.ts`
- Test: `packages/gen-reg-starter/src/modules/select-plan/v1/service.test.ts`

**Interfaces:**
- Consumes: `ISignupSessionRepo`, `SignupState`, `SignupStepOutOfOrderError`, `PlanNotFoundError`, `findPlanByCode` (Tasks 1/2).
- Produces: `SelectPlanService`, `SelectPlanController`, `selectPlanRoutes(controller)`, `SelectPlanInput`/`SelectPlanSchema` — consumed by Task 9 (factory wiring).

- [ ] **Step 1: Write the schema**

```typescript
// src/modules/select-plan/v1/schema.ts
import { z } from "zod";

export const SelectPlanSchema = z.object({
  sessionId: z.string().uuid(),
  planCode: z.string().min(1).max(64),
  billingCycle: z.enum(["MONTHLY", "ANNUAL"]).default("MONTHLY"),
});

export type SelectPlanInput = z.infer<typeof SelectPlanSchema>;
```

- [ ] **Step 2: Write the failing test**

```typescript
// src/modules/select-plan/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { SelectPlanService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError } from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.EMAIL_VERIFIED,
    emailVerificationTokenHash: null, emailVerifiedAt: new Date(), resumeTokenHash: null,
    authUserId: "auth-user-1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(async () => baseSession()),
    findByEmailVerificationTokenHash: vi.fn(),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
    ...overrides,
  };
}

describe("SelectPlanService", () => {
  it("throws SignupSessionNotFoundError when the session doesn't exist", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "missing", planCode: "STARTER", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(SignupSessionNotFoundError);
  });

  it("throws SignupStepOutOfOrderError when the session isn't EMAIL_VERIFIED", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.STARTED })) });
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "session-1", planCode: "STARTER", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(SignupStepOutOfOrderError);
  });

  it("throws PlanNotFoundError for an unknown plan code", async () => {
    const repo = fakeRepo();
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "session-1", planCode: "NOT_A_PLAN", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(PlanNotFoundError);
  });

  it("transitions to PLAN_SELECTED and persists the plan code/billing cycle", async () => {
    const repo = fakeRepo();
    const service = new SelectPlanService(repo);

    const result = await service.selectPlan({ sessionId: "session-1", planCode: "STARTER", billingCycle: "ANNUAL" });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED, {
      selectedPlanCode: "STARTER",
      selectedBillingCycle: "ANNUAL",
    });
    expect(result).toEqual({
      sessionId: "session-1",
      planCode: "STARTER",
      billingCycle: "ANNUAL",
      nextStep: "CHECKOUT",
    });
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/modules/select-plan/v1/service.test.ts
```

Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 4: Write the service**

```typescript
// src/modules/select-plan/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { findPlanByCode } from "../../../common/plans.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError } from "../../../common/errors.ts";
import type { SelectPlanInput } from "./schema.ts";

export interface SelectPlanResult {
  sessionId: string;
  planCode: string;
  billingCycle: "MONTHLY" | "ANNUAL";
  nextStep: "CHECKOUT";
}

export class SelectPlanService {
  constructor(private readonly repo: ISignupSessionRepo) {}

  async selectPlan(input: SelectPlanInput): Promise<SelectPlanResult> {
    const session = await this.repo.findById(input.sessionId);
    if (!session) throw new SignupSessionNotFoundError(input.sessionId);

    if (session.state !== SignupState.EMAIL_VERIFIED) {
      throw new SignupStepOutOfOrderError(SignupState.EMAIL_VERIFIED, session.state);
    }

    if (!findPlanByCode(input.planCode)) {
      throw new PlanNotFoundError(input.planCode);
    }

    await this.repo.updateState(session.id, SignupState.PLAN_SELECTED, {
      selectedPlanCode: input.planCode,
      selectedBillingCycle: input.billingCycle,
    });

    return {
      sessionId: session.id,
      planCode: input.planCode,
      billingCycle: input.billingCycle,
      nextStep: "CHECKOUT",
    };
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/modules/select-plan/v1/service.test.ts
```

Expected: 4 tests PASS.

- [ ] **Step 6: Write the controller and routes**

```typescript
// src/modules/select-plan/v1/controller.ts
import type { Request, Response } from "express";
import type { SelectPlanService } from "./service.ts";
import { SelectPlanSchema } from "./schema.ts";

export class SelectPlanController {
  constructor(private readonly service: SelectPlanService) {}

  selectPlan = async (req: Request, res: Response): Promise<void> => {
    const input = SelectPlanSchema.parse(req.body);
    const result = await this.service.selectPlan(input);
    res.status(200).json(result);
  };
}
```

```typescript
// src/modules/select-plan/v1/routes.ts
import { Router } from "express";
import type { SelectPlanController } from "./controller.ts";

export function selectPlanRoutes(controller: SelectPlanController): Router {
  const router = Router();
  router.post("/signup/select-plan", controller.selectPlan);
  return router;
}
```

- [ ] **Step 7: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 16 files / 79 tests passing, typecheck clean.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-reg-starter/src/modules/select-plan
git commit -m "feat: add select-plan module (EMAIL_VERIFIED -> PLAN_SELECTED)"
```

---

### Task 7: `checkout` module

**Files:**
- Create: `packages/gen-reg-starter/src/modules/checkout/v1/schema.ts`
- Create: `packages/gen-reg-starter/src/modules/checkout/v1/service.ts`
- Create: `packages/gen-reg-starter/src/modules/checkout/v1/controller.ts`
- Create: `packages/gen-reg-starter/src/modules/checkout/v1/routes.ts`
- Test: `packages/gen-reg-starter/src/modules/checkout/v1/service.test.ts`

**Interfaces:**
- Consumes: `ISignupSessionRepo`, `SignupState`, `SignupStepOutOfOrderError` (Task 1), `IPaymentProvider`/`PaymentProviderKind` (Task 3), `findPlanByCode` (Task 2).
- Produces: `CheckoutService` (constructor: `repo, paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>>, defaultProvider: PaymentProviderKind`), `CheckoutController`, `checkoutRoutes(controller)`, `CreateCheckoutInput`/`CreateCheckoutSchema` — consumed by Task 9.

- [ ] **Step 1: Write the schema**

```typescript
// src/modules/checkout/v1/schema.ts
import { z } from "zod";

export const CreateCheckoutSchema = z.object({
  sessionId: z.string().uuid(),
  successUrl: z.string().url(),
  cancelUrl: z.string().url(),
  paymentProvider: z.enum(["stripe", "razorpay"]).optional(),
});

export type CreateCheckoutInput = z.infer<typeof CreateCheckoutSchema>;
```

- [ ] **Step 2: Write the failing test**

```typescript
// src/modules/checkout/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { CheckoutService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PaymentProviderError } from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { IPaymentProvider } from "../../../domain/ports/payment-provider.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.PLAN_SELECTED,
    emailVerificationTokenHash: null, emailVerifiedAt: new Date(), resumeTokenHash: null,
    authUserId: "auth-user-1", selectedPlanCode: "STARTER", selectedBillingCycle: "MONTHLY",
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(async () => baseSession()),
    findByEmailVerificationTokenHash: vi.fn(),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
    ...overrides,
  };
}

function fakeProvider(kind: "stripe" | "razorpay", overrides: Partial<IPaymentProvider> = {}): IPaymentProvider {
  return {
    provider: kind,
    createCheckoutSession: vi.fn(async () => ({ sessionId: "cs_1", sessionUrl: "https://pay", customerId: "cus_1" })),
    getWebhookSignatureHeader: vi.fn(() => "sig-header"),
    verifyAndNormalizeWebhook: vi.fn(),
    ...overrides,
  };
}

describe("CheckoutService", () => {
  it("throws SignupSessionNotFoundError when the session doesn't exist", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new CheckoutService(repo, { stripe: fakeProvider("stripe") }, "stripe");
    await expect(
      service.createSession({ sessionId: "missing", successUrl: "https://s", cancelUrl: "https://c" }),
    ).rejects.toThrow(SignupSessionNotFoundError);
  });

  it("throws SignupStepOutOfOrderError when the session isn't PLAN_SELECTED", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED })) });
    const service = new CheckoutService(repo, { stripe: fakeProvider("stripe") }, "stripe");
    await expect(
      service.createSession({ sessionId: "session-1", successUrl: "https://s", cancelUrl: "https://c" }),
    ).rejects.toThrow(SignupStepOutOfOrderError);
  });

  it("uses the default provider when none is requested, and persists checkoutSessionId/paymentProvider", async () => {
    const repo = fakeRepo();
    const stripe = fakeProvider("stripe");
    const service = new CheckoutService(repo, { stripe, razorpay: fakeProvider("razorpay") }, "stripe");

    const result = await service.createSession({ sessionId: "session-1", successUrl: "https://s", cancelUrl: "https://c" });

    expect(stripe.createCheckoutSession).toHaveBeenCalledWith(
      expect.objectContaining({ sessionId: "session-1", planCode: "STARTER", billingCycle: "MONTHLY" }),
    );
    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PAYMENT_PENDING, {
      checkoutSessionId: "cs_1",
      paymentProvider: "stripe",
    });
    expect(result).toEqual({ sessionId: "cs_1", sessionUrl: "https://pay", customerId: "cus_1" });
  });

  it("uses the requested provider override instead of the default", async () => {
    const repo = fakeRepo();
    const razorpay = fakeProvider("razorpay");
    const service = new CheckoutService(repo, { stripe: fakeProvider("stripe"), razorpay }, "stripe");

    await service.createSession({
      sessionId: "session-1", successUrl: "https://s", cancelUrl: "https://c", paymentProvider: "razorpay",
    });

    expect(razorpay.createCheckoutSession).toHaveBeenCalledOnce();
  });

  it("propagates PaymentProviderError from the adapter", async () => {
    const repo = fakeRepo();
    const failing = fakeProvider("stripe", {
      createCheckoutSession: vi.fn(async () => { throw new PaymentProviderError("boom"); }),
    });
    const service = new CheckoutService(repo, { stripe: failing }, "stripe");

    await expect(
      service.createSession({ sessionId: "session-1", successUrl: "https://s", cancelUrl: "https://c" }),
    ).rejects.toThrow(PaymentProviderError);
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/modules/checkout/v1/service.test.ts
```

Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 4: Write the service**

```typescript
// src/modules/checkout/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { IPaymentProvider, PaymentProviderKind, PaymentCheckoutResult } from "../../../domain/ports/payment-provider.port.ts";
import { findPlanByCode } from "../../../common/plans.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError } from "../../../common/errors.ts";
import type { CreateCheckoutInput } from "./schema.ts";

export class CheckoutService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>>,
    private readonly defaultProvider: PaymentProviderKind,
  ) {}

  async createSession(input: CreateCheckoutInput): Promise<PaymentCheckoutResult> {
    const session = await this.repo.findById(input.sessionId);
    if (!session) throw new SignupSessionNotFoundError(input.sessionId);

    if (session.state !== SignupState.PLAN_SELECTED) {
      throw new SignupStepOutOfOrderError(SignupState.PLAN_SELECTED, session.state);
    }

    const planCode = session.selectedPlanCode!;
    const plan = findPlanByCode(planCode);
    if (!plan) throw new PlanNotFoundError(planCode);

    const billingCycle = (session.selectedBillingCycle as "MONTHLY" | "ANNUAL" | null) ?? "MONTHLY";
    const isAnnual = billingCycle === "ANNUAL";
    const amountCentsUsd = isAnnual ? plan.annualPriceCentsUsd : plan.monthlyPriceCentsUsd;
    const amountPaiseInr = isAnnual ? plan.annualPricePaiseInr : plan.monthlyPricePaiseInr;

    const kind = input.paymentProvider ?? this.defaultProvider;
    const provider = this.paymentProviders[kind];
    if (!provider) throw new PlanNotFoundError(`payment provider "${kind}" is not configured`);

    const result = await provider.createCheckoutSession({
      sessionId: session.id,
      email: session.email,
      planCode,
      billingCycle,
      amountCentsUsd,
      amountPaiseInr,
      successUrl: input.successUrl,
      cancelUrl: input.cancelUrl,
      trialDays: plan.trialDays,
    });

    await this.repo.updateState(session.id, SignupState.PAYMENT_PENDING, {
      checkoutSessionId: result.sessionId,
      paymentProvider: kind,
    });

    return result;
  }
}
```

(Reusing `PlanNotFoundError` for the "requested provider not configured" guard keeps the error taxonomy small — it is still a 404-shaped "the thing you asked for doesn't exist" condition, not a new distinct case worth its own class.)

- [ ] **Step 5: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/modules/checkout/v1/service.test.ts
```

Expected: 5 tests PASS.

- [ ] **Step 6: Write the controller and routes**

```typescript
// src/modules/checkout/v1/controller.ts
import type { Request, Response } from "express";
import type { CheckoutService } from "./service.ts";
import { CreateCheckoutSchema } from "./schema.ts";

export class CheckoutController {
  constructor(private readonly service: CheckoutService) {}

  createSession = async (req: Request, res: Response): Promise<void> => {
    const input = CreateCheckoutSchema.parse(req.body);
    const result = await this.service.createSession(input);
    res.status(201).json(result);
  };
}
```

```typescript
// src/modules/checkout/v1/routes.ts
import { Router } from "express";
import type { CheckoutController } from "./controller.ts";

export function checkoutRoutes(controller: CheckoutController): Router {
  const router = Router();
  router.post("/signup/checkout", controller.createSession);
  return router;
}
```

- [ ] **Step 7: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 17 files / 84 tests passing, typecheck clean.

- [ ] **Step 8: Commit**

```bash
git add packages/gen-reg-starter/src/modules/checkout
git commit -m "feat: add checkout module (PLAN_SELECTED -> PAYMENT_PENDING)"
```

---

### Task 8: `webhooks` module + trim `VerifyEmailService`

**Files:**
- Create: `packages/gen-reg-starter/src/modules/webhooks/v1/schema.ts`
- Create: `packages/gen-reg-starter/src/modules/webhooks/v1/service.ts`
- Create: `packages/gen-reg-starter/src/modules/webhooks/v1/controller.ts`
- Create: `packages/gen-reg-starter/src/modules/webhooks/v1/routes.ts`
- Test: `packages/gen-reg-starter/src/modules/webhooks/v1/service.test.ts`
- Modify: `packages/gen-reg-starter/src/modules/verify-email/v1/service.ts` (remove auto-provisioning)
- Modify: `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts` (drop the `ITntClient`/tenant-creation assertions)

**Interfaces:**
- Consumes: `ISignupSessionRepo`, `SignupState`, `SignupStepOutOfOrderError`, `WebhookSignatureInvalidError` (Task 1), `IPaymentProvider`/`PaymentProviderKind`/`NormalizedPaymentWebhookEvent` (Task 3), `IWebhookDedupStore` (Task 5), `ITntClient` (Phase 1).
- Produces: `WebhooksService` (constructor: `repo, tntClient, paymentProviders, dedupStore`), `WebhooksController`, `webhooksRoutes(controller)` — consumed by Task 9.
- Changes: `VerifyEmailService` drops its `ITntClient` constructor param and its provisioning call; `VerifyEmailResult.nextStep` becomes `"PLAN_SELECTION"`.

- [ ] **Step 1: Trim `VerifyEmailService`**

Replace `packages/gen-reg-starter/src/modules/verify-email/v1/service.ts` entirely:

```typescript
// src/modules/verify-email/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { hashToken } from "../../../common/token.ts";
import { EmailVerificationTokenInvalidError, EmailAlreadyVerifiedError } from "../../../common/errors.ts";

export interface VerifyEmailResult {
  sessionId: string;
  email: string;
  nextStep: "PLAN_SELECTION";
}

export class VerifyEmailService {
  constructor(private readonly repo: ISignupSessionRepo) {}

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

    return { sessionId: session.id, email: session.email, nextStep: "PLAN_SELECTION" };
  }
}
```

- [ ] **Step 2: Update `VerifyEmailService`'s test**

Replace `packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts` entirely:

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

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.STARTED,
    emailVerificationTokenHash: "hash-1", emailVerifiedAt: null, resumeTokenHash: "resume-1",
    authUserId: "auth-user-1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
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

describe("VerifyEmailService", () => {
  it("throws EmailVerificationTokenInvalidError when no session matches the token", async () => {
    const repo = fakeRepo({ findByEmailVerificationTokenHash: vi.fn(async () => null) });
    const service = new VerifyEmailService(repo);
    await expect(service.verifyEmail("bad-token")).rejects.toThrow(EmailVerificationTokenInvalidError);
  });

  it("throws EmailAlreadyVerifiedError when the session isn't in STARTED", async () => {
    const repo = fakeRepo({
      findByEmailVerificationTokenHash: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED, emailVerifiedAt: new Date() })),
    });
    const service = new VerifyEmailService(repo);
    await expect(service.verifyEmail("token")).rejects.toThrow(EmailAlreadyVerifiedError);
  });

  it("marks the session EMAIL_VERIFIED and returns nextStep PLAN_SELECTION", async () => {
    const repo = fakeRepo();
    const service = new VerifyEmailService(repo);

    const result = await service.verifyEmail("valid-token");

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.EMAIL_VERIFIED, expect.objectContaining({ emailVerificationTokenHash: null }));
    expect(result).toEqual({ sessionId: "session-1", email: "founder@example.com", nextStep: "PLAN_SELECTION" });
  });
});
```

- [ ] **Step 3: Write the webhooks schema**

```typescript
// src/modules/webhooks/v1/schema.ts
import { z } from "zod";

export const WebhookProviderParamSchema = z.enum(["stripe", "razorpay"]);
```

- [ ] **Step 4: Write the failing test**

```typescript
// src/modules/webhooks/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { WebhooksService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { WebhookSignatureInvalidError } from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import type { IPaymentProvider, NormalizedPaymentWebhookEvent } from "../../../domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "../../../domain/ports/webhook-dedup.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.PAYMENT_PENDING,
    emailVerificationTokenHash: null, emailVerifiedAt: new Date(), resumeTokenHash: null,
    authUserId: "auth-user-1", selectedPlanCode: "STARTER", selectedBillingCycle: "MONTHLY",
    paymentProvider: "stripe", checkoutSessionId: "cs_1", paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(async () => baseSession()),
    findByEmailVerificationTokenHash: vi.fn(),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
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
    getTenant: vi.fn(),
    getJob: vi.fn(),
    ...overrides,
  };
}

function fakeProvider(event: NormalizedPaymentWebhookEvent, overrides: Partial<IPaymentProvider> = {}): IPaymentProvider {
  return {
    provider: "stripe",
    createCheckoutSession: vi.fn(),
    getWebhookSignatureHeader: vi.fn(() => "stripe-signature"),
    verifyAndNormalizeWebhook: vi.fn(() => event),
    ...overrides,
  };
}

function fakeDedupStore(overrides: Partial<IWebhookDedupStore> = {}): IWebhookDedupStore {
  return {
    tryAcquire: vi.fn(async () => true),
    release: vi.fn(async () => undefined),
    ...overrides,
  };
}

describe("WebhooksService", () => {
  it("throws WebhookSignatureInvalidError when the provider rejects the signature", () => {
    const provider = fakeProvider({} as NormalizedPaymentWebhookEvent, {
      verifyAndNormalizeWebhook: vi.fn(() => { throw new WebhookSignatureInvalidError(); }),
    });
    const service = new WebhooksService(fakeRepo(), fakeTntClient(), { stripe: provider }, fakeDedupStore());
    expect(() => service.verifyAndNormalize("stripe", Buffer.from("{}"), "bad-sig")).toThrow(WebhookSignatureInvalidError);
  });

  it("skips processing (no-op) when the dedup store reports a duplicate", async () => {
    const repo = fakeRepo();
    const dedupStore = fakeDedupStore({ tryAcquire: vi.fn(async () => false) });
    const service = new WebhooksService(repo, fakeTntClient(), {}, dedupStore);

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).not.toHaveBeenCalled();
  });

  it("checkout.completed: transitions PAYMENT_PENDING -> PAYMENT_SUCCEEDED -> PROVISIONING and calls Gen_TNT", async () => {
    const repo = fakeRepo();
    const tntClient = fakeTntClient();
    const dedupStore = fakeDedupStore();
    const service = new WebhooksService(repo, tntClient, {}, dedupStore);

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenNthCalledWith(1, "session-1", SignupState.PAYMENT_SUCCEEDED, {});
    expect(tntClient.createTenant).toHaveBeenCalledWith({
      name: "Acme", slug: "acme", primaryOwnerUserId: "auth-user-1", idempotencyKey: "session-1",
    });
    expect(repo.updateState).toHaveBeenNthCalledWith(2, "session-1", SignupState.PROVISIONING, {
      provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
    });
  });

  it("checkout.completed is a no-op when the session is already past PAYMENT_PENDING", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.PROVISIONING })) });
    const tntClient = fakeTntClient();
    const service = new WebhooksService(repo, tntClient, {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(tntClient.createTenant).not.toHaveBeenCalled();
  });

  it("checkout.expired reverts PAYMENT_PENDING -> PLAN_SELECTED", async () => {
    const repo = fakeRepo();
    const service = new WebhooksService(repo, fakeTntClient(), {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-2", type: "checkout.expired", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED);
  });

  it("checkout.failed reverts PAYMENT_PENDING -> PLAN_SELECTED", async () => {
    const repo = fakeRepo();
    const service = new WebhooksService(repo, fakeTntClient(), {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-3", type: "checkout.failed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED);
  });

  it("releases the dedup key and rethrows when processing throws", async () => {
    const repo = fakeRepo({
      updateState: vi.fn(async () => { throw new Error("db down"); }),
    });
    const dedupStore = fakeDedupStore();
    const service = new WebhooksService(repo, fakeTntClient(), {}, dedupStore);

    await expect(
      service.handleEvent({ id: "evt-4", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {} }),
    ).rejects.toThrow("db down");
    expect(dedupStore.release).toHaveBeenCalledWith("evt-4");
  });
});
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
npx vitest run packages/gen-reg-starter/src/modules/webhooks/v1/service.test.ts
```

Expected: FAIL — `./service.ts` does not exist.

- [ ] **Step 6: Write the service**

```typescript
// src/modules/webhooks/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { IPaymentProvider, PaymentProviderKind, NormalizedPaymentWebhookEvent } from "../../../domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "../../../domain/ports/webhook-dedup.port.ts";
import { logger } from "../../../common/logger.ts";

const DEDUP_TTL_SECONDS = 7 * 24 * 3600;

export class WebhooksService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly tntClient: ITntClient,
    private readonly paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>>,
    private readonly dedupStore: IWebhookDedupStore,
  ) {}

  getWebhookSignatureHeader(kind: PaymentProviderKind): string {
    return this.provider(kind).getWebhookSignatureHeader();
  }

  verifyAndNormalize(kind: PaymentProviderKind, rawBody: Buffer, signature: string): NormalizedPaymentWebhookEvent {
    return this.provider(kind).verifyAndNormalizeWebhook(rawBody, signature);
  }

  async handleEvent(event: NormalizedPaymentWebhookEvent): Promise<void> {
    const acquired = await this.dedupStore.tryAcquire(event.id, DEDUP_TTL_SECONDS);
    if (!acquired) {
      logger.info({ eventId: event.id, type: event.type }, "[PaymentWebhook] Duplicate - skipping");
      return;
    }

    try {
      switch (event.type) {
        case "checkout.completed":
          await this.handleCheckoutCompleted(event);
          break;
        case "checkout.expired":
        case "checkout.failed":
          await this.handleCheckoutReverted(event);
          break;
        default:
          logger.debug({ eventId: event.id, type: event.type }, "[PaymentWebhook] Unhandled event type");
      }
    } catch (err) {
      // Release the dedup key so the provider's automatic retry can reprocess this event.
      await this.dedupStore.release(event.id).catch(() => {});
      throw err;
    }
  }

  private provider(kind: PaymentProviderKind): IPaymentProvider {
    const provider = this.paymentProviders[kind];
    if (!provider) throw new Error(`Payment provider "${kind}" is not configured`);
    return provider;
  }

  private async handleCheckoutCompleted(event: NormalizedPaymentWebhookEvent): Promise<void> {
    const sessionId = event.metadata["session_id"];
    if (!sessionId) {
      logger.warn({ eventId: event.id }, "[PaymentWebhook] Missing session_id metadata");
      return;
    }

    const session = await this.repo.findById(sessionId);
    if (!session) {
      logger.warn({ sessionId }, "[PaymentWebhook] Session not found");
      return;
    }

    if (session.state !== SignupState.PAYMENT_PENDING) {
      logger.info({ sessionId }, "[PaymentWebhook] Not in PAYMENT_PENDING - idempotent skip");
      return;
    }

    await this.repo.updateState(session.id, SignupState.PAYMENT_SUCCEEDED, {});

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

    logger.info({ sessionId: session.id, tenantId: tenant.id }, "[PaymentWebhook] Payment succeeded, provisioning started");
  }

  private async handleCheckoutReverted(event: NormalizedPaymentWebhookEvent): Promise<void> {
    const sessionId = event.metadata["session_id"];
    if (!sessionId) return;

    const session = await this.repo.findById(sessionId);
    if (!session || session.state !== SignupState.PAYMENT_PENDING) return;

    await this.repo.updateState(session.id, SignupState.PLAN_SELECTED);
    logger.warn({ sessionId: session.id, eventType: event.type }, "[PaymentWebhook] Checkout reverted to PLAN_SELECTED");
  }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
npx vitest run packages/gen-reg-starter/src/modules/webhooks/v1/service.test.ts
```

Expected: 8 tests PASS.

- [ ] **Step 8: Write the controller and routes**

The webhook route needs the raw request body (for signature verification) instead of the app-wide JSON-parsed body — the controller receives the raw `Buffer` via `express.raw()` middleware applied at the route level in Task 9's factory wiring, not here.

```typescript
// src/modules/webhooks/v1/controller.ts
import type { Request, Response } from "express";
import type { WebhooksService } from "./service.ts";
import { WebhookProviderParamSchema } from "./schema.ts";

export class WebhooksController {
  constructor(private readonly service: WebhooksService) {}

  handleWebhook = async (req: Request, res: Response): Promise<void> => {
    const provider = WebhookProviderParamSchema.parse(req.params.provider);
    const signature = req.header(this.service.getWebhookSignatureHeader(provider)) ?? "";
    const rawBody = req.body as Buffer;

    const event = this.service.verifyAndNormalize(provider, rawBody, signature);
    await this.service.handleEvent(event);

    res.status(200).json({ received: true });
  };
}
```

```typescript
// src/modules/webhooks/v1/routes.ts
import { Router } from "express";
import type { WebhooksController } from "./controller.ts";

export function webhooksRoutes(controller: WebhooksController): Router {
  const router = Router();
  router.post("/signup/webhooks/:provider", controller.handleWebhook);
  return router;
}
```

- [ ] **Step 9: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: 18 files / 91 tests passing (verify-email's test file shrank by 1 assertion set but is still present — net count reflects removed tenant-creation test replaced by the simpler one, plus 8 new webhooks tests), typecheck clean. If the exact count differs slightly from what's listed here because of the verify-email test rewrite, treat the actual passing count (with zero failures) as correct — the important invariant is zero failures, not the precise number.

- [ ] **Step 10: Commit**

```bash
git add packages/gen-reg-starter/src/modules/webhooks \
        packages/gen-reg-starter/src/modules/verify-email/v1/service.ts \
        packages/gen-reg-starter/src/modules/verify-email/v1/service.test.ts
git commit -m "feat: add webhooks module, trim VerifyEmailService (no longer auto-provisions)"
```

---

### Task 9: Wire everything into `createGenReg()`

**Files:**
- Modify: `packages/gen-reg-starter/src/create-gen-reg.ts`
- Modify: `packages/gen-reg-starter/src/index.ts`
- Modify: `packages/gen-reg-starter/src/create-gen-reg.test.ts` (extend with payment coverage)

**Interfaces:**
- Consumes: everything produced by Tasks 1–8.
- Produces: `GenRegConfig.paymentProviders`, `GenRegConfig.defaultPaymentProvider`, `GenRegConfig.webhookDedupStore`, `GenRegConfig.modules.payment` — the public factory surface for this whole sub-project.

- [ ] **Step 1: Update `create-gen-reg.ts`**

Replace `packages/gen-reg-starter/src/create-gen-reg.ts` entirely:

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
import { StripePaymentProvider } from "./infra/payment/stripe-provider.ts";
import { RazorpayPaymentProvider } from "./infra/payment/razorpay-provider.ts";
import { ValkeyDedupStore } from "./infra/cache/valkey-dedup-store.ts";
import { SignupService } from "./modules/signup/v1/service.ts";
import { SignupController } from "./modules/signup/v1/controller.ts";
import { signupRoutes } from "./modules/signup/v1/routes.ts";
import { VerifyEmailService } from "./modules/verify-email/v1/service.ts";
import { VerifyEmailController } from "./modules/verify-email/v1/controller.ts";
import { verifyEmailRoutes } from "./modules/verify-email/v1/routes.ts";
import { SelectPlanService } from "./modules/select-plan/v1/service.ts";
import { SelectPlanController } from "./modules/select-plan/v1/controller.ts";
import { selectPlanRoutes } from "./modules/select-plan/v1/routes.ts";
import { CheckoutService } from "./modules/checkout/v1/service.ts";
import { CheckoutController } from "./modules/checkout/v1/controller.ts";
import { checkoutRoutes } from "./modules/checkout/v1/routes.ts";
import { WebhooksService } from "./modules/webhooks/v1/service.ts";
import { WebhooksController } from "./modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "./modules/webhooks/v1/routes.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { SignupSessionNotFoundError } from "./common/errors.ts";
import { sweepProvisioning, sweepAbandoned } from "./worker.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "./domain/ports/auth-client.port.ts";
import type { EmailSender } from "./common/email-sender.ts";
import type { IPaymentProvider, PaymentProviderKind } from "./domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "./domain/ports/webhook-dedup.port.ts";

const SessionIdParamSchema = z.string().uuid();

export interface GenRegModulesConfig {
  signup?: boolean;
  verifyEmail?: boolean;
  payment?: boolean;
  worker?: boolean;
}

export interface GenRegConfig {
  repo?: ISignupSessionRepo;
  emailSender?: EmailSender;
  tntClient?: ITntClient;
  authClient?: IAuthClient;
  paymentProviders?: Partial<Record<PaymentProviderKind, IPaymentProvider>>;
  defaultPaymentProvider?: PaymentProviderKind;
  webhookDedupStore?: IWebhookDedupStore;
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

// `isDefault` controls fail-fast behavior: the provider that `defaultPaymentProvider`
// points at MUST be configured (missing env throws GenRegConfigError at boot,
// same as every other required-by-default adapter in this file). The OTHER
// provider is genuinely optional — a deployment using only Stripe should never
// be forced to configure Razorpay — so it silently resolves to `undefined`
// when none of its env vars are set, and only throws on a *partial* config
// (some but not all of its vars set), which is a real misconfiguration worth
// surfacing rather than silently ignoring.
function resolveStripeProvider(override: IPaymentProvider | undefined, isDefault: boolean): IPaymentProvider | undefined {
  if (override) return override;
  if (!isDefault && !process.env.STRIPE_SECRET_KEY && !process.env.STRIPE_WEBHOOK_SECRET) return undefined;
  return new StripePaymentProvider(requireEnv("STRIPE_SECRET_KEY"), requireEnv("STRIPE_WEBHOOK_SECRET"));
}

function resolveRazorpayProvider(override: IPaymentProvider | undefined, isDefault: boolean): IPaymentProvider | undefined {
  if (override) return override;
  if (!isDefault && !process.env.RAZORPAY_KEY_ID && !process.env.RAZORPAY_KEY_SECRET && !process.env.RAZORPAY_WEBHOOK_SECRET) {
    return undefined;
  }
  return new RazorpayPaymentProvider(
    requireEnv("RAZORPAY_KEY_ID"),
    requireEnv("RAZORPAY_KEY_SECRET"),
    requireEnv("RAZORPAY_WEBHOOK_SECRET"),
  );
}

function resolvePaymentProviders(
  override: Partial<Record<PaymentProviderKind, IPaymentProvider>> | undefined,
  defaultProvider: PaymentProviderKind,
): Partial<Record<PaymentProviderKind, IPaymentProvider>> {
  return {
    stripe: resolveStripeProvider(override?.stripe, defaultProvider === "stripe"),
    razorpay: resolveRazorpayProvider(override?.razorpay, defaultProvider === "razorpay"),
  };
}

function resolveWebhookDedupStore(override: IWebhookDedupStore | undefined): IWebhookDedupStore {
  if (override) return override;
  const redisUrl = requireEnv("VALKEY_URL");
  return new ValkeyDedupStore(redisUrl);
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
    payment: config.modules?.payment ?? true,
    worker: config.modules?.worker ?? true,
  };

  const repo = resolveRepo(config.repo);
  const emailSender = resolveEmailSender(config.emailSender);

  const needsTntClient = modules.signup || modules.verifyEmail || modules.payment || modules.worker;
  const tntClient = needsTntClient ? resolveTntClient(config.tntClient) : undefined;

  const app = express();
  app.use(helmet());
  app.use(cors());

  if (modules.payment) {
    // Raw body needed for payment-provider webhook signature verification —
    // must be registered before the app-wide express.json() below, and only
    // for this specific path, or signature verification would see re-serialized
    // JSON instead of the exact bytes the provider signed.
    app.use("/api/v1/signup/webhooks", express.raw({ type: "application/json" }));
  }
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  if (modules.signup) {
    const authClient = resolveAuthClient(config.authClient);
    const signupService = new SignupService(repo, tntClient!, authClient, emailSender);
    app.use("/api/v1", signupRoutes(new SignupController(signupService)));
  }

  if (modules.verifyEmail) {
    const verifyEmailService = new VerifyEmailService(repo);
    app.use("/api/v1", verifyEmailRoutes(new VerifyEmailController(verifyEmailService)));
  }

  let paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>> = {};
  if (modules.payment) {
    const defaultProvider = config.defaultPaymentProvider ?? "stripe";
    paymentProviders = resolvePaymentProviders(config.paymentProviders, defaultProvider);
    const dedupStore = resolveWebhookDedupStore(config.webhookDedupStore);

    const selectPlanService = new SelectPlanService(repo);
    app.use("/api/v1", selectPlanRoutes(new SelectPlanController(selectPlanService)));

    const checkoutService = new CheckoutService(repo, paymentProviders, defaultProvider);
    app.use("/api/v1", checkoutRoutes(new CheckoutController(checkoutService)));

    const webhooksService = new WebhooksService(repo, tntClient!, paymentProviders, dedupStore);
    app.use("/api/v1", webhooksRoutes(new WebhooksController(webhooksService)));
  }

  // Registered after verifyEmailRoutes/select-plan/checkout/webhooks so the
  // :sessionId wildcard doesn't shadow any literal route above (all of those
  // are POST-only or a distinct GET path, but this ordering keeps the same
  // discipline that fixed the original signup/verifyEmail shadowing bug).
  if (modules.signup) {
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

  app.use(errorHandler);

  const worker = modules.worker ? buildWorker(repo, tntClient!) : undefined;

  return { app, worker };
}
```

- [ ] **Step 2: Update `index.ts`**

Replace `packages/gen-reg-starter/src/index.ts` entirely:

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
export type {
  IPaymentProvider,
  PaymentProviderKind,
  PaymentCheckoutParams,
  PaymentCheckoutResult,
  PaymentEventType,
  NormalizedPaymentWebhookEvent,
} from "./domain/ports/payment-provider.port.ts";
export type { IWebhookDedupStore } from "./domain/ports/webhook-dedup.port.ts";

export { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
export { ConsoleEmailSender } from "./common/email-sender.ts";
export { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
export { HttpAuthClient } from "./infra/auth-client/auth-client.ts";
export { StripePaymentProvider } from "./infra/payment/stripe-provider.ts";
export { RazorpayPaymentProvider } from "./infra/payment/razorpay-provider.ts";
export { ValkeyDedupStore } from "./infra/cache/valkey-dedup-store.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";
export { PLANS, findPlanByCode } from "./common/plans.ts";
export type { Plan } from "./common/plans.ts";

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
  SignupStepOutOfOrderError,
  PlanNotFoundError,
  PaymentProviderError,
  WebhookSignatureInvalidError,
} from "./common/errors.ts";
```

- [ ] **Step 3: Extend `create-gen-reg.test.ts`**

**Important — read before editing:** `modules.payment` now defaults to `true`, and whenever payment is enabled, `createGenReg()` always calls `resolveWebhookDedupStore` (which `requireEnv("VALKEY_URL")` if no override is supplied) and always calls `resolveStripeProvider`/`resolveRazorpayProvider` for whichever provider is the *default* (which `requireEnv`s its keys if no override is supplied). Every pre-existing test in this file that does not supply `modules: { payment: false }` (or explicit `paymentProviders`/`webhookDedupStore` overrides) will now hit these resolvers — and since none of those pre-existing tests set `STRIPE_SECRET_KEY`/`VALKEY_URL` in their own scope, they will start throwing `GenRegConfigError` once `modules.payment` defaults to `true`. Before adding any new test, first fix every affected pre-existing test as follows — each one is being modified because payment is genuinely irrelevant to what it tests, not because the test itself is wrong:

In `describe("override path — proves pluggability actually wires through", ...)`'s existing test, add `modules: { payment: false }` to the `createGenReg({...})` call:

```typescript
      const { app } = createGenReg({ repo, tntClient, authClient, emailSender, modules: { payment: false } });
```

In `describe("module toggles", ...)`, update all five existing tests' `createGenReg({...})` calls:

- `"does not return a worker when modules.worker is false"`: change `modules: { worker: false }` to `modules: { worker: false, payment: false }`.
- `"returns a worker with start/stop when modules.worker is true (default)"`: this call currently has no `modules` key at all — add `modules: { payment: false }`.
- `"does not mount signup routes when modules.signup is false"`: change `modules: { signup: false, worker: false }` to `modules: { signup: false, worker: false, payment: false }`.
- `"does not mount verify-email routes when modules.verifyEmail is false"`: change `modules: { verifyEmail: false, worker: false }` to `modules: { verifyEmail: false, worker: false, payment: false }`.
- `"reaches the verify-email route (not shadowed by the :sessionId catch-all)..."`: change `modules: { worker: false }` to `modules: { worker: false, payment: false }`.

In `describe("lazy env validation", ...)`, update the first existing test: change `modules: { signup: false, verifyEmail: false, worker: false }` to `modules: { signup: false, verifyEmail: false, worker: false, payment: false }` in `"does not require GEN_TNT_BASE_URL when signup/verifyEmail/worker are all disabled"`. The second existing test (`"throws GenRegConfigError when signup is enabled, no tntClient override, and GEN_TNT_BASE_URL is missing"`) does not strictly need a change — `resolveTntClient` runs before payment resolution and throws first — but add `payment: false` to it too for the same reason: relying on resolver *ordering* to keep a test valid is a hidden coupling that a later refactor could silently break. Change its `modules: { signup: true, verifyEmail: false, worker: false }` to `modules: { signup: true, verifyEmail: false, worker: false, payment: false }`.

Now add new fake builders and test cases. Insert after the existing `fakeEmailSender` function:

```typescript
function fakePaymentProvider(kind: "stripe" | "razorpay" = "stripe"): import("./domain/ports/payment-provider.port.ts").IPaymentProvider {
  return {
    provider: kind,
    createCheckoutSession: vi.fn(async () => ({ sessionId: "cs_1", sessionUrl: "https://pay", customerId: "cus_1" })),
    getWebhookSignatureHeader: vi.fn(() => "stripe-signature"),
    verifyAndNormalizeWebhook: vi.fn(() => ({
      id: "evt_1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    })),
  };
}

function fakeDedupStore(): import("./domain/ports/webhook-dedup.port.ts").IWebhookDedupStore {
  return { tryAcquire: vi.fn(async () => true), release: vi.fn(async () => undefined) };
}
```

Then, inside `describe("override path — proves pluggability actually wires through", ...)`, add a new test:

```typescript
    it("uses the supplied paymentProviders/webhookDedupStore instead of building defaults", async () => {
      const repo = fakeRepo({
        findById: vi.fn(async () => baseSession({ state: SignupState.PAYMENT_PENDING })),
      });
      const stripe = fakePaymentProvider("stripe");
      const dedupStore = fakeDedupStore();

      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        paymentProviders: { stripe },
        webhookDedupStore: dedupStore,
      });

      await request(app)
        .post("/api/v1/signup/webhooks/stripe")
        .set("stripe-signature", "sig")
        .set("Content-Type", "application/json")
        .send(Buffer.from("{}"));

      expect(stripe.verifyAndNormalizeWebhook).toHaveBeenCalledOnce();
      expect(dedupStore.tryAcquire).toHaveBeenCalledWith("evt_1", expect.any(Number));
    });
```

Then, inside `describe("module toggles", ...)`, add:

```typescript
    it("does not mount payment routes when modules.payment is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo();
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { worker: false, payment: false },
      });
      const res = await request(app).post("/api/v1/signup/select-plan").send({ sessionId: "session-1", planCode: "STARTER" });
      expect(res.status).toBe(404);
    });

    it("mounts payment routes when modules.payment is true (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED })) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        paymentProviders: { stripe: fakePaymentProvider("stripe") },
        webhookDedupStore: fakeDedupStore(),
        modules: { worker: false },
      });
      const res = await request(app).post("/api/v1/signup/select-plan").send({ sessionId: "session-1", planCode: "STARTER" });
      expect(res.status).toBe(200);
    });
```

Then, inside `describe("lazy env validation", ...)`, add:

```typescript
    it("does not require STRIPE_SECRET_KEY/VALKEY_URL when modules.payment is false", async () => {
      delete process.env.STRIPE_SECRET_KEY;
      delete process.env.STRIPE_WEBHOOK_SECRET;
      delete process.env.VALKEY_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, payment: false, worker: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when payment is enabled, no webhookDedupStore override, and VALKEY_URL is missing", async () => {
      delete process.env.VALKEY_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          paymentProviders: { stripe: fakePaymentProvider("stripe") },
          modules: { signup: false, verifyEmail: false, payment: true, worker: false },
        }),
      ).toThrow(GenRegConfigError);
    });

    it("throws GenRegConfigError when payment is enabled, no paymentProviders override, and STRIPE_SECRET_KEY is missing (stripe is the default provider)", async () => {
      delete process.env.STRIPE_SECRET_KEY;
      delete process.env.STRIPE_WEBHOOK_SECRET;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          webhookDedupStore: fakeDedupStore(),
          modules: { signup: false, verifyEmail: false, payment: true, worker: false },
        }),
      ).toThrow(GenRegConfigError);
    });
```

Finally, add `process.env.STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`/`VALKEY_URL` to the `describe("default config", ...)` block's `beforeEach`, alongside the existing four:

```typescript
    beforeEach(() => {
      process.env.DATABASE_URL = "postgresql://unused/unused";
      process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
      process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
      process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
      process.env.STRIPE_SECRET_KEY = "sk_test_unused";
      process.env.STRIPE_WEBHOOK_SECRET = "whsec_test_unused";
      process.env.VALKEY_URL = "redis://localhost:6382";
    });
```

- [ ] **Step 4: Run the full suite and typecheck**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
```

Expected: all tests passing (baseline from Task 8 plus 6 new `create-gen-reg.test.ts` cases: 1 override-path, 2 module-toggle, 3 lazy-env), typecheck clean. Treat the exact passing count as correct as long as it is zero-failures — the running total noted throughout this plan is an estimate, not a hard requirement. Fix any test flakiness from the `express.raw()` webhook body-parsing path — the override-path test above sends a raw `Buffer`, not a JSON object, via supertest's `.send()`.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-reg-starter/src/create-gen-reg.ts \
        packages/gen-reg-starter/src/index.ts \
        packages/gen-reg-starter/src/create-gen-reg.test.ts
git commit -m "feat: wire select-plan/checkout/webhooks into createGenReg(), add modules.payment toggle"
```

---

### Task 10: Final verification, env examples, README, smoke script

**Files:**
- Modify: `packages/gen-reg-starter/.env.example`
- Modify: `packages/gen-reg-demo/.env.example`
- Modify: `README.md`
- Modify: `scripts/smoke-signup.sh`

- [ ] **Step 1: Update both `.env.example` files**

Add to the end of both `packages/gen-reg-starter/.env.example` and `packages/gen-reg-demo/.env.example`:

```
STRIPE_SECRET_KEY=sk_test_replace_me
STRIPE_WEBHOOK_SECRET=whsec_replace_me
RAZORPAY_KEY_ID=rzp_test_replace_me
RAZORPAY_KEY_SECRET=replace_me
RAZORPAY_WEBHOOK_SECRET=replace_me
VALKEY_URL=redis://localhost:6382
```

- [ ] **Step 2: Update README.md**

Add a new section after the existing `createGenReg()` config documentation, describing: the new `PLAN_SELECTED`/`PAYMENT_PENDING`/`PAYMENT_SUCCEEDED` states and where they sit in the funnel; the `modules.payment` toggle; the `paymentProviders`/`defaultPaymentProvider`/`webhookDedupStore` config fields; the new required env vars when payment is enabled and no overrides are supplied; and a one-line note that plans are a hardcoded static table in `common/plans.ts` (no external pricing service). Keep it consistent with the existing README's style and level of detail — read the current README first and match its section structure exactly.

- [ ] **Step 3: Extend the smoke script**

Add a new section to `scripts/smoke-signup.sh` (after the existing signup → verify-email flow, before the provisioning poll loop) that: calls `POST /api/v1/signup/select-plan` with `sessionId`/`planCode=STARTER`, then `POST /api/v1/signup/checkout` with `sessionId`/`successUrl`/`cancelUrl`, and prints the returned checkout URL — stopping there with an instruction to complete payment manually (or, if a Stripe/Razorpay test-mode webhook simulator is available, note that as the way to trigger the `checkout.completed` webhook that continues the flow to provisioning). Follow the existing script's style (bash, `set -euo pipefail`, the same `|| true` pattern on any `grep -o` pipeline per the fix already applied in this file).

- [ ] **Step 4: Run full verification**

```bash
npm test --workspace=@gen-ms/gen-reg-starter
npm test --workspace=@gen-ms/gen-reg-demo
npx tsc -p packages/gen-reg-starter/tsconfig.json --noEmit
npx tsc -p packages/gen-reg-demo/tsconfig.json --noEmit
npm run build --workspace=@gen-ms/gen-reg-starter
npm run build --workspace=@gen-ms/gen-reg-demo
```

Expected: everything green, no regressions from Phase 1 or the sub-project-1 baseline. If `gen-reg-demo`'s test needs a rebuild of `gen-reg-starter` first (its compiled `dist/` needs to be current), run the starter build before the demo test, same as established in sub-project 1.

- [ ] **Step 5: Commit**

```bash
git add packages/gen-reg-starter/.env.example \
        packages/gen-reg-demo/.env.example \
        README.md \
        scripts/smoke-signup.sh
git commit -m "docs: document payment/plan-selection config, update .env.example and smoke script"
```

---

## Self-Review Notes (for the plan author / executor)

- Task 8 removes `VerifyEmailService`'s `ITntClient` dependency entirely — this is a real behavior change (email verification no longer provisions a tenant), and it is the whole point of this plan, not an oversight.
- The known gap already documented in Phase 1's plan (Gen_Auth registration succeeding but `repo.create` failing afterward, orphaning an auth user) is unchanged by this plan and remains a pre-existing, out-of-scope issue.
- Razorpay's checkout model (Payment Links) is a reasonable approximation of Stripe's Checkout Sessions for the purposes of this port, but the two are not perfectly symmetric (Razorpay has no native "expired session" webhook in the same shape as Stripe's `checkout.session.expired`) — the `payment_link.expired` mapping is the closest real Razorpay event and should be verified against the actual Razorpay dashboard/docs during implementation if this becomes a real production integration.
