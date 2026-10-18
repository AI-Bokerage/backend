package com.example.authsvc.api.mapper;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.infrastructure.persistence.entity.AuthAuditLogEntity;

import java.util.UUID;

public final class AuthAuditLogMapper {

    private AuthAuditLogMapper() {}

    public static AuthAuditLogEntity toEntity(AuditLogRequest request) {
        return AuthAuditLogEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(request.tenantId())
                .userId(request.userId())
                .action(request.action())
                .ipAddress(request.ip())
                .userAgent(request.userAgent())
                .details(request.details())
                .build();
    }
}
