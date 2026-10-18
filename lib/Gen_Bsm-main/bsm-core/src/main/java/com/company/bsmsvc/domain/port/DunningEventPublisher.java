package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.DunningStatus;
import java.util.UUID;

/**
 * Publishes dunning lifecycle events (started/retried/recovered/suspended/cancelled). Implementations
 * decide the transport (message broker, outbox table, log) — {@code bsm-core} only knows the event
 * occurred. Implementations must be thread-safe/stateless.
 */
public interface DunningEventPublisher {
    void publishStarted(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber);
    void publishRetry(UUID subscriptionId, UUID tenantId, int attemptNumber, DunningStatus newStatus);
    void publishRecovered(UUID subscriptionId, UUID tenantId, UUID invoiceId);
    void publishSuspended(UUID subscriptionId, UUID tenantId);
    void publishCancelled(UUID subscriptionId, UUID tenantId);
}
