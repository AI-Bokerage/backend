package io.genfin.pricing.pipeline;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.calculation.PricingPolicies;
import io.genfin.pricing.coupon.CouponPolicies;
import io.genfin.pricing.coupon.CouponRegistries;
import io.genfin.pricing.coupon.CouponValidators;
import io.genfin.pricing.credit.CreditPolicies;
import io.genfin.pricing.credit.CreditRegistries;
import io.genfin.pricing.credit.CreditValidators;
import io.genfin.pricing.discount.DiscountPolicies;
import io.genfin.pricing.discount.DiscountValidators;
import io.genfin.pricing.internal.pipeline.BasePriceResolutionStage;
import io.genfin.pricing.internal.pipeline.CatalogResolutionStage;
import io.genfin.pricing.internal.pipeline.CouponEngineStage;
import io.genfin.pricing.internal.pipeline.CreditEngineStage;
import io.genfin.pricing.internal.pipeline.DefaultPricingPipeline;
import io.genfin.pricing.internal.pipeline.DiscountEngineStage;
import io.genfin.pricing.internal.pipeline.PassThroughStage;
import io.genfin.pricing.internal.pipeline.PricingValidationStage;
import io.genfin.pricing.internal.pipeline.PromotionEngineStage;
import io.genfin.pricing.internal.pipeline.TaxPlaceholderStage;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.calculation.PricingRule;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.pipeline.PricingPipeline;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.promotion.PromotionPolicies;
import io.genfin.pricing.tax.TaxExtensionPoint;
import io.genfin.pricing.tax.TaxPlaceholder;
import io.genfin.pricing.validation.Validators;
import java.util.List;

/**
 * Factory for {@link PricingPipeline} instances. {@link #standard} wires the full, structurally
 * complete stage order (Catalog Resolution -&gt; Base Price Resolution -&gt; Discount Engine -&gt;
 * Promotion Engine -&gt; Coupon Engine -&gt; Credit Engine -&gt; Tax Placeholder -&gt; Rounding
 * -&gt; Pricing Validation). The Discount, Promotion, Coupon and Credit Engines are real stages;
 * the remaining Tax Placeholder/Rounding stages are passed through unchanged until each one's real
 * engine arrives in a later phase.
 */
public final class PricingPipelines {

  private PricingPipelines() {}

  /**
   * The standard stage order with no discounting, promoting, couponing or crediting configured -
   * equivalent to {@link #standard(PricingPolicy, DiscountPolicy, PromotionPolicy, CouponPolicy,
   * CouponRegistry, CreditPolicy, CreditRegistry, CatalogRegistry, List)} with {@link
   * DiscountPolicies#empty()}, {@link PromotionPolicies#empty()}, {@link CouponPolicies#empty()}
   * and {@link CreditPolicies#empty()}.
   */
  public static PricingPipeline standard(
      PricingPolicy policy, CatalogRegistry catalogRegistry, List<PricingRule> validationRules) {
    CouponRegistry couponRegistry = CouponRegistries.empty();
    CreditRegistry creditRegistry = CreditRegistries.empty();
    return standard(
        policy,
        DiscountPolicies.empty(),
        PromotionPolicies.empty(),
        CouponPolicies.empty(),
        couponRegistry,
        CreditPolicies.empty(),
        creditRegistry,
        catalogRegistry,
        validationRules);
  }

  /**
   * The standard stage order. {@code policy} drives Base Price Resolution, {@code discountPolicy}
   * drives the Discount Engine, {@code promotionPolicy} drives the Promotion Engine, {@code
   * couponPolicy} drives the Coupon Engine (validated against {@code couponRegistry}'s usage
   * records), {@code creditPolicy} drives the Credit Engine (validated against {@code
   * creditRegistry}'s usage records), {@code catalogRegistry} drives Catalog Resolution, and {@code
   * validationRules} (may be empty) drive Pricing Validation.
   */
  public static PricingPipeline standard(
      PricingPolicy policy,
      DiscountPolicy discountPolicy,
      PromotionPolicy promotionPolicy,
      CouponPolicy couponPolicy,
      CouponRegistry couponRegistry,
      CreditPolicy creditPolicy,
      CreditRegistry creditRegistry,
      CatalogRegistry catalogRegistry,
      List<PricingRule> validationRules) {
    return standard(
        policy,
        discountPolicy,
        promotionPolicy,
        couponPolicy,
        couponRegistry,
        creditPolicy,
        creditRegistry,
        catalogRegistry,
        validationRules,
        TaxExtensionPoint.noOp());
  }

  /**
   * The standard stage order with an explicit {@link TaxPlaceholder} - {@code taxPlaceholder}
   * drives the Tax Placeholder stage, which never calculates jurisdiction-specific tax itself; pass
   * {@link TaxExtensionPoint#noOp()} (fin-pricing's own default) until a real Tax Engine is
   * registered.
   */
  public static PricingPipeline standard(
      PricingPolicy policy,
      DiscountPolicy discountPolicy,
      PromotionPolicy promotionPolicy,
      CouponPolicy couponPolicy,
      CouponRegistry couponRegistry,
      CreditPolicy creditPolicy,
      CreditRegistry creditRegistry,
      CatalogRegistry catalogRegistry,
      List<PricingRule> validationRules,
      TaxPlaceholder taxPlaceholder) {
    return of(
        List.of(
            new CatalogResolutionStage(catalogRegistry),
            new BasePriceResolutionStage(policy),
            new DiscountEngineStage(discountPolicy, DiscountValidators.standard()),
            new PromotionEngineStage(promotionPolicy),
            new CouponEngineStage(couponPolicy, CouponValidators.standard(couponRegistry)),
            new CreditEngineStage(creditPolicy, CreditValidators.standard(creditRegistry)),
            new TaxPlaceholderStage(taxPlaceholder),
            new PassThroughStage(PricingStage.ROUNDING),
            new PricingValidationStage(validationRules, Validators.standard())));
  }

  /** A pipeline that runs exactly {@code stages}, in order. */
  public static PricingPipeline of(List<PricingPipelineStage> stages) {
    return new DefaultPricingPipeline(stages);
  }

  /**
   * Resolves the {@link PricingPipeline} registered in {@code registry} if present; otherwise
   * builds {@link #standard} from the registry's {@link PricingPolicy} (via {@code
   * io.genfin.pricing.calculation.PricingPolicies#from}), {@link DiscountPolicy} (via {@link
   * DiscountPolicies#from}), {@link PromotionPolicy} (via {@link PromotionPolicies#from}), {@link
   * CouponPolicy} (via {@link CouponPolicies#from}, backed by {@code couponRegistry}), {@link
   * CreditPolicy} (via {@link CreditPolicies#from}, backed by {@code creditRegistry}), {@code
   * catalogRegistry} and every registered {@link PricingRule}.
   */
  public static PricingPipeline from(
      ExtensionRegistry registry,
      CatalogRegistry catalogRegistry,
      CouponRegistry couponRegistry,
      CreditRegistry creditRegistry) {
    return registry
        .find(PricingPipeline.class)
        .orElseGet(
            () ->
                standard(
                    PricingPolicies.from(registry),
                    DiscountPolicies.from(registry),
                    PromotionPolicies.from(registry),
                    CouponPolicies.from(registry, couponRegistry),
                    couponRegistry,
                    CreditPolicies.from(registry, creditRegistry),
                    creditRegistry,
                    catalogRegistry,
                    registry.findAll(PricingRule.class),
                    TaxExtensionPoint.from(registry)));
  }
}
