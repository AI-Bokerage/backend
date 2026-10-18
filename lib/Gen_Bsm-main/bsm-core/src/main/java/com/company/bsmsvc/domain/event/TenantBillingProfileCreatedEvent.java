package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record TenantBillingProfileCreatedEvent(
    UUID profileId,
    UUID tenantId,
    PaymentProvider paymentProvider,
    Instant occurredAt
) {}
