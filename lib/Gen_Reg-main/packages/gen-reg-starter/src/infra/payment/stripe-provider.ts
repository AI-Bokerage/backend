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

    const object = event.data.object as unknown as Record<string, unknown>;
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
