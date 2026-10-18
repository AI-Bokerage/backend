package io.genfin.pricing.discount;

/**
 * The shape one {@link Discount} takes. Fixed by fin-pricing itself (it defines the Discount
 * Engine's building blocks), unlike {@code io.genfin.pricing.catalog.CatalogCategory}, which is an
 * application-defined taxonomy.
 */
public enum DiscountType {
  PERCENTAGE,
  FIXED,
  TIER,
  VOLUME,
  CONDITIONAL
}
