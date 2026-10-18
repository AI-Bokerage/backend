package io.genfin.pricing.strategy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.price.PriceAdjustment;

/**
 * One candidate reduction competing for a line - one eligible {@code
 * io.genfin.pricing.discount.Discount}, {@code io.genfin.pricing.promotion.Promotion} or coupon
 * redemption, offered to a {@link PricingConflictStrategy} so it can decide which candidate(s) win
 * when more than one could apply simultaneously. Carries only what a conflict-resolution decision
 * needs: a human-readable {@code label}, the {@link PriceAdjustment} it would contribute, and an
 * application-assigned {@code priority} (higher wins ties under {@link
 * io.genfin.pricing.internal.strategy.PriorityOrderStrategy}) - never the originating Discount/
 * Promotion/Coupon type itself, so this stays reusable across all three engines.
 */
public record PricingCandidate(String label, PriceAdjustment adjustment, int priority)
    implements ValueObject {

  public PricingCandidate {
    Validate.notBlank(label, "label must not be blank.");
    Validate.notNull(adjustment, "adjustment must not be null.");
  }

  /** A candidate with no declared priority (0) - relevant only to priority-aware strategies. */
  public static PricingCandidate of(String label, PriceAdjustment adjustment) {
    return new PricingCandidate(label, adjustment, 0);
  }

  public static PricingCandidate of(String label, PriceAdjustment adjustment, int priority) {
    return new PricingCandidate(label, adjustment, priority);
  }

  /** How large this candidate's reduction is, regardless of sign - used to compare discounts. */
  public Money magnitude() {
    return adjustment.amount().abs();
  }

  /** {@code runningAmount} after this candidate's adjustment is applied. */
  public Money resultingAmount(Money runningAmount) {
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    return runningAmount.add(adjustment.amount());
  }
}
