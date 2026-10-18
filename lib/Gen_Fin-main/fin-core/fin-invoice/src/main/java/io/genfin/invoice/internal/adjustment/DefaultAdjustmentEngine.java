package io.genfin.invoice.internal.adjustment;

import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.invoice.port.adjustment.AdjustmentEngine;
import io.genfin.money.money.Money;
import java.util.List;

public final class DefaultAdjustmentEngine implements AdjustmentEngine {

  @Override
  public Money applyAll(List<Adjustment> adjustments, Money baseAmount) {
    Money result = baseAmount;
    for (Adjustment adjustment : adjustments) {
      result = result.add(adjustment.amount());
    }
    return result;
  }
}
