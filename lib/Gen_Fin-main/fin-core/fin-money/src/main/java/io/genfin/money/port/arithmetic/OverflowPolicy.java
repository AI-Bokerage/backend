package io.genfin.money.port.arithmetic;

import io.genfin.money.currency.Currency;
import java.math.BigDecimal;

/** Guards against unrepresentable/unreasonable monetary magnitudes after a calculation. */
public interface OverflowPolicy {

  void check(BigDecimal value, Currency currency);
}
