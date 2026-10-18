package io.genfin.pricing.port.coupon;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.coupon.CouponResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * The single entry point the Coupon Engine pipeline stage depends on: resolves and applies at most
 * one redeemed campaign per already-discounted-and-promoted line, so the stage itself never picks a
 * {@link CouponStrategy}, looks a code up in a {@link CouponRegistry}, or performs coupon
 * arithmetic directly. Mirrors {@code io.genfin.pricing.port.promotion.PromotionPolicy}, returning
 * one {@link CouponResult} per line rather than a bare {@link Price} list, since which redemption
 * (if any) occurred is itself part of the outcome.
 */
public interface CouponPolicy extends Extension {

  List<CouponResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context);
}
