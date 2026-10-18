package io.genfin.pricing.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.calculation.PricingCalculator;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.pricing.port.pipeline.PricingPipeline;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingMetadata;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.pricing.PricingSummary;
import io.genfin.pricing.pricing.PricingVersion;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Default {@link PricingEngine}: threads a {@link PricingRequest} through the configured {@link
 * PricingPipeline}, rejects the outcome outright if the resulting {@link CalculationResult} carries
 * an error-or-worse {@link CalculationIssue}, and otherwise collapses the resolved {@link
 * io.genfin.pricing.price.Price}s into an immutable {@link PricingResult}. Mirrors {@code
 * io.genfin.ledger.internal.posting.DefaultPostingEngine}.
 */
public final class DefaultPricingEngine implements PricingEngine {

  private final PricingPipeline pipeline;

  public DefaultPricingEngine(PricingPipeline pipeline) {
    this.pipeline = Validate.notNull(pipeline, "pipeline must not be null.");
  }

  @Override
  public PricingResult calculate(
      PricingRequest request, PricingVersion version, Instant calculatedAt) {
    Validate.notNull(request, "request must not be null.");
    Validate.notNull(version, "version must not be null.");
    Validate.notNull(calculatedAt, "calculatedAt must not be null.");

    PricingContext context =
        PricingContext.start(request.id(), request.lines(), request.attributes());
    CalculationResult result = pipeline.run(context);
    if (!result.isValid()) {
      throw new IllegalStateException("Rejected invalid pricing calculation: " + describe(result));
    }

    Currency currency = result.prices().get(0).amount().currency();
    PricingSummary summary = PricingCalculator.summarize(result.prices(), currency);

    return new PricingResult(
        PricingResultId.generate(),
        request.id(),
        version,
        summary,
        PricingMetadata.empty(),
        calculatedAt);
  }

  private static String describe(CalculationResult result) {
    return result.issues().stream()
        .map(CalculationIssue::message)
        .collect(Collectors.joining("; "));
  }
}
