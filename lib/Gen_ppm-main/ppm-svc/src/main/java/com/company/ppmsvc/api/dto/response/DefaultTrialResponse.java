package com.company.ppmsvc.api.dto.response;

import java.util.UUID;

/**
 * Returned by {@code GET /api/v1/ppm/plans/default-trial}.
 *
 * <p>Provides BSM with the single canonical trial plan version to use when
 * provisioning a TRIALING subscription for a new tenant.  The {@code planVersionId}
 * is the value BSM should store as {@code ppmPlanVersionId} on the subscription.</p>
 *
 * <p>Public endpoint — no Authorization header required.</p>
 */
public record DefaultTrialResponse(
    UUID   planId,
    String planCode,
    UUID   planVersionId
) {}
