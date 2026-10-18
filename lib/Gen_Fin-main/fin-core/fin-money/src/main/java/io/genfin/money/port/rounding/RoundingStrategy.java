package io.genfin.money.port.rounding;

import java.math.BigDecimal;

@FunctionalInterface
public interface RoundingStrategy {

  BigDecimal round(BigDecimal value, int scale);
}
