package io.genfin.invoice.calculation;

import io.genfin.invoice.adjustment.AdjustmentEngines;
import io.genfin.invoice.discount.DiscountEngines;
import io.genfin.invoice.port.adjustment.AdjustmentEngine;
import io.genfin.invoice.port.discount.DiscountEngine;

/**
 * The engines an {@code InvoiceCalculator} consults — swap either without touching the calculator.
 */
public record InvoiceCalculationPolicy(
    DiscountEngine discountEngine, AdjustmentEngine adjustmentEngine) {

  public static InvoiceCalculationPolicy standard() {
    return new InvoiceCalculationPolicy(DiscountEngines.standard(), AdjustmentEngines.standard());
  }
}
