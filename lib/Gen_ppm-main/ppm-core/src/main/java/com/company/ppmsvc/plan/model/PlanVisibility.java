package com.company.ppmsvc.plan.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Visibility classification for subscription plans in the catalog.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.  Using a fixed string rather than the
 * enum name means the constant can be renamed without a schema migration or a
 * breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.PlanVisibilityConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum PlanVisibility {

    /** Plan is visible to all tenants in the catalog. */
    PUBLIC  ("public"),

    /** Plan is hidden from the public catalog; only accessible via direct reference. */
    PRIVATE ("private"),

    /** Plan is retained for existing subscribers but no longer offered to new tenants. */
    LEGACY  ("legacy");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static PlanVisibility fromValue(String value) {
        for (PlanVisibility v : values()) {
            if (v.value.equals(value)) return v;
        }
        throw new IllegalArgumentException("Unknown PlanVisibility value: " + value);
    }
}
