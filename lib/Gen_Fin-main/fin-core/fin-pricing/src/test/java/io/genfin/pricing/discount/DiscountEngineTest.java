package io.genfin.pricing.discount;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.discount.DiscountValidator;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises every {@link Discount} shape plus the {@link DiscountPolicy}/{@link DiscountCalculator}
 * seam the Discount Engine pipeline stage depends on.
 */
class DiscountEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void percentageDiscountReducesTheRunningAmount() {
    Discount discount = PercentageDiscount.of("10% off", Percentage.ofPercent(BigDecimal.TEN));

    var adjustment = discount.applyTo(Money.of(100, USD), line(), context());

    assertThat(adjustment.amount()).isEqualTo(Money.of(-10, USD));
  }

  @Test
  void fixedDiscountAppliesRegardlessOfBaseSize() {
    Discount discount = FixedDiscount.of("$5 off", Money.of(5, USD));

    assertThat(discount.applyTo(Money.of(100, USD), line(), context()).amount())
        .isEqualTo(Money.of(-5, USD));
    assertThat(discount.applyTo(Money.of(6, USD), line(), context()).amount())
        .isEqualTo(Money.of(-5, USD));
  }

  @Test
  void tierDiscountPicksTheHighestReachedThreshold() {
    Discount discount =
        TierDiscount.of(
            "spend tiers",
            List.of(
                new TierDiscount.Tier(Money.of(0, USD), Percentage.ofPercent(BigDecimal.ZERO)),
                new TierDiscount.Tier(Money.of(100, USD), Percentage.ofPercent(BigDecimal.TEN)),
                new TierDiscount.Tier(
                    Money.of(500, USD), Percentage.ofPercent(BigDecimal.valueOf(20)))));

    assertThat(discount.applyTo(Money.of(50, USD), line(), context()).amount())
        .isEqualTo(Money.of(0, USD));
    assertThat(discount.applyTo(Money.of(200, USD), line(), context()).amount())
        .isEqualTo(Money.of(-20, USD));
    assertThat(discount.applyTo(Money.of(600, USD), line(), context()).amount())
        .isEqualTo(Money.of(-120, USD));
  }

  @Test
  void volumeDiscountPicksTheHighestReachedQuantity() {
    Discount discount =
        VolumeDiscount.of(
            "bulk tiers",
            List.of(
                new VolumeDiscount.Tier(1, Percentage.ofPercent(BigDecimal.ZERO)),
                new VolumeDiscount.Tier(10, Percentage.ofPercent(BigDecimal.valueOf(15)))));

    assertThat(
            discount
                .applyTo(
                    Money.of(100, USD), new PricingRequest.Line(CatalogId.generate(), 3), context())
                .amount())
        .isEqualTo(Money.of(0, USD));
    assertThat(
            discount
                .applyTo(
                    Money.of(100, USD),
                    new PricingRequest.Line(CatalogId.generate(), 20),
                    context())
                .amount())
        .isEqualTo(Money.of(-15, USD));
  }

  @Test
  void conditionalDiscountOnlyDelegatesWhenTheConditionMatches() {
    Discount delegate = FixedDiscount.of("$10 off", Money.of(10, USD));
    Discount matchesRegion =
        ConditionalDiscount.of(
            "APAC only",
            (candidateLine, ctx) -> "APAC".equals(ctx.attributes().find("region").orElse("")),
            delegate);

    PricingContext withRegion =
        PricingContext.start(
            PricingRequestId.generate(),
            List.of(line()),
            PricingAttributes.empty().with("region", "APAC"));
    PricingContext withoutRegion = context();

    assertThat(matchesRegion.applyTo(Money.of(100, USD), line(), withRegion).amount())
        .isEqualTo(Money.of(-10, USD));
    assertThat(matchesRegion.applyTo(Money.of(100, USD), line(), withoutRegion).amount())
        .isEqualTo(Money.of(0, USD));
  }

  @Test
  void discountCalculatorStacksDiscountsAgainstTheRunningAmount() {
    Price base =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
    List<Discount> discounts =
        List.of(
            PercentageDiscount.of("10% off", Percentage.ofPercent(BigDecimal.TEN)),
            FixedDiscount.of("$5 off", Money.of(5, USD)));

    Price discounted = DiscountCalculator.apply(base, discounts, line(), context());

    // 100 -> -10 (10% of 100) -> -5 fixed = net 85
    assertThat(discounted.amount()).isEqualTo(Money.of(85, USD));
    assertThat(discounted.breakdown().components()).hasSize(3);
  }

  @Test
  void ruleBasedStrategyOnlyContributesMatchingRules() {
    Discount discount = FixedDiscount.of("$1 off", Money.of(1, USD));
    DiscountRule rule =
        DiscountRule.of(
            "ALWAYS", discount, (candidateLine, ctx) -> ctx.attributes().find("tier").isPresent());
    DiscountPolicy policy =
        DiscountPolicies.of(List.of(DiscountStrategies.fromRules(List.of(rule))));

    Price base =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(50, USD))));
    PricingContext withTier =
        PricingContext.start(
            PricingRequestId.generate(),
            List.of(line()),
            PricingAttributes.empty().with("tier", "gold"));

    List<Price> applied = policy.apply(List.of(base), List.of(line()), withTier);
    List<Price> untouched = policy.apply(List.of(base), List.of(line()), context());

    assertThat(applied.get(0).amount()).isEqualTo(Money.of(49, USD));
    assertThat(untouched.get(0).amount()).isEqualTo(Money.of(50, USD));
  }

  @Test
  void defaultValidatorFlagsANegativeNetAmount() {
    DiscountValidator validator = DiscountValidators.standard();
    Price negative =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(-5, USD))));

    assertThat(validator.validate(negative, context())).hasSize(1);
    Price positive =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(5, USD))));
    assertThat(validator.validate(positive, context())).isEmpty();
  }

  private static PricingRequest.Line line() {
    return new PricingRequest.Line(CatalogId.generate(), 1);
  }

  private static PricingContext context() {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line()), PricingAttributes.empty());
  }
}
