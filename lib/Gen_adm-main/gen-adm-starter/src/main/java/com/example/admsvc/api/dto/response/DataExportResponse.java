package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;

import java.time.Instant;
import java.util.UUID;

public record DataExportResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        String status,
        long recordCount,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedByUserId,
        Instant createdAt) {

    public static DataExportResponse from(DataExportEntity export) {
        return new DataExportResponse(
                export.getId(),
                export.getTenantId(),
                export.getRequestedByUserId(),
                export.getStatus().name(),
                export.getRecordCount(),
                export.getExpiresAt(),
                export.getRevokedAt(),
                export.getRevokedByUserId(),
                export.getCreatedAt());
    }
}
