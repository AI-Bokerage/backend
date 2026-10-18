package io.genfin.money.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.port.allocation.AllocationStrategy;
import io.genfin.money.port.conversion.ExchangeRateProvider;
import io.genfin.money.port.currency.CurrencyProvider;
import io.genfin.money.port.format.MoneyFormatter;
import io.genfin.money.port.tax.TaxCalculator;
import org.junit.jupiter.api.Test;

class MoneyExtensionsTest {

  @Test
  void allDefaultMoneyExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    MoneyExtensions.registerDefaults(registry);

    assertThat(registry.find(CurrencyProvider.class)).isPresent();
    assertThat(registry.find(AllocationStrategy.class)).isPresent();
    assertThat(registry.find(MoneyFormatter.class)).isPresent();
    assertThat(registry.find(ExchangeRateProvider.class)).isPresent();
    assertThat(registry.find(TaxCalculator.class)).isPresent();
  }
}
