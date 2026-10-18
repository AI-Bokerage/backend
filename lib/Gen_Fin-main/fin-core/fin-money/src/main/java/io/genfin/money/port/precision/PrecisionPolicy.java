package io.genfin.money.port.precision;

import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Governs the precision of intermediate calculations (multiply/divide), independent of final
 * currency scale.
 */
public interface PrecisionPolicy {

  MathContext mathContext();

  RoundingMode roundingMode();
}
