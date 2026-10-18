package io.genfin.money.percentage;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.math.BigDecimal;

/**
 * An immutable percentage, stored as a fraction (15% is {@code 0.15}), independent of display
 * formatting.
 */
public record Percentage(BigDecimal fraction) implements ValueObject {

  public Percentage {
    Validate.notNull(fraction, "fraction must not be null.");
  }

  public static Percentage ofFraction(BigDecimal fraction) {
    return new Percentage(fraction);
  }

  public static Percentage ofPercent(BigDecimal percent) {
    return new Percentage(percent.movePointLeft(2));
  }

  public static Percentage ofBasisPoints(long basisPoints) {
    return new Percentage(BigDecimal.valueOf(basisPoints).movePointLeft(4));
  }

  public BigDecimal asPercent() {
    return fraction.movePointRight(2);
  }

  public Percentage negate() {
    return new Percentage(fraction.negate());
  }
}
