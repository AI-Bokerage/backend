package com.example.authsvc.api.mapper;

import com.example.authsvc.domain.model.RefreshToken;

import java.time.Instant;
import java.util.UUID;

public final class RefreshTokenMapper {

    private RefreshTokenMapper() {}

    /**
     * Builds the next-generation domain {@link RefreshToken} produced during token rotation.
     *
     * <p>The {@code familyId} is carried forward from the previous token so that
     * replay-attack family revocation covers the entire token chain.
     * {@code generation} is the caller-supplied increment (previous + 1).
     *
     * <p>{@code expiresAt} is the remaining absolute session window (not a fresh full TTL),
     * so the token's Redis TTL shrinks with each rotation. {@code absoluteExpiresAt} is the
     * original login-time boundary, unchanged through the entire token family's lifetime.
     */
    public static RefreshToken toRotatedToken(UUID userId, UUID tenantId, UUID sessionId,
                                              String tokenHash, UUID familyId, int generation,
                                              String deviceFingerprint,
                                              Instant expiresAt, Instant absoluteExpiresAt,
                                              Instant createdAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .generation(generation)
                .deviceFingerprint(deviceFingerprint)
                .used(false)
                .expiresAt(expiresAt)
                .absoluteExpiresAt(absoluteExpiresAt)
                .createdAt(createdAt)
                .build();
    }
}
