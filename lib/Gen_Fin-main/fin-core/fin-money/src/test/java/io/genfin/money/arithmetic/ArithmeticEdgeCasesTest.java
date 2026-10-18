package io.genfin.money.arithmetic;

import static io.genfin.money.support.TestCurrencies.BHD;
import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.exception.MoneyOverflowException;
import io.genfin.money.internal.arithmetic.MaxDigitsOverflowPolicy;
import io.genfin.money.money.Money;
import io.genfin.money.precision.PrecisionPolicies;
import io.genfin.money.rounding.RoundingStrategies;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ArithmeticEdgeCasesTest {

  @Test
  void threeFractionDigitCurrencyScalesCorrectly() {
    assertThat(Money.of("1.2345", BHD).amount()).isEqualByComparingTo("1.235");
  }

  @Test
  void overflowPolicyRejectsTooManySignificantDigits() {
    ArithmeticPolicy strict =
        new ArithmeticPolicy(
            PrecisionPolicies.currencyFraction(),
            PrecisionPolicies.standard(),
            RoundingStrategies.HALF_UP,
            new MaxDigitsOverflowPolicy(5));

    assertThatThrownBy(() -> Money.of(new BigDecimal("123456.78"), USD, strict))
        .isInstanceOf(MoneyOverflowException.class);
  }

  @Test
  void unboundedPolicyAllowsVeryLargeAmounts() {
    Money huge = Money.of(new BigDecimal("123456789012345.67"), USD, ArithmeticPolicies.standard());

    assertThat(huge.amount()).isEqualByComparingTo("123456789012345.67");
  }

  @Test
  void zeroDivisorIsRejectedByBigDecimalSemantics() {
    assertThatThrownBy(() -> Money.of("10.00", USD).divide(BigDecimal.ZERO))
        .isInstanceOf(ArithmeticException.class);
  }
}
