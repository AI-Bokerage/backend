package io.genfin.pricing.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the default {@link CouponPolicy} (via {@link CouponPolicies}/{@link CouponStrategies})
 * and the default {@link CouponValidator} (via {@link CouponValidators}): a presented {@link
 * CouponCode} is only applied when registered and eligible, at most once, and a redemption that has
 * already exhausted its usage limit is flagged rather than silently accepted.
 */
class CouponEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void appliesARegisteredEligibleCode() {
    CouponRegistry registry = CouponRegistries.empty();
    CouponCode code = CouponCode.of("save10");
    CouponCampaign campaign = CouponCampaign.of(code, "Save 10", "Save 10");
    registry.register(campaign, tenPercentOff(), (line, context) -> true);
    CouponPolicy policy = CouponPolicies.of(registry, List.of(CouponStrategies.submitted()));

    PricingRequest.Line line = line();
    PricingContext context = contextWithCode(line, "SAVE10");

    List<CouponResult> results = policy.apply(List.of(basePrice()), List.of(line), context);

    assertThat(results.get(0).hasCoupon()).isTrue();
    assertThat(results.get(0).price().amount()).isEqualTo(Money.of(90, USD));
    assertThat(results.get(0).redemption().orElseThrow().code()).isEqualTo(code);
  }

  @Test
  void leavesThePriceUnchangedWhenNoCodeIsPresented() {
    CouponRegistry registry = CouponRegistries.empty();
    CouponPolicy policy = CouponPolicies.of(registry, List.of(CouponStrategies.submitted()));

    PricingRequest.Line line = line();
    List<CouponResult> results =
        policy.apply(List.of(basePrice()), List.of(line), contextFor(line));

    assertThat(results.get(0).hasCoupon()).isFalse();
  }

  @Test
  void leavesThePriceUnchangedWhenTheCodeIsUnregistered() {
    CouponRegistry registry = CouponRegistries.empty();
    CouponPolicy policy = CouponPolicies.of(registry, List.of(CouponStrategies.submitted()));

    PricingRequest.Line line = line();
    List<CouponResult> results =
        policy.apply(List.of(basePrice()), List.of(line), contextWithCode(line, "UNKNOWN"));

    assertThat(results.get(0).hasCoupon()).isFalse();
  }

  @Test
  void leavesThePriceUnchangedWhenEligibilityFails() {
    CouponRegistry registry = CouponRegistries.empty();
    CouponCode code = CouponCode.of("VIP10");
    CouponCampaign campaign = CouponCampaign.of(code, "VIP", "VIP only");
    registry.register(campaign, tenPercentOff(), (line, context) -> false);
    CouponPolicy policy = CouponPolicies.of(registry, List.of(CouponStrategies.submitted()));

    PricingRequest.Line line = line();
    List<CouponResult> results =
        policy.apply(List.of(basePrice()), List.of(line), contextWithCode(line, "VIP10"));

    assertThat(results.get(0).hasCoupon()).isFalse();
  }

  @Test
  void validatorFlagsARedemptionThatHasAlreadyExhaustedItsUsageLimit() {
    CouponRegistry registry = CouponRegistries.empty();
    CouponCode code = CouponCode.of("ONEUSE");
    CouponCampaign campaign =
        CouponCampaign.of(code, "One use", "One use only").withMaxRedemptions(1);
    registry.register(campaign, tenPercentOff(), (line, context) -> true);
    registry.recordRedemption(CouponRedemption.of(campaign, PricingRequestId.generate()));

    CouponValidator validator = CouponValidators.standard(registry);
    CouponResult result =
        CouponResult.applied(
            basePrice(), CouponRedemption.of(campaign, PricingRequestId.generate()));

    List<io.genfin.pricing.calculation.CalculationIssue> issues =
        validator.validate(result, contextFor(line()));

    assertThat(issues).anyMatch(issue -> "coupon-exhausted".equals(issue.ruleCode()));
  }

  private static Coupon tenPercentOff() {
    return (runningAmount, line, context) ->
        PriceAdjustment.of(
            PriceType.COUPON,
            PriceModifier.percentage(Percentage.ofPercent(BigDecimal.TEN).negate()),
            runningAmount,
            "test coupon");
  }

  private static Price basePrice() {
    return new Price(
        CatalogId.generate(),
        PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
  }

  private static PricingRequest.Line line() {
    return new PricingRequest.Line(CatalogId.generate(), 1);
  }

  private static PricingContext contextFor(PricingRequest.Line line) {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line), PricingAttributes.empty());
  }

  private static PricingContext contextWithCode(PricingRequest.Line line, String code) {
    return PricingContext.start(
        PricingRequestId.generate(),
        List.of(line),
        PricingAttributes.empty().with(CouponCode.ATTRIBUTE_KEY, code));
  }
}
