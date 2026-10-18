package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to a new invitation
 * (e.g. actually send the invite email) without Gen_ADM owning a mail/SMTP
 * dependency. Not an outbox — no persistence, no retry of the notification
 * itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpInvitationEventPublisher}).
 */
public interface InvitationEventPublisher {

    void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken);
}
