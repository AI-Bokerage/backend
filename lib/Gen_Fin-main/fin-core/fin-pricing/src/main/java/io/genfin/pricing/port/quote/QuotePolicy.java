package io.genfin.pricing.port.quote;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.quote.QuoteExpiration;

/**
 * The single extension point the Quote Engine depends on: how long an offer built from a {@link
 * PricingResult} stays valid. fin-pricing ships one standard shape (a fixed validity {@link
 * java.time.Duration} from the moment it is issued, see {@code
 * io.genfin.pricing.quote.QuotePolicies#standard()}), but an application may replace it with e.g. a
 * shorter validity for time-sensitive promotions or a longer one for enterprise deals. Mirrors
 * {@code io.genfin.pricing.port.credit.CreditPolicy}'s "single seam the engine depends on" shape.
 */
public interface QuotePolicy extends Extension {

  QuoteExpiration expirationFor(PricingResult result);
}
