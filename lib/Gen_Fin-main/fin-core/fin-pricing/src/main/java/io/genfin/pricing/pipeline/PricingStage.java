package io.genfin.pricing.pipeline;

/**
 * The fixed, ordered set of stages the Pricing Pipeline runs a {@link
 * io.genfin.pricing.calculation.CalculationResult} through. Fixed by fin-pricing itself (it defines
 * the pipeline's shape), unlike {@code io.genfin.pricing.catalog.CatalogCategory}, which is an
 * application-defined taxonomy. {@link #TAX_PLACEHOLDER} never calculates jurisdiction-specific tax
 * itself - it is only the extension point a future Tax Engine plugs into.
 */
public enum PricingStage {
  CATALOG_RESOLUTION,
  BASE_PRICE_RESOLUTION,
  DISCOUNT,
  PROMOTION,
  COUPON,
  CREDIT,
  TAX_PLACEHOLDER,
  ROUNDING,
  VALIDATION
}
