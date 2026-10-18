package io.genfin.money.port.arithmetic;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.arithmetic.ArithmeticPolicy;
import java.math.BigDecimal;

/**
 * The engine {@code Money} delegates every calculation to. Money itself contains no arithmetic —
 * only orchestration. Swap this to change rounding, precision, or overflow behavior framework-wide.
 */
public interface MoneyCalculator extends Extension {

  BigDecimal add(BigDecimal left, BigDecimal right, ArithmeticPolicy policy);

  BigDecimal subtract(BigDecimal left, BigDecimal right, ArithmeticPolicy policy);

  BigDecimal multiply(BigDecimal amount, BigDecimal factor, ArithmeticPolicy policy);

  BigDecimal divide(BigDecimal amount, BigDecimal divisor, ArithmeticPolicy policy);
}
