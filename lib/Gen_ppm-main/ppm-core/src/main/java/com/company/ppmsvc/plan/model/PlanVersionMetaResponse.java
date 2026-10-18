package com.company.ppmsvc.plan.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight read-only metadata for a plan version — joined with its parent plan.
 *
 * <p>Returned by {@code GET /api/v1/ppm/plan-versions/{id}/meta}.
 * This endpoint is public (no Authorization header required) and is called by BSM for:
 * <ul>
 *   <li>Event publisher — resolving {@code planCode} for PPM-backed subscriptions (GAP-1 fix).</li>
 *   <li>Upgrade/downgrade validation — checking {@code planActive} and {@code versionActive}.</li>
 *   <li>C6 Phase 2 — version drift detection using {@code versionNo}.</li>
 * </ul>
 */
public record PlanVersionMetaResponse(
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
