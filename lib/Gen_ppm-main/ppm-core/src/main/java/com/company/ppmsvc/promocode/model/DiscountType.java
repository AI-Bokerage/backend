package com.company.ppmsvc.promocode.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Classification of a promo code discount.
 *
 * <p>Each constant carries a stable {@code value} used as both the JSON wire
 * format and the database storage value.  Using a fixed string rather than the
 * enum name means the constant can be renamed without a schema migration or a
 * breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.DiscountTypeConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum DiscountType {

    /** Discount expressed as a percentage of the original price (e.g. 20%). */
    PERCENTAGE ("percentage"),

    /** Discount expressed as a fixed monetary amount (e.g. ₹100 off). */
    FLAT       ("flat");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static DiscountType fromValue(String value) {
        for (DiscountType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown DiscountType value: " + value);
    }
}
