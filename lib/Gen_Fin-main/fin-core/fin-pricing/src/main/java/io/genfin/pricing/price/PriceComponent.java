package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.PriceComponentId;

/**
 * One settled contribution to a {@link PriceBreakdown} - e.g. the base price line, one discount,
 * one tax-placeholder figure. {@code amount} may be negative (a reduction, such as a discount or
 * credit) or positive (an addition, such as a fee or surcharge); {@link PriceBreakdown} simply sums
 * every component's amount to reach the net.
 */
public record PriceComponent(PriceComponentId id, PriceType type, String label, Money amount)
    implements ValueObject {

  public PriceComponent {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(type, "type must not be null.");
    Validate.notBlank(label, "label must not be blank.");
    Validate.notNull(amount, "amount must not be null.");
  }

  public static PriceComponent of(PriceType type, String label, Money amount) {
    return new PriceComponent(PriceComponentId.generate(), type, label, amount);
  }
}
