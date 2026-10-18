package com.company.ppmsvc.planaddon.model;

import com.company.ppmsvc.common.AuditableEntity;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a single plan ↔ add-on availability assignment.
 *
 * <p>This is a lightweight join aggregate — it carries only the identity of
 * both sides (UUIDs) and creation audit metadata.  No object references to
 * {@link Plan} or {@link AddOn} are held; cross-aggregate navigation is done
 * in the application service via the respective repository ports.
 *
 * <p>Assignments are immutable after creation: the only mutation is deletion.
 * There is therefore no {@code updatedAt} / {@code updatedBy} on this model.
 */
@Getter
public class PlanAddOn extends AuditableEntity {

    /** The plan side of the assignment. */
    private final UUID planId;

    /** The add-on side of the assignment. */
    private final UUID addOnId;

    @Builder
    private PlanAddOn(UUID id, Long version,
                      Instant createdAt, UUID createdBy,
                      UUID planId, UUID addOnId) {
        super(id, createdAt, null, createdBy, null);
        this.version = version;
        this.planId  = planId;
        this.addOnId = addOnId;
    }
}
