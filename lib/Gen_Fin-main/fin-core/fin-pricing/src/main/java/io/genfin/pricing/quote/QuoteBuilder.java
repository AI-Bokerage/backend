package io.genfin.pricing.quote;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingMetadata;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, fluent assembly of a {@link Quote} one {@link QuoteItem} at a time - the imperative
 * counterpart to {@link QuoteFactory#from}'s one-shot conversion from an already-computed {@link
 * java.util.List} of {@link Price}s, for callers that build up a quote's lines incrementally.
 */
public final class QuoteBuilder {

  private final PricingResultId pricingResultId;
  private final List<QuoteItem> items = new ArrayList<>();
  private QuoteExpiration expiration;
  private PricingMetadata metadata = PricingMetadata.empty();

  private QuoteBuilder(PricingResultId pricingResultId) {
    this.pricingResultId = Validate.notNull(pricingResultId, "pricingResultId must not be null.");
  }

  public static QuoteBuilder forResult(PricingResultId pricingResultId) {
    return new QuoteBuilder(pricingResultId);
  }

  public QuoteBuilder addItem(CatalogId catalogId, int quantity, Price price) {
    items.add(new QuoteItem(catalogId, quantity, price));
    return this;
  }

  public QuoteBuilder addItem(PricingRequest.Line line, Price price) {
    items.add(QuoteItem.of(line, price));
    return this;
  }

  public QuoteBuilder expiresAt(QuoteExpiration expiration) {
    this.expiration = Validate.notNull(expiration, "expiration must not be null.");
    return this;
  }

  public QuoteBuilder metadata(PricingMetadata metadata) {
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
    return this;
  }

  public Quote build() {
    Validate.argument(!items.isEmpty(), "At least one item must be added before building a quote.");
    Validate.state(expiration != null, "expiresAt(QuoteExpiration) must be set before building.");
    return Quote.draft(pricingResultId, List.copyOf(items), expiration).withMetadata(metadata);
  }
}
