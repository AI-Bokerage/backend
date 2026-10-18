package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lifecycle status of a {@link Promotion}.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.
 *
 * <p>At quote time only {@link #ACTIVE} is applicable — both {@link #INACTIVE}
 * and {@link #DRAFT} resolve to {@code PROMOTION_INACTIVE}. {@code DRAFT} is
 * reserved so marketers staging a promotion don't have to overload
 * {@code INACTIVE}; it has no other behaviour in Phase 0.
 */
@Getter
@RequiredArgsConstructor
public enum PromotionStatus {

    ACTIVE  ("active"),
    INACTIVE("inactive"),
    DRAFT   ("draft");

    @JsonValue
    private final String value;

    @JsonCreator
    public static PromotionStatus fromValue(String value) {
        for (PromotionStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown PromotionStatus value: " + value);
    }
}
