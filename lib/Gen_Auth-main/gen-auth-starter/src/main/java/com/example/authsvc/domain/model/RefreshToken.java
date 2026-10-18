package com.example.authsvc.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    private UUID    id;
    private UUID    userId;
    private UUID    sessionId;
    private UUID    tenantId;
    private String  tokenHash;
    private UUID    familyId;
    private int     generation;
    private String  deviceFingerprint;
    private boolean used;
    /**
     * Token-instance expiry — shrinks with each rotation to match the remaining
     * absolute session window. Used as the Redis TTL and cookie Max-Age.
     */
    private Instant expiresAt;
    /**
     * Absolute session boundary — stamped ONCE at login and carried forward
     * unchanged through every rotation. Session lifetime never extends beyond this.
     * {@code null} for tokens issued before this field was introduced (legacy tokens
     * fall back to {@code expiresAt} in the refresh flow).
     */
    private Instant absoluteExpiresAt;
    private Instant createdAt;
}
