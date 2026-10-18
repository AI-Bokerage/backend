package com.company.bsmsvc.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * BSM-side representation of PPM's PlanVersionMetaResponse.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plan-versions/{id}/meta}.
 * Used by {@link PpmVersionMetaClient} to resolve the plan code for a given
 * PPM plan version ID — fixing GAP-1 in BsmSubscriptionEventPublisher.
 */
public record PpmVersionMetaResult(
    UUID      versionId,
    UUID      planId,
    String    planCode,
    Integer   versionNo,
    boolean   versionActive,
    boolean   planActive,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String    tier
) {}
