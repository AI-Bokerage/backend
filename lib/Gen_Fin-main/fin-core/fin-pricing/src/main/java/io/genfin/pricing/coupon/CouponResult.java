package io.genfin.pricing.coupon;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.price.Price;
import java.util.Optional;

/**
 * The Coupon Engine's outcome for one line: the resulting {@link Price} (unchanged if no presented
 * {@link CouponCode} resolved to an eligible campaign) and, when one did, the {@link
 * CouponRedemption} evidence of it - unlike {@code io.genfin.pricing.discount.Discount}, which
 * stacks silently, a coupon is a customer-presented code, so callers (receipts, redemption
 * tracking) need to know which one was credited. Mirrors {@code
 * io.genfin.pricing.promotion.PromotionResult}.
 */
public record CouponResult(Price price, Optional<CouponRedemption> redemption)
    implements ValueObject {

  public CouponResult {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(redemption, "redemption must not be null.");
  }

  /** No presented code resolved to an eligible campaign - the line's price is unchanged. */
  public static CouponResult unchanged(Price price) {
    return new CouponResult(price, Optional.empty());
  }

  /** {@code redemption}'s campaign was applied, producing {@code price}. */
  public static CouponResult applied(Price price, CouponRedemption redemption) {
    Validate.notNull(redemption, "redemption must not be null.");
    return new CouponResult(price, Optional.of(redemption));
  }

  public boolean hasCoupon() {
    return redemption.isPresent();
  }
}
