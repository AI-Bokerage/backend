package io.genfin.pricing.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.calculation.PricingStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * Tries each configured {@link PricingStrategy} in order and resolves with the first one that
 * {@link PricingStrategy#supports supports} the line, mirroring {@code
 * io.genfin.ledger.internal.posting.DefaultPostingPolicy}.
 */
public final class DefaultPricingPolicy implements PricingPolicy {

  private final List<PricingStrategy> strategies;

  public DefaultPricingPolicy(List<PricingStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public List<Price> resolve(List<PricingRequest.Line> lines, PricingContext context) {
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(context, "context must not be null.");
    return lines.stream().map(line -> resolveOne(line, context)).toList();
  }

  private Price resolveOne(PricingRequest.Line line, PricingContext context) {
    for (PricingStrategy strategy : strategies) {
      if (strategy.supports(line, context)) {
        return strategy.resolve(line, context);
      }
    }
    throw new IllegalStateException(
        "No PricingStrategy registered for catalog item " + line.catalogId().value() + ".");
  }
}
