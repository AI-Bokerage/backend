package com.company.ppmsvc.addon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Classification of a purchasable add-on catalog item.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.  Using a fixed string rather than the
 * enum name means the constant can be renamed without a schema migration or a
 * breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.AddOnTypeConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum AddOnType {

    /** A discrete capability toggle (e.g. SSO, White Label). */
    FEATURE ("feature"),

    /** A numeric limit add-on (e.g. extra users, extra storage). */
    QUOTA   ("quota"),

    /** A human-delivered or managed service (e.g. Premium Support, Dedicated CSM). */
    SERVICE ("service");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static AddOnType fromValue(String value) {
        for (AddOnType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown AddOnType value: " + value);
    }
}
