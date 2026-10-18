package io.genfin.payment.calculation;

import io.genfin.payment.internal.calculation.DefaultPaymentCalculator;
import io.genfin.payment.port.calculation.PaymentCalculator;

public final class PaymentCalculators {

  private static final PaymentCalculator STANDARD = new DefaultPaymentCalculator();

  private PaymentCalculators() {}

  public static PaymentCalculator standard() {
    return STANDARD;
  }
}
