package io.genfin.pricing.validation;

import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import java.util.List;

/**
 * One composable check over a {@link CalculationResult}. A {@link
 * io.genfin.pricing.port.validation.PricingValidator} runs every registered rule and never
 * short-circuits, so a single validation pass surfaces every issue at once. Reuses {@link
 * CalculationIssue} - the same shape the rest of the Pricing Pipeline already accumulates on {@link
 * CalculationResult} - rather than introducing a parallel issue type for the same run.
 */
@FunctionalInterface
public interface ValidationRule {

  List<CalculationIssue> apply(CalculationResult result, ValidationContext context);
}
