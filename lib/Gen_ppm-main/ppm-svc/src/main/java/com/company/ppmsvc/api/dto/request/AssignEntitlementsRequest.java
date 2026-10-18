package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for assigning or replacing entitlements on a plan.
 *
 * <p>{@link Set} is used rather than {@link java.util.List} to provide
 * BR-P4 idempotency at the binding layer: duplicate entitlement IDs in the
 * request are automatically collapsed to a single assignment before the
 * service processes them.
 */
public record AssignEntitlementsRequest(

    @NotEmpty
    Set<UUID> entitlementIds
) {}
