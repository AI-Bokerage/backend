package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.model.PlanVersionLimitsResponse;
import com.company.ppmsvc.plan.model.PlanVersionMetaResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Plan Version Catalog.
 *
 * <p>Manages the lifecycle of plan version entries (create, update, delete, query).
 * All methods operate on domain types — no DTOs cross this boundary.
 *
 * <p>Version identity is {@code (planId, versionNo)}.  A plan may have multiple
 * versions with non-overlapping effective date ranges.  When a new version is
 * created, the previously open-ended version is automatically closed.
 */
public interface PlanVersionApplicationService {

    /**
     * Creates a new plan version entry.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: The referenced plan must exist.</li>
     *   <li>BR-2: {@code versionNo} must be unique per plan among active rows.</li>
     *   <li>BR-3: {@code effectiveFrom} must be unique per plan among active rows.</li>
     *   <li>BR-6: If there is an existing open-ended version (effectiveTo = null) whose
     *       effectiveFrom is earlier than the new version's effectiveFrom, it is
     *       automatically closed by setting effectiveTo = newEffectiveFrom.minusDays(1).</li>
     *   <li>BR-7: The new effectiveFrom must not fall inside any existing closed version's
     *       date range — this would create an overlapping timeline.</li>
     *   <li>BR-8: {@code active} defaults to {@code true} when not supplied.</li>
     * </ul>
     */
    PlanVersion createVersion(UUID actorId, UUID planId, Integer versionNo, LocalDate effectiveFrom,
        Boolean active, Integer maxInternalUsers, Integer maxClientUsers, Integer maxActiveProjects,
        Long storageQuotaBytes, Boolean customDomainEnabled, Boolean ssoEnabled, Boolean prioritySupport);

    /**
     * Partially updates an existing plan version entry (PATCH semantics).
     *
     * <p>Only {@code effectiveTo}, {@code active}, and the limit fields may be changed.
     * The identity fields ({@code planId}, {@code versionNo}, {@code effectiveFrom})
     * are immutable after creation (BR-4). A {@code null} argument means "leave unchanged".
     */
    PlanVersion updateVersion(UUID actorId, UUID versionId, LocalDate effectiveTo, Boolean active,
        Integer maxInternalUsers, Integer maxClientUsers, Integer maxActiveProjects,
        Long storageQuotaBytes, Boolean customDomainEnabled, Boolean ssoEnabled, Boolean prioritySupport);

    /**
     * Returns a single plan version by its ID.
     */
    PlanVersion getVersion(UUID versionId);

    /**
     * Returns the most recent active version for the given plan (highest {@code versionNo}).
     */
    PlanVersion getLatestVersion(UUID planId);

    /**
     * Returns all non-deleted plan versions for a plan (or all plans when {@code planId} is null),
     * optionally filtered by {@code active}. Ordered by {@code versionNo} descending per plan.
     */
    List<PlanVersion> listVersions(UUID planId, Boolean active);

    /**
     * Soft-deletes an existing plan version entry.
     */
    void deleteVersion(UUID actorId, UUID versionId);

    /**
     * Returns lightweight metadata for a plan version joined with its parent plan.
     */
    PlanVersionMetaResponse getPlanVersionMeta(UUID versionId);

    /**
     * Returns the capacity limits for a plan version.
     */
    PlanVersionLimitsResponse getPlanVersionLimits(UUID versionId);
}
