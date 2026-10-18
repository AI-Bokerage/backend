package com.company.ppmsvc.api.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a plan version entry.
 */
public record PlanVersionResponse(
    UUID      id,
    UUID      planId,
    Integer   versionNo,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean   active,
    Instant   createdAt,
    Instant   updatedAt
) {}
