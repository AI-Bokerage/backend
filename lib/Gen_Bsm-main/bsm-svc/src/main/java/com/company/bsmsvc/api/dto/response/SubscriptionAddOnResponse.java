package com.company.bsmsvc.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionAddOnResponse(
    UUID    id,
    UUID    subscriptionId,
    UUID    ppmAddOnId,
    UUID    ppmAddOnPriceId,
    Long    ppmResolvedPriceMinor,
    boolean active,
    Instant createdAt,
    String  createdBy
) {}
