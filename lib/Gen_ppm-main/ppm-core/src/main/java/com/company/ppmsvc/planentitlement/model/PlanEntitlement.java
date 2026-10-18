package com.company.ppmsvc.planentitlement.model;

import com.company.ppmsvc.common.AuditableEntity;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a single plan ↔ entitlement assignment with a concrete value.
 *
 * <p>This is a lightweight join aggregate — it carries the identity of both
 * sides (UUIDs), the assigned value, and creation audit metadata.  No object
 * references to {@link Plan} or {@link Entitlement} are held; cross-aggregate
 * navigation is done in the application service via the respective ports.
 *
 * <p>Assignments are immutable after creation: the only mutation is deletion.
 * There is therefore no {@code updatedAt} / {@code updatedBy} on this model.
 *
 * <p>PPM-04 design invariant: slugs and codes never appear here — all
 * relational joins use UUID foreign keys.
 */
@Getter
public class PlanEntitlement extends AuditableEntity {

    /** The plan side of the assignment. */
    private final UUID planId;

    /** The entitlement definition being assigned. */
    private final UUID entitlementId;

    /**
     * The concrete value for this entitlement on this plan.
     * Stored as a VARCHAR and interpreted according to {@link Entitlement#getType()}.
     * Examples: {@code "true"}, {@code "25"}, {@code "500"}.
     */
    private final String value;

    /**
     * Builder constructor.
     *
     * <p>{@code updatedAt} and {@code updatedBy} are intentionally excluded —
     * assignment rows are create-only.  The {@code AuditableEntity} superclass
     * receives {@code null} for those fields.
     */
    @Builder
    private PlanEntitlement(UUID id, Long version,
                            Instant createdAt, UUID createdBy,
                            UUID planId, UUID entitlementId, String value) {
        super(id, createdAt, null, createdBy, null);
        this.version       = version;
        this.planId        = planId;
        this.entitlementId = entitlementId;
        this.value         = value;
    }
}
