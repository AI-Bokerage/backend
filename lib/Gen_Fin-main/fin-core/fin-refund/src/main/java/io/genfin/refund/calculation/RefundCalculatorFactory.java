package io.genfin.refund.calculation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.refund.port.calculation.RefundCalculator;

/**
 * Resolves the {@link RefundCalculator} registered in an {@link ExtensionRegistry}, falling back to
 * {@link RefundCalculators#standard()}.
 */
public final class RefundCalculatorFactory {

  private RefundCalculatorFactory() {}

  public static RefundCalculator from(ExtensionRegistry registry) {
    return registry.find(RefundCalculator.class).orElseGet(RefundCalculators::standard);
  }
}
