package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * A Discount/Promotion/Coupon/Credit Engine stage's proposed change to a {@link Price}: which
 * {@link PriceModifier} it applied, why, and the resulting {@link Money} delta. Turned into a
 * {@link PriceComponent} (with a generated id) once the pipeline accepts it into a {@link
 * PriceBreakdown} - kept as its own type rather than building a {@link PriceComponent} directly so
 * a stage can express "why" before "what" gets an identity.
 */
public record PriceAdjustment(PriceType type, PriceModifier modifier, Money amount, String reason)
    implements ValueObject {

  public PriceAdjustment {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(modifier, "modifier must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notBlank(reason, "reason must not be blank.");
  }

  public static PriceAdjustment of(
      PriceType type, PriceModifier modifier, Money base, String reason) {
    Validate.notNull(modifier, "modifier must not be null.");
    return new PriceAdjustment(type, modifier, modifier.applyTo(base), reason);
  }

  public PriceComponent toComponent(String label) {
    return PriceComponent.of(type, label, amount);
  }
}
