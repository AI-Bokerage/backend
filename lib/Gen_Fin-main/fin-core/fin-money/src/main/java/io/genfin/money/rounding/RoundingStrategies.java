package io.genfin.money.rounding;

import io.genfin.money.port.rounding.RoundingStrategy;
import java.math.RoundingMode;

/** Factory for standard {@link RoundingStrategy} implementations, plus a hook for custom ones. */
public final class RoundingStrategies {

  public static final RoundingStrategy HALF_UP = of(RoundingMode.HALF_UP);
  public static final RoundingStrategy HALF_EVEN = of(RoundingMode.HALF_EVEN);
  public static final RoundingStrategy DOWN = of(RoundingMode.DOWN);
  public static final RoundingStrategy CEILING = of(RoundingMode.CEILING);
  public static final RoundingStrategy FLOOR = of(RoundingMode.FLOOR);

  private RoundingStrategies() {}

  public static RoundingStrategy of(RoundingMode mode) {
    return (value, scale) -> value.setScale(scale, mode);
  }

  public static RoundingStrategy custom(RoundingStrategy strategy) {
    return strategy;
  }
}
