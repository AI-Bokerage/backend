package io.genfin.pricing.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.List;

/**
 * One commercial check the Commercial Rule Engine runs over a priced {@link CalculationResult} -
 * e.g. minimum price, maximum discount, coupon/promotion stacking limits, credit limit, regional or
 * partner pricing. Returns an empty list when nothing is wrong; a {@link CommercialRuleEngine}
 * composes many of these and collects every {@link RuleResult} instead of stopping at the first one
 * found. fin-pricing ships no built-in commercial rules of its own - see {@code
 * io.genfin.pricing.rule.ExampleCommercialRules} for illustrative, opt-in examples an application
 * may register.
 */
public interface CommercialRule extends Extension {

  List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext);
}
