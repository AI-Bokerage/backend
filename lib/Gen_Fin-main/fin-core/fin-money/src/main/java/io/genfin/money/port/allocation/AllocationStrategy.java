package io.genfin.money.port.allocation;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * Splits a {@link Money} total into shares proportional to {@code weights}, in the same order, with
 * every minor unit accounted for — implementations must never let cents disappear or invent extra
 * ones.
 */
public interface AllocationStrategy extends Extension {

  List<Money> allocate(Money total, List<BigDecimal> weights);
}
