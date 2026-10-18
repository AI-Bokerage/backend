package io.genfin.pricing.price;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.allocation.AllocationStrategies;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.CatalogId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceModelTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void breakdownNetsBaseAndDiscountComponents() {
    PriceComponent base = PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD));
    PriceAdjustment discount =
        PriceAdjustment.of(
            PriceType.DISCOUNT,
            PriceModifier.percentage(Percentage.ofPercent(BigDecimal.TEN).negate()),
            Money.of(100, USD),
            "loyalty discount");

    PriceBreakdown breakdown = PriceBreakdown.of(base, discount.toComponent("loyalty discount"));

    assertThat(breakdown.netAmount()).isEqualTo(Money.of(90, USD));
    assertThat(PriceSummary.of(breakdown).adjustmentAmount()).isEqualTo(Money.of(-10, USD));
  }

  @Test
  void allocationSplitsTotalWithoutLosingAnyMinorUnit() {
    CatalogId a = CatalogId.generate();
    CatalogId b = CatalogId.generate();

    List<PriceAllocation> allocations =
        PriceAllocation.allocate(
            Money.of("10.01", USD),
            List.of(a, b),
            List.of(BigDecimal.ONE, BigDecimal.ONE),
            AllocationStrategies.largestRemainder());

    Money sum = allocations.get(0).amount().add(allocations.get(1).amount());
    assertThat(sum).isEqualTo(Money.of("10.01", USD));
  }

  @Test
  void fixedModifierIgnoresBaseAmount() {
    PriceModifier fixed = PriceModifier.fixed(Money.of(5, USD));
    assertThat(fixed.applyTo(Money.of(999, USD))).isEqualTo(Money.of(5, USD));
  }
}
