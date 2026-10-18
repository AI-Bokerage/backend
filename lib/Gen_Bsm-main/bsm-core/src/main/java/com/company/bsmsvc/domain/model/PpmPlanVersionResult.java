package com.company.bsmsvc.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * BSM-side representation of PPM's PlanVersionResponse.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plans/{planId}/versions/latest}.
 * Used during C2 version-locking at checkout — BSM records this ID on the
 * subscription so future catalog edits never affect grandfathered subscribers.
 */
public record PpmPlanVersionResult(
    UUID      id,
    UUID      planId,
    Integer   versionNo,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean   active
) {}
