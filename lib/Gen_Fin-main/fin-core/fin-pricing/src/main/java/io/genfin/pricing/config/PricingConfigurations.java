package io.genfin.pricing.config;

import io.genfin.pricing.calculation.PricingPolicies;
import io.genfin.pricing.catalog.CatalogRegistries;
import io.genfin.pricing.coupon.CouponPolicies;
import io.genfin.pricing.coupon.CouponRegistries;
import io.genfin.pricing.credit.CreditPolicies;
import io.genfin.pricing.credit.CreditRegistries;
import io.genfin.pricing.discount.DiscountPolicies;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.promotion.PromotionPolicies;
import io.genfin.pricing.quote.QuotePolicies;
import io.genfin.pricing.rule.CommercialRuleEngines;
import io.genfin.pricing.rule.CommercialRuleRegistries;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.tax.TaxExtensionPoint;
import io.genfin.pricing.validation.Validators;
import java.time.Instant;

/**
 * Factory for the default {@link PricingConfiguration}. Every sub-configuration starts from its own
 * "empty"/"standard" default - Gen-Fin ships no built-in catalog items, discount/promotion/coupon
 * strategies, credit wallets or commercial rules, so a consuming application registers its own
 * before pricing anything. Mirrors {@code io.genfin.ledger.config.LedgerConfigurations}.
 */
public final class PricingConfigurations {

  private PricingConfigurations() {}

  public static PricingConfiguration standard() {
    return PricingConfiguration.builder()
        .pricingPolicy(PricingPolicies.empty())
        .lifecycleProvider(PricingLifecycles.standard())
        .taxPlaceholder(TaxExtensionPoint.noOp())
        .pricingValidator(Validators.standard())
        .catalogConfiguration(
            CatalogConfiguration.builder().catalogRegistry(CatalogRegistries.empty()).build())
        .discountConfiguration(
            DiscountConfiguration.builder().discountPolicy(DiscountPolicies.empty()).build())
        .promotionConfiguration(
            PromotionConfiguration.builder().promotionPolicy(PromotionPolicies.empty()).build())
        .couponConfiguration(
            CouponConfiguration.builder()
                .couponRegistry(CouponRegistries.empty())
                .couponPolicy(CouponPolicies.empty())
                .build())
        .creditConfiguration(
            CreditConfiguration.builder()
                .creditRegistry(CreditRegistries.empty())
                .creditPolicy(CreditPolicies.empty())
                .build())
        .quoteConfiguration(
            QuoteConfiguration.builder().quotePolicy(QuotePolicies.standard()).build())
        .commercialRuleConfiguration(
            CommercialRuleConfiguration.builder()
                .commercialRuleRegistry(CommercialRuleRegistries.empty())
                .commercialRuleEngine(CommercialRuleEngines.empty())
                .ruleContext(RuleContext.at(Instant.now()))
                .build())
        .build();
  }
}
