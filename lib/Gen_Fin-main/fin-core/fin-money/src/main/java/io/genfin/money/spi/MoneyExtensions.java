package io.genfin.money.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.allocation.AllocationStrategies;
import io.genfin.money.conversion.CurrencyConverters;
import io.genfin.money.format.MoneyFormatters;
import io.genfin.money.internal.currency.IsoCurrencyCatalog;
import io.genfin.money.port.allocation.AllocationStrategy;
import io.genfin.money.port.conversion.ExchangeRateProvider;
import io.genfin.money.port.currency.CurrencyProvider;
import io.genfin.money.port.format.LocaleResolver;
import io.genfin.money.port.format.MoneyFormatter;
import io.genfin.money.port.format.MoneyParser;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.money.tax.TaxCalculators;

/**
 * Registers every default Money-engine extension into an {@link ExtensionRegistry} so downstream
 * modules discover currency sources, formatters, allocation, and tax wiring through one mechanism —
 * no {@code switch}/{@code instanceof} chains anywhere in the framework.
 */
public final class MoneyExtensions {

  private MoneyExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(CurrencyProvider.class, new IsoCurrencyCatalog());
    registry.register(AllocationStrategy.class, AllocationStrategies.largestRemainder());
    registry.register(MoneyFormatter.class, MoneyFormatters.standard());
    registry.register(MoneyParser.class, MoneyFormatters.standardParser());
    registry.register(LocaleResolver.class, MoneyFormatters.systemLocale());
    registry.register(ExchangeRateProvider.class, CurrencyConverters.inMemoryRates());
    registry.register(TaxCalculator.class, TaxCalculators.noOp());
  }
}
