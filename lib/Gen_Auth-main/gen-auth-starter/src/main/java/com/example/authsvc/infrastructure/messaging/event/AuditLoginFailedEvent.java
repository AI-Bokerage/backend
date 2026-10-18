package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditLoginFailedEvent(UUID tenantId, Instant occurredAt, String reason) {}
