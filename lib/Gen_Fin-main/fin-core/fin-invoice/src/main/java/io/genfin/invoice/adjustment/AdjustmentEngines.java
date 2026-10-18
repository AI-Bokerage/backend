package io.genfin.invoice.adjustment;

import io.genfin.invoice.internal.adjustment.DefaultAdjustmentEngine;
import io.genfin.invoice.port.adjustment.AdjustmentEngine;

public final class AdjustmentEngines {

  private static final AdjustmentEngine STANDARD = new DefaultAdjustmentEngine();

  private AdjustmentEngines() {}

  public static AdjustmentEngine standard() {
    return STANDARD;
  }
}
