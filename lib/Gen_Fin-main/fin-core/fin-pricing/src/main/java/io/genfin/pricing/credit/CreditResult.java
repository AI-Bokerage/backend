package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.price.Price;
import java.util.Optional;

/**
 * The Credit Engine's outcome for one line: the resulting {@link Price} (unchanged if no eligible,
 * non-empty wallet resolved) and, when one did, the {@link CreditAllocation} evidence of it - a
 * wallet-drawn counterpart to {@code io.genfin.pricing.coupon.CouponResult}, since callers
 * (receipts, wallet debiting) need to know how much was drawn and from which categories. Mirrors
 * {@code io.genfin.pricing.coupon.CouponResult}.
 */
public record CreditResult(Price price, Optional<CreditAllocation> allocation)
    implements ValueObject {

  public CreditResult {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(allocation, "allocation must not be null.");
  }

  /** No eligible wallet resolved, or it carried no spendable balance - the price is unchanged. */
  public static CreditResult unchanged(Price price) {
    return new CreditResult(price, Optional.empty());
  }

  /** {@code allocation}'s wallet was drawn against, producing {@code price}. */
  public static CreditResult applied(Price price, CreditAllocation allocation) {
    Validate.notNull(allocation, "allocation must not be null.");
    return new CreditResult(price, Optional.of(allocation));
  }

  public boolean hasCredit() {
    return allocation.isPresent();
  }
}
