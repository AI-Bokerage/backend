package io.genfin.invoice.discount;

import io.genfin.invoice.internal.discount.DefaultDiscountEngine;
import io.genfin.invoice.port.discount.DiscountEngine;

public final class DiscountEngines {

  private static final DiscountEngine STANDARD = new DefaultDiscountEngine();

  private DiscountEngines() {}

  public static DiscountEngine standard() {
    return STANDARD;
  }
}
