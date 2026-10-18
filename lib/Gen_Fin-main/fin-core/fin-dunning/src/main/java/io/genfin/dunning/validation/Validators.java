package io.genfin.dunning.validation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.dunning.internal.validation.DefaultDunningValidator;
import io.genfin.dunning.internal.validation.rules.CollectionPlanValidationRule;
import io.genfin.dunning.internal.validation.rules.DunningPolicyValidationRule;
import io.genfin.dunning.internal.validation.rules.EscalationDecisionValidationRule;
import io.genfin.dunning.internal.validation.rules.ReminderPlanValidationRule;
import io.genfin.dunning.internal.validation.rules.RetryPlanValidationRule;
import io.genfin.dunning.port.validation.DunningValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link DunningValidator}s. {@link #standard()} carries every default rule; extend via
 * {@link #withRules}. Mirrors {@code io.genfin.ledger.validation.Validators} / {@code
 * io.genfin.pricing.validation.Validators}.
 */
public final class Validators {

  private Validators() {}

  public static List<ValidationRule> defaultRules() {
    return List.of(
        new DunningPolicyValidationRule(),
        new RetryPlanValidationRule(),
        new ReminderPlanValidationRule(),
        new EscalationDecisionValidationRule(),
        new CollectionPlanValidationRule());
  }

  public static DunningValidator standard() {
    return new DefaultDunningValidator(defaultRules());
  }

  public static DunningValidator withRules(List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules());
    combined.addAll(additionalRules);
    return new DefaultDunningValidator(combined);
  }

  public static DunningValidator from(ExtensionRegistry registry) {
    return registry.find(DunningValidator.class).orElseGet(Validators::standard);
  }
}
