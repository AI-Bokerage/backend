package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Lifecycle status of a {@code ReferralEvent} (one tracked conversion attempt). */
@Getter
@RequiredArgsConstructor
public enum ReferralEventStatus {

    PENDING  ("pending"),
    CONVERTED("converted"),
    CANCELLED("cancelled");

    @JsonValue
    private final String value;

    @JsonCreator
    public static ReferralEventStatus fromValue(String value) {
        for (ReferralEventStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ReferralEventStatus value: " + value);
    }
}
