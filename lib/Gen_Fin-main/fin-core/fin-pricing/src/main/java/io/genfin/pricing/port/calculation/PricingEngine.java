package io.genfin.pricing.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.pricing.PricingVersion;
import java.time.Instant;

/**
 * The Pricing Engine: runs a {@link PricingRequest} through the Pricing Pipeline (Catalog
 * Resolution -&gt; Base Price Resolution -&gt; Discount/Promotion/Coupon/Credit Engines -&gt; Tax
 * Placeholder -&gt; Rounding -&gt; Pricing Validation) and rejects the outcome outright if it comes
 * back invalid - callers never receive a {@link PricingResult} built from a failed calculation.
 */
public interface PricingEngine extends Extension {

  PricingResult calculate(PricingRequest request, PricingVersion version, Instant calculatedAt);
}
