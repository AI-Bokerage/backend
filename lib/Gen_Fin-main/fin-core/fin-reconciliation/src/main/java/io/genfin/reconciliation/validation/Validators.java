package io.genfin.reconciliation.validation;

import io.genfin.reconciliation.internal.validation.DefaultReconciliationValidator;
import io.genfin.reconciliation.internal.validation.rules.EmptyReconciliationRule;
import io.genfin.reconciliation.internal.validation.rules.UnresolvedDiscrepancyRule;
import io.genfin.reconciliation.port.validation.ReconciliationValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link ReconciliationValidator}s. {@link #standard()} carries every default rule;
 * extend via {@link #withRules}.
 */
public final class Validators {

  private Validators() {}

  public static List<ValidationRule> defaultRules() {
    return List.of(new EmptyReconciliationRule(), new UnresolvedDiscrepancyRule());
  }

  public static ReconciliationValidator standard() {
    return new DefaultReconciliationValidator(defaultRules());
  }

  public static ReconciliationValidator withRules(List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules());
    combined.addAll(additionalRules);
    return new DefaultReconciliationValidator(combined);
  }
}
