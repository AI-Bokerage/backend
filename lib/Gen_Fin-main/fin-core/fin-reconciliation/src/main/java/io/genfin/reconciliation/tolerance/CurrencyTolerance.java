package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import java.util.Set;

/**
 * A group of currencies configuration considers equivalent for reconciliation purposes — e.g. a
 * provider settles in a local currency while the payment was taken in a presentment currency, and
 * the two are still expected to line up. Two candidates match on the currency dimension when both
 * currencies are the same, or both belong to this set.
 */
public record CurrencyTolerance(Set<Currency> equivalentCurrencies)
    implements Tolerance, ValueObject {

  public CurrencyTolerance {
    equivalentCurrencies = CollectionUtils.immutableSet(equivalentCurrencies);
    Validate.required(
        equivalentCurrencies.size() >= 2,
        "equivalentCurrencies must list at least two currencies to be meaningful.");
  }

  public static CurrencyTolerance of(Set<Currency> equivalentCurrencies) {
    return new CurrencyTolerance(equivalentCurrencies);
  }

  @Override
  public ToleranceType type() {
    return StandardToleranceType.CURRENCY;
  }
}
