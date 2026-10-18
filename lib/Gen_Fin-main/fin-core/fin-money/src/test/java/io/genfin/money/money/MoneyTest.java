package io.genfin.money.money;

import static io.genfin.money.support.TestCurrencies.EUR;
import static io.genfin.money.support.TestCurrencies.JPY;
import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void addAndSubtractWithinSameCurrency() {
    Money ten = Money.of("10.00", USD);
    Money three = Money.of("3.00", USD);

    assertThat(ten.add(three)).isEqualTo(Money.of("13.00", USD));
    assertThat(ten.subtract(three)).isEqualTo(Money.of("7.00", USD));
  }

  @Test
  void crossCurrencyOperationsThrowWithoutExplicitConversion() {
    Money usd = Money.of("10.00", USD);
    Money eur = Money.of("10.00", EUR);

    assertThatThrownBy(() -> usd.add(eur)).isInstanceOf(CurrencyMismatchException.class);
    assertThatThrownBy(() -> usd.compareTo(eur)).isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void multiplyAndDivideRoundToCurrencyScale() {
    Money price = Money.of("9.99", USD);

    assertThat(price.multiply(BigDecimal.valueOf(3))).isEqualTo(Money.of("29.97", USD));
    assertThat(Money.of("10.00", USD).divide(BigDecimal.valueOf(3)))
        .isEqualTo(Money.of("3.33", USD));
  }

  @Test
  void zeroFractionDigitCurrencyNeverGetsDecimals() {
    Money yen = Money.of("101.6", JPY);

    assertThat(yen.amount()).isEqualByComparingTo("102");
  }

  @Test
  void negateAbsMinMax() {
    Money positive = Money.of("5.00", USD);
    Money negative = Money.of("-5.00", USD);

    assertThat(positive.negate()).isEqualTo(negative);
    assertThat(negative.abs()).isEqualTo(positive);
    assertThat(positive.min(negative)).isEqualTo(negative);
    assertThat(positive.max(negative)).isEqualTo(positive);
  }

  @Test
  void zeroPositiveNegativePredicates() {
    assertThat(Money.zero(USD).isZero()).isTrue();
    assertThat(Money.of("1", USD).isPositive()).isTrue();
    assertThat(Money.of("-1", USD).isNegative()).isTrue();
  }

  @Test
  void equalityIgnoresTrailingZerosButNotCurrency() {
    assertThat(Money.of(new BigDecimal("10.00"), USD))
        .isEqualTo(Money.of(new BigDecimal("10.0"), USD));
    assertThat(Money.of("10.00", USD)).isNotEqualTo(Money.of("10.00", EUR));
  }

  @Test
  void veryLargeAndVerySmallValuesRoundTripThroughArithmetic() {
    Money huge = Money.of("999999999999999.99", USD);
    Money tiny = Money.of("0.01", USD);

    assertThat(huge.add(tiny)).isEqualTo(Money.of("1000000000000000.00", USD));
  }
}
