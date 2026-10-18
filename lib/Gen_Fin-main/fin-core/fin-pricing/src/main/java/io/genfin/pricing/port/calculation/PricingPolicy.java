package io.genfin.pricing.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * The single entry point for resolving every {@link PricingRequest.Line} to its base {@link Price}:
 * composes an ordered set of {@link PricingStrategy} implementations so the Base Price Resolution
 * pipeline stage depends on one SPI instead of picking a strategy itself. Mirrors {@code
 * io.genfin.ledger.port.posting.PostingPolicy}.
 */
public interface PricingPolicy extends Extension {

  List<Price> resolve(List<PricingRequest.Line> lines, PricingContext context);
}
