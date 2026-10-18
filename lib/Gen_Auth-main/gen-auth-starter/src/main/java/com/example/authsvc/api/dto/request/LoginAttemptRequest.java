package com.example.authsvc.api.dto.request;

import java.util.UUID;

public record LoginAttemptRequest(
        UUID tenantId,
        UUID userId,
        String email,
        String ip,
        String userAgent,
        boolean success,
        String failureReason
) {}
