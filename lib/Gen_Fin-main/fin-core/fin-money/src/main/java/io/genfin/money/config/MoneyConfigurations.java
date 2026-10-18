package io.genfin.money.config;

import io.genfin.money.allocation.AllocationStrategies;
import io.genfin.money.currency.Currency;
import io.genfin.money.format.FormatStyle;
import io.genfin.money.format.FormattingContext;
import io.genfin.money.format.FormattingPolicy;
import java.util.Locale;

/**
 * The "ConfigurationFactory" entry point: builds a {@link MoneyConfiguration} with sane, explicit
 * defaults.
 */
public final class MoneyConfigurations {

  private MoneyConfigurations() {}

  public static MoneyConfiguration standard(Currency defaultCurrency) {
    return MoneyConfiguration.builder()
        .defaultCurrency(defaultCurrency)
        .formattingPolicy(
            FormattingPolicy.of(
                FormattingContext.of(Locale.getDefault(), FormatStyle.SYMBOL_FIRST)))
        .defaultAllocation(AllocationStrategies.largestRemainder())
        .build();
  }
}
