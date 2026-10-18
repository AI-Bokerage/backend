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
