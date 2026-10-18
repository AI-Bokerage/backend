package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.math.BigDecimal;

/**
 * A discount expressed as a fixed monetary amount, in the resolved price's
 * currency (no currency field of its own).
 */
@JsonTypeName("flat")
public record FlatDiscount(BigDecimal amount) implements PriceAction {
}
