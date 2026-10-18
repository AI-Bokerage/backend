package io.genfin.refund.calculation;

import io.genfin.refund.internal.calculation.DefaultRefundCalculator;
import io.genfin.refund.port.calculation.RefundCalculator;

public final class RefundCalculators {

  private static final RefundCalculator STANDARD = new DefaultRefundCalculator();

  private RefundCalculators() {}

  public static RefundCalculator standard() {
    return STANDARD;
  }
}
