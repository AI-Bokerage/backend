package io.genfin.money.arithmetic;

import io.genfin.money.port.arithmetic.OverflowPolicy;
import io.genfin.money.port.precision.PrecisionPolicy;
import io.genfin.money.port.precision.ScalePolicy;
import io.genfin.money.port.rounding.RoundingStrategy;

/**
 * The full set of policies {@link io.genfin.money.port.arithmetic.MoneyCalculator} consults for
 * every operation.
 */
public record ArithmeticPolicy(
    ScalePolicy scalePolicy,
    PrecisionPolicy precisionPolicy,
    RoundingStrategy roundingStrategy,
    OverflowPolicy overflowPolicy) {}
