package io.genfin.pricing.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * One extra check the Pricing Validation stage runs over a {@link CalculationResult} - e.g. "net
 * amount is not negative", "a required discount was applied". Returns an empty list when nothing is
 * wrong; the stage composes every registered rule and collects every {@link CalculationIssue}
 * rather than stopping at the first one found, mirroring {@code
 * io.genfin.ledger.port.posting.PostingRule}.
 *
 * <p>This is the basic shape only - the detailed Commercial Rule Engine ({@code
 * io.genfin.pricing.rule}) arrives in a later stage; for now applications may register their own
 * rules directly via this SPI.
 */
public interface PricingRule extends Extension {

  List<CalculationIssue> evaluate(CalculationResult result, PricingContext context);
}
