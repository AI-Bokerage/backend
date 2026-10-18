package io.genfin.pricing.promotion;

/**
 * How aggressively one {@link PromotionCampaign} should win against another when more than one is
 * eligible for the same line - promotions are typically non-stacking (a customer gets the single
 * best applicable campaign, unlike {@code io.genfin.pricing.discount.Discount}s, which stack).
 * {@link PromotionCalculator} picks the highest {@link #rank()} among every eligible candidate.
 * Fixed by fin-pricing itself (it defines the Promotion Engine's selection rule), unlike {@code
 * io.genfin.pricing.catalog.CatalogCategory}, which is an application-defined taxonomy.
 */
public enum PromotionPriority {
  LOW(0),
  STANDARD(1),
  HIGH(2),
  CRITICAL(3);

  private final int rank;

  PromotionPriority(int rank) {
    this.rank = rank;
  }

  public int rank() {
    return rank;
  }
}
