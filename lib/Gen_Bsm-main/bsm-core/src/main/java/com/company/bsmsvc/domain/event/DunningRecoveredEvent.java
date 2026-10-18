package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DunningRecoveredEvent(UUID subscriptionId, UUID tenantId, UUID invoiceId, Instant occurredAt) {}
