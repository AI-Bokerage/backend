package io.genfin.money.factory;

import io.genfin.money.config.MoneyConfiguration;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.math.BigDecimal;

/** Creates {@link Money} bound to a {@link MoneyConfiguration}'s arithmetic policy. */
public final class MoneyFactory {

  private final MoneyConfiguration configuration;

  private MoneyFactory(MoneyConfiguration configuration) {
    this.configuration = configuration;
  }

  public static MoneyFactory using(MoneyConfiguration configuration) {
    return new MoneyFactory(configuration);
  }

  public Money of(BigDecimal amount) {
    return of(amount, configuration.defaultCurrency());
  }

  public Money of(BigDecimal amount, Currency currency) {
    return Money.of(amount, currency, configuration.arithmeticPolicy());
  }

  public Money zero() {
    return Money.of(
        BigDecimal.ZERO, configuration.defaultCurrency(), configuration.arithmeticPolicy());
  }
}
