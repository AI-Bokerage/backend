package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to an offboarding job's
 * outcome (e.g. fire its own event) without Gen_ADM owning a broker
 * dependency. Not an outbox — no persistence, no retry of the notification
 * itself. Defaults to a no-op ({@link com.example.admsvc.infrastructure.event.NoOpOffboardingEventPublisher}).
 */
public interface OffboardingEventPublisher {

    void onCompleted(UUID tenantId, UUID userId);

    void onFailed(UUID tenantId, UUID userId, String reason);
}
