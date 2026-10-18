package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record LoginSuccessEvent(
        UUID userId,
        UUID tenantId,
        UUID sessionId,
        String ip,
        String userAgent,
        Instant timestamp
) {}
