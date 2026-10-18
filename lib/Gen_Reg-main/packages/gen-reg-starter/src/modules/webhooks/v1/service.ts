// src/modules/webhooks/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { IPaymentProvider, PaymentProviderKind, NormalizedPaymentWebhookEvent } from "../../../domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "../../../domain/ports/webhook-dedup.port.ts";
import { logger } from "../../../common/logger.ts";
import { PaymentProviderNotConfiguredError } from "../../../common/errors.ts";

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
    if (!provider) throw new PaymentProviderNotConfiguredError(kind);
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
