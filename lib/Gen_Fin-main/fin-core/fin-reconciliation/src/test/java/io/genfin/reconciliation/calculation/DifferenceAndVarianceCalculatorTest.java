package io.genfin.reconciliation.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.reconciliation.port.calculation.DifferenceCalculator;
import io.genfin.reconciliation.port.calculation.VarianceCalculator;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DifferenceAndVarianceCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private static final DifferenceCalculator DIFFERENCE = DifferenceCalculators.standard();
  private static final VarianceCalculator VARIANCE = VarianceCalculators.standard();

  @Test
  void computesSignedAndAbsoluteDifference() {
    Money left = Money.of(new BigDecimal("100.00"), USD);
    Money right = Money.of(new BigDecimal("99.50"), USD);

    AmountDifference result = DIFFERENCE.difference(left, right);

    assertThat(result.signed()).isEqualTo(Money.of(new BigDecimal("0.50"), USD));
    assertThat(result.absolute()).isEqualTo(Money.of(new BigDecimal("0.50"), USD));
    assertThat(result.isZero()).isFalse();
  }

  @Test
  void rejectsDifferentCurrencies() {
    Currency eur =
        CurrencyFactory.newCurrency()
            .code("EUR")
            .symbol("€")
            .displayName("Euro")
            .fractionDigits(2)
            .build();
    Money left = Money.of(new BigDecimal("100.00"), USD);
    Money right = Money.of(new BigDecimal("100.00"), eur);

    assertThatThrownBy(() -> DIFFERENCE.difference(left, right))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void computesVarianceAsAFractionOfTheBase() {
    Money base = Money.of(new BigDecimal("100.00"), USD);
    Money actual = Money.of(new BigDecimal("110.00"), USD);

    Optional<Percentage> variance = VARIANCE.varianceOf(base, actual);

    assertThat(variance).isPresent();
    assertThat(variance.get().fraction()).isEqualByComparingTo(new BigDecimal("0.1"));
  }

  @Test
  void varianceIsNotApplicableAgainstAZeroBase() {
    Money base = Money.zero(USD);
    Money actual = Money.of(new BigDecimal("10.00"), USD);

    assertThat(VARIANCE.varianceOf(base, actual)).isEmpty();
  }
}
