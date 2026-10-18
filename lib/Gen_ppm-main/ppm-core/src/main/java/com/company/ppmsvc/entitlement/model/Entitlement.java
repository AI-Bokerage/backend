package com.company.ppmsvc.entitlement.model;

import com.company.ppmsvc.common.AuditableEntity;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Entitlement catalog entity.
 *
 * <p>Represents a reusable limit or permission definition that can be assigned
 * to subscription plans with a concrete value.  Examples: {@code max_internal_users},
 * {@code reporting_enabled}, {@code api_requests_per_minute}.
 *
 * <p>Entitlements are platform-wide catalog records — they are NOT tenant-owned.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Entitlement extends AuditableEntity {

    /** Stable machine-readable identifier (e.g. {@code max_internal_users}). */
    private final String code;

    /** Human-readable display name. */
    private final String name;

    /** Optional description of what this entitlement controls. */
    private final String description;

    /** Classifies how the entitlement value should be interpreted. */
    private final EntitlementType type;

    /**
     * Whether this entitlement is currently active in the catalog.
     * Inactive entitlements are hidden from catalog queries but retained for history.
     */
    private final boolean active;

    @Builder
    private Entitlement(UUID id, Long version,
                        Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                        String code, String name, String description,
                        EntitlementType type, boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.code        = code;
        this.name        = name;
        this.description = description;
        this.type        = type;
        this.active      = active;
    }
}
