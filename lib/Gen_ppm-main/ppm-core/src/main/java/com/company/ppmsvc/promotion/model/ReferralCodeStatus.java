package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Lifecycle status of a {@code ReferralCode}. */
@Getter
@RequiredArgsConstructor
public enum ReferralCodeStatus {

    ACTIVE  ("active"),
    INACTIVE("inactive");

    @JsonValue
    private final String value;

    @JsonCreator
    public static ReferralCodeStatus fromValue(String value) {
        for (ReferralCodeStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ReferralCodeStatus value: " + value);
    }
}
