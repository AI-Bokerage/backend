package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.PricingRequestId;
import java.time.Instant;
import java.util.Map;

/**
 * Evidence that {@code amount}, drawn from {@code walletId}'s {@link CreditBalance} (broken down
 * per {@link CreditCategory#code()} in {@code byCategory}), was applied to one {@link
 * io.genfin.pricing.pricing.PricingRequest} - fin-pricing produces this fact as part of a {@link
 * CreditResult} but never commits it: whether it is actually debited against the wallet (by calling
 * {@code CreditRegistry#recordAllocation}) is left to the consuming application, typically only
 * once the priced request is turned into a real order. Mirrors {@code
 * io.genfin.pricing.coupon.CouponRedemption}.
 */
public record CreditAllocation(
    WalletId walletId,
    Money amount,
    Map<String, Money> byCategory,
    PricingRequestId requestId,
    Instant allocatedAt)
    implements ValueObject {

  public CreditAllocation {
    Validate.notNull(walletId, "walletId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(amount.isPositive(), "amount must be positive.");
    Validate.notNull(byCategory, "byCategory must not be null.");
    byCategory = Map.copyOf(byCategory);
    Validate.notNull(requestId, "requestId must not be null.");
    Validate.notNull(allocatedAt, "allocatedAt must not be null.");
  }

  /**
   * An allocation of {@code amount} from {@code walletId} for {@code requestId}, timestamped now.
   */
  public static CreditAllocation of(
      WalletId walletId, Money amount, Map<String, Money> byCategory, PricingRequestId requestId) {
    return new CreditAllocation(walletId, amount, byCategory, requestId, Instant.now());
  }
}
