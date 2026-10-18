package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to an impersonation
 * session's consent outcome (e.g. fire its own security notification)
 * without Gen_ADM owning a broker dependency. Not an outbox — no
 * persistence, no retry of the notification itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpImpersonationEventPublisher}).
 */
public interface ImpersonationEventPublisher {

    void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);

    void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);

    void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);
}
