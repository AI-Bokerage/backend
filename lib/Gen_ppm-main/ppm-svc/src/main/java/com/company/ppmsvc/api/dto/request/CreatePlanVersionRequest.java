package com.company.ppmsvc.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new plan version entry.
 *
 * <p>{@code active} defaults to {@code true} when omitted (BR-8).
 * {@code effectiveFrom} may be a future date — future-dated versions are valid.
 *
 * <p>The version identity {@code (planId, versionNo)} and {@code effectiveFrom}
 * are immutable after creation and may not be changed via the update endpoint.
 */
public record CreatePlanVersionRequest(

    @NotNull
    UUID planId,

    @NotNull
    Integer versionNo,

    @NotNull
    LocalDate effectiveFrom,

    Boolean active,

    // ── Limit fields (PPM-12B) — all optional; null = unlimited / false ───────
    Integer maxInternalUsers,
    Integer maxClientUsers,
    Integer maxActiveProjects,
    Long    storageQuotaBytes,
    Boolean customDomainEnabled,
    Boolean ssoEnabled,
    Boolean prioritySupport
) {}
