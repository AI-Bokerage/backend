package io.genfin.pricing.rule;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.rule.CommercialRuleEngine;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CommercialRuleEngines}, {@link CommercialRuleRegistries} and every {@link
 * ExampleCommercialRules} rule - the non-short-circuiting collection contract plus each rule's own
 * trigger condition.
 */
class CommercialRuleEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

  @Test
  void engineWithNoRulesRaisesNothing() {
    CommercialRuleEngine engine = CommercialRuleEngines.empty();

    assertThat(engine.evaluate(resultOf(price(50, 0, 0, 0)), context(), RuleContext.at(NOW)))
        .isEmpty();
  }

  @Test
  void engineCollectsResultsFromEveryRuleWithoutShortCircuiting() {
    CommercialRuleEngine engine = CommercialRuleEngines.of(ExampleCommercialRules.all());
    RuleContext ruleContext =
        RuleContext.at(NOW).withMinPrice(Money.of(1000, USD)).withMaxStackedCoupons(0);
    Price price = price(50, 0, 0, 20);

    List<RuleResult> results = engine.evaluate(resultOf(price), context(), ruleContext);

    assertThat(results)
        .extracting(RuleResult::ruleCode)
        .containsExactlyInAnyOrder("MIN_PRICE", "COUPON_STACK_LIMIT");
  }

  @Test
  void minimumPriceRuleFiresOnlyBelowTheThreshold() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.minimumPrice()));
    RuleContext limited = RuleContext.at(NOW).withMinPrice(Money.of(100, USD));

    assertThat(engine.evaluate(resultOf(price(50, 0, 0, 0)), context(), limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("MIN_PRICE");
    assertThat(engine.evaluate(resultOf(price(150, 0, 0, 0)), context(), limited)).isEmpty();
  }

  @Test
  void maximumDiscountRuleFiresWhenReductionExceedsThePercentage() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.maximumDiscount()));
    RuleContext limited =
        RuleContext.at(NOW).withMaxDiscountPercentage(Percentage.ofPercent(BigDecimal.TEN));

    // base 100, discount -20 => 20% > 10% limit
    assertThat(engine.evaluate(resultOf(price(100, -20, 0, 0)), context(), limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("MAX_DISCOUNT_PERCENTAGE");
    // base 100, discount -5 => 5% <= 10% limit
    assertThat(engine.evaluate(resultOf(price(100, -5, 0, 0)), context(), limited)).isEmpty();
  }

  @Test
  void couponStackingLimitRuleCountsCouponComponents() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.couponStackingLimit()));
    RuleContext limited = RuleContext.at(NOW).withMaxStackedCoupons(1);

    assertThat(engine.evaluate(resultOf(priceWithCoupons(2)), context(), limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("COUPON_STACK_LIMIT");
    assertThat(engine.evaluate(resultOf(priceWithCoupons(1)), context(), limited)).isEmpty();
  }

  @Test
  void creditLimitRuleSumsCreditAcrossPrices() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.creditLimit()));
    RuleContext limited = RuleContext.at(NOW).withCreditLimit(Money.of(30, USD));
    CalculationResult twoLines =
        new CalculationResult(List.of(price(100, 0, -20, 0), price(100, 0, -20, 0)), List.of());

    assertThat(engine.evaluate(twoLines, context(), limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("CREDIT_LIMIT");
  }

  @Test
  void regionalPricingRuleFiresOnlyWhenRegionAttributeIsDisallowed() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.regionalPricing()));
    RuleContext limited = RuleContext.at(NOW).withAllowedRegions(Set.of("APAC"));
    PricingContext withRegion = attributes("region", "EMEA");

    assertThat(engine.evaluate(resultOf(price(100, 0, 0, 0)), withRegion, limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("REGIONAL_PRICING");
    assertThat(engine.evaluate(resultOf(price(100, 0, 0, 0)), context(), limited)).isEmpty();
  }

  @Test
  void partnerPricingRuleFiresOnlyWhenPartnerTierAttributeIsDisallowed() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(List.of(ExampleCommercialRules.partnerPricing()));
    RuleContext limited = RuleContext.at(NOW).withAllowedPartnerTiers(Set.of("gold"));
    PricingContext withTier = attributes("partnerTier", "bronze");

    assertThat(engine.evaluate(resultOf(price(100, 0, 0, 0)), withTier, limited))
        .extracting(RuleResult::ruleCode)
        .containsExactly("PARTNER_PRICING");
    assertThat(engine.evaluate(resultOf(price(100, 0, 0, 0)), context(), limited)).isEmpty();
  }

  @Test
  void registryFactoryCollectsRulesFromAProvider() {
    CommercialRuleEngine engine =
        CommercialRuleEngines.of(
            CommercialRuleRegistries.withProvider(ExampleCommercialRules::all));

    assertThat(
            engine.evaluate(
                resultOf(price(50, 0, 0, 0)),
                context(),
                RuleContext.at(NOW).withMinPrice(Money.of(100, USD))))
        .extracting(RuleResult::ruleCode)
        .containsExactly("MIN_PRICE");
  }

  private static Price price(long base, long discount, long credit, long coupon) {
    return new Price(
        CatalogId.generate(),
        PriceBreakdown.of(
            PriceComponent.of(PriceType.BASE, "base", Money.of(base, USD)),
            PriceComponent.of(PriceType.DISCOUNT, "discount", Money.of(discount, USD)),
            PriceComponent.of(PriceType.CREDIT, "credit", Money.of(credit, USD)),
            PriceComponent.of(PriceType.COUPON, "coupon", Money.of(coupon, USD))));
  }

  private static Price priceWithCoupons(int count) {
    PriceComponent[] components = new PriceComponent[count + 1];
    components[0] = PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD));
    for (int i = 0; i < count; i++) {
      components[i + 1] = PriceComponent.of(PriceType.COUPON, "coupon-" + i, Money.of(-1, USD));
    }
    return new Price(CatalogId.generate(), new PriceBreakdown(List.of(components)));
  }

  private static CalculationResult resultOf(Price price) {
    return new CalculationResult(List.of(price), List.of());
  }

  private static PricingRequest.Line line() {
    return new PricingRequest.Line(CatalogId.generate(), 1);
  }

  private static PricingContext context() {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line()), PricingAttributes.empty());
  }

  private static PricingContext attributes(String key, String value) {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line()), PricingAttributes.empty().with(key, value));
  }
}
