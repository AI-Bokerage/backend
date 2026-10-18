package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Wire-vocab tag identifying what an {@link AppliedEntitlement} grants. */
@Getter
@RequiredArgsConstructor
public enum EntitlementType {

    FREE_PERIOD("free_period"),
    FREE_MODULE("free_module"),
    FREE_ADDON ("free_addon");

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
