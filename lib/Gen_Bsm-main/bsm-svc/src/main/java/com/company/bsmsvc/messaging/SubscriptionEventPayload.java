package com.company.bsmsvc.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical wire payload for all BSM subscription lifecycle events.
 *
 * <p>Published on {@code cpms.events} exchange with routing keys:
 * <ul>
 *   <li>{@code bsm.subscription.created}</li>
 *   <li>{@code bsm.subscription.changed}  (upgrade, downgrade, pause, resume)</li>
 *   <li>{@code bsm.subscription.canceled}</li>
 *   <li>{@code bsm.subscription.expired}  (trial or dunning exhaustion)</li>
 *   <li>{@code bsm.subscription.renewed}  (billing period advanced)</li>
 * </ul>
 *
 * <p><strong>Option B (parallel publish):</strong> {@code ppmPlanId} is added as a nullable
 * field alongside the existing {@code oldPlanCode}/{@code newPlanCode} fields.
 * Consumers (tnt-svc) should use {@code ppmPlanId} as the authoritative plan identifier
 * for PPM-backed subscriptions when {@code newPlanCode} is null or stale.
 * BSM-native subscriptions have {@code ppmPlanId = null}.
 *
 * <p>{@code version = 1} for future compatibility.
 */
public record SubscriptionEventPayload(
    int     version,
    String  eventType,
    UUID    tenantId,
    UUID    subscriptionId,
    String  oldPlanCode,
    String  newPlanCode,
    String  billingCycle,
    String  status,
    Instant trialEndsAt,
    Instant effectiveAt,
    UUID    ppmPlanId
) {
    public static final int CURRENT_VERSION = 1;
}
