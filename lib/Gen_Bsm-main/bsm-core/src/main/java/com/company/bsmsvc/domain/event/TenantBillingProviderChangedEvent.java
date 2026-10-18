package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record TenantBillingProviderChangedEvent(
    UUID profileId,
    UUID tenantId,
    PaymentProvider oldProvider,
    PaymentProvider newProvider,
    Instant occurredAt
) {}
