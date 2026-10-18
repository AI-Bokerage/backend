package io.genfin.pricing.rule;

import io.genfin.pricing.internal.rule.CouponStackingLimitRule;
import io.genfin.pricing.internal.rule.CreditLimitRule;
import io.genfin.pricing.internal.rule.MaximumDiscountRule;
import io.genfin.pricing.internal.rule.MinimumPriceRule;
import io.genfin.pricing.internal.rule.PartnerPricingRule;
import io.genfin.pricing.internal.rule.PromotionStackingLimitRule;
import io.genfin.pricing.internal.rule.RegionalPricingRule;
import io.genfin.pricing.port.rule.CommercialRule;
import java.util.List;

/**
 * Illustrative, opt-in {@link CommercialRule}s covering common commercial checks: minimum price,
 * maximum discount, coupon/promotion stacking limits, credit limit, regional pricing, partner
 * pricing. None of these are wired into {@link CommercialRuleEngines} or {@link
 * CommercialRuleRegistries} by default - the whole point of the Commercial Rule Engine is that an
 * application registers only the rules (and thresholds, via {@link RuleContext}) that its business
 * actually needs. Mirrors {@code io.genfin.reconciliation.rule.ReconciliationRules}, but as
 * examples rather than defaults, since commercial rules are inherently business-specific.
 */
public final class ExampleCommercialRules {

  private ExampleCommercialRules() {}

  public static CommercialRule minimumPrice() {
    return new MinimumPriceRule();
  }

  public static CommercialRule maximumDiscount() {
    return new MaximumDiscountRule();
  }

  public static CommercialRule couponStackingLimit() {
    return new CouponStackingLimitRule();
  }

  public static CommercialRule promotionStackingLimit() {
    return new PromotionStackingLimitRule();
  }

  public static CommercialRule creditLimit() {
    return new CreditLimitRule();
  }

  public static CommercialRule regionalPricing() {
    return new RegionalPricingRule();
  }

  public static CommercialRule partnerPricing() {
    return new PartnerPricingRule();
  }

  /** Every illustrative rule, for applications that want the full example set as a start. */
  public static List<CommercialRule> all() {
    return List.of(
        minimumPrice(),
        maximumDiscount(),
        couponStackingLimit(),
        promotionStackingLimit(),
        creditLimit(),
        regionalPricing(),
        partnerPricing());
  }
}
