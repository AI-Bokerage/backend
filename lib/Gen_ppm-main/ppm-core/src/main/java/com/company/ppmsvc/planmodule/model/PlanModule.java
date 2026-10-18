package com.company.ppmsvc.planmodule.model;

import com.company.ppmsvc.common.AuditableEntity;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a single plan ↔ module assignment.
 *
 * <p>This is a lightweight join aggregate — it carries only the identity of
 * both sides (UUIDs) and creation audit metadata.  No object references to
 * {@link Plan} or {@link Module} are held; cross-aggregate navigation is done
 * in the application service via the respective repository ports.
 *
 * <p>Mappings are immutable after creation: the only mutation is deletion.
 * There is therefore no {@code updatedAt} / {@code updatedBy} on this model.
 *
 * <p>PPM-03 design invariant: slugs and codes never appear here — all
 * relational joins use UUID foreign keys.
 */
@Getter
public class PlanModule extends AuditableEntity {

    /** The plan side of the assignment. */
    private final UUID planId;

    /** The module side of the assignment. */
    private final UUID moduleId;

    /**
     * Builder constructor.
     *
     * <p>{@code updatedAt} and {@code updatedBy} are intentionally excluded —
     * mapping rows are create-only.  The {@code AuditableEntity} superclass
     * receives {@code null} for those fields.
     */
    @Builder
    private PlanModule(UUID id, Long version,
                       Instant createdAt, UUID createdBy,
                       UUID planId, UUID moduleId) {
        super(id, createdAt, null, createdBy, null);
        this.version  = version;
        this.planId   = planId;
        this.moduleId = moduleId;
    }
}
