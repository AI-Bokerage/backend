package io.genfin.pricing.price;

/**
 * The structural role a {@link PriceComponent} plays within a {@link PriceBreakdown}. Fixed by
 * fin-pricing itself (it defines the Pricing Pipeline's stages), unlike {@code
 * io.genfin.pricing.catalog.CatalogCategory}, which is an application-defined taxonomy.
 */
public enum PriceType {
  BASE,
  DISCOUNT,
  PROMOTION,
  COUPON,
  CREDIT,
  TAX,
  FEE,
  SURCHARGE,
  ROUNDING
}
