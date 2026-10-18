package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;

/** One line of a {@link TaxBreakdown} (e.g. "CGST" at 9%, contributing a given Money amount). */
public record TaxComponent(String code, Percentage rate, Money amount) implements ValueObject {}
