package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;

import java.time.Instant;
import java.util.UUID;

public record ImpersonationSessionResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        UUID targetUserId,
        String reason,
        String status,
        UUID reviewedByUserId,
        Instant reviewedAt,
        UUID endedByUserId,
        Instant endedAt,
        Instant expiresAt,
        Instant createdAt) {

    public static ImpersonationSessionResponse from(ImpersonationSessionEntity session) {
        return new ImpersonationSessionResponse(
                session.getId(),
                session.getTenantId(),
                session.getRequestedByUserId(),
                session.getTargetUserId(),
                session.getReason(),
                session.getStatus().name(),
                session.getReviewedByUserId(),
                session.getReviewedAt(),
                session.getEndedByUserId(),
                session.getEndedAt(),
                session.getExpiresAt(),
                session.getCreatedAt());
    }
}
