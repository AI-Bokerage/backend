package io.genfin.pricing.port.discount;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * The single entry point the Discount Engine pipeline stage depends on: resolves and applies every
 * discount for a run's already-priced lines, so the stage itself never picks a {@link
 * DiscountStrategy} or performs discount arithmetic directly. Mirrors {@code
 * io.genfin.pricing.port.calculation.PricingPolicy}.
 */
public interface DiscountPolicy extends Extension {

  List<Price> apply(List<Price> prices, List<PricingRequest.Line> lines, PricingContext context);
}
