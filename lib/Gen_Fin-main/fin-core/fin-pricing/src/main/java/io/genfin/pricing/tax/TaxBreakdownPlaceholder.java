package io.genfin.pricing.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import java.util.List;

/**
 * The tax-shaped {@link PriceComponent}s (always {@link PriceType#TAX}) a {@link TaxPlaceholder}
 * contributes for one line - structurally identical to {@code
 * io.genfin.pricing.price.PriceBreakdown} but named separately since it is never itself
 * jurisdiction logic, only the empty-by-default carrier a real Tax Engine fills in. {@link
 * #empty()} is the no-op default: no tax components at all.
 */
public record TaxBreakdownPlaceholder(List<PriceComponent> components) implements ValueObject {

  public TaxBreakdownPlaceholder {
    Validate.notNull(components, "components must not be null.");
    components = List.copyOf(components);
    for (PriceComponent component : components) {
      Validate.argument(
          component.type() == PriceType.TAX, "every component must be PriceType.TAX.");
    }
  }

  /** No tax estimated - fin-pricing's own default; a real Tax Engine returns something else. */
  public static TaxBreakdownPlaceholder empty() {
    return new TaxBreakdownPlaceholder(List.of());
  }

  /** The summed amount across every component, or zero {@code currency} if none are present. */
  public Money total(Currency currency) {
    Validate.notNull(currency, "currency must not be null.");
    return components.stream().map(PriceComponent::amount).reduce(Money.zero(currency), Money::add);
  }
}
