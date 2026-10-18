package io.genfin.pricing.port.promotion;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.promotion.PromotionResult;
import java.util.List;

/**
 * The single entry point the Promotion Engine pipeline stage depends on: resolves and applies at
 * most one winning campaign per already-discounted line, so the stage itself never picks a {@link
 * PromotionStrategy}, judges priority, or performs promotion arithmetic directly. Mirrors {@code
 * io.genfin.pricing.port.discount.DiscountPolicy}, but returns one {@link PromotionResult} per line
 * rather than a bare {@link Price} list, since which campaign (if any) was credited is itself part
 * of the outcome.
 */
public interface PromotionPolicy extends Extension {

  List<PromotionResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context);
}
