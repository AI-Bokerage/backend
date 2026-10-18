package io.genfin.pricing.promotion;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.strategy.PricingCandidate;
import io.genfin.pricing.strategy.PricingStrategies;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure arithmetic over a {@link Price} and its candidate {@link PromotionRule}s: filters to the
 * ones whose {@link PromotionEligibility} matches the line, hands the eligible ones to a
 * configurable {@link PricingConflictStrategy} to decide which one(s) win (see {@code
 * io.genfin.pricing.strategy.PricingStrategies} - {@link PricingStrategies#priorityOrder()} by
 * default, matching this calculator's original fixed highest-{@link PromotionPriority} rule), and
 * applies the winner's {@link Promotion} against the running net amount - unlike {@code
 * io.genfin.pricing.discount.DiscountCalculator}, which stacks every discount, only a
 * stacking-oriented strategy (e.g. Maximum Savings) lets more than one promotion apply. Mirrors
 * {@code io.genfin.pricing.discount.DiscountCalculator}'s "thread the running amount through"
 * shape.
 */
public final class PromotionCalculator {

  private PromotionCalculator() {}

  /** Resolves conflicts via {@link PricingStrategies#priorityOrder()}, fin-pricing's default. */
  public static PromotionResult apply(
      Price price,
      List<PromotionRule> candidates,
      PricingRequest.Line line,
      PricingContext context) {
    return apply(price, candidates, line, context, PricingStrategies.priorityOrder());
  }

  public static PromotionResult apply(
      Price price,
      List<PromotionRule> candidates,
      PricingRequest.Line line,
      PricingContext context,
      PricingConflictStrategy strategy) {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(candidates, "candidates must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(strategy, "strategy must not be null.");
    List<PromotionRule> eligible =
        candidates.stream().filter(rule -> rule.eligibility().test(line, context)).toList();
    if (eligible.isEmpty()) {
      return PromotionResult.unchanged(price);
    }
    Money runningAmount = price.amount();
    List<PricingCandidate> pricingCandidates =
        eligible.stream()
            .map(
                rule ->
                    PricingCandidate.of(
                        rule.campaign().description(),
                        rule.promotion().applyTo(runningAmount, line, context),
                        rule.campaign().priority().rank()))
            .toList();
    List<PricingCandidate> selected = strategy.select(pricingCandidates, runningAmount, context);
    if (selected.isEmpty()) {
      return PromotionResult.unchanged(price);
    }
    PricingCandidate winner = selected.get(0);
    PromotionRule rule = eligible.get(pricingCandidates.indexOf(winner));
    List<PriceComponent> components = new ArrayList<>(price.breakdown().components());
    components.add(winner.adjustment().toComponent(rule.campaign().description()));
    Price applied = new Price(price.catalogId(), new PriceBreakdown(components));
    return PromotionResult.applied(applied, rule.campaign());
  }
}
