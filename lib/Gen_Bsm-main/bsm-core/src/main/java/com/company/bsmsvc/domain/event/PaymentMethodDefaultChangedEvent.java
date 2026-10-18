package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentMethodDefaultChangedEvent(
    UUID paymentMethodId,
    UUID tenantId,
    Instant occurredAt
) {}
