package io.genfin.invoice.config;

import io.genfin.invoice.adjustment.AdjustmentEngines;
import io.genfin.invoice.calculation.InvoiceCalculationPolicy;
import io.genfin.invoice.discount.DiscountEngines;
import io.genfin.invoice.lifecycle.InvoiceLifecycles;
import io.genfin.invoice.numbering.InvoiceNumberGenerators;
import io.genfin.invoice.validation.InvoiceValidators;
import io.genfin.money.currency.Currency;
import io.genfin.money.format.FormatStyle;
import io.genfin.money.format.FormattingContext;
import java.util.Locale;

public final class InvoiceConfigurations {

  private InvoiceConfigurations() {}

  public static InvoiceConfiguration standard(Currency defaultCurrency) {
    return InvoiceConfiguration.builder()
        .defaultCurrency(defaultCurrency)
        .lifecycleProvider(InvoiceLifecycles.standard())
        .validator(InvoiceValidators.standard())
        .calculationPolicy(InvoiceCalculationPolicy.standard())
        .discountEngine(DiscountEngines.standard())
        .adjustmentEngine(AdjustmentEngines.standard())
        .numberGenerator(InvoiceNumberGenerators.timestamp())
        .formattingContext(FormattingContext.of(Locale.getDefault(), FormatStyle.SYMBOL_FIRST))
        .build();
  }
}
