package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ImpersonationStartedEvent(
        UUID superAdminId,
        UUID tenantId,
        String reason,
        Instant expiresAt,
        Instant timestamp
) {}
