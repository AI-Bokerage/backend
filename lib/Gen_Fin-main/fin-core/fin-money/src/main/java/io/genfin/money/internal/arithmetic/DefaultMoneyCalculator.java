package io.genfin.money.internal.arithmetic;

import io.genfin.money.arithmetic.ArithmeticPolicy;
import io.genfin.money.currency.Currency;
import io.genfin.money.port.arithmetic.MoneyCalculator;
import java.math.BigDecimal;

public final class DefaultMoneyCalculator implements MoneyCalculator {

  private final Currency currency;

  public DefaultMoneyCalculator(Currency currency) {
    this.currency = currency;
  }

  @Override
  public BigDecimal add(BigDecimal left, BigDecimal right, ArithmeticPolicy policy) {
    return finish(left.add(right, policy.precisionPolicy().mathContext()), policy);
  }

  @Override
  public BigDecimal subtract(BigDecimal left, BigDecimal right, ArithmeticPolicy policy) {
    return finish(left.subtract(right, policy.precisionPolicy().mathContext()), policy);
  }

  @Override
  public BigDecimal multiply(BigDecimal amount, BigDecimal factor, ArithmeticPolicy policy) {
    return finish(amount.multiply(factor, policy.precisionPolicy().mathContext()), policy);
  }

  @Override
  public BigDecimal divide(BigDecimal amount, BigDecimal divisor, ArithmeticPolicy policy) {
    return finish(amount.divide(divisor, policy.precisionPolicy().mathContext()), policy);
  }

  private BigDecimal finish(BigDecimal raw, ArithmeticPolicy policy) {
    int scale = policy.scalePolicy().scaleFor(currency);
    BigDecimal scaled = policy.roundingStrategy().round(raw, scale);
    policy.overflowPolicy().check(scaled, currency);
    return scaled;
  }
}
