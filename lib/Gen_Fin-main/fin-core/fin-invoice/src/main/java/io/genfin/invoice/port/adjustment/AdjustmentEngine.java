package io.genfin.invoice.port.adjustment;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.money.money.Money;
import java.util.List;

/** Folds a list of {@link Adjustment}s into a base amount. */
public interface AdjustmentEngine extends Extension {

  Money applyAll(List<Adjustment> adjustments, Money baseAmount);
}
