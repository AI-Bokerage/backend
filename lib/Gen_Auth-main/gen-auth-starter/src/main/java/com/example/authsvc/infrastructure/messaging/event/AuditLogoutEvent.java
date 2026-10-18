package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditLogoutEvent(UUID userId, UUID tenantId, Instant occurredAt) {}
