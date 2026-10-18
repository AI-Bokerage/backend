package io.genfin.money.config;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.factory.CalculatorFactory;
import io.genfin.money.factory.MoneyFactory;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyConfigurationTest {

  @Test
  void standardConfigurationHasExplicitDefaults() {
    MoneyConfiguration configuration = MoneyConfigurations.standard(USD);

    assertThat(configuration.defaultCurrency()).isEqualTo(USD);
    assertThat(configuration.arithmeticPolicy()).isNotNull();
    assertThat(configuration.taxConfiguration().taxEnabled()).isFalse();
  }

  @Test
  void moneyFactoryUsesConfiguredDefaultCurrency() {
    MoneyFactory factory = MoneyFactory.using(MoneyConfigurations.standard(USD));

    assertThat(factory.of(new BigDecimal("10.00"))).isEqualTo(Money.of("10.00", USD));
    assertThat(factory.zero()).isEqualTo(Money.zero(USD));
  }

  @Test
  void calculatorFactoryBuildsWorkingCalculator() {
    var calculator = CalculatorFactory.forCurrency(USD);
    var policy = MoneyConfigurations.standard(USD).arithmeticPolicy();

    assertThat(calculator.add(new BigDecimal("1.005"), new BigDecimal("1.005"), policy))
        .isEqualByComparingTo("2.01");
  }
}
