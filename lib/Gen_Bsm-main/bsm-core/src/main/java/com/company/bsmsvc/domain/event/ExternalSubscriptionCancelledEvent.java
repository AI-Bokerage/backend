package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record ExternalSubscriptionCancelledEvent(
    UUID subscriptionId,
    UUID tenantId,
    String externalSubscriptionId,
    PaymentProvider paymentProvider,
    boolean immediately,
    Instant occurredAt
) {}
