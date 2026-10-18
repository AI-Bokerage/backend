package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record CustomerCreatedEvent(
    UUID profileId,
    UUID tenantId,
    PaymentProvider paymentProvider,
    String externalCustomerId,
    Instant occurredAt
) {}
