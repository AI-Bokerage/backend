package io.genfin.pricing.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CatalogId;

/**
 * The Tax Placeholder stage's outcome for one line: which {@link CatalogId} it estimated for, and
 * the resulting {@link TaxBreakdownPlaceholder}. {@link #zero(CatalogId)} is what fin-pricing's own
 * no-op {@link TaxPlaceholder} always returns; a future Tax Engine returns a populated one instead.
 */
public record TaxEstimate(CatalogId catalogId, TaxBreakdownPlaceholder breakdown)
    implements ValueObject {

  public TaxEstimate {
    Validate.notNull(catalogId, "catalogId must not be null.");
    Validate.notNull(breakdown, "breakdown must not be null.");
  }

  /** No tax estimated for {@code catalogId}. */
  public static TaxEstimate zero(CatalogId catalogId) {
    return new TaxEstimate(catalogId, TaxBreakdownPlaceholder.empty());
  }

  public Money total(Currency currency) {
    return breakdown.total(currency);
  }
}
