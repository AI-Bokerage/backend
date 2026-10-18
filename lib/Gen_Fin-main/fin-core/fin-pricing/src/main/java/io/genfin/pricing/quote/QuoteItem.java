package io.genfin.pricing.quote;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * One catalog item quoted at a fixed {@link Price} - the Quote Engine's counterpart to a {@link
 * PricingRequest.Line}, but carrying the resolved price rather than only the quantity to price.
 * Never an invoice line: fin-pricing has no invoice-related type to reference here, and this record
 * carries nothing beyond identity, quantity and price.
 */
public record QuoteItem(CatalogId catalogId, int quantity, Price price) implements ValueObject {

  public QuoteItem {
    Validate.notNull(catalogId, "catalogId must not be null.");
    Validate.positive(quantity, "quantity must be positive.");
    Validate.notNull(price, "price must not be null.");
  }

  /**
   * One quoted line, pairing a priced {@link PricingRequest.Line} with its resolved {@link Price}.
   */
  public static QuoteItem of(PricingRequest.Line line, Price price) {
    Validate.notNull(line, "line must not be null.");
    return new QuoteItem(line.catalogId(), line.quantity(), price);
  }
}
