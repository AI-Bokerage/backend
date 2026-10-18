package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DunningStartedEvent(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber, Instant occurredAt) {}
