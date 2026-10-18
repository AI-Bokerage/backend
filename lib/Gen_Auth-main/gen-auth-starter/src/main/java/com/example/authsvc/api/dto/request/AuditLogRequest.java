package com.example.authsvc.api.dto.request;

import java.util.UUID;

public record AuditLogRequest(
        UUID tenantId,
        UUID userId,
        String action,
        String ip,
        String userAgent,
        String details
) {}
