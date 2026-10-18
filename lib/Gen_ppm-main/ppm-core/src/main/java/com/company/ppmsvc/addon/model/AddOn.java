package com.company.ppmsvc.addon.model;

import com.company.ppmsvc.common.AuditableEntity;

import com.company.ppmsvc.addon.model.AddOnType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code AddOn} catalog entity.
 *
 * <p>Represents a purchasable add-on item offered alongside subscription plans
 * (e.g. Extra Users, Extra Storage, Premium Support).  Add-ons are platform-wide
 * catalog records — they are NOT tenant-owned and do NOT use RLS.
 *
 * <p><strong>Construction:</strong> use the static {@link #builder()} for
 * reconstituting from persistence or creating new instances in application services.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class AddOn extends AuditableEntity {

    /** Stable machine-readable identifier for this add-on (e.g. {@code "EXTRA_USERS_10"}). */
    private final String code;

    /** Human-readable display name shown in the UI. */
    private final String name;

    /** Optional description of what this add-on provides. */
    private final String description;

    /** Classifies how the add-on should be interpreted and priced. */
    private final AddOnType type;

    /**
     * Whether this add-on is currently available for purchase.
     * Inactive add-ons are hidden from the catalog but retained for history.
     */
    private final boolean active;

    @Builder
    private AddOn(UUID id, Long version,
                  Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                  String code, String name, String description,
                  AddOnType type, boolean active) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.code        = code;
        this.name        = name;
        this.description = description;
        this.type        = type;
        this.active      = active;
    }
}
