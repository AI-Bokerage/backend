package com.company.ppmsvc.entitlement.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Classification of an entitlement definition.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.  Using a fixed string rather than the
 * enum name means the constant can be renamed without a schema migration or a
 * breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.EntitlementTypeConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum EntitlementType {

    /** A simple true/false feature flag (e.g. {@code reporting_enabled}). */
    BOOLEAN    ("boolean"),

    /** A numeric upper bound (e.g. {@code max_internal_users = 25}). */
    QUOTA      ("quota"),

    /** A throughput limit measured per unit of time (e.g. {@code api_requests_per_minute = 500}). */
    RATE_LIMIT ("rate_limit");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static EntitlementType fromValue(String value) {
        for (EntitlementType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown EntitlementType value: " + value);
    }
}
