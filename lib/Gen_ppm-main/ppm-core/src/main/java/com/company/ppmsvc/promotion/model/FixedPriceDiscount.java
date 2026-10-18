package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.math.BigDecimal;

/**
 * Sets the final price to a fixed amount. The discount is the difference
 * between the base price and this amount, floored at zero (a fixed price
 * above the base yields no discount). Amount is in the resolved price's
 * currency — this record carries no currency field of its own.
 */
@JsonTypeName("fixed_price")
public record FixedPriceDiscount(BigDecimal price) implements PriceAction {
}
