package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;

/**
 * Describes how a Discount/Promotion/Coupon/Credit Engine stage would adjust a base {@link Money}
 * amount, without baking that arithmetic into the pipeline itself - a stage picks or builds one of
 * these (typically via its own Strategy/Policy), then calls {@link #applyTo(Money)} to get the
 * resulting delta. Sealed to the two shapes every commercial adjustment reduces to; anything more
 * specific (a tiered rate, a rule combining several) is a Strategy that produces one of these two,
 * not a third case here.
 */
public sealed interface PriceModifier extends ValueObject {

  Money applyTo(Money base);

  static PriceModifier percentage(Percentage percentage) {
    return new PercentageModifier(percentage);
  }

  static PriceModifier fixed(Money amount) {
    return new FixedAmountModifier(amount);
  }

  /** Adjusts {@code base} by a {@link Percentage} of it (e.g. "10% off"). */
  record PercentageModifier(Percentage percentage) implements PriceModifier {

    public PercentageModifier {
      Validate.notNull(percentage, "percentage must not be null.");
    }

    @Override
    public Money applyTo(Money base) {
      Validate.notNull(base, "base must not be null.");
      return base.multiply(percentage.fraction());
    }
  }

  /** Adjusts {@code base} by a fixed {@link Money} amount, ignoring {@code base}'s size. */
  record FixedAmountModifier(Money amount) implements PriceModifier {

    public FixedAmountModifier {
      Validate.notNull(amount, "amount must not be null.");
    }

    @Override
    public Money applyTo(Money base) {
      return amount;
    }
  }
}
