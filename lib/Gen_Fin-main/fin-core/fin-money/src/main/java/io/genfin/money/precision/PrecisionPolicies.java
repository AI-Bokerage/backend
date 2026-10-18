package io.genfin.money.precision;

import io.genfin.money.internal.precision.CurrencyFractionScalePolicy;
import io.genfin.money.internal.precision.DefaultPrecisionPolicy;
import io.genfin.money.port.precision.PrecisionPolicy;
import io.genfin.money.port.precision.ScalePolicy;
import java.math.MathContext;
import java.math.RoundingMode;

/** Factory for {@link PrecisionPolicy} and {@link ScalePolicy} instances. */
public final class PrecisionPolicies {

  private static final PrecisionPolicy STANDARD =
      new DefaultPrecisionPolicy(MathContext.DECIMAL64, RoundingMode.HALF_UP);
  private static final ScalePolicy CURRENCY_FRACTION = new CurrencyFractionScalePolicy();

  private PrecisionPolicies() {}

  public static PrecisionPolicy standard() {
    return STANDARD;
  }

  public static PrecisionPolicy of(MathContext mathContext, RoundingMode roundingMode) {
    return new DefaultPrecisionPolicy(mathContext, roundingMode);
  }

  public static ScalePolicy currencyFraction() {
    return CURRENCY_FRACTION;
  }

  public static ScalePolicy fixed(int scale) {
    return currency -> scale;
  }
}
