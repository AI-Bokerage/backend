package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DunningCancelledEvent(UUID subscriptionId, UUID tenantId, Instant occurredAt) {}
