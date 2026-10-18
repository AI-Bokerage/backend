package com.company.ppmsvc.plan.model;

import com.company.ppmsvc.common.AuditableEntity;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code Plan} catalog entity.
 *
 * <p>Represents a subscription plan offered on the platform (e.g. Trial,
 * Starter, Growth, Scale, Enterprise).  Plans are platform-wide records —
 * they are NOT tenant-owned and do NOT use RLS.
 *
 * <p>A plan carries a {@link #code} (stable machine identifier), a {@link #slug}
 * (human-readable, URL-safe, immutable after creation), display metadata, and
 * a {@link #visibility} that controls catalog exposure.
 *
 * <p><strong>Construction:</strong> use the static {@link #builder()} for
 * reconstituting from persistence or creating new instances in application services.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Plan extends AuditableEntity {

    /** Stable machine-readable identifier for this plan (e.g. {@code "starter"}). */
    private final String code;

    /**
     * Human-readable, URL-safe slug — immutable after creation.
     * Used for public-facing API lookups (e.g. {@code GET /plans/slug/starter}).
     */
    private final String slug;

    /** Human-readable display name shown in the UI. */
    private final String name;

    /** Short marketing line displayed on pricing pages; optional. */
    private final String tagline;

    /** Optional long-form description of what this plan includes. */
    private final String description;

    /** Controls catalog visibility — PUBLIC, PRIVATE, or LEGACY. */
    private final PlanVisibility visibility;

    /** Number of free trial days offered with this plan (0 = no trial). */
    private final int trialDays;

    /**
     * Whether this plan is currently available for selection.
     * Inactive plans are hidden from the catalog but retained for history.
     */
    private final boolean active;

    /**
     * Tier identifier used for upgrade/downgrade validation (e.g. {@code "trial"},
     * {@code "starter"}, {@code "growth"}, {@code "scale"}, {@code "enterprise"}).
     * Nullable — existing plans without a tier are treated as unknown by BSM and
     * the tier guard is skipped rather than failing.
     */
    private final String tier;

    @Builder
    private Plan(UUID id, Long version,
                 Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                 String code, String slug, String name, String tagline, String description,
                 PlanVisibility visibility, int trialDays, boolean active, String tier) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.code        = code;
        this.slug        = slug;
        this.name        = name;
        this.tagline     = tagline;
        this.description = description;
        this.visibility  = visibility;
        this.trialDays   = trialDays;
        this.active      = active;
        this.tier        = tier;
    }
}
