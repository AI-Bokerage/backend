package io.genfin.pricing.coupon;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Optional;

/**
 * How many times a {@link CouponCode} has been redeemed so far, and against what limit (if any) -
 * the read model a {@link io.genfin.pricing.port.coupon.CouponRegistry} reports and a {@link
 * CouponValidator} checks before letting a redemption stand. fin-pricing never persists this
 * itself: a registry implementation tracks it from every {@link CouponRedemption} the consuming
 * application chooses to record.
 */
public record CouponUsage(CouponCode code, int redemptions, Optional<Integer> limit)
    implements ValueObject {

  public CouponUsage {
    Validate.notNull(code, "code must not be null.");
    Validate.argument(redemptions >= 0, "redemptions must not be negative.");
    Validate.notNull(limit, "limit must not be null.");
    limit.ifPresent(value -> Validate.positive(value, "limit must be positive."));
  }

  /** No redemptions recorded yet, and no limit configured. */
  public static CouponUsage unlimited(CouponCode code) {
    return new CouponUsage(code, 0, Optional.empty());
  }

  public static CouponUsage of(CouponCode code, int redemptions, Optional<Integer> limit) {
    return new CouponUsage(code, redemptions, limit);
  }

  /** Whether every redemption allowed by {@link #limit()} has already been used. */
  public boolean isExhausted() {
    return limit.map(value -> redemptions >= value).orElse(false);
  }

  /** How many redemptions remain, if a limit is configured. */
  public Optional<Integer> remaining() {
    return limit.map(value -> Math.max(0, value - redemptions));
  }

  /** This usage with one more redemption recorded against it. */
  public CouponUsage increment() {
    return new CouponUsage(code, redemptions + 1, limit);
  }
}
