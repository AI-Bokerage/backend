package io.genfin.pricing.internal.quote;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.quote.QuotePolicy;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.quote.QuoteExpiration;
import java.time.Duration;

/**
 * The standard {@link QuotePolicy}: every offer is valid for the same fixed {@link Duration} from
 * the moment it is built, regardless of what it prices. Backs {@code
 * io.genfin.pricing.quote.QuotePolicies#standard()}.
 */
public final class DefaultQuotePolicy implements QuotePolicy {

  private final Duration validity;

  public DefaultQuotePolicy(Duration validity) {
    this.validity = Validate.notNull(validity, "validity must not be null.");
    Validate.argument(!validity.isNegative() && !validity.isZero(), "validity must be positive.");
  }

  @Override
  public QuoteExpiration expirationFor(PricingResult result) {
    Validate.notNull(result, "result must not be null.");
    return QuoteExpiration.after(validity);
  }
}
