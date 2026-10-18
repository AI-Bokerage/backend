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
