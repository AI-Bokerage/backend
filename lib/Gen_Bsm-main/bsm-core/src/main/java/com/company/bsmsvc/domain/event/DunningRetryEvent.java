package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.DunningStatus;
import java.time.Instant;
import java.util.UUID;

public record DunningRetryEvent(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber, DunningStatus newStatus, Instant occurredAt) {}
