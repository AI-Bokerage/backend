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
