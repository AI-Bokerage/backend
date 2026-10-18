package io.genfin.autoconfigure.money;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.money.port.conversion.ExchangeRateProvider;
import io.genfin.money.port.currency.CurrencyProvider;
import io.genfin.money.spi.MoneyExtensions;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers every default fin-money extension into the shared {@link ExtensionRegistry}. Any
 * application-supplied {@link CurrencyProvider}/{@link ExchangeRateProvider} bean is registered
 * first, so it wins over fin-money's own defaults (first-registered wins on lookup).
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class MoneyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "moneyExtensionsRegistered")
  public Boolean moneyExtensionsRegistered(
      ExtensionRegistry registry,
      ObjectProvider<List<CurrencyProvider>> currencyProviders,
      ObjectProvider<List<ExchangeRateProvider>> exchangeRateProviders) {
    currencyProviders
        .getIfAvailable(List::of)
        .forEach(provider -> registry.register(CurrencyProvider.class, provider));
    exchangeRateProviders
        .getIfAvailable(List::of)
        .forEach(provider -> registry.register(ExchangeRateProvider.class, provider));
    MoneyExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
