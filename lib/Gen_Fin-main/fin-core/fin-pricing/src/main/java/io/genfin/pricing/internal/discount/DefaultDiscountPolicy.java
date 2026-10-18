package io.genfin.pricing.internal.discount;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.discount.Discount;
import io.genfin.pricing.discount.DiscountCalculator;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.discount.DiscountStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries each configured {@link DiscountStrategy} in order and applies the discounts of the first
 * one that {@link DiscountStrategy#supports supports} the line, via {@link DiscountCalculator}. A
 * line with no matching strategy simply carries no discount - unlike base price resolution,
 * discounting is always optional. Mirrors {@code
 * io.genfin.pricing.internal.calculation.DefaultPricingPolicy}.
 */
public final class DefaultDiscountPolicy implements DiscountPolicy {

  private final List<DiscountStrategy> strategies;

  public DefaultDiscountPolicy(List<DiscountStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public List<Price> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context) {
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.argument(prices.size() == lines.size(), "prices and lines must be the same size.");
    List<Price> updated = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      PricingRequest.Line line = lines.get(i);
      Price price = prices.get(i);
      List<Discount> discounts = resolve(line, context);
      updated.add(DiscountCalculator.apply(price, discounts, line, context));
    }
    return updated;
  }

  private List<Discount> resolve(PricingRequest.Line line, PricingContext context) {
    for (DiscountStrategy strategy : strategies) {
      if (strategy.supports(line, context)) {
        return strategy.resolve(line, context);
      }
    }
    return List.of();
  }
}
