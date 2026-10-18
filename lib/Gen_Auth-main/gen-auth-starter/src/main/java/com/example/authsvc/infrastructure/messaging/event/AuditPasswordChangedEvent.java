package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditPasswordChangedEvent(UUID userId, UUID tenantId, Instant occurredAt) {}
