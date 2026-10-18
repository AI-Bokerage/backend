package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.List;

/** The full set of {@link TaxComponent}s computed for a {@link TaxableAmount}. */
public record TaxBreakdown(List<TaxComponent> components, Currency currency)
    implements ValueObject {

  public TaxBreakdown {
    components = List.copyOf(components);
  }

  public static TaxBreakdown none(Currency currency) {
    return new TaxBreakdown(List.of(), currency);
  }

  public Money totalTax() {
    return components.stream().map(TaxComponent::amount).reduce(Money.zero(currency), Money::add);
  }
}
