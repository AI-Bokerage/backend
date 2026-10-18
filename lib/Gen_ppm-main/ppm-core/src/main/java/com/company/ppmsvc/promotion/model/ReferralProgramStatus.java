package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Lifecycle status of a {@code ReferralProgram}. */
@Getter
@RequiredArgsConstructor
public enum ReferralProgramStatus {

    ACTIVE  ("active"),
    INACTIVE("inactive");

    @JsonValue
    private final String value;

    @JsonCreator
    public static ReferralProgramStatus fromValue(String value) {
        for (ReferralProgramStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ReferralProgramStatus value: " + value);
    }
}
