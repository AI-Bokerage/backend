package io.genfin.pricing.internal.coupon;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.coupon.Coupon;
import io.genfin.pricing.coupon.CouponCampaign;
import io.genfin.pricing.coupon.CouponCode;
import io.genfin.pricing.coupon.CouponEligibility;
import io.genfin.pricing.coupon.CouponRedemption;
import io.genfin.pricing.coupon.CouponResult;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.port.coupon.CouponStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gathers every {@link CouponCode} each configured {@link CouponStrategy} that {@link
 * CouponStrategy#supports supports} the line offers, then tries each in order: the first code that
 * resolves to a registered {@link CouponCampaign} (with its {@link Coupon} arithmetic and {@link
 * CouponEligibility}) whose eligibility passes wins - unlike the Discount Engine, which stacks
 * every match, at most one coupon is ever applied per line. A line with no matching, eligible code
 * simply carries no coupon. Mirrors {@code io.genfin.pricing.internal.promotion.
 * DefaultPromotionPolicy}'s "gather candidates, delegate the winner-pick" shape.
 */
public final class DefaultCouponPolicy implements CouponPolicy {

  private final CouponRegistry registry;
  private final List<CouponStrategy> strategies;

  public DefaultCouponPolicy(CouponRegistry registry, List<CouponStrategy> strategies) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public List<CouponResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context) {
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.argument(prices.size() == lines.size(), "prices and lines must be the same size.");
    List<CouponResult> results = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      results.add(applyTo(prices.get(i), lines.get(i), context));
    }
    return results;
  }

  private CouponResult applyTo(Price price, PricingRequest.Line line, PricingContext context) {
    for (CouponCode code : candidatesFor(line, context)) {
      Optional<CouponCampaign> campaign = registry.findCampaign(code);
      Optional<Coupon> coupon = registry.findCoupon(code);
      Optional<CouponEligibility> eligibility = registry.findEligibility(code);
      if (campaign.isPresent()
          && coupon.isPresent()
          && eligibility.isPresent()
          && eligibility.get().test(line, context)) {
        return applyCoupon(price, campaign.get(), coupon.get(), line, context);
      }
    }
    return CouponResult.unchanged(price);
  }

  private CouponResult applyCoupon(
      Price price,
      CouponCampaign campaign,
      Coupon coupon,
      PricingRequest.Line line,
      PricingContext context) {
    Money runningAmount = price.amount();
    PriceAdjustment adjustment = coupon.applyTo(runningAmount, line, context);
    List<PriceComponent> components = new ArrayList<>(price.breakdown().components());
    components.add(adjustment.toComponent(campaign.description()));
    Price applied = new Price(price.catalogId(), new PriceBreakdown(components));
    CouponRedemption redemption = CouponRedemption.of(campaign, context.requestId());
    return CouponResult.applied(applied, redemption);
  }

  private List<CouponCode> candidatesFor(PricingRequest.Line line, PricingContext context) {
    List<CouponCode> candidates = new ArrayList<>();
    for (CouponStrategy strategy : strategies) {
      if (strategy.supports(line, context)) {
        candidates.addAll(strategy.resolve(line, context));
      }
    }
    return candidates;
  }
}
