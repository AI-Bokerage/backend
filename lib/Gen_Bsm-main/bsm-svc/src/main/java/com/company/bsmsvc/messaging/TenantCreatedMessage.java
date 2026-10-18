package com.company.bsmsvc.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire DTO matching TNT-SVC's {@code TenantCreatedEvent} JSON structure.
 * All fields are optional (ignoreUnknown = true) to tolerate future schema additions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantCreatedMessage(
    UUID eventId,
    UUID tenantId,
    String tenantSlug,
    String tenantName,
    String planTier,
    String billingCycle,
    UUID ownerUserId,
    String ownerEmail,
    Instant occurredAt,
    UUID ppmPlanId,
    UUID ppmPlanVersionId,
    String region,
    String sourceChannel
) {}
