package com.company.ppmsvc.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Billing recurrence cycle for a plan price.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.  Using a fixed string rather than the
 * enum name means the constant can be renamed without a schema migration or a
 * breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.BillingCycleConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum BillingCycle {

    /** Billed every calendar month. */
    MONTHLY ("monthly"),

    /** Billed once per year; typically offered at a discount over twelve monthly payments. */
    ANNUAL  ("annual");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static BillingCycle fromValue(String value) {
        for (BillingCycle c : values()) {
            if (c.value.equals(value)) return c;
        }
        throw new IllegalArgumentException("Unknown BillingCycle value: " + value);
    }
}
