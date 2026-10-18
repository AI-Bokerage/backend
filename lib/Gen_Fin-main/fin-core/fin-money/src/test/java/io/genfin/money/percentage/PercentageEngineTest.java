package io.genfin.money.percentage;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PercentageEngineTest {

  @Test
  void percentageFactoriesAgreeOnFraction() {
    assertThat(Percentage.ofPercent(new BigDecimal("15")).fraction()).isEqualByComparingTo("0.15");
    assertThat(Percentage.ofBasisPoints(150).fraction()).isEqualByComparingTo("0.015");
    assertThat(Percentage.ofFraction(new BigDecimal("0.15")).asPercent())
        .isEqualByComparingTo("15");
  }

  @Test
  void discountReducesPrice() {
    Money discounted =
        DiscountCalculator.apply(
            Money.of("200.00", USD), Percentage.ofPercent(new BigDecimal("25")));

    assertThat(discounted).isEqualTo(Money.of("150.00", USD));
  }

  @Test
  void markupIncreasesCost() {
    Money marked =
        MarkupCalculator.apply(Money.of("100.00", USD), Percentage.ofPercent(new BigDecimal("20")));

    assertThat(marked).isEqualTo(Money.of("120.00", USD));
  }

  @Test
  void marginComputesSellingPriceFromCost() {
    Money price =
        MarginCalculator.priceForMargin(
            Money.of("80.00", USD), Percentage.ofPercent(new BigDecimal("20")));

    assertThat(price).isEqualTo(Money.of("100.00", USD));
  }

  @Test
  void compoundDiscountsMatchSequentialApplication() {
    Percentage combined =
        CompoundPercentage.compoundDiscounts(
            List.of(
                Percentage.ofPercent(new BigDecimal("10")),
                Percentage.ofPercent(new BigDecimal("20"))));

    Money sequential =
        DiscountCalculator.apply(
            DiscountCalculator.apply(
                Money.of("100.00", USD), Percentage.ofPercent(new BigDecimal("10"))),
            Percentage.ofPercent(new BigDecimal("20")));
    Money combinedResult = DiscountCalculator.apply(Money.of("100.00", USD), combined);

    assertThat(combinedResult).isEqualTo(sequential);
  }

  @Test
  void compoundMarkupsMatchSequentialApplication() {
    Percentage combined =
        CompoundPercentage.compoundMarkups(
            List.of(
                Percentage.ofPercent(new BigDecimal("10")),
                Percentage.ofPercent(new BigDecimal("5"))));

    Money sequential =
        MarkupCalculator.apply(
            MarkupCalculator.apply(
                Money.of("100.00", USD), Percentage.ofPercent(new BigDecimal("10"))),
            Percentage.ofPercent(new BigDecimal("5")));
    Money combinedResult = MarkupCalculator.apply(Money.of("100.00", USD), combined);

    assertThat(combinedResult).isEqualTo(sequential);
  }
}
