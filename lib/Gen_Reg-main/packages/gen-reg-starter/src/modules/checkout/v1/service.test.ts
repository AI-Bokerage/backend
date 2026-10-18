import { describe, it, expect, vi } from "vitest";
import { CheckoutService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PaymentProviderError, PaymentProviderNotConfiguredError } from "../../../common/errors.ts";
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

  it("throws PaymentProviderNotConfiguredError when the requested provider isn't configured", async () => {
    const repo = fakeRepo();
    const service = new CheckoutService(repo, { stripe: fakeProvider("stripe") }, "stripe");

    await expect(
      service.createSession({
        sessionId: "session-1", successUrl: "https://s", cancelUrl: "https://c", paymentProvider: "razorpay",
      }),
    ).rejects.toThrow(PaymentProviderNotConfiguredError);
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
