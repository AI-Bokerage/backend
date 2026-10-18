package com.company.ppmsvc.plan.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Catalog-facing read model for a plan version snapshot.
 *
 * <p>{@code effectiveTo} is nullable — {@code null} means the version has no
 * scheduled end date (open-ended).
 */
public record CatalogVersionResponse(
        UUID versionId,
        Integer versionNo,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active
) {}
