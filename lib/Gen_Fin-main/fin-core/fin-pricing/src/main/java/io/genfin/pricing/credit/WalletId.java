package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The identifier a consuming application uses for one {@link CreditWallet} (e.g. a customer or
 * account key). fin-pricing never generates or stores actual wallet identifiers: this type is only
 * the shape a consuming application's wallet keys take, resolved by a {@link
 * io.genfin.pricing.port.credit.CreditStrategy} and looked up in a {@link
 * io.genfin.pricing.port.credit.CreditRegistry}. Mirrors {@code
 * io.genfin.pricing.coupon.CouponCode}'s "application-provided, normalized" shape.
 */
public record WalletId(String value) implements ValueObject {

  /**
   * The {@link io.genfin.pricing.pricing.PricingAttributes} key a wallet identifier is
   * conventionally carried under, read by {@link CreditStrategies#fromAttribute()}.
   */
  public static final String ATTRIBUTE_KEY = "credit.walletId";

  public WalletId {
    Validate.notBlank(value, "value must not be blank.");
    value = value.trim();
  }

  public static WalletId of(String value) {
    return new WalletId(value);
  }
}
