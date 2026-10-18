package io.genfin.money.internal.precision;

import io.genfin.money.port.precision.PrecisionPolicy;
import java.math.MathContext;
import java.math.RoundingMode;

public final class DefaultPrecisionPolicy implements PrecisionPolicy {

  private final MathContext mathContext;
  private final RoundingMode roundingMode;

  public DefaultPrecisionPolicy(MathContext mathContext, RoundingMode roundingMode) {
    this.mathContext = mathContext;
    this.roundingMode = roundingMode;
  }

  @Override
  public MathContext mathContext() {
    return mathContext;
  }

  @Override
  public RoundingMode roundingMode() {
    return roundingMode;
  }
}
