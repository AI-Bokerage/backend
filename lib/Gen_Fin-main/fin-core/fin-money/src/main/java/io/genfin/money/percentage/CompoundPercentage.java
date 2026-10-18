package io.genfin.money.percentage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Combines a sequence of successively-applied percentages into one equivalent {@link Percentage}.
 */
public final class CompoundPercentage {

  private CompoundPercentage() {}

  public static Percentage compoundDiscounts(List<Percentage> discounts) {
    BigDecimal remaining =
        discounts.stream()
            .map(d -> BigDecimal.ONE.subtract(d.fraction()))
            .reduce(BigDecimal.ONE, (a, b) -> a.multiply(b, MathContext.DECIMAL64));
    return Percentage.ofFraction(BigDecimal.ONE.subtract(remaining));
  }

  public static Percentage compoundMarkups(List<Percentage> markups) {
    BigDecimal grown =
        markups.stream()
            .map(m -> BigDecimal.ONE.add(m.fraction()))
            .reduce(BigDecimal.ONE, (a, b) -> a.multiply(b, MathContext.DECIMAL64));
    return Percentage.ofFraction(grown.subtract(BigDecimal.ONE));
  }
}
