package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

/** An amount subject to tax, classified under a {@link TaxCategory}. */
public record TaxableAmount(Money amount, TaxCategory category) implements ValueObject {}
