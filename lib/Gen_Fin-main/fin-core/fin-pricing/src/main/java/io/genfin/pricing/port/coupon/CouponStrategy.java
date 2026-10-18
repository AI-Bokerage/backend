package io.genfin.pricing.port.coupon;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.coupon.CouponCode;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * One application-registered way of resolving which {@link CouponCode}s a customer has presented
 * for one {@link PricingRequest.Line} - e.g. reading a code carried in {@link
 * io.genfin.pricing.pricing.PricingAttributes} (see {@link
 * io.genfin.pricing.coupon.CouponStrategies#submitted()}), or a per-channel code source.
 * fin-pricing ships no coupon catalog: which codes exist, what they mean and whether they are
 * eligible is entirely the consuming application's business rule, looked up via {@link
 * CouponRegistry} once resolved here. Mirrors {@code
 * io.genfin.pricing.port.promotion.PromotionStrategy}.
 */
public interface CouponStrategy extends Extension {

  /** Whether this strategy knows of any presented {@link CouponCode} for {@code line}. */
  boolean supports(PricingRequest.Line line, PricingContext context);

  /**
   * The candidate {@link CouponCode}s presented for {@code line}, in the order they should be
   * tried; the caller still looks each one up in a {@link CouponRegistry} and applies at most one.
   */
  List<CouponCode> resolve(PricingRequest.Line line, PricingContext context);
}
