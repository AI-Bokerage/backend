package io.genfin.tax.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.money.spi.MoneyExtensions;
import io.genfin.tax.internal.calculation.DelegatingTaxCalculator;
import io.genfin.tax.port.DomesticPolicy;
import io.genfin.tax.port.ExportPolicy;
import io.genfin.tax.port.ImportPolicy;
import io.genfin.tax.port.RegistrationPolicy;
import io.genfin.tax.port.TaxDecisionResolver;
import io.genfin.tax.port.TaxRateProvider;
import org.junit.jupiter.api.Test;

class TaxExtensionsTest {

  @Test
  void registerDefaultsRegistersEveryExtensionPoint() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    TaxExtensions.registerDefaults(registry);

    assertThat(registry.find(RegistrationPolicy.class)).isPresent();
    assertThat(registry.find(DomesticPolicy.class)).isPresent();
    assertThat(registry.find(ExportPolicy.class)).isPresent();
    assertThat(registry.find(ImportPolicy.class)).isPresent();
    assertThat(registry.find(TaxDecisionResolver.class)).isPresent();
    assertThat(registry.find(TaxRateProvider.class)).isPresent();
    assertThat(registry.find(TaxCalculator.class)).isPresent();
  }

  @Test
  void registeringTaxExtensionsBeforeMoneyExtensionsMakesTheTaxEngineWin() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    TaxExtensions.registerDefaults(registry);
    MoneyExtensions.registerDefaults(registry);

    // find() returns the first-registered implementation - TaxExtensions ran first, so its
    // DelegatingTaxCalculator (not MoneyExtensions' own NoOpTaxCalculator) is what callers get.
    assertThat(registry.find(TaxCalculator.class).orElseThrow())
        .isInstanceOf(DelegatingTaxCalculator.class);
  }
}
