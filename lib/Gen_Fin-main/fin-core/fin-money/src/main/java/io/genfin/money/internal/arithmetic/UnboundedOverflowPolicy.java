package io.genfin.money.internal.arithmetic;

import io.genfin.money.currency.Currency;
import io.genfin.money.port.arithmetic.OverflowPolicy;
import java.math.BigDecimal;

/** Permits any magnitude. The default — most applications never hit a real overflow ceiling. */
public final class UnboundedOverflowPolicy implements OverflowPolicy {

  @Override
  public void check(BigDecimal value, Currency currency) {
    // no-op: unbounded
  }
}
