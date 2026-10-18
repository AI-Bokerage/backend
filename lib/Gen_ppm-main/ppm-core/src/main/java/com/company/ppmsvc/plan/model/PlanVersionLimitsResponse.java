package com.company.ppmsvc.plan.model;

import java.util.UUID;

/**
 * Plan capacity limits for a specific version.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plan-versions/{id}/limits}.
 * This endpoint is public (no Authorization header required) and is called by BSM
 * during {@code executeDowngradePreflight} to determine whether the tenant's current
 * usage exceeds the target plan's limits.
 *
 * <p>Null fields mean "unlimited" for numeric caps and {@code false} for boolean flags.
 * BSM maps null numeric values to {@code Integer.MAX_VALUE / Long.MAX_VALUE} before
 * applying limit comparisons.
 */
public record PlanVersionLimitsResponse(
    UUID    planVersionId,
    Integer maxInternalUsers,
    Integer maxClientUsers,
    Integer maxActiveProjects,
    Long    storageQuotaBytes,
    Boolean customDomainEnabled,
    Boolean ssoEnabled,
    Boolean prioritySupport
) {}
