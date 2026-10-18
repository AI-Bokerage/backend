package io.genfin.money.arithmetic;

import io.genfin.money.internal.arithmetic.UnboundedOverflowPolicy;
import io.genfin.money.port.arithmetic.OverflowPolicy;
import io.genfin.money.precision.PrecisionPolicies;
import io.genfin.money.rounding.RoundingStrategies;

/** Factory for {@link ArithmeticPolicy} instances. */
public final class ArithmeticPolicies {

  private static final OverflowPolicy UNBOUNDED = new UnboundedOverflowPolicy();

  private static final ArithmeticPolicy STANDARD =
      new ArithmeticPolicy(
          PrecisionPolicies.currencyFraction(),
          PrecisionPolicies.standard(),
          RoundingStrategies.HALF_UP,
          UNBOUNDED);

  private ArithmeticPolicies() {}

  public static ArithmeticPolicy standard() {
    return STANDARD;
  }

  public static OverflowPolicy unbounded() {
    return UNBOUNDED;
  }
}
