package io.genfin.ledger.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.ledger.port.calculation.VarianceCalculator;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VarianceCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void computesTheSignedFractionalDriftFromTheBase() {
    VarianceCalculator calculator = VarianceCalculators.standard();

    Optional<Percentage> variance =
        calculator.varianceOf(Money.of("100.00", USD), Money.of("110.00", USD));

    assertThat(variance).isPresent();
    assertThat(variance.get().fraction()).isEqualByComparingTo(new BigDecimal("0.1"));
  }

  @Test
  void isEmptyWhenTheBaseIsZero() {
    VarianceCalculator calculator = VarianceCalculators.standard();

    Optional<Percentage> variance = calculator.varianceOf(Money.zero(USD), Money.of("10.00", USD));

    assertThat(variance).isEmpty();
  }
}
