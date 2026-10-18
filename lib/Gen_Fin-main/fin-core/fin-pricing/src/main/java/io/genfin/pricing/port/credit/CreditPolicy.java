package io.genfin.pricing.port.credit;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.credit.CreditResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * The single entry point the Credit Engine pipeline stage depends on: resolves and applies at most
 * one wallet's draw per already-discounted/promoted/coupon-applied line, so the stage itself never
 * picks a {@link CreditStrategy}, looks a wallet up in a {@link CreditRegistry}, or performs credit
 * arithmetic directly. Mirrors {@code io.genfin.pricing.port.coupon.CouponPolicy}, returning one
 * {@link CreditResult} per line rather than a bare {@link Price} list, since how much was drawn (if
 * any) is itself part of the outcome.
 */
public interface CreditPolicy extends Extension {

  List<CreditResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context);
}
