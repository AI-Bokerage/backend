package com.company.ppmsvc.plan.model;

import com.company.ppmsvc.common.AuditableEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * An immutable historical version snapshot for a subscription plan.
 *
 * <p>The business key is {@code (planId, versionNo)}.
 * Each version records the date range during which it is (or was) active.
 *
 * <p>The snapshotting strategy — whether a version stores inline copies of
 * pricing, modules, and entitlements or references them by foreign key — is
 * deliberately deferred to Phase 2.  This model is intentionally minimal.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class PlanVersion extends AuditableEntity {

    /** Reference to the owning plan. Stored as UUID — no Plan object reference. */
    private final UUID planId;

    /** Monotonically increasing version number within a plan. */
    private final Integer versionNo;

    /** The date from which this version becomes effective. */
    private final LocalDate effectiveFrom;

    /**
     * The date on which this version is superseded, or {@code null} if open-ended.
     * A null value means the version has no scheduled end date.
     */
    private final LocalDate effectiveTo;

    /** Whether this version is currently active. */
    private final boolean active;

    // ── Limit fields (PPM-12B) ────────────────────────────────────────────────
    // Null = unlimited for numeric caps; null = false for boolean feature flags.
    private final Integer maxInternalUsers;
    private final Integer maxClientUsers;
    private final Integer maxActiveProjects;
    private final Long    storageQuotaBytes;
    private final Boolean customDomainEnabled;
    private final Boolean ssoEnabled;
    private final Boolean prioritySupport;

    @Builder
    private PlanVersion(UUID id, Long version,
                        Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                        UUID planId, Integer versionNo,
                        LocalDate effectiveFrom, LocalDate effectiveTo, boolean active,
                        Integer maxInternalUsers, Integer maxClientUsers, Integer maxActiveProjects,
                        Long storageQuotaBytes, Boolean customDomainEnabled,
                        Boolean ssoEnabled, Boolean prioritySupport) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version             = version;
        this.planId              = planId;
        this.versionNo           = versionNo;
        this.effectiveFrom       = effectiveFrom;
        this.effectiveTo         = effectiveTo;
        this.active              = active;
        this.maxInternalUsers    = maxInternalUsers;
        this.maxClientUsers      = maxClientUsers;
        this.maxActiveProjects   = maxActiveProjects;
        this.storageQuotaBytes   = storageQuotaBytes;
        this.customDomainEnabled = customDomainEnabled;
        this.ssoEnabled          = ssoEnabled;
        this.prioritySupport     = prioritySupport;
    }
}
