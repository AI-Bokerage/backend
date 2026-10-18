package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * How much of a {@link CreditWallet}'s registered capacity has been consumed so far, and against
 * what {@code limit} - the read model a {@link io.genfin.pricing.port.credit.CreditRegistry}
 * reports and a {@link CreditValidator} checks before letting an allocation stand. fin-pricing
 * never persists this itself: a registry implementation tracks it from every {@link
 * CreditAllocation} the consuming application chooses to record. Mirrors {@code
 * io.genfin.pricing.coupon.CouponUsage}, with a {@link Money} limit rather than an optional
 * redemption count, since a wallet's capacity is always a concrete amount rather than an open-ended
 * count.
 */
public record CreditUsage(WalletId walletId, Money consumed, Money limit) implements ValueObject {

  public CreditUsage {
    Validate.notNull(walletId, "walletId must not be null.");
    Validate.notNull(consumed, "consumed must not be null.");
    Validate.notNull(limit, "limit must not be null.");
    Validate.argument(!consumed.isNegative(), "consumed must not be negative.");
  }

  /** No allocations recorded yet against {@code limit}. */
  public static CreditUsage none(WalletId walletId, Money limit) {
    return new CreditUsage(walletId, Money.zero(limit.currency()), limit);
  }

  /** Whether every unit of {@code limit} has already been consumed. */
  public boolean isExhausted() {
    return consumed.compareTo(limit) >= 0;
  }

  /** How much of {@code limit} remains, never negative. */
  public Money remaining() {
    Money left = limit.subtract(consumed);
    return left.isNegative() ? Money.zero(limit.currency()) : left;
  }

  /** This usage with {@code amount} more consumed against it. */
  public CreditUsage increment(Money amount) {
    Validate.notNull(amount, "amount must not be null.");
    return new CreditUsage(walletId, consumed.add(amount), limit);
  }
}
