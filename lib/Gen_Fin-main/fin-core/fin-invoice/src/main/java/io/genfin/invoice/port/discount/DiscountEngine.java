package io.genfin.invoice.port.discount;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.discount.Discount;
import io.genfin.money.money.Money;

/** Computes the reduction a {@link Discount} contributes against a base amount. */
public interface DiscountEngine extends Extension {

  Money apply(Discount discount, Money baseAmount);
}
