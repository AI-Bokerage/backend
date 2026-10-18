package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * The ordered set of {@link PriceComponent}s a {@link Price} was built from - one entry per Pricing
 * Pipeline stage that touched it (Base Price Resolution, Discount/Promotion/Coupon/Credit Engines,
 * Tax Placeholder, Rounding). Never empty: every {@link Price} carries at least its base component.
 */
public record PriceBreakdown(List<PriceComponent> components) implements ValueObject {

  public PriceBreakdown {
    Validate.notNull(components, "components must not be null.");
    Validate.argument(!components.isEmpty(), "components must not be empty.");
    components = List.copyOf(components);
  }

  public static PriceBreakdown of(PriceComponent... components) {
    return new PriceBreakdown(List.of(components));
  }

  /** The net amount across every component - what the {@link Price} actually resolves to. */
  public Money netAmount() {
    return components.stream().map(PriceComponent::amount).reduce(Money::add).orElseThrow();
  }

  public List<PriceComponent> componentsOf(PriceType type) {
    Validate.notNull(type, "type must not be null.");
    return components.stream().filter(component -> component.type() == type).toList();
  }

  /** The summed amount of every component of {@code type}, or zero if none are present. */
  public Money amountOf(PriceType type) {
    Currency currency = components.get(0).amount().currency();
    return componentsOf(type).stream()
        .map(PriceComponent::amount)
        .reduce(Money::add)
        .orElse(Money.zero(currency));
  }
}
