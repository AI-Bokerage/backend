package io.genfin.pricing.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.calculation.PricingEngines;
import io.genfin.pricing.calculation.PricingPolicies;
import io.genfin.pricing.catalog.CatalogRegistries;
import io.genfin.pricing.coupon.CouponPolicies;
import io.genfin.pricing.coupon.CouponRegistries;
import io.genfin.pricing.coupon.CouponStrategies;
import io.genfin.pricing.coupon.CouponValidator;
import io.genfin.pricing.coupon.CouponValidators;
import io.genfin.pricing.credit.CreditCalculator;
import io.genfin.pricing.credit.CreditCalculators;
import io.genfin.pricing.credit.CreditPolicies;
import io.genfin.pricing.credit.CreditRegistries;
import io.genfin.pricing.credit.CreditStrategies;
import io.genfin.pricing.credit.CreditValidator;
import io.genfin.pricing.credit.CreditValidators;
import io.genfin.pricing.discount.DiscountPolicies;
import io.genfin.pricing.discount.DiscountValidators;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.pipeline.PricingPipelines;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.port.coupon.CouponStrategy;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.port.credit.CreditStrategy;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.discount.DiscountValidator;
import io.genfin.pricing.port.lifecycle.PricingLifecycleProvider;
import io.genfin.pricing.port.pipeline.PricingPipeline;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.port.quote.QuotePolicy;
import io.genfin.pricing.port.rule.CommercialRuleEngine;
import io.genfin.pricing.port.rule.CommercialRuleRegistry;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.port.validation.PricingValidator;
import io.genfin.pricing.promotion.PromotionPolicies;
import io.genfin.pricing.quote.QuotePolicies;
import io.genfin.pricing.rule.CommercialRuleEngines;
import io.genfin.pricing.rule.CommercialRuleRegistries;
import io.genfin.pricing.strategy.PricingStrategies;
import io.genfin.pricing.tax.TaxExtensionPoint;
import io.genfin.pricing.tax.TaxPlaceholder;
import io.genfin.pricing.validation.ValidationRule;
import io.genfin.pricing.validation.Validators;

/**
 * Registers every default fin-pricing extension so downstream code discovers them through one
 * mechanism. Mirrors {@code io.genfin.ledger.spi.LedgerExtensions}.
 *
 * <p>Catalog items, coupons, credit wallets and commercial rules have no built-in catalog - Gen-Fin
 * never hardcodes actual products, coupon data or business rules - so {@link CatalogRegistry},
 * {@link CouponRegistry}, {@link CreditRegistry} and {@link CommercialRuleRegistry} are registered
 * empty; a consuming application populates them or overrides them entirely via {@link
 * ExtensionRegistry#register}. Likewise {@link PricingPolicy}, {@link DiscountPolicy}, {@link
 * PromotionPolicy}, {@link CouponPolicy} and {@link CreditPolicy} start empty/without strategies -
 * fin-pricing ships no built-in prices, discounts, promotions, coupon rules or credit rules.
 */
public final class PricingExtensions {

  private PricingExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(PricingLifecycleProvider.class, PricingLifecycles.standard());

    CatalogRegistry catalogRegistry = CatalogRegistries.empty();
    registry.register(CatalogRegistry.class, catalogRegistry);

    registry.register(PricingPolicy.class, PricingPolicies.empty());
    registry.register(PricingConflictStrategy.class, PricingStrategies.priorityOrder());

    registerDiscount(registry);
    registerPromotion(registry);
    CouponRegistry couponRegistry = registerCoupon(registry);
    CreditRegistry creditRegistry = registerCredit(registry);

    registry.register(QuotePolicy.class, QuotePolicies.standard());

    CommercialRuleRegistry ruleRegistry = CommercialRuleRegistries.empty();
    registry.register(CommercialRuleRegistry.class, ruleRegistry);
    registry.register(CommercialRuleEngine.class, CommercialRuleEngines.of(ruleRegistry));

    registry.register(TaxPlaceholder.class, TaxExtensionPoint.noOp());

    registerValidationRules(registry);
    registry.register(PricingValidator.class, Validators.standard());

    PricingPipeline pipeline =
        PricingPipelines.from(registry, catalogRegistry, couponRegistry, creditRegistry);
    registry.register(PricingPipeline.class, pipeline);
    registry.register(PricingEngine.class, PricingEngines.of(pipeline));
  }

  private static void registerDiscount(ExtensionRegistry registry) {
    registry.register(DiscountPolicy.class, DiscountPolicies.empty());
    registry.register(DiscountValidator.class, DiscountValidators.standard());
  }

  private static void registerPromotion(ExtensionRegistry registry) {
    registry.register(PromotionPolicy.class, PromotionPolicies.empty());
  }

  private static CouponRegistry registerCoupon(ExtensionRegistry registry) {
    CouponRegistry couponRegistry = CouponRegistries.empty();
    registry.register(CouponRegistry.class, couponRegistry);
    registry.register(CouponPolicy.class, CouponPolicies.empty());
    registry.register(CouponStrategy.class, CouponStrategies.submitted());
    registry.register(CouponValidator.class, CouponValidators.standard(couponRegistry));
    return couponRegistry;
  }

  private static CreditRegistry registerCredit(ExtensionRegistry registry) {
    CreditRegistry creditRegistry = CreditRegistries.empty();
    registry.register(CreditRegistry.class, creditRegistry);
    registry.register(CreditPolicy.class, CreditPolicies.empty());
    registry.register(CreditStrategy.class, CreditStrategies.fromAttribute());
    registry.register(CreditCalculator.class, CreditCalculators.fullBalance());
    registry.register(CreditValidator.class, CreditValidators.standard(creditRegistry));
    return creditRegistry;
  }

  private static void registerValidationRules(ExtensionRegistry registry) {
    Validators.defaultRules().forEach(rule -> registry.register(ValidationRule.class, rule));
  }
}
