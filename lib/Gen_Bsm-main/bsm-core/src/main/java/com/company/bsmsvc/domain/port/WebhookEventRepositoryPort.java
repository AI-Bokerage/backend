package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.model.WebhookEvent;
import java.util.Optional;

/**
 * Persistence/query port for inbound payment-provider webhook events, keyed by provider plus
 * external event id for idempotent processing. Implementations must be thread-safe/stateless.
 */
public interface WebhookEventRepositoryPort {
    WebhookEvent save(WebhookEvent event);
    Optional<WebhookEvent> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId);
}
