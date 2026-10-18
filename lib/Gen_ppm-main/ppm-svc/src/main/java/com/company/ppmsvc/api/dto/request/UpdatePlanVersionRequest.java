package com.company.ppmsvc.api.dto.request;

import java.time.LocalDate;

/**
 * Request DTO for partially updating an existing plan version (PATCH semantics).
 *
 * <p>Only mutable fields are present.  The version identity fields
 * ({@code planId}, {@code versionNo}, {@code effectiveFrom}) are intentionally
 * absent — they are immutable after creation (BR-4).
 *
 * <p>A {@code null} field means "leave the existing value unchanged".
 */
public record UpdatePlanVersionRequest(
    LocalDate effectiveTo,
    Boolean   active,

    // ── Limit fields (PPM-12B) — all optional; null = leave existing unchanged ─
    Integer maxInternalUsers,
    Integer maxClientUsers,
    Integer maxActiveProjects,
    Long    storageQuotaBytes,
    Boolean customDomainEnabled,
    Boolean ssoEnabled,
    Boolean prioritySupport
) {}
