package com.company.ppmsvc.common;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Extends {@link BaseEntity} with standard audit metadata.
 *
 * <p>Every PPM entity that must record <em>who</em> created or last modified
 * it should extend this class.
 *
 * <p>PPM-SVC is a catalog service — there is no {@code tenantId} here.
 */
@Getter
public abstract class AuditableEntity extends BaseEntity {

    protected Instant createdAt;
    protected Instant updatedAt;
    protected UUID    createdBy;
    protected UUID    updatedBy;

    protected AuditableEntity(UUID id) {
        super(id);
    }

    /** Reconstitution constructor — used exclusively by persistence mappers. */
    protected AuditableEntity(UUID id, Instant createdAt, Instant updatedAt,
                               UUID createdBy, UUID updatedBy) {
        super(id);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    protected void initAudit(UUID actorId) {
        Instant now   = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    protected void touchAudit(UUID actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
