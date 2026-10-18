package com.company.bsmsvc.domain.model;

import java.util.UUID;

/**
 * BSM-side representation of PPM's PlanVersionLimitsResponse.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plan-versions/{id}/limits}.
 * Used by {@link PpmPlanLimitsClient} during downgrade preflight to compare
 * tenant usage against target plan limits without reading BSM's local catalog.
 *
 * <p>Null fields mean "unlimited" for numeric caps and {@code false} for boolean flags.
 * Callers must treat null as {@code Integer.MAX_VALUE} / {@code Long.MAX_VALUE}
 * before applying limit comparisons (same convention as BSM's local PlanVersion).
 */
public record PpmPlanLimitsResult(
    UUID    planVersionId,
    Integer maxInternalUsers,
    Integer maxClientUsers,
    Integer maxActiveProjects,
    Long    storageQuotaBytes,
    Boolean customDomainEnabled,
    Boolean ssoEnabled,
    Boolean prioritySupport
) {}
