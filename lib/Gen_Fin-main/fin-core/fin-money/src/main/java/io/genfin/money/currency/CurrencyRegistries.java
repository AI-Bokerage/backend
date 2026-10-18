package io.genfin.money.currency;

import io.genfin.money.internal.currency.DefaultCurrencyRegistry;
import io.genfin.money.internal.currency.IsoCurrencyCatalog;
import io.genfin.money.port.currency.CurrencyProvider;
import io.genfin.money.port.currency.CurrencyRegistry;

/** Factory for {@link CurrencyRegistry} instances. Consumers must obtain registries here. */
public final class CurrencyRegistries {

  private CurrencyRegistries() {}

  public static CurrencyRegistry empty() {
    return new DefaultCurrencyRegistry();
  }

  public static CurrencyRegistry iso() {
    return withProvider(new IsoCurrencyCatalog());
  }

  public static CurrencyRegistry withProvider(CurrencyProvider provider) {
    CurrencyRegistry registry = new DefaultCurrencyRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }
}
