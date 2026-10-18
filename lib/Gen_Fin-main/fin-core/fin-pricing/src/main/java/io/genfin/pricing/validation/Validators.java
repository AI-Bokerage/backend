package io.genfin.pricing.validation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.validation.DefaultPricingValidator;
import io.genfin.pricing.internal.validation.rules.ConfigurationValidationRule;
import io.genfin.pricing.internal.validation.rules.CouponValidationRule;
import io.genfin.pricing.internal.validation.rules.CreditValidationRule;
import io.genfin.pricing.internal.validation.rules.DiscountValidationRule;
import io.genfin.pricing.internal.validation.rules.PriceValidationRule;
import io.genfin.pricing.internal.validation.rules.PromotionValidationRule;
import io.genfin.pricing.internal.validation.rules.RuleValidationRule;
import io.genfin.pricing.port.validation.PricingValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link PricingValidator}s. {@link #standard()} carries every default rule; extend via
 * {@link #withRules}. Mirrors {@code io.genfin.ledger.validation.Validators}.
 */
public final class Validators {

  private Validators() {}

  public static List<ValidationRule> defaultRules() {
    return List.of(
        new PriceValidationRule(),
        new DiscountValidationRule(),
        new PromotionValidationRule(),
        new CouponValidationRule(),
        new CreditValidationRule(),
        new RuleValidationRule(),
        new ConfigurationValidationRule());
  }

  public static PricingValidator standard() {
    return new DefaultPricingValidator(defaultRules());
  }

  public static PricingValidator withRules(List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules());
    combined.addAll(additionalRules);
    return new DefaultPricingValidator(combined);
  }

  public static PricingValidator from(ExtensionRegistry registry) {
    return registry.find(PricingValidator.class).orElseGet(Validators::standard);
  }
}
