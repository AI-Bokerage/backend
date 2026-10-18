package io.genfin.pricing.quote;

/**
 * The lifecycle state of a {@link Quote}. Fixed by fin-pricing itself (a Quote's transitions are
 * structural, not a business rule an application would want to replace), unlike {@code
 * io.genfin.pricing.credit.CreditCategory} or {@code io.genfin.pricing.catalog.CatalogCategory},
 * which are application-owned taxonomies.
 */
public enum QuoteStatus {
  DRAFT,
  ISSUED,
  ACCEPTED,
  REJECTED,
  EXPIRED
}
