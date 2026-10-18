package io.genfin.pricing.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.port.promotion.PromotionStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PromotionCalculator}'s priority-based winner selection, {@link
 * DefaultPromotionPolicy}'s (via {@link PromotionPolicies}) per-line orchestration, and the
 * illustrative example shapes in {@link PromotionStrategies}.
 */
class PromotionEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void calculatorPicksTheHighestPriorityEligibleCandidate() {
    PromotionCampaign lowPriority = PromotionCampaign.of("Low", "Low", PromotionPriority.LOW);
    PromotionCampaign highPriority = PromotionCampaign.of("High", "High", PromotionPriority.HIGH);
    Promotion tenOff = flatPercentage(Percentage.ofPercent(BigDecimal.TEN));
    Promotion twentyOff = flatPercentage(Percentage.ofPercent(BigDecimal.valueOf(20)));
    List<PromotionRule> candidates =
        List.of(
            PromotionRule.always(lowPriority, tenOff),
            PromotionRule.always(highPriority, twentyOff));

    PromotionResult result = PromotionCalculator.apply(basePrice(), candidates, line(), context());

    assertThat(result.hasPromotion()).isTrue();
    assertThat(result.appliedCampaign()).contains(highPriority);
    assertThat(result.price().amount()).isEqualTo(Money.of(80, USD));
  }

  @Test
  void calculatorLeavesThePriceUnchangedWhenNoCandidateIsEligible() {
    PromotionCampaign campaign = PromotionCampaign.of("Never", "Never", PromotionPriority.HIGH);
    PromotionRule neverEligible =
        PromotionRule.of(
            campaign, flatPercentage(Percentage.ofPercent(BigDecimal.TEN)), (l, c) -> false);

    PromotionResult result =
        PromotionCalculator.apply(basePrice(), List.of(neverEligible), line(), context());

    assertThat(result.hasPromotion()).isFalse();
    assertThat(result.price().amount()).isEqualTo(Money.of(100, USD));
  }

  @Test
  void buyOneGetOneOnlyAppliesAtQuantityTwoOrMore() {
    PromotionStrategy bogo =
        PromotionStrategies.buyOneGetOne(
            "BOGO", PromotionPriority.STANDARD, Percentage.ofPercent(BigDecimal.valueOf(50)));
    PromotionPolicy policy = PromotionPolicies.of(List.of(bogo));

    PricingRequest.Line singleUnit = new PricingRequest.Line(CatalogId.generate(), 1);
    PricingRequest.Line twoUnits = new PricingRequest.Line(CatalogId.generate(), 2);

    List<PromotionResult> forSingle =
        policy.apply(List.of(basePrice()), List.of(singleUnit), contextFor(singleUnit));
    List<PromotionResult> forTwo =
        policy.apply(List.of(basePrice()), List.of(twoUnits), contextFor(twoUnits));

    assertThat(forSingle.get(0).hasPromotion()).isFalse();
    assertThat(forTwo.get(0).hasPromotion()).isTrue();
    assertThat(forTwo.get(0).price().amount()).isEqualTo(Money.of(50, USD));
  }

  @Test
  void flashSaleOnlyAppliesWithinItsActiveWindow() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-01-02T00:00:00Z");
    PromotionStrategy flashSale =
        PromotionStrategies.flashSale(
            "New Year Flash Sale",
            PromotionPriority.HIGH,
            start,
            end,
            Percentage.ofPercent(BigDecimal.valueOf(25)),
            () -> start.plus(1, ChronoUnit.HOURS));
    PromotionPolicy duringWindow = PromotionPolicies.of(List.of(flashSale));
    PromotionStrategy expiredFlashSale =
        PromotionStrategies.flashSale(
            "New Year Flash Sale",
            PromotionPriority.HIGH,
            start,
            end,
            Percentage.ofPercent(BigDecimal.valueOf(25)),
            () -> end.plus(1, ChronoUnit.HOURS));
    PromotionPolicy afterWindow = PromotionPolicies.of(List.of(expiredFlashSale));

    List<PromotionResult> active =
        duringWindow.apply(List.of(basePrice()), List.of(line()), context());
    List<PromotionResult> expired =
        afterWindow.apply(List.of(basePrice()), List.of(line()), context());

    assertThat(active.get(0).hasPromotion()).isTrue();
    assertThat(active.get(0).price().amount()).isEqualTo(Money.of(75, USD));
    assertThat(expired.get(0).hasPromotion()).isFalse();
  }

  @Test
  void bundleDiscountOnlyAppliesWhenTheCompanionItemIsAlsoInTheRequest() {
    CatalogId companion = CatalogId.generate();
    PromotionStrategy bundle =
        PromotionStrategies.bundleDiscount(
            "Bundle", PromotionPriority.STANDARD, companion, Percentage.ofPercent(BigDecimal.TEN));
    PromotionPolicy policy = PromotionPolicies.of(List.of(bundle));

    PricingRequest.Line withCompanion = line();
    PricingContext bundledContext =
        PricingContext.start(
            PricingRequestId.generate(),
            List.of(withCompanion, new PricingRequest.Line(companion, 1)),
            PricingAttributes.empty());

    List<PromotionResult> bundled =
        policy.apply(List.of(basePrice()), List.of(withCompanion), bundledContext);
    List<PromotionResult> alone =
        policy.apply(List.of(basePrice()), List.of(withCompanion), context());

    assertThat(bundled.get(0).hasPromotion()).isTrue();
    assertThat(alone.get(0).hasPromotion()).isFalse();
  }

  private static Promotion flatPercentage(Percentage percentage) {
    return (runningAmount, l, c) ->
        io.genfin.pricing.price.PriceAdjustment.of(
            PriceType.PROMOTION,
            io.genfin.pricing.price.PriceModifier.percentage(percentage.negate()),
            runningAmount,
            "test promotion");
  }

  private static Price basePrice() {
    return new Price(
        CatalogId.generate(),
        PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
  }

  private static PricingRequest.Line line() {
    return new PricingRequest.Line(CatalogId.generate(), 1);
  }

  private static PricingContext context() {
    return contextFor(line());
  }

  private static PricingContext contextFor(PricingRequest.Line line) {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line), PricingAttributes.empty());
  }
}
