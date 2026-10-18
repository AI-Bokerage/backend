package io.genfin.invoice.config;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvoiceConfigurationTest {

  @Test
  void standardConfigurationHasExplicitDefaultsForEveryPolicy() {
    InvoiceConfiguration configuration = InvoiceConfigurations.standard(USD);

    assertThat(configuration.defaultCurrency()).isEqualTo(USD);
    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.validator()).isNotNull();
    assertThat(configuration.calculationPolicy()).isNotNull();
    assertThat(configuration.discountEngine()).isNotNull();
    assertThat(configuration.adjustmentEngine()).isNotNull();
    assertThat(configuration.numberGenerator()).isNotNull();
    assertThat(configuration.formattingContext()).isNotNull();
  }
}
