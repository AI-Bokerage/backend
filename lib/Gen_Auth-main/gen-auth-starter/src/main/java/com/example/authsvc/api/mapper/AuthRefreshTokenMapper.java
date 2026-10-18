package com.example.authsvc.api.mapper;

import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.infrastructure.persistence.entity.AuthRefreshTokenEntity;

import java.time.Instant;
import java.util.UUID;

public final class AuthRefreshTokenMapper {

    private AuthRefreshTokenMapper() {}

    /**
     * Builds the PG audit-history entity for a newly issued refresh token.
     *
     * <p>{@code familyId} must be the same value stored in Redis so that
     * replay-attack family revocation operates on the correct token set.
     * {@code generation} starts at 0 for all newly-minted tokens.
     */
    public static AuthRefreshTokenEntity toEntity(UUID userId, UUID tenantId, UUID sessionId,
                                                   String tokenHash, UUID familyId,
                                                   Instant expiresAt) {
        return AuthRefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .generation(0)
                .used(false)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Builds the PG audit-history entity from an already-constructed domain
     * {@link RefreshToken}. Used during token rotation so the entity fields
     * stay in sync with the Redis record without duplicating field assignments.
     */
    public static AuthRefreshTokenEntity toRotatedEntity(RefreshToken token) {
        return AuthRefreshTokenEntity.builder()
                .id(token.getId())
                .userId(token.getUserId())
                .tenantId(token.getTenantId())
                .sessionId(token.getSessionId())
                .tokenHash(token.getTokenHash())
                .familyId(token.getFamilyId())
                .generation(token.getGeneration())
                .used(false)
                .expiresAt(token.getExpiresAt())
                .build();
    }
}
