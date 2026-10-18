package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TenantBillingCurrencyChangedEvent(
    UUID profileId,
    UUID tenantId,
    String oldCurrency,
    String newCurrency,
    Instant occurredAt
) {}
