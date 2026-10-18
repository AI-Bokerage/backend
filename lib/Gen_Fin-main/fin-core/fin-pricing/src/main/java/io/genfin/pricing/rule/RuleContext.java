package io.genfin.pricing.rule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * The configurable thresholds a {@code CommercialRule} may check a {@code CalculationResult}
 * against - minimum price, maximum discount, coupon/promotion stacking limits, credit limit,
 * allowed regions/partner tiers. Every field but {@code asOf} is optional - a {@code null}/{@link
 * Optional#empty()} value means the corresponding rule(s) have nothing to check and report no
 * {@link RuleResult}, mirroring {@code io.genfin.reconciliation.rule.RuleContext}. fin-pricing
 * ships no default thresholds; an application supplies whichever of these its own commercial rules
 * need.
 */
public record RuleContext(
    Instant asOf,
    Optional<Money> minPrice,
    Optional<Percentage> maxDiscountPercentage,
    Optional<Integer> maxStackedCoupons,
    Optional<Integer> maxStackedPromotions,
    Optional<Money> creditLimit,
    Optional<Set<String>> allowedRegions,
    Optional<Set<String>> allowedPartnerTiers)
    implements ValueObject {

  public RuleContext {
    Validate.notNull(asOf, "asOf must not be null.");
    Validate.notNull(minPrice, "minPrice must not be null.");
    Validate.notNull(maxDiscountPercentage, "maxDiscountPercentage must not be null.");
    Validate.notNull(maxStackedCoupons, "maxStackedCoupons must not be null.");
    Validate.notNull(maxStackedPromotions, "maxStackedPromotions must not be null.");
    Validate.notNull(creditLimit, "creditLimit must not be null.");
    Validate.notNull(allowedRegions, "allowedRegions must not be null.");
    Validate.notNull(allowedPartnerTiers, "allowedPartnerTiers must not be null.");
  }

  public static RuleContext at(Instant asOf) {
    return new RuleContext(
        asOf,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public RuleContext withMinPrice(Money minPrice) {
    return new RuleContext(
        asOf,
        Optional.of(minPrice),
        maxDiscountPercentage,
        maxStackedCoupons,
        maxStackedPromotions,
        creditLimit,
        allowedRegions,
        allowedPartnerTiers);
  }

  public RuleContext withMaxDiscountPercentage(Percentage maxDiscountPercentage) {
    return new RuleContext(
        asOf,
        minPrice,
        Optional.of(maxDiscountPercentage),
        maxStackedCoupons,
        maxStackedPromotions,
        creditLimit,
        allowedRegions,
        allowedPartnerTiers);
  }

  public RuleContext withMaxStackedCoupons(int maxStackedCoupons) {
    return new RuleContext(
        asOf,
        minPrice,
        maxDiscountPercentage,
        Optional.of(maxStackedCoupons),
        maxStackedPromotions,
        creditLimit,
        allowedRegions,
        allowedPartnerTiers);
  }

  public RuleContext withMaxStackedPromotions(int maxStackedPromotions) {
    return new RuleContext(
        asOf,
        minPrice,
        maxDiscountPercentage,
        maxStackedCoupons,
        Optional.of(maxStackedPromotions),
        creditLimit,
        allowedRegions,
        allowedPartnerTiers);
  }

  public RuleContext withCreditLimit(Money creditLimit) {
    return new RuleContext(
        asOf,
        minPrice,
        maxDiscountPercentage,
        maxStackedCoupons,
        maxStackedPromotions,
        Optional.of(creditLimit),
        allowedRegions,
        allowedPartnerTiers);
  }

  public RuleContext withAllowedRegions(Set<String> allowedRegions) {
    return new RuleContext(
        asOf,
        minPrice,
        maxDiscountPercentage,
        maxStackedCoupons,
        maxStackedPromotions,
        creditLimit,
        Optional.of(Set.copyOf(allowedRegions)),
        allowedPartnerTiers);
  }

  public RuleContext withAllowedPartnerTiers(Set<String> allowedPartnerTiers) {
    return new RuleContext(
        asOf,
        minPrice,
        maxDiscountPercentage,
        maxStackedCoupons,
        maxStackedPromotions,
        creditLimit,
        allowedRegions,
        Optional.of(Set.copyOf(allowedPartnerTiers)));
  }
}
