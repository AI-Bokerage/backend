package io.genfin.pricing.port.credit;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.credit.WalletId;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * One application-registered way of resolving which {@link WalletId}s apply to one {@link
 * PricingRequest.Line} - e.g. reading a wallet id carried in {@link
 * io.genfin.pricing.pricing.PricingAttributes} (see {@link
 * io.genfin.pricing.credit.CreditStrategies#fromAttribute()}), or a per-customer wallet lookup.
 * fin-pricing ships no wallet catalog: which wallets exist and what they hold is entirely the
 * consuming application's business rule, looked up via {@link CreditRegistry} once resolved here.
 * Mirrors {@code io.genfin.pricing.port.coupon.CouponStrategy}.
 */
public interface CreditStrategy extends Extension {

  /** Whether this strategy knows of any candidate wallet for {@code line}. */
  boolean supports(PricingRequest.Line line, PricingContext context);

  /**
   * The candidate {@link WalletId}s for {@code line}, in the order they should be tried; the caller
   * still looks each one up in a {@link CreditRegistry} and draws against at most one.
   */
  List<WalletId> resolve(PricingRequest.Line line, PricingContext context);
}
