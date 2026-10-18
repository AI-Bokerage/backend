package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Subscription response")
public record SubscriptionResponse(
    UUID id,
    UUID tenantId,
    UUID planVersionId,
    SubscriptionStatus status,
    BillingCycle billingCycle,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant trialEndsAt,
    Instant cancelledAt,
    boolean cancelAtPeriodEnd,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    // C2: PPM catalog identifiers locked at checkout; null for native BSM subscriptions
    UUID ppmPlanId,
    UUID ppmPriceId,
    UUID ppmPlanVersionId
) {
}
