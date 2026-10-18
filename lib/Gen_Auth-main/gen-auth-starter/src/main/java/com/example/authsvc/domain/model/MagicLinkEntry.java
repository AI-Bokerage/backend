package com.example.authsvc.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The value stored in Redis for an active magic-link / password-reset token.
 * Only the SHA-256 hash of the raw token is used as the Redis key — this
 * record is never stored directly; its JSON representation is the value.
 */
public record MagicLinkEntry(
        UUID    userId,
        UUID    tenantId,
        String  purpose,
        Instant expiresAt
) {
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
}
