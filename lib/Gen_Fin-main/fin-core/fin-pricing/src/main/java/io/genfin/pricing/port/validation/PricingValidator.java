package io.genfin.pricing.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationResult;

/**
 * Validates a {@link CalculationResult} - a composable, SPI-replaceable check made up of {@link
 * io.genfin.pricing.validation.ValidationRule}s. Distinct from {@code
 * io.genfin.pricing.port.rule.CommercialRuleEngine}: that engine enforces application-supplied
 * commercial thresholds (min price, max discount, stacking limits); this validator enforces
 * fin-pricing's own structural invariants (price/discount/coupon/promotion/credit/configuration
 * shape) and folds in whatever {@code RuleResult}s the caller already collected from a commercial
 * rule run.
 */
public interface PricingValidator extends Extension {

  ValidationResult validate(CalculationResult result, ValidationContext context);
}
