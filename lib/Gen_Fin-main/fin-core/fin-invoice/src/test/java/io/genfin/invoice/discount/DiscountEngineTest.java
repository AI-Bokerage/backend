package io.genfin.invoice.discount;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscountEngineTest {

  @Test
  void fixedDiscountNeverExceedsBase() {
    Discount discount = Discount.fixed(Money.of("500.00", USD), "overpay-guard");

    Money result = DiscountEngines.standard().apply(discount, Money.of("100.00", USD));

    assertThat(result).isEqualTo(Money.of("100.00", USD));
  }

  @Test
  void percentageDiscountAppliesToBase() {
    Discount discount = Discount.percentage(Percentage.ofPercent(new BigDecimal("10")), "loyalty");

    Money result = DiscountEngines.standard().apply(discount, Money.of("200.00", USD));

    assertThat(result).isEqualTo(Money.of("20.00", USD));
  }
}
