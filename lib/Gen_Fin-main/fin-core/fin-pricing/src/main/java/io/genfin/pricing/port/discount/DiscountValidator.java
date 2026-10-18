package io.genfin.pricing.port.discount;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * One extra check the Discount Engine pipeline stage runs over a discounted {@link Price} - e.g.
 * "net amount is still not negative". Returns an empty list when nothing is wrong; the stage
 * collects every {@link CalculationIssue} rather than stopping at the first one found, mirroring
 * {@code io.genfin.pricing.port.calculation.PricingRule}.
 */
public interface DiscountValidator extends Extension {

  List<CalculationIssue> validate(Price price, PricingContext context);
}
