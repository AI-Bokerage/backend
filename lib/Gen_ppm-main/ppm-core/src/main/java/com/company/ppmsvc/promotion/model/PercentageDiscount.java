package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.math.BigDecimal;

/**
 * A discount expressed as a percentage of the base price.
 *
 * <p>{@code maxDiscountValue} / {@code minDiscountValue} clamp the computed
 * discount amount (not the percentage itself); either may be {@code null} for
 * no bound in that direction. Amounts are in the resolved price's currency —
 * this record carries no currency field of its own.
 */
@JsonTypeName("percentage")
public record PercentageDiscount(
    BigDecimal percentage,
    BigDecimal maxDiscountValue,
    BigDecimal minDiscountValue
) implements PriceAction {
}
