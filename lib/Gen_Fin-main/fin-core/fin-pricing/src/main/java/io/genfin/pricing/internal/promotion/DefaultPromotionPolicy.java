package io.genfin.pricing.internal.promotion;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.port.promotion.PromotionStrategy;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.promotion.PromotionCalculator;
import io.genfin.pricing.promotion.PromotionResult;
import io.genfin.pricing.promotion.PromotionRule;
import io.genfin.pricing.strategy.PricingStrategies;
import java.util.ArrayList;
import java.util.List;

/**
 * Gathers every candidate {@link PromotionRule} each configured {@link PromotionStrategy} that
 * {@link PromotionStrategy#supports supports} the line offers, then delegates to {@link
 * PromotionCalculator} to filter by eligibility and apply the winner(s) the configured {@link
 * PricingConflictStrategy} selects - a line with no eligible campaign simply carries no promotion,
 * unlike Base Price Resolution. Mirrors {@code io.genfin.pricing.internal.discount
 * .DefaultDiscountPolicy}.
 */
public final class DefaultPromotionPolicy implements PromotionPolicy {

  private final List<PromotionStrategy> strategies;
  private final PricingConflictStrategy conflictStrategy;

  public DefaultPromotionPolicy(List<PromotionStrategy> strategies) {
    this(strategies, PricingStrategies.priorityOrder());
  }

  public DefaultPromotionPolicy(
      List<PromotionStrategy> strategies, PricingConflictStrategy conflictStrategy) {
    this.strategies = List.copyOf(strategies);
    this.conflictStrategy =
        Validate.notNull(conflictStrategy, "conflictStrategy must not be null.");
  }

  @Override
  public List<PromotionResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context) {
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.argument(prices.size() == lines.size(), "prices and lines must be the same size.");
    List<PromotionResult> results = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      PricingRequest.Line line = lines.get(i);
      Price price = prices.get(i);
      List<PromotionRule> candidates = candidatesFor(line, context);
      results.add(PromotionCalculator.apply(price, candidates, line, context, conflictStrategy));
    }
    return results;
  }

  private List<PromotionRule> candidatesFor(PricingRequest.Line line, PricingContext context) {
    List<PromotionRule> candidates = new ArrayList<>();
    for (PromotionStrategy strategy : strategies) {
      if (strategy.supports(line, context)) {
        candidates.addAll(strategy.resolve(line, context));
      }
    }
    return candidates;
  }
}
