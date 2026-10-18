package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.config.PricingConfiguration;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.calculation.PricingRule;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.port.validation.PricingValidator;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.validation.ValidationContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The Pricing Validation stage - the second-to-last stage of the Pricing Pipeline, right before the
 * run collapses into a {@code PricingResult}. Runs every ad hoc {@link PricingRule} an application
 * registered directly, then the composable {@link PricingValidator} (price/discount/promotion
 * /coupon/credit/rule/configuration checks, see {@code io.genfin.pricing.validation.Validators}),
 * collecting every {@link CalculationIssue} from both - never short-circuits on the first one
 * found.
 */
public final class PricingValidationStage implements PricingPipelineStage {

  private final List<PricingRule> rules;
  private final PricingValidator validator;
  private final PricingConfiguration configuration;

  public PricingValidationStage(List<PricingRule> rules, PricingValidator validator) {
    this(rules, validator, null);
  }

  public PricingValidationStage(
      List<PricingRule> rules, PricingValidator validator, PricingConfiguration configuration) {
    this.rules = List.copyOf(rules);
    this.validator = Validate.notNull(validator, "validator must not be null.");
    this.configuration = configuration;
  }

  @Override
  public PricingStage stage() {
    return PricingStage.VALIDATION;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<CalculationIssue> issues = new ArrayList<>();
    for (PricingRule rule : rules) {
      issues.addAll(rule.evaluate(result, context));
    }
    ValidationContext validationContext = ValidationContext.of(context).withMinPrice(minPrice());
    issues.addAll(validator.validate(result, validationContext).issues());
    return result.addIssues(issues);
  }

  private Money minPrice() {
    return configuration == null
        ? null
        : configuration.commercialRuleConfiguration().ruleContext().minPrice().orElse(null);
  }
}
