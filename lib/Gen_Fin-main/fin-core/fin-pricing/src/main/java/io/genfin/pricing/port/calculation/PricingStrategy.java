package io.genfin.pricing.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * One application-registered way of resolving a base {@link Price} for one {@link
 * PricingRequest.Line} - e.g. flat-rate lookup, tiered/volume pricing, usage-based pricing.
 * fin-pricing ships no implementation: what a catalog item actually costs is entirely the consuming
 * application's own business rule, registered here rather than hardcoded. Mirrors {@code
 * io.genfin.ledger.port.posting.PostingStrategy}.
 */
public interface PricingStrategy extends Extension {

  /** Whether this strategy knows how to price {@code line}. */
  boolean supports(PricingRequest.Line line, PricingContext context);

  /** The base {@link Price} {@code line} resolves to, given {@code context}. */
  Price resolve(PricingRequest.Line line, PricingContext context);
}
