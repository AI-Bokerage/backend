package io.genfin.pricing.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.price.Price;
import java.util.ArrayList;
import java.util.List;

/**
 * The running, immutable output the Pricing Pipeline threads through its stages: the {@link Price}
 * resolved so far for each {@link io.genfin.pricing.pricing.PricingRequest.Line}, plus every {@link
 * CalculationIssue} raised along the way. Each {@code io.genfin.pricing.port.pipeline
 * .PricingPipelineStage} receives one result and returns a new one carrying its contribution -
 * nothing here is mutated in place. The final result of a pipeline run is what {@link
 * io.genfin.pricing.port.calculation.PricingEngine} collapses into a {@link
 * io.genfin.pricing.pricing.PricingResult}.
 */
public record CalculationResult(List<Price> prices, List<CalculationIssue> issues)
    implements ValueObject {

  public CalculationResult {
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(issues, "issues must not be null.");
    prices = List.copyOf(prices);
    issues = List.copyOf(issues);
  }

  /** The starting point of a pipeline run - no prices resolved yet, no issues raised yet. */
  public static CalculationResult empty() {
    return new CalculationResult(List.of(), List.of());
  }

  public CalculationResult withPrices(List<Price> prices) {
    return new CalculationResult(prices, issues);
  }

  public CalculationResult addIssues(List<CalculationIssue> newIssues) {
    Validate.notNull(newIssues, "newIssues must not be null.");
    List<CalculationIssue> merged = new ArrayList<>(issues);
    merged.addAll(newIssues);
    return new CalculationResult(prices, merged);
  }

  /** Whether every raised issue is below {@link Severity#ERROR}. */
  public boolean isValid() {
    return issues.stream()
        .noneMatch(
            issue -> issue.severity() == Severity.ERROR || issue.severity() == Severity.CRITICAL);
  }
}
