package com.company.ppmsvc.module.model;

import com.company.ppmsvc.common.AuditableEntity;

import com.company.ppmsvc.module.model.ModuleCode;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code Module} catalog entity.
 *
 * <p>Represents a platform capability that may be attached to subscription
 * plans (e.g. Lead Management, Invoicing, SSO).  Modules are platform-wide
 * records — they are NOT tenant-owned and do NOT use RLS.
 *
 * <p><strong>Construction:</strong> use the static {@link #builder()} for
 * reconstituting from persistence.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Module extends AuditableEntity {

    /** Stable machine-readable identifier for this capability. */
    private final ModuleCode code;

    /** Human-readable display name shown in the UI. */
    private final String name;

    /** Optional description of what this module provides. */
    private final String description;

    /**
     * Whether this module is currently available for attachment to plans.
     * Inactive modules are hidden from the catalog but retained for history.
     */
    private final boolean active;

    @Builder
    private Module(UUID id, Long version,
                   Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                   ModuleCode code, String name, String description, boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.code        = code;
        this.name        = name;
        this.description = description;
        this.active      = active;
    }
}
