package io.genfin.pricing.quote;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.quote.QuotePolicy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.pricing.PricingResult;
import java.util.List;

/**
 * One-shot conversion of a completed Pricing Pipeline run into a {@link Quote}: pairs the {@link
 * PricingRequest.Line}s priced against a {@link PricingResult} with their resolved {@link Price}s,
 * and asks {@code policy} how long the resulting offer stays valid. Delegates to {@link
 * QuoteBuilder} rather than duplicating its assembly logic.
 */
public final class QuoteFactory {

  private QuoteFactory() {}

  /**
   * Builds a {@code DRAFT} {@link Quote} from {@code result}, zipping {@code lines} with their
   * resolved {@code prices} (same order, same size) into {@link QuoteItem}s, and using {@code
   * policy} to resolve the offer's {@link QuoteExpiration}.
   */
  public static Quote from(
      PricingResult result,
      List<PricingRequest.Line> lines,
      List<Price> prices,
      QuotePolicy policy) {
    Validate.notNull(result, "result must not be null.");
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(policy, "policy must not be null.");
    Validate.argument(lines.size() == prices.size(), "lines and prices must be the same size.");
    Validate.argument(!lines.isEmpty(), "lines must not be empty.");

    QuoteBuilder builder = QuoteBuilder.forResult(result.id());
    for (int i = 0; i < lines.size(); i++) {
      builder.addItem(lines.get(i), prices.get(i));
    }
    return builder.expiresAt(policy.expirationFor(result)).metadata(result.metadata()).build();
  }
}
