package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.pipeline.PricingPipeline;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * Default {@link PricingPipeline}: runs every configured {@link PricingPipelineStage} in order,
 * starting from {@link CalculationResult#empty()} and threading each stage's output into the next.
 * Mirrors {@code io.genfin.ledger.internal.posting.DefaultPostingEngine}'s pipeline-of-strategies
 * shape.
 */
public final class DefaultPricingPipeline implements PricingPipeline {

  private final List<PricingPipelineStage> stages;

  public DefaultPricingPipeline(List<PricingPipelineStage> stages) {
    this.stages = List.copyOf(stages);
  }

  @Override
  public CalculationResult run(PricingContext context) {
    Validate.notNull(context, "context must not be null.");
    CalculationResult result = CalculationResult.empty();
    for (PricingPipelineStage stage : stages) {
      result = stage.apply(context, result);
    }
    return result;
  }
}
