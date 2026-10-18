package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * The Catalog Resolution stage: confirms every {@link PricingRequest.Line} in the running {@link
 * PricingContext} references a {@link io.genfin.pricing.catalog.CatalogItem} actually registered in
 * the {@link CatalogRegistry}, failing fast (via {@link CatalogRegistry#require}) if not. Later
 * stages resolve the item's price themselves; this stage only guarantees it exists.
 */
public final class CatalogResolutionStage implements PricingPipelineStage {

  private final CatalogRegistry catalogRegistry;

  public CatalogResolutionStage(CatalogRegistry catalogRegistry) {
    this.catalogRegistry = Validate.notNull(catalogRegistry, "catalogRegistry must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.CATALOG_RESOLUTION;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    for (PricingRequest.Line line : context.lines()) {
      catalogRegistry.require(line.catalogId());
    }
    return result;
  }
}
