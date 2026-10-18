package io.genfin.pricing.port.discount;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.discount.Discount;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * One application-registered way of resolving which {@link Discount}s apply to one {@link
 * PricingRequest.Line} - e.g. a customer-segment discount, a bulk-order discount. fin-pricing ships
 * no implementation: which discounts exist and when they apply is entirely the consuming
 * application's own business rule, registered here rather than hardcoded. Mirrors {@code
 * io.genfin.pricing.port.calculation.PricingStrategy}.
 */
public interface DiscountStrategy extends Extension {

  /** Whether this strategy knows which discounts (if any) apply to {@code line}. */
  boolean supports(PricingRequest.Line line, PricingContext context);

  /** The {@link Discount}s to apply to {@code line}, in the order they should stack. */
  List<Discount> resolve(PricingRequest.Line line, PricingContext context);
}
