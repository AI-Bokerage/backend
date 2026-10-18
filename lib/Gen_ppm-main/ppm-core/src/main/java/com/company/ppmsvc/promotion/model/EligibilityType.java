package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Customer eligibility category checked by {@link EligibilityCondition}.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the JSONB storage value.
 */
@Getter
@RequiredArgsConstructor
public enum EligibilityType {

    NEW_CUSTOMER     ("new_customer"),
    EXISTING_CUSTOMER("existing_customer");

    @JsonValue
    private final String value;

    @JsonCreator
    public static EligibilityType fromValue(String value) {
        for (EligibilityType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown EligibilityType value: " + value);
    }
}
