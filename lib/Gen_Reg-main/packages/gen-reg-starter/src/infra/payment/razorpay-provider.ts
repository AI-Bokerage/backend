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
